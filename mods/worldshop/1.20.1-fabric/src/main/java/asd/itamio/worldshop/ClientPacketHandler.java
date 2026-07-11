package asd.itamio.worldshop;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public class ClientPacketHandler {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ShopPacket.PACKET_ID, (client, handler, buf, responseSender) -> {
            ShopPacket packet = ShopPacket.read(buf);
            client.execute(() -> {
                switch (packet.getType()) {
                    case ShopPacket.OPEN_SHOP:
                        Minecraft.getInstance().setScreen(new GuiShopCategories());
                        break;
                    case ShopPacket.OPEN_SELL_GUI:
                        Minecraft.getInstance().setScreen(new GuiSellGui());
                        break;
                }
            });
        });
    }
}
