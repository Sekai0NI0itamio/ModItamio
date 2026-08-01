package asd.itamio.modernshop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple packet data class for shop network communication.
 * 21 packet types for all shop operations.
 */
public class ShopPacket implements CustomPacketPayload {
    public static final int OPEN_SHOP = 0;
    public static final int BUY_ITEM = 1;
    public static final int SELL_HAND = 2;
    public static final int OPEN_SELL_GUI = 3;
    public static final int SELL_GUI_ITEMS = 4;

    public static final int REMOVE_ITEM = 5;
    public static final int REMOVE_CATEGORY = 6;
    public static final int ADD_CATEGORY = 7;
    public static final int ADD_ITEM = 8;
    public static final int EDIT_ITEM = 9;

    public static final int OPEN_PLAYER_SHOP = 10;
    public static final int SYNC_SHOP_DATA = 11;

    public static final int REORDER_CATEGORIES = 12;

    public static final int RESET_CATEGORY_ORDER = 13;
    public static final int RESET_ALL_PRICES = 14;
    public static final int RECALCULATE_CATEGORY = 15;
    public static final int RESET_CATEGORY = 16;
    public static final int RECALCULATE_BLOCK = 17;
    public static final int RESET_BLOCK = 18;
    public static final int SAVE_CONFIG = 19;

    public static final int OPEN_ITEM_DETAIL = 20;

