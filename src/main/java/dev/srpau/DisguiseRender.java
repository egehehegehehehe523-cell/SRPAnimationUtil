package dev.srpau;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.util.*;

/** Render-only player proxy; never spawned into the world or sent to server.
 * Entity remains the original SRP Adventurer, including health and collision. */
public final class DisguiseRender {
    private static final Map<UUID,NBTTagCompound> STATES=new LinkedHashMap<UUID,NBTTagCompound>(64,.75F,true){
        @Override protected boolean removeEldestEntry(Map.Entry<UUID,NBTTagCompound> e){return size()>2048;}
    };
    private static final Map<EntityLivingBase,EntityOtherPlayerMP> PROXIES=new WeakHashMap<>();
    private static final UUID STEVE=UUID.fromString("00000000-0000-0000-0000-000000000002");
    static void receive(SkinwalkerNetwork.Message m){
        Minecraft mc=Minecraft.getMinecraft();
        mc.addScheduledTask(()->{
            if(mc.world!=null&&mc.world.provider.getDimension()==m.dimension&&m.state!=null)STATES.put(m.id,m.state);
        });
    }
    static boolean peeking(EntityLivingBase e){
        NBTTagCompound s=STATES.get(e.getUniqueID());
        return s!=null?s.getBoolean("Peek"):e.isSneaking();
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event){
        if(event.getWorld().isRemote){STATES.clear();PROXIES.clear();}
    }
    private EntityOtherPlayerMP proxy(EntityLivingBase e,NBTTagCompound id){
        EntityOtherPlayerMP p=PROXIES.get(e);if(p!=null)return p;
        UUID uuid=id.getBoolean("Stand")?STEVE:UUID.fromString(id.getString("UUID"));
        p=new EntityOtherPlayerMP((WorldClient)e.world,new GameProfile(uuid,id.getString("Name")));
        NBTTagList gear=id.getTagList("Gear",10);
        for(int i=0;i<gear.tagCount();i++){
            NBTTagCompound entry=gear.getCompoundTagAt(i);
            if(entry.hasKey("Item",10))p.setItemStackToSlot(EntityEquipmentSlot.valueOf(entry.getString("Slot")),new ItemStack(entry.getCompoundTag("Item")));
        }
        PROXIES.put(e,p);return p;
    }
    @SubscribeEvent public void before(RenderLivingEvent.Pre<?> event){
        EntityLivingBase e=event.getEntity();
        if(!SkinwalkerAI.TYPE.equals(e.getClass().getName()))return;
        NBTTagCompound s=STATES.get(e.getUniqueID());
        if(s==null||!s.getBoolean("Disguised"))return;
        EntityOtherPlayerMP p=proxy(e,s.getCompoundTag("Identity"));
        p.setPosition(e.posX,e.posY,e.posZ);
        p.prevPosX=e.prevPosX;p.prevPosY=e.prevPosY;p.prevPosZ=e.prevPosZ;
        p.lastTickPosX=e.lastTickPosX;p.lastTickPosY=e.lastTickPosY;p.lastTickPosZ=e.lastTickPosZ;
        p.rotationYaw=e.rotationYaw;p.prevRotationYaw=e.prevRotationYaw;
        p.rotationYawHead=e.rotationYawHead;p.prevRotationYawHead=e.prevRotationYawHead;
        p.renderYawOffset=e.renderYawOffset;p.prevRenderYawOffset=e.prevRenderYawOffset;
        p.rotationPitch=e.rotationPitch;p.prevRotationPitch=e.prevRotationPitch;
        p.limbSwing=e.limbSwing;p.limbSwingAmount=e.limbSwingAmount;p.prevLimbSwingAmount=e.prevLimbSwingAmount;
        p.ticksExisted=e.ticksExisted;p.onGround=e.onGround;
        p.setSneaking(false);p.setSprinting(e.isSprinting());
        p.swingProgress=e.swingProgress;p.prevSwingProgress=e.prevSwingProgress;
        p.hurtTime=e.hurtTime;p.deathTime=e.deathTime;
        float pt=Minecraft.getMinecraft().getRenderPartialTicks();
        event.setCanceled(true);
        Minecraft.getMinecraft().getRenderManager().renderEntity(p,event.getX(),event.getY(),event.getZ(),e.rotationYaw,pt,false);
    }
}
