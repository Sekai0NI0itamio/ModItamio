package asd.itamio.worldshop;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class ShopPayload implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ShopPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(WorldShop.MOD_ID, "shop_packet"));

    public static final StreamCodec<FriendlyByteBuf, ShopPayload> STREAM_CODEC = new StreamCodec<FriendlyByteBuf, ShopPayload>() {
        @Override
        public ShopPayload decode(FriendlyByteBuf buf) {
            int type = buf.readVarInt();
            switch (type) {
                case ShopMessage.OPEN_SHOP:
                    return new ShopPayload(ShopMessage.openShop());
                case ShopMessage.BUY_ITEM:
                    return new ShopPayload(ShopMessage.buyItem(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
                case ShopMessage.SELL_HAND:
                    return new ShopPayload(ShopMessage.sellHand());
                case ShopMessage.OPEN_SELL_GUI:
                    return new ShopPayload(ShopMessage.openSellGui());
                case ShopMessage.SELL_GUI_ITEMS: {
                    int count = buf.readVarInt();
                    List<ShopMessage.SellSlotEntry> entries = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        int slotIndex = buf.readVarInt();
                        int quantity = buf.readVarInt();
                        entries.add(new ShopMessage.SellSlotEntry(slotIndex, quantity));
                    }
                    ShopMessage msg = ShopMessage.sellGuiItems();
                    msg.setSellSlotEntries(entries);
                    return new ShopPayload(msg);
                }
                default:
                    ShopMessage msg = new ShopMessage();
                    msg.type = type;
                    return new ShopPayload(msg);
            }
        }

        @Override
        public void encode(FriendlyByteBuf buf, ShopPayload payload) {
            ShopMessage msg = payload.message;
            buf.writeVarInt(msg.type);
            switch (msg.type) {
                case ShopMessage.BUY_ITEM:
                    buf.writeVarInt(msg.categoryIndex);
                    buf.writeVarInt(msg.itemIndex);
                    buf.writeVarInt(msg.quantity);
                    break;
                case ShopMessage.SELL_GUI_ITEMS:
                    if (msg.getSellSlotEntries() == null || msg.getSellSlotEntries().isEmpty()) {
                        buf.writeVarInt(0);
                    } else {
                        buf.writeVarInt(msg.getSellSlotEntries().size());
                        for (ShopMessage.SellSlotEntry entry : msg.getSellSlotEntries()) {
                            buf.writeVarInt(entry.slotIndex);
                            buf.writeVarInt(entry.quantity);
                        }
                    }
                    break;
            }
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

        private int type;
        private int categoryIndex;
        private int itemIndex;
        private int quantity;
        private List<SellSlotEntry> sellSlotEntries;

        public ShopMessage() {}

        public static ShopMessage openShop() {
            ShopMessage msg = new ShopMessage();
            msg.type = OPEN_SHOP;
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

        public int getType() { return type; }
        public int getCategoryIndex() { return categoryIndex; }
        public int getItemIndex() { return itemIndex; }
        public int getQuantity() { return quantity; }
        public List<SellSlotEntry> getSellSlotEntries() { return sellSlotEntries != null ? sellSlotEntries : new ArrayList<>(); }
        public void setSellSlotEntries(List<SellSlotEntry> entries) { this.sellSlotEntries = entries; }

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
