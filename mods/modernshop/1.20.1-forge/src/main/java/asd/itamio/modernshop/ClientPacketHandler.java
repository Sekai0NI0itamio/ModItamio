package asd.itamio.modernshop;

import net.minecraft.client.Minecraft;

/**
 * Handles S2C (server-to-client) packets on the client side.
 * In Forge, this is called from ModernShop.handlePacket() when the packet
 * direction is PLAY_TO_CLIENT.
 *
 * <p>This class is client-only and must only be referenced from client-side
 * code paths (ModernShop guards the call by checking the packet direction).
 */
public class ClientPacketHandler {
    public static void handle(ShopPacket packet) {
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
            case ShopPacket.OPEN_ITEM_DETAIL: {
                // Deep-link: open the shop GUI directly at the
                // item's buy/detail page (from /shop goto or
                // clickable search results). The pendingDetail
                // index is consumed by GuiShopItems.init().
                java.util.List<ShopCategory> cats = ModernShop.getCategories();
                int catIdx = packet.getCategoryIndex();
                int itemIdx = packet.getItemIndex();
                if (catIdx >= 0 && catIdx < cats.size()) {
                    ShopCategory cat = cats.get(catIdx);
                    GuiShopItems screen = new GuiShopItems(cat, catIdx);
                    screen.setPendingDetail(itemIdx);
                    ScreenManager.open(screen);
                }
                break;
            }
            default:
                // Unknown S2C packet — ignore
                break;
        }
    }
}
