package dev.srpau;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid="srpanimationutil", name="SRP Animation Util", version="0.1.21",
    acceptedMinecraftVersions="[1.12.2]",
    acceptableRemoteVersions="*", dependencies="after:tabula;after:ichunutil;required-after:srparasites")
public final class SRPAnimationUtil {
    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        SkinwalkerNetwork.init();
        MinecraftForge.EVENT_BUS.register(new SkinwalkerNetwork());
        MinecraftForge.EVENT_BUS.register(new SkinwalkerAI());
        if(e.getSide().isClient())ClientBootstrap.init();
        org.apache.logging.log4j.LogManager.getLogger("SRPAnimationUtil").info("SRP Animation Util 0.1.21 initialized; automatic Tabula import enabled for main-menu and in-world local workspaces.");
    }
}