    public static final CustomPacketPayload.Type<ShopPacket> PACKET_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ModernShop.MOD_ID, "shop_packet"));

    public static final StreamCodec<FriendlyByteBuf, ShopPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ShopPacket decode(FriendlyByteBuf buf) {
            return read(buf);
        }

        @Override
        public void encode(FriendlyByteBuf buf, ShopPacket pkt) {
            write(pkt, buf);
        }
    };

    private int type;
    private int categoryIndex;
    private int itemIndex;
    private int quantity;
    private List<ItemStack> items;

    private String stringData1;
    private String stringData2;
    private String stringData3;
    private double doubleData1;
    private double doubleData2;

    private String[] stringArrayData;

    public ShopPacket() {}

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
        pkt.doubleData1 = sellhandConfirmation ? 1.0 : 0.0;
        return pkt;
    }

    public static ShopPacket openItemDetail(int categoryIndex, int itemIndex) {
        ShopPacket pkt = new ShopPacket();
        pkt.type = OPEN_ITEM_DETAIL;
        pkt.categoryIndex = categoryIndex;
        pkt.itemIndex = itemIndex;
        return pkt;
    }

    public static void write(ShopPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.type);
        switch (pkt.type) {
            case BUY_ITEM:
                buf.writeInt(pkt.categoryIndex);
                buf.writeInt(pkt.itemIndex);
                buf.writeInt(pkt.quantity);
                break;
            case SELL_GUI_ITEMS:
                if (pkt.items == null) {
                    buf.writeInt(0);
                } else {
                    buf.writeInt(pkt.items.size());
                    for (ItemStack stack : pkt.items) {
                        writeItemStack(buf, stack);
                    }
                }
                break;
            case REMOVE_ITEM:
                buf.writeInt(pkt.categoryIndex);
                buf.writeInt(pkt.itemIndex);
                break;
            case REMOVE_CATEGORY:
                buf.writeInt(pkt.categoryIndex);
                break;
            case ADD_CATEGORY:
                buf.writeUtf(pkt.stringData1 != null ? pkt.stringData1 : "");
                buf.writeUtf(pkt.stringData2 != null ? pkt.stringData2 : "");
                break;
            case ADD_ITEM:
                buf.writeInt(pkt.categoryIndex);
                buf.writeUtf(pkt.stringData1 != null ? pkt.stringData1 : "");
                break;
            case EDIT_ITEM:
                buf.writeInt(pkt.categoryIndex);
                buf.writeUtf(pkt.stringData1 != null ? pkt.stringData1 : "");
                buf.writeUtf(pkt.stringData2 != null ? pkt.stringData2 : "");
                buf.writeUtf(pkt.stringData3 != null ? pkt.stringData3 : "");
                buf.writeDouble(pkt.doubleData1);
                buf.writeDouble(pkt.doubleData2);
                break;
            case SYNC_SHOP_DATA:
                buf.writeUtf(pkt.stringData1 != null ? pkt.stringData1 : "");
                break;
            case REORDER_CATEGORIES:
                if (pkt.stringArrayData == null) {
                    buf.writeInt(0);
                } else {
                    buf.writeInt(pkt.stringArrayData.length);
                    for (String name : pkt.stringArrayData) {
                        buf.writeUtf(name);
                    }
                }
                break;
            case RECALCULATE_CATEGORY:
            case RESET_CATEGORY:
                buf.writeInt(pkt.categoryIndex);
                break;
            case RECALCULATE_BLOCK:
            case RESET_BLOCK:
                buf.writeUtf(pkt.stringData1 != null ? pkt.stringData1 : "");
                break;
            case SAVE_CONFIG:
                buf.writeDouble(pkt.doubleData1);
                break;
            case OPEN_ITEM_DETAIL:
                buf.writeInt(pkt.categoryIndex);
                buf.writeInt(pkt.itemIndex);
                break;
        }
    }

    public static ShopPacket read(FriendlyByteBuf buf) {
        ShopPacket pkt = new ShopPacket();
        pkt.type = buf.readInt();
        switch (pkt.type) {
            case BUY_ITEM:
                pkt.categoryIndex = buf.readInt();
                pkt.itemIndex = buf.readInt();
                pkt.quantity = buf.readInt();
                break;
            case SELL_GUI_ITEMS:
                int count = buf.readInt();
                pkt.items = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    pkt.items.add(readItemStack(buf));
                }
                break;
            case REMOVE_ITEM:
                pkt.categoryIndex = buf.readInt();
                pkt.itemIndex = buf.readInt();
                break;
            case REMOVE_CATEGORY:
                pkt.categoryIndex = buf.readInt();
                break;
            case ADD_CATEGORY:
                pkt.stringData1 = buf.readUtf();
                pkt.stringData2 = buf.readUtf();
                break;
            case ADD_ITEM:
                pkt.categoryIndex = buf.readInt();
                pkt.stringData1 = buf.readUtf();
                break;
            case EDIT_ITEM:
                pkt.categoryIndex = buf.readInt();
                pkt.stringData1 = buf.readUtf();
                pkt.stringData2 = buf.readUtf();
                pkt.stringData3 = buf.readUtf();
                pkt.doubleData1 = buf.readDouble();
                pkt.doubleData2 = buf.readDouble();
                break;
            case SYNC_SHOP_DATA:
                pkt.stringData1 = buf.readUtf();
                break;
            case REORDER_CATEGORIES:
                int len = buf.readInt();
                pkt.stringArrayData = new String[len];
                for (int i = 0; i < len; i++) {
                    pkt.stringArrayData[i] = buf.readUtf();
                }
                break;
            case RECALCULATE_CATEGORY:
            case RESET_CATEGORY:
                pkt.categoryIndex = buf.readInt();
                break;
            case RECALCULATE_BLOCK:
            case RESET_BLOCK:
                pkt.stringData1 = buf.readUtf();
                break;
            case SAVE_CONFIG:
                pkt.doubleData1 = buf.readDouble();
                break;
            case OPEN_ITEM_DETAIL:
                pkt.categoryIndex = buf.readInt();
                pkt.itemIndex = buf.readInt();
                break;
        }
        return pkt;
    }

    public int getType() { return type; }
    public int getCategoryIndex() { return categoryIndex; }
    public int getItemIndex() { return itemIndex; }
    public int getQuantity() { return quantity; }
    public List<ItemStack> getItems() { return items; }
    public String getStringData1() { return stringData1; }
    public String getStringData2() { return stringData2; }
    public String getStringData3() { return stringData3; }
    public double getDoubleData1() { return doubleData1; }
    public double getDoubleData2() { return doubleData2; }
    public String[] getStringArrayData() { return stringArrayData; }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return PACKET_TYPE;
    }

    // Simplified ItemStack serialization (item registry name + count)
    private static void writeItemStack(FriendlyByteBuf buf, ItemStack stack) {
        buf.writeBoolean(stack != null && !stack.isEmpty());
        if (stack != null && !stack.isEmpty()) {
            String key = BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).getRegisteredName();
            buf.writeUtf(key);
            buf.writeInt(stack.getCount());
        }
    }

    private static ItemStack readItemStack(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return ItemStack.EMPTY;
        }
        String key = buf.readUtf();
        int count = buf.readInt();
        Identifier id = Identifier.parse(key);
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null) return ItemStack.EMPTY;
        return new ItemStack(item, count);
    }
}
