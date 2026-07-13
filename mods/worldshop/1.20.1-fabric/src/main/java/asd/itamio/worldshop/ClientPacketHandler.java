package asd.itamio.worldshop;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;

@Environment(EnvType.CLIENT)
public class ClientPacketHandler {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ShopPacket.PACKET_ID, new ClientPlayNetworking.PlayChannelHandler() {
            @Override
            public void receive(Minecraft client, ClientPacketListener handler, FriendlyByteBuf buf, net.fabricmc.fabric.api.networking.v1.PacketSender responseSender) {
                ShopPacket packet = ShopPacket.read(buf);
                client.execute(new Runnable() {
                    @Override
                    public void run() {
                        switch (packet.getType()) {
                            case ShopPacket.OPEN_SHOP:
                                ScreenManager.open(new GuiShopCategories(false));
                                break;
                            case ShopPacket.OPEN_PLAYER_SHOP:
                                ScreenManager.open(new GuiShopCategories(true));
                                break;
                            case ShopPacket.OPEN_SELL_GUI:
                                ScreenManager.open(new GuiSellGui());
                                break;
                        }
                    }
                });
            }
        });
    }
}
