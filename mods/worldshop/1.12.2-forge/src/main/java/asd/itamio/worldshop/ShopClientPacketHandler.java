package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ShopClientPacketHandler implements IMessageHandler<ShopPacket, IMessage> {
    @Override
    @SideOnly(Side.CLIENT)
    public IMessage onMessage(ShopPacket message, MessageContext ctx) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            switch (message.getType()) {
                case ShopPacket.OPEN_SHOP:
                    Minecraft.getMinecraft().displayGuiScreen(new GuiShopCategories());
                    break;
                case ShopPacket.OPEN_SELL_GUI:
                    Minecraft.getMinecraft().displayGuiScreen(new GuiSellGui());
                    break;
            }
        });
        return null;
    }
}
