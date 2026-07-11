package asd.itamio.worldshop;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class WorldShopClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register client-side packet receiver for S2C payloads
        ClientPlayNetworking.registerGlobalReceiver(ShopPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                switch (payload.message().getType()) {
                    case ShopPayload.ShopMessage.OPEN_SHOP:
                        context.client().setScreen(new GuiShopCategories());
                        break;
                    case ShopPayload.ShopMessage.OPEN_SELL_GUI:
                        context.client().setScreen(new GuiSellGui());
                        break;
                }
            });
        });
    }
}
