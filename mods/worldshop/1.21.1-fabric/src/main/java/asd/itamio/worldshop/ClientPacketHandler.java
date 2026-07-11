package asd.itamio.worldshop;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public class ClientPacketHandler {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ShopPacket.PACKET_TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft mc = Minecraft.getInstance();
                switch (payload.getType()) {
                    case ShopPacket.OPEN_SHOP:
                        mc.setScreen(new GuiShopCategories());
                        break;
                    case ShopPacket.OPEN_SELL_GUI:
                        mc.setScreen(new GuiSellGui());
                        break;
                }
            });
        });
    }
}
