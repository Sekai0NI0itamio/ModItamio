package asd.itamio.worldshop;

import asd.itamio.ModInfoPrinter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class WorldShop implements ModInitializer {
    public static final String MOD_ID = "worldshop";
    public static final String MOD_NAME = "World Shop";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static List<ShopCategory> categories = Collections.emptyList();
    private static PriceEngine priceEngine = new PriceEngine();

    @Override
    public void onInitialize() {
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("World Shop mod initializing...");

        // Register networking
        PayloadTypeRegistry.playC2S().register(ShopPayload.TYPE, ShopPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ShopPayload.TYPE, ShopPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ShopPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ShopPacketHandler.handle(context.player(), payload.message());
            });
        });

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CommandShop.register(dispatcher);
            CommandSellHand.register(dispatcher);
            CommandSellGui.register(dispatcher);
            CommandBalance.register(dispatcher);
            CommandPay.register(dispatcher);
        });

        // Register player join event
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            EconomyData economy = EconomyData.get(server.getLevel(server.overworld().dimension()));
            economy.registerPlayer(handler.player.getScoreboardName(), handler.player.getUUID());
            LOGGER.info("Registered player {} -> {}", handler.player.getScoreboardName(), handler.player.getUUID());
        });

        // Build shop categories on world load
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            priceEngine.setServer(server);
            buildShopCategories(server);
        });

        LOGGER.info("World Shop initialized");
    }

    private void buildShopCategories(MinecraftServer server) {
        categories = ShopCategory.buildFromItemGroups(server);
        priceEngine.clearCache();
        LOGGER.info("Built " + categories.size() + " shop categories from item groups");
    }

    public static List<ShopCategory> getCategories() {
        return categories;
    }

    public static PriceEngine getPriceEngine() {
        return priceEngine;
    }
}
