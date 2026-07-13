package asd.itamio.worldshop;

import asd.itamio.ModInfoPrinter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class WorldShop implements ModInitializer {
    public static final String MOD_ID = "worldshop";
    public static final String MOD_NAME = "World Shop";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static List<ShopCategory> categories = Collections.emptyList();
    private static PriceEngine priceEngine = new PriceEngine();
    private static MinecraftServer currentServer = null;

    @Override
    public void onInitialize() {
        ModInfoPrinter.print(new ModInfoPrinter.LogLine() {
            @Override
            public void log(String msg) {
                LOGGER.info(msg);
            }
        }, MOD_NAME, VERSION);
        LOGGER.info("World Shop mod initializing...");

        // Initialize PriceConfig from the server config directory
        // This will be properly set later when the server starts, but we create an initial client-side one too
        PriceConfig clientConfig = PriceConfig.forClient();
        if (clientConfig != null) {
            priceEngine.setPriceConfig(clientConfig);
            LOGGER.info("World Shop price config initialized: {}", clientConfig.getConfigFilePath());
        }

        // Register server packet handler
        ServerPlayNetworking.registerGlobalReceiver(ShopPacket.PACKET_ID, new ServerPlayNetworking.PlayChannelHandler() {
            @Override
            public void receive(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf, net.fabricmc.fabric.api.networking.v1.PacketSender responseSender) {
                ShopPacket packet = ShopPacket.read(buf);
                server.execute(new Runnable() {
                    @Override
                    public void run() {
                        ServerPacketHandler.handle(packet, player);
                    }
                });
            }
        });

        // Register commands
        CommandRegistrationCallback.EVENT.register(new CommandRegistrationCallback() {
            @Override
            public void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher, net.minecraft.commands.CommandBuildContext registryAccess, net.minecraft.commands.Commands.CommandSelection environment) {
                ModCommands.register(dispatcher);
            }
        });

        // Register player login handler
        ServerPlayConnectionEvents.JOIN.register(new ServerPlayConnectionEvents.Join() {
            @Override
            public void onPlayReady(ServerGamePacketListenerImpl handler, net.fabricmc.fabric.api.networking.v1.PacketSender sender, MinecraftServer server) {
                ServerPlayer player = handler.getPlayer();
                EconomyData economy = EconomyData.get(player.serverLevel());
                economy.registerPlayer(player.getScoreboardName(), player.getUUID());
                LOGGER.info("Registered player {} -> {}", player.getScoreboardName(), player.getUUID());

                // Re-initialize PriceConfig with the server's config directory
                // This ensures prices are properly persisted server-side
                if (currentServer == null || currentServer != server) {
                    currentServer = server;
                    PriceConfig serverConfig = PriceConfig.forServer(server);
                    priceEngine.setPriceConfig(serverConfig);
                    LOGGER.info("World Shop server price config initialized: {}", serverConfig.getConfigFilePath());
                    // Apply persisted category order after server config is ready
                    applyPersistedCategoryOrder();
                }
            }
        });

        buildShopCategories();
        LOGGER.info("World Shop initialized with {} categories", categories.size());
    }

    public static void buildShopCategories() {
        categories = ShopCategory.buildFromCreativeTabs();
        priceEngine.clearCache();
        LOGGER.info("Built {} shop categories from creative tabs", categories.size());
    }

    public static List<ShopCategory> getCategories() {
        return categories;
    }

    /**
     * Apply the persisted category order from ShopData (if available).
     * Called when the server is fully started and ShopData can be loaded.
     */
    public static void applyPersistedCategoryOrder() {
        if (currentServer == null) return;
        try {
            File configDir = new File(currentServer.getServerDirectory(), "config");
            if (!configDir.exists()) return;
            ShopData shopData = new ShopData(configDir);
            List<String> nameOrder = shopData.getCategoryOrder();
            if (nameOrder == null || nameOrder.isEmpty()) return;

            // Reorder categories to match the saved order
            List<ShopCategory> reordered = new java.util.ArrayList<>();
            for (String name : nameOrder) {
                for (ShopCategory cat : categories) {
                    if (cat.getName().equals(name) && !reordered.contains(cat)) {
                        reordered.add(cat);
                        break;
                    }
                }
            }
            // Add any new categories not in the saved order
            for (ShopCategory cat : categories) {
                if (!reordered.contains(cat)) {
                    reordered.add(cat);
                }
            }
            if (reordered.size() == categories.size()) {
                categories.clear();
                categories.addAll(reordered);
                LOGGER.info("Applied persisted category order from shop_data.json ({} categories)", categories.size());
            }
        } catch (Exception e) {
            LOGGER.warn("Could not apply persisted category order: {}", e.getMessage());
        }
    }

    public static PriceEngine getPriceEngine() {
        return priceEngine;
    }

    /**
     * Get the current server instance (may be null on client).
     */
    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }
}
