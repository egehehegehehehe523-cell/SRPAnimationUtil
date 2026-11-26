package dev.srpau;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.EnumHandSide;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.*;

/** A detached player rendered instead of, not inside, the parasite renderer.
 * No world spawn, tab-list entry, real inventory, or server player identity. */
public final class DisguiseRender {
    private static final Map<UUID,NBTTagCompound> STATES=new LinkedHashMap<UUID,NBTTagCompound>(64,.75F,true){
        @Override protected boolean removeEldestEntry(Map.Entry<UUID,NBTTagCompound> e){return size()>2048;}
    };
    private static final Map<EntityLivingBase,Proxy> PROXIES=new WeakHashMap<>();
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
    private static final class Proxy extends EntityOtherPlayerMP{
        final NetworkPlayerInfo fallback;
        final boolean stand;
        int lastCapeTick=-1;
        Proxy(WorldClient world,GameProfile profile,boolean stand){
            super(world,profile);this.stand=stand;fallback=stand?null:new NetworkPlayerInfo(profile);
        }
        @Override protected NetworkPlayerInfo getPlayerInfo(){
            if(stand)return null;
            NetworkPlayerInfo live=super.getPlayerInfo();
            return live==null?fallback:live;
        }
        @Override public boolean isSpectator(){return false;}
        @Override public boolean isCreative(){return false;}
        void appearance(NBTTagCompound id){
            getDataManager().set(PLAYER_MODEL_FLAG,(byte)(id.hasKey("ModelFlags")?id.getInteger("ModelFlags"):127));
            setPrimaryHand(id.getBoolean("LeftHand")?EnumHandSide.LEFT:EnumHandSide.RIGHT);
        }
    }
    private static Proxy proxy(EntityLivingBase e,NBTTagCompound id){
        Proxy p=PROXIES.get(e);
        if(p!=null){p.inventory.currentItem=Math.max(0,Math.min(8,id.getInteger("Selected")));return p;}
        boolean stand=id.getBoolean("Stand");
        UUID uuid=stand?STEVE:UUID.fromString(id.getString("UUID"));
        GameProfile profile=id.hasKey("Profile",10)&&!stand?NBTUtil.readGameProfileFromNBT(id.getCompoundTag("Profile")):null;
        if(profile==null)profile=new GameProfile(uuid,id.getString("Name"));
        p=new Proxy((WorldClient)e.world,profile,stand);
        p.appearance(id);
        NBTTagList bar=id.getTagList("Hotbar",10);
        for(int i=0;i<bar.tagCount();i++){
            NBTTagCompound entry=bar.getCompoundTagAt(i);
            int slot=entry.getInteger("Slot");if(slot>=0&&slot<9)p.inventory.setInventorySlotContents(slot,new ItemStack(entry));
        }
        p.inventory.currentItem=Math.max(0,Math.min(8,id.getInteger("Selected")));
        NBTTagList gear=id.getTagList("Gear",10);
        for(int i=0;i<gear.tagCount();i++){
            NBTTagCompound entry=gear.getCompoundTagAt(i);
            if(entry.hasKey("Item",10)){
                EntityEquipmentSlot slot=EntityEquipmentSlot.valueOf(entry.getString("Slot"));
                // Legacy saves had only a main-hand item. Preserve it.
                if(slot!=EntityEquipmentSlot.MAINHAND||!id.hasKey("Hotbar",9))
                    p.setItemStackToSlot(slot,new ItemStack(entry.getCompoundTag("Item")));
            }
        }
        PROXIES.put(e,p);return p;
    }
    private static void update(EntityLivingBase e,Proxy p){
        p.setPosition(e.posX,e.posY,e.posZ);
        p.prevPosX=e.prevPosX;p.prevPosY=e.prevPosY;p.prevPosZ=e.prevPosZ;
        p.lastTickPosX=e.lastTickPosX;p.lastTickPosY=e.lastTickPosY;p.lastTickPosZ=e.lastTickPosZ;
        p.rotationYaw=e.rotationYaw;p.prevRotationYaw=e.prevRotationYaw;
        p.rotationYawHead=e.rotationYawHead;p.prevRotationYawHead=e.prevRotationYawHead;
        p.renderYawOffset=e.renderYawOffset;p.prevRenderYawOffset=e.prevRenderYawOffset;
        p.rotationPitch=e.rotationPitch;p.prevRotationPitch=e.prevRotationPitch;
        p.limbSwing=e.limbSwing;p.limbSwingAmount=e.limbSwingAmount;p.prevLimbSwingAmount=e.prevLimbSwingAmount;
        p.ticksExisted=e.ticksExisted;p.onGround=e.onGround;p.isAirBorne=e.isAirBorne;
        p.motionX=e.motionX;p.motionY=e.motionY;p.motionZ=e.motionZ;p.fallDistance=e.fallDistance;
        p.setSneaking(e.isSneaking());p.setSprinting(e.isSprinting());
        p.setInvisible(e.isInvisible());p.setGlowing(e.isGlowing());
        p.swingProgress=e.swingProgress;p.prevSwingProgress=e.prevSwingProgress;
        p.isSwingInProgress=e.isSwingInProgress;p.swingProgressInt=e.swingProgressInt;
        p.hurtTime=e.hurtTime;p.deathTime=e.deathTime;
        // Cape interpolation expects the chasing position history. Follow the
        // source rather than letting an unspawned actor retain (0,0,0).
        if(p.lastCapeTick!=e.ticksExisted){p.prevChasingPosX=p.chasingPosX;p.prevChasingPosY=p.chasingPosY;p.prevChasingPosZ=p.chasingPosZ;}
        if(p.lastCapeTick<0||Math.abs(p.chasingPosX-e.posX)>10||Math.abs(p.chasingPosY-e.posY)>10||Math.abs(p.chasingPosZ-e.posZ)>10){
            p.prevChasingPosX=p.chasingPosX=e.posX;p.prevChasingPosY=p.chasingPosY=e.posY;p.prevChasingPosZ=p.chasingPosZ=e.posZ;
        }else if(p.lastCapeTick!=e.ticksExisted){
            p.chasingPosX+=(e.posX-p.chasingPosX)*.25;p.chasingPosY+=(e.posY-p.chasingPosY)*.25;p.chasingPosZ+=(e.posZ-p.chasingPosZ)*.25;
        }
        p.lastCapeTick=e.ticksExisted;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Iterator<Map.Entry<EntityLivingBase,Proxy>> it=PROXIES.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry<EntityLivingBase,Proxy> entry=it.next();EntityLivingBase e=entry.getKey();
            if(e==null||e.isDead||e.world!=Minecraft.getMinecraft().world){it.remove();continue;}
            update(e,entry.getValue());
        }
    }
    public static Entity substitute(Entity original){
        if(!(original instanceof EntityLivingBase)||!SkinwalkerAI.TYPE.equals(original.getClass().getName()))return original;
        EntityLivingBase e=(EntityLivingBase)original;NBTTagCompound s=STATES.get(e.getUniqueID());
        if(s==null||!s.getBoolean("Disguised"))return original;
        Proxy p=proxy(e,s.getCompoundTag("Identity"));update(e,p);return p;
    }
}
