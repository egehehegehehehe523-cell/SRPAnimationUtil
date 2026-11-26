package dev.srpau.mixin;

import dev.srpau.AdventurerRuntime;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** SRP is distributed with SRG method names. Redirect only its single root draw,
 * leaving the original setRotationAngles, renderer and armour layer untouched. */
@Pseudo
@Mixin(targets="com.dhanantry.scapeandrunparasites.client.model.entity.infected.ModelInfPlayer",remap=false)
public abstract class AdventurerRenderMixin {
    @Unique private AdventurerRuntime srpau$runtime;
    @Redirect(method="func_78088_a",at=@At(value="INVOKE",target="Lnet/minecraft/client/model/ModelRenderer;func_78785_a(F)V",remap=false),remap=false,require=0)
    private void srpau$draw(ModelRenderer root,float scale,Entity entity,float swing,float amount,float age,float yaw,float pitch,float nativeScale){
        if(srpau$runtime==null)srpau$runtime=new AdventurerRuntime();
        srpau$runtime.render((ModelBase)(Object)this,root,scale,entity,amount);
    }
}
