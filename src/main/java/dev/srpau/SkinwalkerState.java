package dev.srpau;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Persistent, server-owned state. Only the render-relevant fields are sent. */
final class SkinwalkerState {
    static final String KEY="srpau_skinwalker";
    static NBTTagCompound data(EntityLivingBase e){
        NBTTagCompound persistent=e.getEntityData();
        if(!persistent.hasKey(KEY,10))persistent.setTag(KEY,new NBTTagCompound());
        return persistent.getCompoundTag(KEY);
    }
    static boolean disguised(EntityLivingBase e){return data(e).getBoolean("Disguised");}
    static boolean ready(EntityLivingBase e){return data(e).hasKey("Identity",10);}
    static void observe(EntityLivingBase e,EntityLivingBase target){
        NBTTagCompound d=data(e);
        if(ready(e))return;
        if(!d.hasKey("Seen",10))d.setTag("Seen",new NBTTagCompound());
        NBTTagCompound seen=d.getCompoundTag("Seen");
        String id=target.getUniqueID().toString();
        // Bound the saved map. Existing progress is always retained.
        if(!seen.hasKey(id)&&seen.getKeySet().size()>=64)return;
        int count=SeenProgress.advance(seen.getInteger(id));
        seen.setInteger(id,count);
        d.setInteger("SeenTicks",count);
        if(count<SeenProgress.REQUIRED)return;
        NBTTagCompound identity=snapshot(target);
        d.setTag("Identity",identity);
        org.apache.logging.log4j.LogManager.getLogger("SRPAnimationUtil").info("Adventurer learned disguise for "+identity.getString("Name")+" after "+count+" peek ticks.");
    }
    private static NBTTagCompound snapshot(EntityLivingBase target){
        NBTTagCompound identity=new NBTTagCompound();
        identity.setString("UUID",target.getUniqueID().toString());
        identity.setString("Name",target instanceof EntityPlayer?target.getName():"Steve");
        identity.setBoolean("Stand",!(target instanceof EntityPlayer));
        identity.setBoolean("LeftHand",target.getPrimaryHand()==net.minecraft.util.EnumHandSide.LEFT);
        NBTTagList hotbar=new NBTTagList(),gear=new NBTTagList();
        int flags=127,selected=0;
        if(target instanceof EntityPlayer){
            EntityPlayer player=(EntityPlayer)target;
            identity.setTag("Profile",NBTUtil.writeGameProfile(new NBTTagCompound(),player.getGameProfile()));
            flags=0;
            for(EnumPlayerModelParts part:EnumPlayerModelParts.values())if(player.isWearing(part))flags|=part.getPartMask();
            selected=player.inventory.currentItem;
            for(int i=0;i<9;i++){
                ItemStack stack=player.inventory.getStackInSlot(i);
                if(!stack.isEmpty()){
                    NBTTagCompound entry=stack.writeToNBT(new NBTTagCompound());
                    entry.setInteger("Slot",i);hotbar.appendTag(entry);
                }
            }
        }else{
            ItemStack main=target.getHeldItemMainhand();
            if(!main.isEmpty()){
                NBTTagCompound entry=main.writeToNBT(new NBTTagCompound());
                entry.setInteger("Slot",0);hotbar.appendTag(entry);
            }
        }
        for(EntityEquipmentSlot slot:EntityEquipmentSlot.values()){
            if(slot==EntityEquipmentSlot.MAINHAND)continue;
            ItemStack item=target.getItemStackFromSlot(slot);
            if(item.isEmpty())continue;
            NBTTagCompound entry=new NBTTagCompound();
            entry.setString("Slot",slot.name());entry.setTag("Item",item.writeToNBT(new NBTTagCompound()));
            gear.appendTag(entry);
        }
        identity.setInteger("ModelFlags",flags);identity.setInteger("Selected",Math.max(0,Math.min(8,selected)));
        identity.setTag("Hotbar",hotbar);identity.setTag("Gear",gear);
        return identity;
    }
    static void refreshIdentity(EntityLivingBase e,EntityLivingBase target){
        if(target!=null&&target.isEntityAlive()&&ready(e)&&!disguised(e)&&
            data(e).getCompoundTag("Identity").getString("UUID").equals(target.getUniqueID().toString()))
            data(e).setTag("Identity",snapshot(target));
    }
    static int selected(EntityLivingBase e){return data(e).getCompoundTag("Identity").getInteger("Selected");}
    static void select(EntityLivingBase e,int slot){
        NBTTagCompound id=data(e).getCompoundTag("Identity");
        slot=Math.max(0,Math.min(8,slot));
        if(id.getInteger("Selected")!=slot){id.setInteger("Selected",slot);SkinwalkerNetwork.sync(e);}
    }
    static NBTTagCompound visual(EntityLivingBase e){
        NBTTagCompound d=data(e),out=new NBTTagCompound();
        out.setBoolean("Peek",d.getBoolean("Peek"));
        if(d.getBoolean("Peek"))out.setFloat("PeekYaw",d.getFloat("PeekYaw"));
        out.setBoolean("Disguised",d.getBoolean("Disguised"));
        if(d.getBoolean("Disguised")){out.setTag("Identity",d.getCompoundTag("Identity").copy());out.setFloat("Health",DisguiseControl.health(e));out.setInteger("HurtSerial",d.getInteger("HurtSerial"));}
        return out;
    }
    static void peek(EntityLivingBase e,boolean value){peek(e,value,0);}
    static void peek(EntityLivingBase e,boolean value,float yaw){
        NBTTagCompound d=data(e);
        if(d.getBoolean("Peek")==value&&(!value||d.getFloat("PeekYaw")==yaw))return;
        d.setBoolean("Peek",value);
        if(value)d.setFloat("PeekYaw",yaw);
        SkinwalkerNetwork.sync(e);
    }
    static void disguise(EntityLivingBase e){
        NBTTagCompound d=data(e);if(!ready(e)||disguised(e))return;
        d.setBoolean("Peek",false);d.setBoolean("Disguised",true);
        d.setFloat("DisguiseHealth",DisguiseVitals.MAX);d.setInteger("RegenDelay",DisguiseVitals.REGEN_DELAY);d.setInteger("HurtCooldown",0);d.setFloat("LastHit",0);
        SkinwalkerNetwork.sync(e);
    }
}
