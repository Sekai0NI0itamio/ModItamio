package asd.itamio.worldshop;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple packet data class for shop network communication.
 * Uses Forge's SimpleChannel networking with instance toBytes() + constructor(FriendlyByteBuf).
 */
public class ShopPacket {
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
    // (used by /shop goto and clickable search results). Carries the
    // categoryIndex + itemIndex to deep-link into GuiShopItems.
    public static final int OPEN_ITEM_DETAIL = 20;

    public static final ResourceLocation PACKET_ID = new ResourceLocation(WorldShop.MOD_ID, "shop_packet");

    private int type;
    private int categoryIndex;
    private int itemIndex;
    private int quantity;
    private List<ItemStack> items;

    // Admin operation fields
    private String stringData1;  // category name, item ID, display name
    private String stringData2;  // icon item ID
    private String stringData3;  // icon item ID for edit
    private double doubleData1;  // buy price
    private double doubleData2;  // sell price

    // Reorder fields
    private String[] stringArrayData;  // reordered category names (as alternative to intArrayData)

    public ShopPacket() {}

    public ShopPacket(FriendlyByteBuf buf) {
        this.type = buf.readInt();
        switch (this.type) {
            case BUY_ITEM:
                this.categoryIndex = buf.readInt();
                this.itemIndex = buf.readInt();
                this.quantity = buf.readInt();
                break;
            case SELL_GUI_ITEMS:
                int count = buf.readInt();
                this.items = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    this.items.add(buf.readItem());
                }
                break;
            case REMOVE_ITEM:
                this.categoryIndex = buf.readInt();
                this.itemIndex = buf.readInt();
                break;
            case REMOVE_CATEGORY:
                this.categoryIndex = buf.readInt();
                break;
            case ADD_CATEGORY:
                this.stringData1 = buf.readUtf();
                this.stringData2 = buf.readUtf();
                break;
            case ADD_ITEM:
                this.categoryIndex = buf.readInt();
                this.stringData1 = buf.readUtf();
                break;
            case EDIT_ITEM:
                this.categoryIndex = buf.readInt();
                this.stringData1 = buf.readUtf();
                this.stringData2 = buf.readUtf();
                this.stringData3 = buf.readUtf();
                this.doubleData1 = buf.readDouble();
                this.doubleData2 = buf.readDouble();
                break;
            case SYNC_SHOP_DATA:
                this.stringData1 = buf.readUtf();
                break;
            case REORDER_CATEGORIES:
                int len = buf.readInt();
                this.stringArrayData = new String[len];
                for (int i = 0; i < len; i++) {
                    this.stringArrayData[i] = buf.readUtf();
                }
                break;
            case RECALCULATE_CATEGORY:
            case RESET_CATEGORY:
                this.categoryIndex = buf.readInt();
                break;
            case RECALCULATE_BLOCK:
            case RESET_BLOCK:
                this.stringData1 = buf.readUtf();
                break;
            case SAVE_CONFIG:
                this.doubleData1 = buf.readDouble();
                break;
            case OPEN_ITEM_DETAIL:
                this.categoryIndex = buf.readInt();
                this.itemIndex = buf.readInt();
                break;
            // OPEN_SHOP, SELL_HAND, OPEN_SELL_GUI, OPEN_PLAYER_SHOP, RESET_CATEGORY_ORDER, RESET_ALL_PRICES have no extra data
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(type);
        switch (type) {
            case BUY_ITEM:
                buf.writeInt(categoryIndex);
                buf.writeInt(itemIndex);
                buf.writeInt(quantity);
                break;
            case SELL_GUI_ITEMS:
                if (items == null) {
                    buf.writeInt(0);
                } else {
                    buf.writeInt(items.size());
                    for (ItemStack stack : items) {
                        buf.writeItem(stack);
                    }
                }
                break;
            case REMOVE_ITEM:
                buf.writeInt(categoryIndex);
                buf.writeInt(itemIndex);
                break;
            case REMOVE_CATEGORY:
                buf.writeInt(categoryIndex);
                break;
            case ADD_CATEGORY:
                buf.writeUtf(stringData1 != null ? stringData1 : "");
                buf.writeUtf(stringData2 != null ? stringData2 : "");
                break;
            case ADD_ITEM:
                buf.writeInt(categoryIndex);
                buf.writeUtf(stringData1 != null ? stringData1 : "");
                break;
            case EDIT_ITEM:
                buf.writeInt(categoryIndex);
                buf.writeUtf(stringData1 != null ? stringData1 : "");
                buf.writeUtf(stringData2 != null ? stringData2 : "");
                buf.writeUtf(stringData3 != null ? stringData3 : "");
                buf.writeDouble(doubleData1);
                buf.writeDouble(doubleData2);
                break;
            case SYNC_SHOP_DATA:
                buf.writeUtf(stringData1 != null ? stringData1 : "");
                break;
            case REORDER_CATEGORIES:
                if (stringArrayData == null) {
                    buf.writeInt(0);
                } else {
                    buf.writeInt(stringArrayData.length);
                    for (String name : stringArrayData) {
                        buf.writeUtf(name);
                    }
                }
                break;
            case RECALCULATE_CATEGORY:
            case RESET_CATEGORY:
                buf.writeInt(categoryIndex);
                break;
            case RECALCULATE_BLOCK:
            case RESET_BLOCK:
                buf.writeUtf(stringData1 != null ? stringData1 : "");
                break;
            case SAVE_CONFIG:
                buf.writeDouble(doubleData1);
                break;
            case OPEN_ITEM_DETAIL:
                buf.writeInt(categoryIndex);
                buf.writeInt(itemIndex);
                break;
            // OPEN_SHOP, SELL_HAND, OPEN_SELL_GUI, OPEN_PLAYER_SHOP, RESET_CATEGORY_ORDER, RESET_ALL_PRICES have no extra data
        }
    }

