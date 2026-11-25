package dev.srpau;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid="srpanimationutil", name="SRP Animation Util", version="0.1.1",
    clientSideOnly=true, acceptedMinecraftVersions="[1.12.2]",
    acceptableRemoteVersions="*", dependencies="required-after:tabula;required-after:ichunutil;required-after:srparasites")
public final class SRPAnimationUtil {
    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        ClientHooks hooks=new ClientHooks();
        MinecraftForge.EVENT_BUS.register(hooks);
    }
}
