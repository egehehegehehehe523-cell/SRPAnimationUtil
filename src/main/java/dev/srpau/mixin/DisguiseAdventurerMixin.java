package dev.srpau.mixin;
import dev.srpau.DisguiseControl;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.init.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(targets="com.dhanantry.scapeandrunparasites.entity.monster.infected.EntityInfPlayer",remap=false)
public abstract class DisguiseAdventurerMixin {
    private boolean srpau$active(){return DisguiseControl.active((EntityLivingBase)(Object)this);}
    @Inject(method="func_70636_d()V",at=@At(value="INVOKE",target="Lcom/dhanantry/scapeandrunparasites/entity/ai/misc/EntityPInfected;func_70636_d()V",shift=At.Shift.AFTER),cancellable=true,remap=false)
    private void srpau$skipMelt(CallbackInfo ci){if(srpau$active())ci.cancel();}
    @Inject(method={"melt()V","melting()V","selfExplode()V"},at=@At("HEAD"),cancellable=true,remap=false)
    private void srpau$preventTransform(CallbackInfo ci){if(srpau$active())ci.cancel();}
    @Inject(method="func_184639_G()Lnet/minecraft/util/SoundEvent;",at=@At("HEAD"),cancellable=true,remap=false)
    private void srpau$ambient(CallbackInfoReturnable<SoundEvent> ci){if(srpau$active())ci.setReturnValue(null);}
    @Inject(method="func_184601_bQ(Lnet/minecraft/util/DamageSource;)Lnet/minecraft/util/SoundEvent;",at=@At("HEAD"),cancellable=true,remap=false)
    private void srpau$hurt(DamageSource source,CallbackInfoReturnable<SoundEvent> ci){if(srpau$active())ci.setReturnValue(SoundEvents.ENTITY_PLAYER_HURT);}
    @Inject(method="func_184615_bR()Lnet/minecraft/util/SoundEvent;",at=@At("HEAD"),cancellable=true,remap=false)
    private void srpau$death(CallbackInfoReturnable<SoundEvent> ci){if(srpau$active())ci.setReturnValue(SoundEvents.ENTITY_PLAYER_DEATH);}
    @Inject(method="func_180429_a(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;)V",at=@At("HEAD"),cancellable=true,remap=false)
    private void srpau$steps(BlockPos pos,Block block,CallbackInfo ci){
        if(!srpau$active())return;
        EntityLivingBase e=(EntityLivingBase)(Object)this;
        SoundType sound=block.getSoundType(e.world.getBlockState(pos),e.world,pos,e);
        e.playSound(sound.getStepSound(),sound.getVolume()*.15F,sound.getPitch());ci.cancel();
    }
}
