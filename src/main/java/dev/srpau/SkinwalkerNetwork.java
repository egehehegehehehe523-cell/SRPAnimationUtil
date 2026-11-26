package dev.srpau;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import java.util.UUID;

public final class SkinwalkerNetwork {
    private static final SimpleNetworkWrapper CHANNEL=NetworkRegistry.INSTANCE.newSimpleChannel("srpau_state");
    static void init(){CHANNEL.registerMessage(Handler.class,Message.class,0,Side.CLIENT);}
    static void sync(EntityLivingBase e){
        if(!e.world.isRemote)CHANNEL.sendToAllTracking(new Message(e),e);
    }
    @SubscribeEvent public void track(PlayerEvent.StartTracking event){
        if(event.getEntityPlayer() instanceof EntityPlayerMP&&event.getTarget() instanceof EntityLivingBase&&
            SkinwalkerAI.TYPE.equals(event.getTarget().getClass().getName()))
            CHANNEL.sendTo(new Message((EntityLivingBase)event.getTarget()),(EntityPlayerMP)event.getEntityPlayer());
    }
    public static final class Message implements IMessage {
        UUID id;NBTTagCompound state;int dimension;
        public Message(){}
        Message(EntityLivingBase e){id=e.getUniqueID();dimension=e.dimension;state=SkinwalkerState.visual(e);}
        public void toBytes(ByteBuf buf){buf.writeLong(id.getMostSignificantBits());buf.writeLong(id.getLeastSignificantBits());buf.writeInt(dimension);ByteBufUtils.writeTag(buf,state);}
        public void fromBytes(ByteBuf buf){id=new UUID(buf.readLong(),buf.readLong());dimension=buf.readInt();state=ByteBufUtils.readTag(buf);}
    }
    public static final class Handler implements IMessageHandler<Message,IMessage>{
        public IMessage onMessage(Message message,MessageContext context){
            if(context.side==Side.CLIENT)ClientBootstrap.receive(message);
            return null;
        }
    }
}
