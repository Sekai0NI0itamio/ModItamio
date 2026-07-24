package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client-side handler for shop packets.
 * Handles opening GUIs and deep-linking to item detail views.
 */
@SideOnly(Side.CLIENT)
public class ClientPacketHandler implements IMessageHandler<ShopPacket, IMessage> {
    @Override
    public IMessage onMessage(ShopPacket message, MessageContext ctx) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
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
        return null;
    }
}
