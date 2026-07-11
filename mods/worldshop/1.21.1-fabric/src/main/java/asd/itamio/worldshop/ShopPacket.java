package asd.itamio.worldshop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopPacket implements CustomPacketPayload {
    public static final int OPEN_SHOP = 0;
    public static final int BUY_ITEM = 1;
    public static final int SELL_HAND = 2;
    public static final int OPEN_SELL_GUI = 3;
    public static final int SELL_GUI_ITEMS = 4;

    public static final CustomPacketPayload.Type<ShopPacket> PACKET_TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WorldShop.MOD_ID, "shop_packet"));

    public static final StreamCodec<FriendlyByteBuf, ShopPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ShopPacket decode(FriendlyByteBuf buf) {
            int type = buf.readInt();
            ShopPacket pkt = new ShopPacket();
            pkt.type = type;
            if (type == BUY_ITEM) {
                pkt.categoryIndex = buf.readInt();
                pkt.itemIndex = buf.readInt();
                pkt.quantity = buf.readInt();
            } else if (type == SELL_GUI_ITEMS) {
                int count = buf.readInt();
                List<ItemStack> items = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    items.add(readItemStack(buf));
                }
                pkt.items = items;
            }
            return pkt;
        }

        @Override
        public void encode(FriendlyByteBuf buf, ShopPacket pkt) {
            buf.writeInt(pkt.type);
            if (pkt.type == BUY_ITEM) {
                buf.writeInt(pkt.categoryIndex);
                buf.writeInt(pkt.itemIndex);
                buf.writeInt(pkt.quantity);
            } else if (pkt.type == SELL_GUI_ITEMS) {
                if (pkt.items == null) {
                    buf.writeInt(0);
                } else {
                    buf.writeInt(pkt.items.size());
                    for (ItemStack stack : pkt.items) {
                        writeItemStack(buf, stack);
                    }
                }
            }
        }
    };

    private int type;
    private int categoryIndex;
    private int itemIndex;
    private int quantity;
    private List<ItemStack> items;

    public ShopPacket() {
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_TYPE;
    }

    // Simplified ItemStack serialization (item ID + count)
    private static void writeItemStack(FriendlyByteBuf buf, ItemStack stack) {
        buf.writeBoolean(!stack.isEmpty());
        if (!stack.isEmpty()) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            buf.writeUtf(key.toString());
            buf.writeInt(stack.getCount());
        }
    }

    private static ItemStack readItemStack(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return ItemStack.EMPTY;
        }
        String key = buf.readUtf();
        int count = buf.readInt();
        ResourceLocation loc = ResourceLocation.parse(key);
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(loc), count);
        return stack;
    }
}
