package dev.srpau;

import me.ichun.mods.ichunutil.common.module.tabula.project.ProjectInfo;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.Animation;
import me.ichun.mods.tabula.client.gui.GuiWorkspace;
import me.ichun.mods.tabula.common.Tabula;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.apache.logging.log4j.LogManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;

/** Captures only a selected live mob, or runs native pose methods on a fresh model.
 * No combat AI or entity timers are altered. All client/render thread. */
public final class ClientHooks {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private final KeyBinding record=new KeyBinding("key.srpau.record",Keyboard.KEY_F7,"SRP Animation Util");
    private final KeyBinding bake=new KeyBinding("key.srpau.bake",Keyboard.KEY_F8,"SRP Animation Util");
    private final KeyBinding append=new KeyBinding("key.srpau.append",Keyboard.KEY_F9,"SRP Animation Util");
    private EntityLivingBase target;
    private Capture live;
    private ModelBase liveModel;
    private long started=-1;
    private boolean recording,appendDown;
    private final List<Capture> pending=new ArrayList<>();
    private static final int MAX_PENDING=24;
    private static final long MAX_PENDING_SAMPLES=240000;
    public ClientHooks(){
        ClientRegistry.registerKeyBinding(record);
        ClientRegistry.registerKeyBinding(bake);
        ClientRegistry.registerKeyBinding(append);
    }
    private static void message(String text){
        LogManager.getLogger("SRPAnimationUtil").info(text);
        if(MC.player!=null)MC.player.sendMessage(new TextComponentString("[SRP Animator] "+text));
    }
    private static boolean srp(Entity entity){
        if(!(entity instanceof EntityLivingBase))return false;
        ResourceLocation key=net.minecraft.entity.EntityList.getKey(entity);
        return key!=null && key.toString().startsWith("srparasites:");
    }
    private EntityLivingBase lookedAt(){
        Entity entity=MC.objectMouseOver==null?null:MC.objectMouseOver.entityHit;
        return srp(entity)?(EntityLivingBase)entity:null;
    }
    private void stop(String reason){
        recording=false;
        if(live!=null && live.usable()){
            pending.add(live);
            message(reason+": "+live.frames.size()+" samples ready. F9 in Tabula imports them.");
        }else message(reason+": not enough rendered samples.");
        target=null;live=null;liveModel=null;started=-1;
    }
    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        if(recording && (MC.world==null || target==null || target.world!=MC.world || target.isDead)){
            stop("Target no longer available");
        }
        if(MC.currentScreen==null && MC.world!=null){
            while(record.isPressed()){
                if(recording){stop("Recording finished");continue;}
                target=lookedAt();
                if(target==null){message("Look directly at an SRP mob first.");continue;}
                if(pending.size()>=MAX_PENDING || pendingSamples()>=MAX_PENDING_SAMPLES){message("Import pending captures into Tabula first.");continue;}
                recording=true;started=-1;
                message("Recording native pose; keep mob visible. F7 again to finish.");
            }
            while(bake.isPressed()){
                if(recording){message("Finish the recording first.");continue;}
                EntityLivingBase mob=lookedAt();
                if(mob==null){message("Look directly at an SRP mob first.");continue;}
                try{bake(mob);}catch(Exception|LinkageError ex){failed("Native bake",ex);}
            }
        }else{
            while(record.isPressed()){}while(bake.isPressed()){}
        }
    }
    @SubscribeEvent
    public void rendered(RenderLivingEvent.Post<?> e){
        if(!recording||e.getEntity()!=target)return;
        try{
            ModelBase model=e.getRenderer().getMainModel();
            if(live==null){
                liveModel=model;
                live=new Capture(new Rig(model),texture(e.getRenderer(),target),"Native capture");
                started=target.world.getTotalWorldTime();
            }else if(model!=liveModel){stop("Renderer changed model");return;}
            long elapsed=target.world.getTotalWorldTime()-started;
            if(elapsed<0||elapsed>1200){stop("Capture time limit");return;}
            if(!live.times.isEmpty() && elapsed<=live.times.get(live.times.size()-1))return;
            if(pendingSamples()+(long)(live.frames.size()+1)*live.rig.bones.size()>MAX_PENDING_SAMPLES){stop("Pending sample limit");return;}
            live.add((int)elapsed,live.rig.sample());
            if(elapsed>=1200)stop("Capture time limit");
        }catch(Exception|LinkageError ex){
            stop("Recording stopped");failed("Native capture",ex);
        }
    }
    private long pendingSamples(){
        long n=0;for(Capture c:pending)n+=(long)c.frames.size()*c.rig.bones.size();return n;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private void bake(EntityLivingBase mob) throws Exception {
        if(pending.size()>=MAX_PENDING){message("Import pending captures first.");return;}
        Render renderer=MC.getRenderManager().getEntityRenderObject(mob);
        if(!(renderer instanceof RenderLivingBase))throw new IllegalArgumentException("Unsupported renderer");
        ModelBase original=((RenderLivingBase)renderer).getMainModel();
        BufferedImage image=texture(renderer,mob);
        java.lang.reflect.Constructor<?> ctor=original.getClass().getDeclaredConstructor();
        ctor.setAccessible(true);
        SortedSet<Integer> states=NativeStates.discover(original.getClass());
        // Previews use isolated entities; if their constructor/state interface is unavailable,
        // only the live recorder is offered rather than writing to the real mob.
        List<Capture> batch=new ArrayList<>();
        long used=pendingSamples();
        int failed=0;
        boolean truncated=false;
        outer: for(int state:states){
            for(boolean idle:new boolean[]{true,false}){
                if(pending.size()+batch.size()>=MAX_PENDING){truncated=true;break outer;}
                try{
                    NativeStates.Preview preview=new NativeStates.Preview(mob);
                    ModelBase model=(ModelBase)ctor.newInstance();
                    Rig rig=new Rig(model);
                    if(used+41L*rig.bones.size()>MAX_PENDING_SAMPLES){truncated=true;break outer;}
                    String label=state==0?(idle?"Native idle sample":"Native walk sample"):
                        "Native status "+state+(idle?" (still)":" (moving)");
                    Capture clip=new Capture(rig,image,label);
                    for(int t=0;t<=40;t++){
                        preview.configure(state,idle,t);
                        float swing=idle?0F:t*.65F,amount=idle?0F:1F;
                        model.setLivingAnimations(preview.entity,swing,amount,0F);
                        model.setRotationAngles(swing,amount,t,0F,0F,.0625F,preview.entity);
                        clip.add(t,rig.sample());
                    }
                    boolean duplicate=false;
                    for(Capture other:batch)if(sameClip(clip,other)){duplicate=true;break;}
                    if(!duplicate){batch.add(clip);used+=41L*rig.bones.size();}
                }catch(Exception|LinkageError ex){
                    failed++;
                    LogManager.getLogger("SRPAnimationUtil").warn("Could not preview native status "+state,ex);
                    if(failed>=4 && batch.isEmpty())break outer;
                }
            }
        }
        pending.addAll(batch);
        message("Discovered states "+states+"; baked "+batch.size()+" distinct clips, "+failed+" failed"+
            (truncated?", sample budget reached":"")+". F9 in Tabula to import; F7 records exact live behavior.");
    }
    private static boolean sameClip(Capture a,Capture b){
        if(!a.rig.signature.equals(b.rig.signature)||!a.times.equals(b.times))return false;
        for(int f=0;f<a.frames.size();f++)for(int j=0;j<a.rig.bones.size();j++)
            for(int k=0;k<6;k++)if(Math.abs(a.frames.get(f)[j][k]-b.frames.get(f)[j][k])>1e-6)return false;
        return true;
    }
    private static BufferedImage texture(Render<?> renderer,Entity entity){
        // MCP and SRG names: reflection is done only at capture/bake start.
        try{
            Method method;
            try{method=Render.class.getDeclaredMethod("getEntityTexture",Entity.class);}
            catch(NoSuchMethodException ex){method=Render.class.getDeclaredMethod("func_110775_a",Entity.class);}
            method.setAccessible(true);
            ResourceLocation loc=(ResourceLocation)method.invoke(renderer,entity);
            if(loc!=null)try(InputStream in=MC.getResourceManager().getResource(loc).getInputStream()){return ImageIO.read(in);}
        }catch(Exception|LinkageError ex){LogManager.getLogger("SRPAnimationUtil").warn("Could not read base texture",ex);}
        return null;
    }
    @SubscribeEvent
    public void tabulaDraw(GuiScreenEvent.DrawScreenEvent.Pre e){
        if(!(e.getGui() instanceof GuiWorkspace)){appendDown=append.getKeyCode()>0 && Keyboard.isKeyDown(append.getKeyCode());return;}
        GuiWorkspace workspace=(GuiWorkspace)e.getGui();
        // 7.1.0 retained the editor; the original toggle merely shows a warning.
        Tabula.config.animationWarning=1;
        // The 7.1 timeline copies its cursor to playTime during its own drawing pass,
        // which happens after the model preview. Synchronize first when scrubbing paused clips.
        ProjectInfo active=workspace.getOpenProject();
        if(active!=null && workspace.windowAnimate!=null){
            String selection=workspace.windowAnimate.animList.selectedIdentifier;
            for(Animation animation:active.anims)if(animation.identifier.equals(selection)&&!animation.playing){
                animation.playTime=Math.max(0,Math.min(animation.getLength(),workspace.windowAnimate.timeline.getCurrentPos()));
                break;
            }
        }
        boolean down=append.getKeyCode()>0 && Keyboard.isKeyDown(append.getKeyCode());
        if(down&&!appendDown)try{importNext(workspace);}catch(Exception|LinkageError ex){failed("Tabula import",ex);}
        appendDown=down;
    }
    @SubscribeEvent
    public void tabulaOverlay(GuiScreenEvent.DrawScreenEvent.Post e){
        if(!(e.getGui() instanceof GuiWorkspace))return;
        String s="SRP Animator | "+pending.size()+" pending | "+Keyboard.getKeyName(append.getKeyCode())+": append/import";
        MC.fontRenderer.drawStringWithShadow(s,5,Math.max(2,e.getGui().height-12),0xFFAAEEFF);
    }
    private void importNext(GuiWorkspace workspace){
        if(workspace.remoteSession){message("Imports are restricted to local Tabula projects.");return;}
        if(pending.isEmpty()){message("No captures pending; use F7 or F8 in-world.");return;}
        Capture first=pending.get(0);
        ProjectInfo open=workspace.getOpenProject();
        // Clone before modification so a failed import cannot corrupt the selected editor project.
        boolean appendTo=first.matches(open);
        ProjectInfo project=appendTo?new com.google.gson.Gson().fromJson(open.getAsJson(),ProjectInfo.class):first.rig.createProject(first.frames.get(0));
        project.identifier=appendTo?open.identifier:"";
        project.bufferedTexture=appendTo?open.bufferedTexture:first.texture;
        List<Capture> imported=new ArrayList<>();
        for(Capture capture:pending)if(capture.matches(project)){
            capture.append(project);
            imported.add(capture);
        }
        if(!appendTo)workspace.openNextNewProject=true;
        Tabula.proxy.tickHandlerClient.mainframe.overrideProject(project.identifier,project.getAsJson(),project.bufferedTexture);
        pending.removeAll(imported);
        message("Imported "+imported.size()+" editable native animation(s). Save the .tbl project.");
    }
    private void failed(String stage,Throwable e){
        LogManager.getLogger("SRPAnimationUtil").warn(stage+" failed",e);
        message(stage+" failed: "+e.getClass().getSimpleName()+" (see latest.log)");
    }
}
