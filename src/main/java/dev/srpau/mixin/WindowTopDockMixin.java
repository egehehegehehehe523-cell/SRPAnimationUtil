package dev.srpau.mixin;

import dev.srpau.BoneEditorEvents;
import me.ichun.mods.ichunutil.client.gui.window.IWorkspace;
import me.ichun.mods.ichunutil.client.gui.window.element.Element;
import dev.srpau.AnimateDockButton;
import me.ichun.mods.tabula.client.gui.GuiWorkspace;
import me.ichun.mods.tabula.client.gui.window.WindowTopDock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=WindowTopDock.class,remap=false)
public abstract class WindowTopDockMixin {
    private static final int SRPAU_ANIMATE=7912;
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void srpau$addButton(IWorkspace workspace,int w,int h,CallbackInfo ci){
        WindowTopDock dock=(WindowTopDock)(Object)this;
        if(!(workspace instanceof GuiWorkspace))return;
        GuiWorkspace gui=(GuiWorkspace)workspace;
        if(gui.remoteSession || gui.host!=null)return;
        AnimateDockButton button=new AnimateDockButton(dock,SRPAU_ANIMATE);
        dock.elements.add(button);
        button.resized();
    }
    @Inject(method="elementTriggered",at=@At("HEAD"),cancellable=true,remap=false)
    private void srpau$open(Element element,CallbackInfo ci){
        if(element.id==SRPAU_ANIMATE){
            WindowTopDock dock=(WindowTopDock)(Object)this;
            BoneEditorEvents.open((GuiWorkspace)dock.workspace);
            ci.cancel();
        }
    }
}
