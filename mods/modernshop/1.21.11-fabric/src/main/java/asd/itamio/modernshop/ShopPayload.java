package asd.itamio.modernshop;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class ShopPayload implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ShopPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(ModernShop.MOD_ID, "shop_packet"));

    public static final StreamCodec<FriendlyByteBuf, ShopPayload> STREAM_CODEC = new StreamCodec<FriendlyByteBuf, ShopPayload>() {
        @Override
        public ShopPayload decode(FriendlyByteBuf buf) {
            return new ShopPayload(ShopMessage.read(buf));
        }

        @Override
        public void encode(FriendlyByteBuf buf, ShopPayload payload) {
            ShopMessage.write(payload.message, buf);
        }
    };

    private final ShopMessage message;

    public ShopPayload(ShopMessage message) {
        this.message = message;
    }

    public ShopMessage message() {
        return message;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static class ShopMessage {
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

        private int type;
        private int categoryIndex;
        private int itemIndex;
        private int quantity;
        private List<SellSlotEntry> sellSlotEntries;

        // Admin operation fields
        private String stringData1;  // category name, item ID, display name
        private String stringData2;  // icon item ID
        private String stringData3;  // icon item ID for edit
        private double doubleData1;  // buy price
        private double doubleData2;  // sell price

        // Reorder fields
        private String[] stringArrayData;  // reordered category names

        public ShopMessage() {}

        // ========== Factory methods ==========

        public static ShopMessage openShop() {
            ShopMessage msg = new ShopMessage();
            msg.type = OPEN_SHOP;
            return msg;
        }

        public static ShopMessage openPlayerShop() {
            ShopMessage msg = new ShopMessage();
            msg.type = OPEN_PLAYER_SHOP;
            return msg;
        }

        public static ShopMessage buyItem(int categoryIndex, int itemIndex, int quantity) {
            ShopMessage msg = new ShopMessage();
            msg.type = BUY_ITEM;
            msg.categoryIndex = categoryIndex;
            msg.itemIndex = itemIndex;
            msg.quantity = quantity;
            return msg;
        }

        public static ShopMessage sellHand() {
            ShopMessage msg = new ShopMessage();
            msg.type = SELL_HAND;
            return msg;
        }

        public static ShopMessage openSellGui() {
            ShopMessage msg = new ShopMessage();
            msg.type = OPEN_SELL_GUI;
            return msg;
        }

        public static ShopMessage sellGuiItems() {
            ShopMessage msg = new ShopMessage();
            msg.type = SELL_GUI_ITEMS;
            return msg;
        }

        // ========== Admin operation factories ==========

        public static ShopMessage removeItem(int categoryIndex, int itemIndex) {
            ShopMessage msg = new ShopMessage();
            msg.type = REMOVE_ITEM;
            msg.categoryIndex = categoryIndex;
            msg.itemIndex = itemIndex;
            return msg;
        }

        public static ShopMessage removeCategory(int categoryIndex) {
            ShopMessage msg = new ShopMessage();
            msg.type = REMOVE_CATEGORY;
            msg.categoryIndex = categoryIndex;
            return msg;
        }

        public static ShopMessage addCategory(String name, String iconItemId) {
            ShopMessage msg = new ShopMessage();
            msg.type = ADD_CATEGORY;
            msg.stringData1 = name;
            msg.stringData2 = iconItemId;
            return msg;
        }

        public static ShopMessage addItemToCategory(int categoryIndex, String itemId) {
            ShopMessage msg = new ShopMessage();
            msg.type = ADD_ITEM;
            msg.categoryIndex = categoryIndex;
            msg.stringData1 = itemId;
            return msg;
        }

        public static ShopMessage editItem(int categoryIndex, String itemId, String displayName, String iconId, double buyPrice, double sellPrice) {
            ShopMessage msg = new ShopMessage();
            msg.type = EDIT_ITEM;
            msg.categoryIndex = categoryIndex;
            msg.stringData1 = itemId;
            msg.stringData2 = displayName;
            msg.stringData3 = iconId;
            msg.doubleData1 = buyPrice;
            msg.doubleData2 = sellPrice;
            return msg;
        }

        public static ShopMessage syncShopData(String jsonData) {
            ShopMessage msg = new ShopMessage();
            msg.type = SYNC_SHOP_DATA;
            msg.stringData1 = jsonData;
            return msg;
        }

        public static ShopMessage reorderCategories(String[] categoryNames) {
            ShopMessage msg = new ShopMessage();
            msg.type = REORDER_CATEGORIES;
            msg.stringArrayData = categoryNames;
            return msg;
        }

        // ========== Settings operation factories ==========

        public static ShopMessage resetCategoryOrder() {
            ShopMessage msg = new ShopMessage();
            msg.type = RESET_CATEGORY_ORDER;
            return msg;
        }

        public static ShopMessage resetAllPrices() {
            ShopMessage msg = new ShopMessage();
            msg.type = RESET_ALL_PRICES;
            return msg;
        }

        public static ShopMessage recalculateCategory(int categoryIndex) {
            ShopMessage msg = new ShopMessage();
            msg.type = RECALCULATE_CATEGORY;
            msg.categoryIndex = categoryIndex;
            return msg;
        }

        public static ShopMessage resetCategory(int categoryIndex) {
            ShopMessage msg = new ShopMessage();
            msg.type = RESET_CATEGORY;
            msg.categoryIndex = categoryIndex;
            return msg;
        }

        public static ShopMessage recalculateBlock(String itemId) {
            ShopMessage msg = new ShopMessage();
            msg.type = RECALCULATE_BLOCK;
            msg.stringData1 = itemId;
            return msg;
        }

        public static ShopMessage resetBlock(String itemId) {
            ShopMessage msg = new ShopMessage();
            msg.type = RESET_BLOCK;
            msg.stringData1 = itemId;
            return msg;
        }

        public static ShopMessage saveConfig(boolean sellhandConfirmation) {
            ShopMessage msg = new ShopMessage();
            msg.type = SAVE_CONFIG;
            // Use doubleData1 to store the boolean (1.0 = true, 0.0 = false)
            msg.doubleData1 = sellhandConfirmation ? 1.0 : 0.0;
            return msg;
        }

        public static ShopMessage openItemDetail(int categoryIndex, int itemIndex) {
            ShopMessage msg = new ShopMessage();
            msg.type = OPEN_ITEM_DETAIL;
            msg.categoryIndex = categoryIndex;
            msg.itemIndex = itemIndex;
            return msg;
        }

        // ========== Serialization ==========

        public static void write(ShopMessage msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.type);
            switch (msg.type) {
                case BUY_ITEM:
                    buf.writeVarInt(msg.categoryIndex);
                    buf.writeVarInt(msg.itemIndex);
                    buf.writeVarInt(msg.quantity);
                    break;
                case SELL_GUI_ITEMS:
                    if (msg.sellSlotEntries == null || msg.sellSlotEntries.isEmpty()) {
                        buf.writeVarInt(0);
                    } else {
                        buf.writeVarInt(msg.sellSlotEntries.size());
                        for (SellSlotEntry entry : msg.sellSlotEntries) {
                            buf.writeVarInt(entry.slotIndex);
                            buf.writeVarInt(entry.quantity);
                        }
                    }
                    break;
                case REMOVE_ITEM:
                    buf.writeVarInt(msg.categoryIndex);
                    buf.writeVarInt(msg.itemIndex);
                    break;
                case REMOVE_CATEGORY:
                    buf.writeVarInt(msg.categoryIndex);
                    break;
                case ADD_CATEGORY:
                    buf.writeUtf(msg.stringData1 != null ? msg.stringData1 : "");
                    buf.writeUtf(msg.stringData2 != null ? msg.stringData2 : "");
                    break;
                case ADD_ITEM:
                    buf.writeVarInt(msg.categoryIndex);
                    buf.writeUtf(msg.stringData1 != null ? msg.stringData1 : "");
                    break;
                case EDIT_ITEM:
                    buf.writeVarInt(msg.categoryIndex);
                    buf.writeUtf(msg.stringData1 != null ? msg.stringData1 : "");
                    buf.writeUtf(msg.stringData2 != null ? msg.stringData2 : "");
                    buf.writeUtf(msg.stringData3 != null ? msg.stringData3 : "");
                    buf.writeDouble(msg.doubleData1);
                    buf.writeDouble(msg.doubleData2);
                    break;
                case SYNC_SHOP_DATA:
                    buf.writeUtf(msg.stringData1 != null ? msg.stringData1 : "");
                    break;
                case REORDER_CATEGORIES:
                    if (msg.stringArrayData == null) {
                        buf.writeVarInt(0);
                    } else {
                        buf.writeVarInt(msg.stringArrayData.length);
                        for (String name : msg.stringArrayData) {
                            buf.writeUtf(name);
                        }
                    }
                    break;
                case RECALCULATE_CATEGORY:
                case RESET_CATEGORY:
                    buf.writeVarInt(msg.categoryIndex);
                    break;
                case RECALCULATE_BLOCK:
                case RESET_BLOCK:
                    buf.writeUtf(msg.stringData1 != null ? msg.stringData1 : "");
                    break;
                case SAVE_CONFIG:
                    buf.writeDouble(msg.doubleData1);
                    break;
                case OPEN_ITEM_DETAIL:
                    buf.writeVarInt(msg.categoryIndex);
                    buf.writeVarInt(msg.itemIndex);
                    break;
                // OPEN_SHOP, SELL_HAND, OPEN_SELL_GUI, OPEN_PLAYER_SHOP, RESET_CATEGORY_ORDER, RESET_ALL_PRICES have no extra data
            }
        }

        public static ShopMessage read(FriendlyByteBuf buf) {
            ShopMessage msg = new ShopMessage();
            msg.type = buf.readVarInt();
            switch (msg.type) {
                case BUY_ITEM:
                    msg.categoryIndex = buf.readVarInt();
                    msg.itemIndex = buf.readVarInt();
                    msg.quantity = buf.readVarInt();
                    break;
                case SELL_GUI_ITEMS: {
                    int count = buf.readVarInt();
                    msg.sellSlotEntries = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        int slotIndex = buf.readVarInt();
                        int quantity = buf.readVarInt();
                        msg.sellSlotEntries.add(new SellSlotEntry(slotIndex, quantity));
                    }
                    break;
                }
                case REMOVE_ITEM:
                    msg.categoryIndex = buf.readVarInt();
                    msg.itemIndex = buf.readVarInt();
                    break;
                case REMOVE_CATEGORY:
                    msg.categoryIndex = buf.readVarInt();
                    break;
                case ADD_CATEGORY:
                    msg.stringData1 = buf.readUtf();
                    msg.stringData2 = buf.readUtf();
                    break;
                case ADD_ITEM:
                    msg.categoryIndex = buf.readVarInt();
                    msg.stringData1 = buf.readUtf();
                    break;
                case EDIT_ITEM:
                    msg.categoryIndex = buf.readVarInt();
                    msg.stringData1 = buf.readUtf();
                    msg.stringData2 = buf.readUtf();
                    msg.stringData3 = buf.readUtf();
                    msg.doubleData1 = buf.readDouble();
                    msg.doubleData2 = buf.readDouble();
                    break;
                case SYNC_SHOP_DATA:
                    msg.stringData1 = buf.readUtf();
                    break;
                case REORDER_CATEGORIES: {
                    int len = buf.readVarInt();
                    msg.stringArrayData = new String[len];
                    for (int i = 0; i < len; i++) {
                        msg.stringArrayData[i] = buf.readUtf();
                    }
                    break;
                }
                case RECALCULATE_CATEGORY:
                case RESET_CATEGORY:
                    msg.categoryIndex = buf.readVarInt();
                    break;
                case RECALCULATE_BLOCK:
                case RESET_BLOCK:
                    msg.stringData1 = buf.readUtf();
                    break;
                case SAVE_CONFIG:
                    msg.doubleData1 = buf.readDouble();
                    break;
                case OPEN_ITEM_DETAIL:
                    msg.categoryIndex = buf.readVarInt();
                    msg.itemIndex = buf.readVarInt();
                    break;
            }
            return msg;
        }

        // ========== Getters ==========

        public int getType() { return type; }
        public int getCategoryIndex() { return categoryIndex; }
        public int getItemIndex() { return itemIndex; }
        public int getQuantity() { return quantity; }
        public List<SellSlotEntry> getSellSlotEntries() { return sellSlotEntries != null ? sellSlotEntries : new ArrayList<>(); }
        public void setSellSlotEntries(List<SellSlotEntry> entries) { this.sellSlotEntries = entries; }
        public String getStringData1() { return stringData1; }
        public String getStringData2() { return stringData2; }
        public String getStringData3() { return stringData3; }
        public double getDoubleData1() { return doubleData1; }
        public double getDoubleData2() { return doubleData2; }
        public String[] getStringArrayData() { return stringArrayData; }

        public static class SellSlotEntry {
            public final int slotIndex;
            public final int quantity;

            public SellSlotEntry(int slotIndex, int quantity) {
                this.slotIndex = slotIndex;
                this.quantity = quantity;
            }
        }
    }
}
