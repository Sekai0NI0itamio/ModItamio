package asd.itamio.modernshop;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ModernShopClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPacketHandler.register();
    }
}
