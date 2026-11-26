package dev.srpau;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
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
        NBTTagCompound identity=new NBTTagCompound();
        identity.setString("UUID",id);
        identity.setString("Name",target instanceof EntityPlayer?target.getName():"Steve");
        identity.setBoolean("Stand",!(target instanceof EntityPlayer));
        NBTTagList gear=new NBTTagList();
        for(EntityEquipmentSlot slot:EntityEquipmentSlot.values()){
            ItemStack item=target.getItemStackFromSlot(slot);
            NBTTagCompound entry=new NBTTagCompound();
            entry.setString("Slot",slot.name());
            if(!item.isEmpty())entry.setTag("Item",item.writeToNBT(new NBTTagCompound()));
            gear.appendTag(entry);
        }
        identity.setTag("Gear",gear);
        d.setTag("Identity",identity);
        org.apache.logging.log4j.LogManager.getLogger("SRPAnimationUtil").info("Adventurer learned disguise for "+identity.getString("Name")+" after "+count+" peek ticks.");
    }
    static NBTTagCompound visual(EntityLivingBase e){
        NBTTagCompound d=data(e),out=new NBTTagCompound();
        out.setBoolean("Peek",d.getBoolean("Peek"));
        out.setBoolean("Disguised",d.getBoolean("Disguised"));
        if(d.getBoolean("Disguised"))out.setTag("Identity",d.getCompoundTag("Identity").copy());
        return out;
    }
    static void peek(EntityLivingBase e,boolean value){
        NBTTagCompound d=data(e);
        if(d.getBoolean("Peek")==value)return;
        d.setBoolean("Peek",value);SkinwalkerNetwork.sync(e);
    }
    static void disguise(EntityLivingBase e){
        NBTTagCompound d=data(e);if(!ready(e)||disguised(e))return;
        d.setBoolean("Peek",false);d.setBoolean("Disguised",true);
        SkinwalkerNetwork.sync(e);
    }
}
