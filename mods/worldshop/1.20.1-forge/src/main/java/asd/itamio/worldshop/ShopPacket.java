package asd.itamio.worldshop;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopPacket {
    public static final int OPEN_SHOP = 0;
    public static final int BUY_ITEM = 1;
    public static final int SELL_HAND = 2;
    public static final int OPEN_SELL_GUI = 3;
    public static final int SELL_GUI_ITEMS = 4;

    private int type;
    private int categoryIndex;
    private int itemIndex;
    private int quantity;
    private List<ItemStack> items;

    public ShopPacket() {}

    public ShopPacket(FriendlyByteBuf buf) {
        this.type = buf.readInt();
        if (this.type == BUY_ITEM) {
            this.categoryIndex = buf.readInt();
            this.itemIndex = buf.readInt();
            this.quantity = buf.readInt();
        } else if (this.type == SELL_GUI_ITEMS) {
            int count = buf.readInt();
            this.items = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                this.items.add(buf.readItem());
            }
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(type);
        if (type == BUY_ITEM) {
            buf.writeInt(categoryIndex);
            buf.writeInt(itemIndex);
            buf.writeInt(quantity);
        } else if (type == SELL_GUI_ITEMS) {
            if (items == null) {
                buf.writeInt(0);
            } else {
                buf.writeInt(items.size());
                for (ItemStack stack : items) {
                    buf.writeItem(stack);
                }
            }
        }
    }

    public static ShopPacket openShop() {
        ShopPacket pkt = new ShopPacket();
        pkt.type = OPEN_SHOP;
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

    public int getType() { return type; }
    public int getCategoryIndex() { return categoryIndex; }
    public int getItemIndex() { return itemIndex; }
    public int getQuantity() { return quantity; }
    public List<ItemStack> getItems() { return items != null ? items : new ArrayList<>(); }
}
