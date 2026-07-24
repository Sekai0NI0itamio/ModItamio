package asd.itamio.worldshop;

import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet for shop network communication. Carries a type discriminator and
 * type-specific payload. Includes all admin and settings operations.
 */
public class ShopPacket implements IMessage {
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

    @Override
    public void toBytes(ByteBuf buf) {
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
                        ByteBufUtils.writeItemStack(buf, stack);
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
                ByteBufUtils.writeUTF8String(buf, stringData1 != null ? stringData1 : "");
                ByteBufUtils.writeUTF8String(buf, stringData2 != null ? stringData2 : "");
                break;
            case ADD_ITEM:
                buf.writeInt(categoryIndex);
                ByteBufUtils.writeUTF8String(buf, stringData1 != null ? stringData1 : "");
                break;
            case EDIT_ITEM:
                buf.writeInt(categoryIndex);
                ByteBufUtils.writeUTF8String(buf, stringData1 != null ? stringData1 : "");
                ByteBufUtils.writeUTF8String(buf, stringData2 != null ? stringData2 : "");
                ByteBufUtils.writeUTF8String(buf, stringData3 != null ? stringData3 : "");
                buf.writeDouble(doubleData1);
                buf.writeDouble(doubleData2);
                break;
            case SYNC_SHOP_DATA:
                ByteBufUtils.writeUTF8String(buf, stringData1 != null ? stringData1 : "");
                break;
            case REORDER_CATEGORIES:
                if (stringArrayData == null) {
                    buf.writeInt(0);
                } else {
                    buf.writeInt(stringArrayData.length);
                    for (String name : stringArrayData) {
                        ByteBufUtils.writeUTF8String(buf, name);
                    }
                }
                break;
            case RECALCULATE_CATEGORY:
            case RESET_CATEGORY:
                buf.writeInt(categoryIndex);
                break;
            case RECALCULATE_BLOCK:
            case RESET_BLOCK:
                ByteBufUtils.writeUTF8String(buf, stringData1 != null ? stringData1 : "");
                break;
            case SAVE_CONFIG:
                buf.writeDouble(doubleData1);
                break;
            case OPEN_ITEM_DETAIL:
                buf.writeInt(categoryIndex);
                buf.writeInt(itemIndex);
                break;
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        type = buf.readInt();
        switch (type) {
            case BUY_ITEM:
                categoryIndex = buf.readInt();
                itemIndex = buf.readInt();
                quantity = buf.readInt();
                break;
            case SELL_GUI_ITEMS:
                int count = buf.readInt();
                items = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    items.add(ByteBufUtils.readItemStack(buf));
                }
                break;
            case REMOVE_ITEM:
                categoryIndex = buf.readInt();
                itemIndex = buf.readInt();
                break;
            case REMOVE_CATEGORY:
                categoryIndex = buf.readInt();
                break;
            case ADD_CATEGORY:
                stringData1 = ByteBufUtils.readUTF8String(buf);
                stringData2 = ByteBufUtils.readUTF8String(buf);
                break;
            case ADD_ITEM:
                categoryIndex = buf.readInt();
                stringData1 = ByteBufUtils.readUTF8String(buf);
                break;
            case EDIT_ITEM:
                categoryIndex = buf.readInt();
                stringData1 = ByteBufUtils.readUTF8String(buf);
                stringData2 = ByteBufUtils.readUTF8String(buf);
                stringData3 = ByteBufUtils.readUTF8String(buf);
                doubleData1 = buf.readDouble();
                doubleData2 = buf.readDouble();
                break;
            case SYNC_SHOP_DATA:
                stringData1 = ByteBufUtils.readUTF8String(buf);
                break;
            case REORDER_CATEGORIES:
                int len = buf.readInt();
                stringArrayData = new String[len];
                for (int i = 0; i < len; i++) {
                    stringArrayData[i] = ByteBufUtils.readUTF8String(buf);
                }
                break;
            case RECALCULATE_CATEGORY:
            case RESET_CATEGORY:
                categoryIndex = buf.readInt();
                break;
            case RECALCULATE_BLOCK:
            case RESET_BLOCK:
                stringData1 = ByteBufUtils.readUTF8String(buf);
                break;
            case SAVE_CONFIG:
                doubleData1 = buf.readDouble();
                break;
            case OPEN_ITEM_DETAIL:
                categoryIndex = buf.readInt();
                itemIndex = buf.readInt();
                break;
        }
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
