package asd.itamio.modernshop;

/**
 * Utility class providing packet type constants and factory methods
 * that create ShopPayload instances. In 1.21.11, the actual payload
 * class is ShopPayload (with nested ShopMessage), but this class
 * provides a compatible API surface matching older versions where
 * ShopPacket was the payload class itself.
 */
public final class ShopPacket {
    // Packet type constants (matching ShopPayload.ShopMessage constants)
    public static final int OPEN_SHOP = 0;
    public static final int BUY_ITEM = 1;
    public static final int SELL_HAND = 2;
    public static final int OPEN_SELL_GUI = 3;
    public static final int SELL_GUI_ITEMS = 4;

    // Admin operation types
    public static final int REMOVE_ITEM = 5;
    public static final int REMOVE_CATEGORY = 6;
    public static final int ADD_CATEGORY = 7;
    public static final int ADD_ITEM = 8;
    public static final int EDIT_ITEM = 9;

    // S2C packet types
    public static final int OPEN_PLAYER_SHOP = 10;
    public static final int SYNC_SHOP_DATA = 11;

    // Admin: reorder categories
    public static final int REORDER_CATEGORIES = 12;

    // Settings operations
    public static final int RESET_CATEGORY_ORDER = 13;
    public static final int RESET_ALL_PRICES = 14;
    public static final int RECALCULATE_CATEGORY = 15;
    public static final int RESET_CATEGORY = 16;
    public static final int RECALCULATE_BLOCK = 17;
    public static final int RESET_BLOCK = 18;
    public static final int SAVE_CONFIG = 19;

    // S2C: open the shop GUI directly at an item's buy/detail page
    public static final int OPEN_ITEM_DETAIL = 20;

    private ShopPacket() {}

    // ========== Factory methods (return ShopPayload) ==========

    public static ShopPayload openShop() {
        return new ShopPayload(ShopPayload.ShopMessage.openShop());
    }

    public static ShopPayload openPlayerShop() {
        return new ShopPayload(ShopPayload.ShopMessage.openPlayerShop());
    }

    public static ShopPayload buyItem(int categoryIndex, int itemIndex, int quantity) {
        return new ShopPayload(ShopPayload.ShopMessage.buyItem(categoryIndex, itemIndex, quantity));
    }

    public static ShopPayload sellHand() {
        return new ShopPayload(ShopPayload.ShopMessage.sellHand());
    }

    public static ShopPayload openSellGui() {
        return new ShopPayload(ShopPayload.ShopMessage.openSellGui());
    }

    public static ShopPayload sellGuiItems() {
        return new ShopPayload(ShopPayload.ShopMessage.sellGuiItems());
    }

    public static ShopPayload removeItem(int categoryIndex, int itemIndex) {
        return new ShopPayload(ShopPayload.ShopMessage.removeItem(categoryIndex, itemIndex));
    }

    public static ShopPayload removeCategory(int categoryIndex) {
        return new ShopPayload(ShopPayload.ShopMessage.removeCategory(categoryIndex));
    }

    public static ShopPayload addCategory(String name, String iconItemId) {
        return new ShopPayload(ShopPayload.ShopMessage.addCategory(name, iconItemId));
    }

    public static ShopPayload addItemToCategory(int categoryIndex, String itemId) {
        return new ShopPayload(ShopPayload.ShopMessage.addItemToCategory(categoryIndex, itemId));
    }

    public static ShopPayload editItem(int categoryIndex, String itemId, String displayName, String iconId, double buyPrice, double sellPrice) {
        return new ShopPayload(ShopPayload.ShopMessage.editItem(categoryIndex, itemId, displayName, iconId, buyPrice, sellPrice));
    }

    public static ShopPayload syncShopData(String jsonData) {
        return new ShopPayload(ShopPayload.ShopMessage.syncShopData(jsonData));
    }

    public static ShopPayload reorderCategories(String[] categoryNames) {
        return new ShopPayload(ShopPayload.ShopMessage.reorderCategories(categoryNames));
    }

    public static ShopPayload resetCategoryOrder() {
        return new ShopPayload(ShopPayload.ShopMessage.resetCategoryOrder());
    }

    public static ShopPayload resetAllPrices() {
        return new ShopPayload(ShopPayload.ShopMessage.resetAllPrices());
    }

    public static ShopPayload recalculateCategory(int categoryIndex) {
        return new ShopPayload(ShopPayload.ShopMessage.recalculateCategory(categoryIndex));
    }

    public static ShopPayload resetCategory(int categoryIndex) {
        return new ShopPayload(ShopPayload.ShopMessage.resetCategory(categoryIndex));
    }

    public static ShopPayload recalculateBlock(String itemId) {
        return new ShopPayload(ShopPayload.ShopMessage.recalculateBlock(itemId));
    }

    public static ShopPayload resetBlock(String itemId) {
        return new ShopPayload(ShopPayload.ShopMessage.resetBlock(itemId));
    }

    public static ShopPayload saveConfig(boolean sellhandConfirmation) {
        return new ShopPayload(ShopPayload.ShopMessage.saveConfig(sellhandConfirmation));
    }

    public static ShopPayload openItemDetail(int categoryIndex, int itemIndex) {
        return new ShopPayload(ShopPayload.ShopMessage.openItemDetail(categoryIndex, itemIndex));
    }
}
