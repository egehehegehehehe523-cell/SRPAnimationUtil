package dev.srpau.mixin;
import dev.srpau.DisguiseControl;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Infected overrides call the native damage and living-update paths. */
@Mixin(targets="com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityPInfected",remap=false)
public abstract class DisguiseInfectedMixin {
    @Inject(method="func_70097_a(Lnet/minecraft/util/DamageSource;F)Z",at=@At("HEAD"),cancellable=true,remap=false)
    private void srpau$damage(DamageSource source,float amount,CallbackInfoReturnable<Boolean> ci){
        EntityLivingBase e=(EntityLivingBase)(Object)this;
        if(DisguiseControl.active(e))ci.setReturnValue(DisguiseControl.hurt(e,source,amount));
    }
    @Inject(method="func_70636_d()V",at=@At(value="INVOKE",target="Lcom/dhanantry/scapeandrunparasites/entity/ai/misc/EntityParasiteBase;func_70636_d()V",shift=At.Shift.AFTER),cancellable=true,remap=false)
    private void srpau$skipInfection(CallbackInfo ci){
        if(DisguiseControl.active((EntityLivingBase)(Object)this))ci.cancel();
    }
    @Inject(method="func_70624_b(Lnet/minecraft/entity/EntityLivingBase;)V",at=@At("HEAD"),cancellable=true,remap=false)
    private void srpau$target(EntityLivingBase target,CallbackInfo ci){
        if(target!=null&&DisguiseControl.active((EntityLivingBase)(Object)this))ci.cancel();
    }
    @Inject(method="func_70652_k(Lnet/minecraft/entity/Entity;)Z",at=@At("HEAD"),cancellable=true,remap=false)
    private void srpau$attack(net.minecraft.entity.Entity target,CallbackInfoReturnable<Boolean> ci){
        if(DisguiseControl.active((EntityLivingBase)(Object)this))ci.setReturnValue(false);
    }
    @Inject(method="func_70103_a(B)V",at=@At("HEAD"),cancellable=true,remap=false)
    private void srpau$parasiteStatus(byte value,CallbackInfo ci){
        if(DisguiseControl.active((EntityLivingBase)(Object)this)&&value>=5&&value<=52)ci.cancel();
    }
}
