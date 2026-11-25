package dev.srpau.mixin;

import dev.srpau.AutoImport;
import me.ichun.mods.ichunutil.client.gui.window.element.Element;
import me.ichun.mods.tabula.client.gui.window.WindowImport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=WindowImport.class,remap=false)
public abstract class WindowImportMixin {
    @Inject(method="elementTriggered",at=@At("HEAD"),cancellable=true,remap=false)
    private void srpau$importAnimations(Element element, CallbackInfo ci){
        if(AutoImport.tryImport((WindowImport)(Object)this,element))ci.cancel();
    }
}
