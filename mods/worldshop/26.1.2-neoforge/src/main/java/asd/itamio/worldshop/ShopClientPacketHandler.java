package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

@OnlyIn(Dist.CLIENT)
public class ShopClientPacketHandler implements IPayloadHandler<ShopPacket> {
    @Override
    public void handle(ShopPacket message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            switch (message.getType()) {
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
                    java.util.List<ShopCategory> cats = WorldShop.getCategories();
                    int catIdx = message.getCategoryIndex();
                    int itemIdx = message.getItemIndex();
                    if (catIdx >= 0 && catIdx < cats.size()) {
                        ShopCategory cat = cats.get(catIdx);
                        GuiShopItems screen = new GuiShopItems(cat, catIdx);
                        screen.setPendingDetail(itemIdx);
                        ScreenManager.open(screen);
                    }
                    break;
                }
            }
        });
    }
}
