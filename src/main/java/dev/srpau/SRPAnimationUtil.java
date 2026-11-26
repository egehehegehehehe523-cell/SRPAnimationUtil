package dev.srpau;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid="srpanimationutil", name="SRP Animation Util", version="0.1.12",
    clientSideOnly=true, acceptedMinecraftVersions="[1.12.2]",
    acceptableRemoteVersions="*", dependencies="required-after:tabula;required-after:ichunutil;required-after:srparasites")
public final class SRPAnimationUtil {
    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        ClientHooks hooks=new ClientHooks();
        MinecraftForge.EVENT_BUS.register(hooks);
        MinecraftForge.EVENT_BUS.register(new BoneEditorEvents());
        org.apache.logging.log4j.LogManager.getLogger("SRPAnimationUtil").info("SRP Animation Util 0.1.12 initialized; automatic Tabula import enabled for main-menu and in-world local workspaces.");
    }
}
