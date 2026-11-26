package dev.srpau.mixin;

import dev.srpau.DisguiseRender;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Substitute before renderer selection so the original SRP shadow, layers
 * and model are never drawn. No recursive parasite RenderLivingEvent. */
@Mixin(value=RenderManager.class,remap=false)
public abstract class DisguiseRenderMixin {
    @Inject(method={"func_188391_a(Lnet/minecraft/entity/Entity;DDDFFZ)V","renderEntity(Lnet/minecraft/entity/Entity;DDDFFZ)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void srpau$playerDisguise(Entity original,double x,double y,double z,float yaw,float partial,boolean outline,CallbackInfo ci){
        Entity player=DisguiseRender.substitute(original);
        if(player!=original){
            ci.cancel();
            ((RenderManager)(Object)this).renderEntity(player,x,y,z,yaw,partial,outline);
        }
    }
}
