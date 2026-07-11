package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

public class ShopClientPacketHandler implements IPayloadHandler<ShopPacket> {
    @Override
    @OnlyIn(Dist.CLIENT)
    public void handle(ShopPacket message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            switch (message.getType()) {
                case ShopPacket.OPEN_SHOP:
                    Minecraft.getInstance().setScreen(new GuiShopCategories());
                    break;
                case ShopPacket.OPEN_SELL_GUI:
                    Minecraft.getInstance().setScreen(new GuiSellGui());
                    break;
            }
        });
    }
}