    public static ShopPacket openShop() {
        ShopPacket pkt = new ShopPacket();
        pkt.type = OPEN_SHOP;
        return pkt;
    }

    public static ShopPacket openPlayerShop() {
        ShopPacket pkt = new ShopPacket();
        pkt.type = OPEN_PLAYER_SHOP;
        return pkt;
    }

    public static ShopPacket buyItem(int categoryIndex, int itemIndex, int quantity) {
        ShopPacket pkt = new ShopPacket();
        pkt.type = BUY_ITEM;
        pkt.categoryIndex = categoryIndex;
        pkt.itemIndex = itemIndex;
        pkt.quantity = quantity;
        return pkt;
    }

    public static ShopPacket sellHand() {
        ShopPacket pkt = new ShopPacket();
        pkt.type = SELL_HAND;
        return pkt;
    }

    public static ShopPacket openSellGui() {
        ShopPacket pkt = new ShopPacket();
        pkt.type = OPEN_SELL_GUI;
        return pkt;
    }

    public static ShopPacket sellGuiItems(List<ItemStack> items) {
        ShopPacket pkt = new ShopPacket();
        pkt.type = SELL_GUI_ITEMS;
        pkt.items = items;
        return pkt;
    }

    // ========== Admin operation factories ==========

    public static ShopPacket removeItem(int categoryIndex, int itemIndex) {
        ShopPacket pkt = new ShopPacket();
        pkt.type = REMOVE_ITEM;
        pkt.categoryIndex = categoryIndex;
        pkt.itemIndex = itemIndex;
        return pkt;
    }

    public static ShopPacket removeCategory(int categoryIndex) {
        ShopPacket pkt = new ShopPacket();
        pkt.type = REMOVE_CATEGORY;
        pkt.categoryIndex = categoryIndex;
        return pkt;
    }

    public static ShopPacket addCategory(String name, String iconItemId) {
        ShopPacket pkt = new ShopPacket();
        pkt.type = ADD_CATEGORY;
        pkt.stringData1 = name;
        pkt.stringData2 = iconItemId;
        return pkt;
    }

