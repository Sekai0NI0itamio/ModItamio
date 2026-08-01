package asd.itamio.modernshop;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.List;

@Environment(EnvType.CLIENT)
public class ClientPacketHandler {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ShopPacket.PACKET_TYPE, (payload, context) -> {
            context.client().execute(() -> {
                switch (payload.getType()) {
                    case ShopPacket.OPEN_SHOP:
                        ScreenManager.open(new GuiShopCategories());
                        break;
                    case ShopPacket.OPEN_PLAYER_SHOP:
                        // Open the shop for the player (same screen; flag would gate admin actions)
                        ScreenManager.open(new GuiShopCategories());
                        break;
                    case ShopPacket.OPEN_SELL_GUI:
                        ScreenManager.open(new GuiSellGui());
                        break;
                    case ShopPacket.OPEN_ITEM_DETAIL: {
                        // Deep-link: open the shop GUI directly at the item's buy/detail page
                        // (from /shop goto or clickable search results). The pendingDetail
                        // index is consumed by GuiShopItems.init().
                        List<ShopCategory> cats = ModernShop.getCategories();
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
    }
}
