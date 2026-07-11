package asd.itamio.worldshop;

import asd.itamio.ModInfoPrinter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
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

        // Register server packet handler
        ServerPlayNetworking.registerGlobalReceiver(ShopPacket.PACKET_ID, (server, player, handler, buf, responseSender) -> {
            ShopPacket packet = ShopPacket.read(buf);
            server.execute(() -> {
                ServerPacketHandler.handle(packet, player);
            });
        });

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModCommands.register(dispatcher);
        });

        // Register player login handler
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            EconomyData economy = EconomyData.get(player.serverLevel());
            economy.registerPlayer(player.getScoreboardName(), player.getUUID());
            LOGGER.info("Registered player {} -> {}", player.getScoreboardName(), player.getUUID());
        });

        buildShopCategories();
        LOGGER.info("World Shop initialized with {} categories", categories.size());
    }

    private void buildShopCategories() {
        categories = ShopCategory.buildFromCreativeTabs();
        priceEngine.clearCache();
        LOGGER.info("Built {} shop categories from creative tabs", categories.size());
    }

    public static List<ShopCategory> getCategories() {
        return categories;
    }

    public static PriceEngine getPriceEngine() {
        return priceEngine;
    }
}
