package dev.srpau.mixin;
import dev.srpau.DisguiseControl;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase",remap=false)
public abstract class DisguiseParasiteMixin extends EntityMob {
    protected DisguiseParasiteMixin(World world){super(world);}
    @Inject(method="func_70636_d()V",at=@At("HEAD"),cancellable=true,remap=false)
    private void srpau$vanillaLiving(CallbackInfo ci){
        if(DisguiseControl.active(this)){
            // Direct parent, not virtual dispatch into SRP again.
            super.onLivingUpdate();ci.cancel();
        }
    }
    @Inject(method="particleStatus(B)V",at=@At("HEAD"),cancellable=true,remap=false)
    private void srpau$particles(byte status,CallbackInfo ci){
        if(DisguiseControl.active(this))ci.cancel();
    }
}
