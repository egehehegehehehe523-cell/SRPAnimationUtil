package dev.srpau;
import net.minecraftforge.common.MinecraftForge;
/** Keep all Tabula/client class loading out of the dedicated server entry point. */
final class ClientBootstrap {
    static void init(){
        MinecraftForge.EVENT_BUS.register(new ClientHooks());
        MinecraftForge.EVENT_BUS.register(new BoneEditorEvents());
        MinecraftForge.EVENT_BUS.register(new PeekBodyRender());
    }
}
