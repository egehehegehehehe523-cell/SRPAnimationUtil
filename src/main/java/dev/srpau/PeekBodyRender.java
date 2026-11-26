package dev.srpau;

import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Entity yaw is sent by vanilla tracking; renderYawOffset is client-derived.
 * Keep the latter aligned with the server's fixed cover-facing yaw for peek.
 * The head remains free to follow its own synchronized look direction. */
public final class PeekBodyRender {
    @SubscribeEvent
    public void before(RenderLivingEvent.Pre<?> event){
        EntityLivingBase e=event.getEntity();
        if(SkinwalkerAI.TYPE.equals(e.getClass().getName())&&DisguiseRender.peeking(e)){
            e.renderYawOffset=e.rotationYaw;
            e.prevRenderYawOffset=e.prevRotationYaw;
        }
    }
}
