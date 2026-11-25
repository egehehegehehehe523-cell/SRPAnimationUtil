package dev.srpau;

import me.ichun.mods.tabula.client.gui.GuiWorkspace;
import me.ichun.mods.ichunutil.common.module.tabula.project.ProjectInfo;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.input.Keyboard;

/** Keep the original GuiWorkspace alive: its onGuiClosed shuts down the mainframe. */
public final class BoneEditorEvents {
    private static BoneEditor active;
    public static void open(GuiWorkspace workspace){
        if(active!=null || workspace.remoteSession || workspace.host!=null)return;
        ProjectInfo project=workspace.getOpenProject();
        if(project==null || project.metadata==null || project.metadata.stream().noneMatch(s->s.startsWith("srpau:rig:"))){
            ClientHooks.message("Import an SRP rig before opening Animate.");
            return;
        }
        try{active=new BoneEditor(workspace,project);}
        catch(Exception|LinkageError e){LogManager.getLogger("SRPAnimationUtil").warn("Cannot open bone editor",e);ClientHooks.message("Cannot open bone editor; see latest.log.");}
    }
    static void close(){active=null;}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void draw(GuiScreenEvent.DrawScreenEvent.Pre e){
        if(active==null || e.getGui()!=active.workspace)return;
        e.setCanceled(true);
        try{active.draw(e.getMouseX(),e.getMouseY(),e.getRenderPartialTicks());}
        catch(Exception|LinkageError ex){
            LogManager.getLogger("SRPAnimationUtil").error("Bone editor preview failed; returning to Tabula",ex);
            close();
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void mouse(GuiScreenEvent.MouseInputEvent.Pre e){
        if(active!=null && e.getGui()==active.workspace)e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void keyboard(GuiScreenEvent.KeyboardInputEvent.Pre e){
        if(active==null || e.getGui()!=active.workspace)return;
        e.setCanceled(true);
        if(Keyboard.getEventKeyState())active.key(Keyboard.getEventCharacter(),Keyboard.getEventKey());
    }
    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END || active==null)return;
        if(Minecraft.getMinecraft().currentScreen!=active.workspace){close();return;}
        active.tick();
    }
}