    public static ShopPacket addItemToCategory(int categoryIndex, String itemId) {
        ShopPacket pkt = new ShopPacket();
        pkt.type = ADD_ITEM;
        pkt.categoryIndex = categoryIndex;
        pkt.stringData1 = itemId;
        return pkt;
    }

    public static ShopPacket editItem(int categoryIndex, String itemId, String displayName, String iconId, double buyPrice, double sellPrice) {
        ShopPacket pkt = new ShopPacket();
        pkt.type = EDIT_ITEM;
        pkt.categoryIndex = categoryIndex;
        pkt.stringData1 = itemId;
        pkt.stringData2 = displayName;
        pkt.stringData3 = iconId;
        pkt.doubleData1 = buyPrice;
        pkt.doubleData2 = sellPrice;
        return pkt;
    }

    public static ShopPacket syncShopData(String jsonData) {
        ShopPacket pkt = new ShopPacket();
        pkt.type = SYNC_SHOP_DATA;
        pkt.stringData1 = jsonData;
        return pkt;
    }

    public static ShopPacket reorderCategories(String[] categoryNames) {
        ShopPacket pkt = new ShopPacket();
        pkt.type = REORDER_CATEGORIES;
        pkt.stringArrayData = categoryNames;
        return pkt;
    }

    // ========== Settings operation factories ==========

    public static ShopPacket resetCategoryOrder() {
        ShopPacket pkt = new ShopPacket();
        pkt.type = RESET_CATEGORY_ORDER;
        return pkt;
    }

    public static ShopPacket resetAllPrices() {
        ShopPacket pkt = new ShopPacket();
        pkt.type = RESET_ALL_PRICES;
        return pkt;
    }

    public static ShopPacket recalculateCategory(int categoryIndex) {
        ShopPacket pkt = new ShopPacket();
        pkt.type = RECALCULATE_CATEGORY;
        pkt.categoryIndex = categoryIndex;
        return pkt;
    }

    public static ShopPacket resetCategory(int categoryIndex) {
        ShopPacket pkt = new ShopPacket();
        pkt.type = RESET_CATEGORY;
        pkt.categoryIndex = categoryIndex;
        return pkt;
    }

    public static ShopPacket recalculateBlock(String itemId) {
        ShopPacket pkt = new ShopPacket();
        pkt.type = RECALCULATE_BLOCK;
        pkt.stringData1 = itemId;
        return pkt;
    }

    public static ShopPacket resetBlock(String itemId) {
        ShopPacket pkt = new ShopPacket();
        pkt.type = RESET_BLOCK;
        pkt.stringData1 = itemId;
        return pkt;
    }

    public static ShopPacket saveConfig(boolean sellhandConfirmation) {
        ShopPacket pkt = new ShopPacket();
        pkt.type = SAVE_CONFIG;
        // Use doubleData1 to store the boolean (1.0 = true, 0.0 = false)
        pkt.doubleData1 = sellhandConfirmation ? 1.0 : 0.0;
        return pkt;
    }

    /**
     * S2C packet that tells the client to open the shop GUI directly at the
     * buy/detail page for a specific item (used by /shop goto and clickable
     * search results). The client opens GuiShopItems in detail view.
     */
    public static ShopPacket openItemDetail(int categoryIndex, int itemIndex) {
        ShopPacket pkt = new ShopPacket();
        pkt.type = OPEN_ITEM_DETAIL;
        pkt.categoryIndex = categoryIndex;
        pkt.itemIndex = itemIndex;
        return pkt;
    }

    public int getType() { return type; }
    public int getCategoryIndex() { return categoryIndex; }
    public int getItemIndex() { return itemIndex; }
    public int getQuantity() { return quantity; }
    public List<ItemStack> getItems() { return items != null ? items : new ArrayList<>(); }
    public String getStringData1() { return stringData1 != null ? stringData1 : ""; }
    public String getStringData2() { return stringData2 != null ? stringData2 : ""; }
    public String getStringData3() { return stringData3 != null ? stringData3 : ""; }
    public double getDoubleData1() { return doubleData1; }
    public double getDoubleData2() { return doubleData2; }
    public String[] getStringArrayData() { return stringArrayData; }
}
