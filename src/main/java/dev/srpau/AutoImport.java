package dev.srpau;

import me.ichun.mods.ichunutil.client.module.tabula.model.ModelInfo;
import me.ichun.mods.ichunutil.client.gui.window.element.Element;
import me.ichun.mods.ichunutil.client.gui.window.element.ElementToggle;
import me.ichun.mods.ichunutil.common.module.tabula.project.ProjectInfo;
import me.ichun.mods.tabula.client.gui.GuiWorkspace;
import me.ichun.mods.tabula.client.gui.window.WindowImport;
import me.ichun.mods.tabula.client.gui.window.element.ElementListTree;
import me.ichun.mods.tabula.common.Tabula;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/** Local Tabula model import. No spawned or targeted parasite is needed. */
public final class AutoImport {
    private AutoImport(){}
    @SuppressWarnings({"unchecked","rawtypes"})
    public static boolean tryImport(WindowImport window,Element element){
        if(element.id!=1 && element.id!=3)return false;
        if(!(window.workspace instanceof GuiWorkspace))return false;
        GuiWorkspace workspace=(GuiWorkspace)window.workspace;
        if(workspace.remoteSession || workspace.host!=null)return false;
        if(Minecraft.getMinecraft().world==null)return false;
        ModelInfo selected=null;
        for(ElementListTree.Tree tree:window.modelList.trees){
            if(tree.selected && tree.attachedObject instanceof ModelInfo){
                selected=(ModelInfo)tree.attachedObject;break;
            }
        }
        if(selected==null || selected.modelParent==null || selected.clz==null ||
            !EntityLivingBase.class.isAssignableFrom(selected.clz))return false;
        Class<? extends EntityLivingBase> entityClass=(Class<? extends EntityLivingBase>)selected.clz;
        ResourceLocation id=EntityList.getKey(entityClass);
        if(id==null||!id.toString().startsWith("srparasites:"))return false;
        boolean texture=true;
        for(Element control:window.elements)if(control.id==2&&control instanceof ElementToggle)
            texture=((ElementToggle)control).toggledState;
        ProjectInfo project;
        BufferedImage image;
        int imported=0;
        ClientHooks.BakeResult result;
        try{
            Class<? extends ModelBase> modelClass=selected.modelParent.getClass();
            image=texture?readTexture(selected.texture):null;
            result=ClientHooks.sampleNative(modelClass,entityClass,
                Minecraft.getMinecraft().world,image,24,120000);
            java.lang.reflect.Constructor<? extends ModelBase> ctor=modelClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            Rig rig=new Rig(ctor.newInstance());
            project=rig.createProject(rig.sample());
            project.bufferedTexture=image;
            project.metadata.add("srpau:entity:"+id);
            for(Capture clip:result.clips){
                if(!clip.matches(project))continue;
                clip.append(project);
                imported++;
            }
        }catch(Exception|LinkageError ex){
            LogManager.getLogger("SRPAnimationUtil").warn("Automatic animation import failed for "+id+"; using normal Tabula import",ex);
            ClientHooks.message("Native animation import failed for "+id+"; using standard model importer. See latest.log.");
            return false;
        }
        // Only commit the prepared project after successful construction.
        // Create a new project even if stock geometry merge was selected: do not overwrite authored work.
        String json=project.getAsJson();
        workspace.openNextNewProject=true;
        Tabula.proxy.tickHandlerClient.mainframe.overrideProject("",json,image);
        window.workspace.removeWindow(window,true);
        ClientHooks.message("Imported "+id+" with "+imported+" editable native animations. "+result.summary());
        if(imported==0)ClientHooks.message("No native clips sampled. F7/F9 remain available for live recording.");
        return true;
    }
    private static BufferedImage readTexture(ResourceLocation location){
        if(location==null)return null;
        try(InputStream in=Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream()){
            return ImageIO.read(in);
        }catch(Exception e){
            LogManager.getLogger("SRPAnimationUtil").warn("Could not import texture "+location,e);
            return null;
        }
    }
}
