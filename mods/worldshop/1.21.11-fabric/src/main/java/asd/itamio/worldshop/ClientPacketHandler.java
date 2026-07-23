package asd.itamio.worldshop;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.List;

@Environment(EnvType.CLIENT)
public class ClientPacketHandler {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ShopPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ShopPayload.ShopMessage msg = payload.message();
                switch (msg.getType()) {
                    case ShopPayload.ShopMessage.OPEN_SHOP:
                        ScreenManager.open(new GuiShopCategories());
                        break;
                    case ShopPayload.ShopMessage.OPEN_PLAYER_SHOP:
                        // Open the shop for the player (same screen; flag would gate admin actions)
                        ScreenManager.open(new GuiShopCategories());
                        break;
                    case ShopPayload.ShopMessage.OPEN_SELL_GUI:
                        ScreenManager.open(new GuiSellGui());
                        break;
                    case ShopPayload.ShopMessage.OPEN_ITEM_DETAIL: {
                        // Deep-link: open the shop GUI directly at the item's buy/detail page
                        // (from /shop goto or clickable search results). The pendingDetail
                        // index is consumed by GuiShopItems.init().
                        List<ShopCategory> cats = WorldShop.getCategories();
                        int catIdx = msg.getCategoryIndex();
                        int itemIdx = msg.getItemIndex();
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
