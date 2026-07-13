package asd.itamio.worldshop;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple packet data class for shop network communication.
 * In Fabric 1.20.1, we use PacketByteBuf-based networking.
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
                        buf.writeItem(stack);
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
            // OPEN_SHOP, SELL_HAND, OPEN_SELL_GUI, OPEN_PLAYER_SHOP have no extra data
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
                    pkt.items.add(buf.readItem());
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
        }
        return pkt;
    }

    /**
     * Create a FriendlyByteBuf from this packet for sending.
     */
    public static FriendlyByteBuf writeDirect(ShopPacket pkt) {
        net.fabricmc.fabric.api.networking.v1.PacketByteBufs factory = null;
        net.minecraft.network.FriendlyByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        write(pkt, buf);
        return buf;
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
}
