package asd.itamio.modernshop;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public class ClientPacketHandler {
    public static void register() {
        try {
            ClientPlayNetworking.registerGlobalReceiver(ShopPacket.PACKET_TYPE, (payload, context) -> {
                context.client().execute(() -> {
                    switch (payload.getType()) {
                        case ShopPacket.OPEN_SHOP:
                            ScreenManager.open(new GuiShopCategories(false));
                            break;
                        case ShopPacket.OPEN_PLAYER_SHOP:
                            ScreenManager.open(new GuiShopCategories(true));
                            break;
                        case ShopPacket.OPEN_SELL_GUI:
                            ScreenManager.open(new GuiSellGui());
                            break;
                        case ShopPacket.OPEN_ITEM_DETAIL: {
                            java.util.List<ShopCategory> cats = ModernShop.getCategories();
                            int catIdx = payload.getCategoryIndex();
                            int itemIdx = payload.getItemIndex();
                            if (catIdx >= 0 && catIdx < cats.size()) {
                                ShopCategory cat = cats.get(catIdx);
                                GuiShopItems screen = new GuiShopItems(cat, catIdx);
                                screen.setPendingDetail(itemIdx);
                                ScreenManager.open(screen);
                            }
                            break;
                        }
                    }
                });
            });
        } catch (Throwable t) {
            ModernShop.LOGGER.warn("Could not register client packet handler: {}", t.getMessage());
        }
    }
}
