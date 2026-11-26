package dev.srpau;

import net.minecraft.entity.*;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** The disguise is a separate regenerating pool; no native damage effects,
 * adaptation, drops or death code are invoked for a disguised hit. */
public final class DisguiseControl {
    public static boolean active(EntityLivingBase e){
        return SkinwalkerAI.TYPE.equals(e.getClass().getName())&&
            (e.world.isRemote?ClientBootstrap.disguised(e):SkinwalkerState.disguised(e));
    }
    static float health(EntityLivingBase e){
        NBTTagCompound d=SkinwalkerState.data(e);
        return d.hasKey("DisguiseHealth")?d.getFloat("DisguiseHealth"):DisguiseVitals.MAX;
    }
    public static boolean hurt(EntityLivingBase e,DamageSource source,float amount){
        if(e.world.isRemote||e.isEntityInvulnerable(source))return false;
        NBTTagCompound d=SkinwalkerState.data(e);
        int cd=d.getInteger("HurtCooldown");
        float effective=DisguiseVitals.hit(amount,cd,d.getFloat("LastHit"));
        if(effective<=0)return false;
        if(cd<=0)d.setInteger("HurtCooldown",DisguiseVitals.HURT_WINDOW);
        d.setFloat("LastHit",amount);d.setInteger("RegenDelay",DisguiseVitals.REGEN_DELAY);
        float remaining=DisguiseVitals.damage(health(e),effective);
        d.setFloat("DisguiseHealth",remaining);
        d.setInteger("HurtSerial",d.getInteger("HurtSerial")+1);
        e.world.playSound(null,e.posX,e.posY,e.posZ,SoundEvents.ENTITY_PLAYER_HURT,SoundCategory.PLAYERS,1F,1F);
        Entity attacker=source.getImmediateSource();
        if(attacker!=null&&cd<=0){
            double x=attacker.posX-e.posX,z=attacker.posZ-e.posZ;
            if(x*x+z*z>1e-6)e.knockBack(attacker,.4F,x,z);
        }
        if(remaining<=0){
            // Consume the shell, not the parasite's real health. Require a
            // new observation cycle instead of immediately disguising again.
            d.setBoolean("Disguised",false);d.setBoolean("Peek",false);
            d.removeTag("Identity");d.removeTag("Seen");d.setInteger("SeenTicks",0);
            d.setBoolean("JustRevealed",true);
            e.setSneaking(false);e.setSprinting(false);
        }
        SkinwalkerNetwork.sync(e);return true;
    }
    @SubscribeEvent public void tick(LivingEvent.LivingUpdateEvent event){
        EntityLivingBase e=event.getEntityLiving();
        if(e.world.isRemote||!active(e))return;
        NBTTagCompound d=SkinwalkerState.data(e);
        int cd=d.getInteger("HurtCooldown");if(cd>0)d.setInteger("HurtCooldown",cd-1);
        int delay=d.getInteger("RegenDelay");
        if(delay>0){d.setInteger("RegenDelay",delay-1);return;}
        float hp=health(e);
        if(hp<DisguiseVitals.MAX){
            d.setFloat("DisguiseHealth",DisguiseVitals.heal(hp));
            d.setInteger("RegenDelay",DisguiseVitals.REGEN_INTERVAL);
            SkinwalkerNetwork.sync(e);
        }
    }
}
