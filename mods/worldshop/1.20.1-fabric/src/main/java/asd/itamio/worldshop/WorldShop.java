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
    /** Grid slot -> category index (-1 = empty). Persists layout positions. */
    private static int[] categorySlotPositions = null;

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

    /** Get the persisted grid slot positions (grid slot -> category index, -1 = empty). */
    public static int[] getCategorySlotPositions() {
        return categorySlotPositions;
    }

    /** Set the persisted grid slot positions for reuse across screens. */
    public static void setCategorySlotPositions(int[] positions) {
        categorySlotPositions = positions;
    }

    /**
     * Save the current category order to a simple JSON file in the server config directory.
     * This is called from ServerPacketHandler.handleReorderCategories.
     */
    public static void saveCategoryOrder(List<ShopCategory> orderedCategories) {
        if (currentServer == null) {
            WorldShop.LOGGER.warn("[CAT_ORDER] Cannot save category order: no server instance");
            return;
        }
        try {
            File configDir = new File(currentServer.getServerDirectory(), "config");
            if (!configDir.exists()) configDir.mkdirs();
            File orderFile = new File(configDir, "worldshop_category_order.json");

            // Build a list of category names in the current display order
            List<String> names = new java.util.ArrayList<>();
            for (ShopCategory cat : orderedCategories) {
                names.add(cat.getName());
            }

            // Write as JSON array
            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(names);
            try (java.io.FileWriter writer = new java.io.FileWriter(orderFile)) {
                writer.write(json);
                writer.flush();
            }
            WorldShop.LOGGER.info("[CAT_ORDER] SAVED category order to {} ({} categories): {}", orderFile.getAbsolutePath(), names.size(), names);
            // Verify by re-reading immediately
            if (orderFile.exists()) {
                WorldShop.LOGGER.info("[CAT_ORDER] Verify: file exists at {}", orderFile.getAbsolutePath());
                StringBuilder verifyContent = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(orderFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        verifyContent.append(line);
                    }
                }
                WorldShop.LOGGER.info("[CAT_ORDER] Verify: file content = {}", verifyContent.toString());
            } else {
                WorldShop.LOGGER.error("[CAT_ORDER] Verify FAILED: file does NOT exist after write!");
            }
        } catch (Exception e) {
            WorldShop.LOGGER.error("[CAT_ORDER] Failed to save category order: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Apply the persisted category order from worldshop_category_order.json.
     * Called when the server is fully started and the config directory is available.
     */
    public static void applyPersistedCategoryOrder() {
        WorldShop.LOGGER.info("[CAT_ORDER] applyPersistedCategoryOrder() called — currentServer={}", currentServer != null ? currentServer.getServerDirectory().getAbsolutePath() : "null");
        if (currentServer == null) {
            WorldShop.LOGGER.warn("[CAT_ORDER] Cannot apply category order: currentServer is null");
            return;
        }
        try {
            File configDir = new File(currentServer.getServerDirectory(), "config");
            WorldShop.LOGGER.info("[CAT_ORDER] Config dir = {}", configDir.getAbsolutePath());
            WorldShop.LOGGER.info("[CAT_ORDER] Config dir exists = {}", configDir.exists());
            if (configDir.exists()) {
                String[] files = configDir.list();
                if (files != null) {
                    WorldShop.LOGGER.info("[CAT_ORDER] Files in config dir: {}", String.join(", ", files));
                }
            }
            if (!configDir.exists()) {
                WorldShop.LOGGER.info("[CAT_ORDER] Config dir does not exist, cannot load category order");
                return;
            }
            File orderFile = new File(configDir, "worldshop_category_order.json");
            WorldShop.LOGGER.info("[CAT_ORDER] Order file path = {}", orderFile.getAbsolutePath());
            WorldShop.LOGGER.info("[CAT_ORDER] Order file exists = {}", orderFile.exists());
            if (!orderFile.exists()) {
                WorldShop.LOGGER.info("[CAT_ORDER] No persisted category order file found");
                return;
            }

            // Read JSON array of category names
            StringBuilder content = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(orderFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
            }
            WorldShop.LOGGER.info("[CAT_ORDER] Raw file content: '{}'", content.toString());

            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.List<String>>() {}.getType();
            java.util.List<String> nameOrder = gson.fromJson(content.toString(), listType);
            if (nameOrder == null || nameOrder.isEmpty()) {
                WorldShop.LOGGER.info("[CAT_ORDER] Parsed nameOrder is null or empty, not applying");
                return;
            }

            WorldShop.LOGGER.info("[CAT_ORDER] Loaded {} category names from file: {}", nameOrder.size(), nameOrder);

            // Log current categories before reordering
            List<String> currentNames = new java.util.ArrayList<>();
            for (ShopCategory c : categories) currentNames.add(c.getName());
            WorldShop.LOGGER.info("[CAT_ORDER] Current categories before reorder ({}): {}", categories.size(), currentNames);

            // Reorder the in-memory categories list to match the saved order
            List<ShopCategory> reordered = new java.util.ArrayList<>();
            for (String name : nameOrder) {
                boolean found = false;
                for (ShopCategory cat : categories) {
                    if (cat.getName().equals(name) && !reordered.contains(cat)) {
                        reordered.add(cat);
                        found = true;
                        WorldShop.LOGGER.info("[CAT_ORDER]   Matched name '{}' -> category '{}'", name, cat.getName());
                        break;
                    }
                }
                if (!found) {
                    WorldShop.LOGGER.warn("[CAT_ORDER]   Name '{}' did NOT match any existing category!", name);
                }
            }
            // Add any new categories not in the saved order (e.g. from mods)
            for (ShopCategory cat : categories) {
                if (!reordered.contains(cat)) {
                    reordered.add(cat);
                    WorldShop.LOGGER.info("[CAT_ORDER]   Added new category not in saved order: {}", cat.getName());
                }
            }
            if (reordered.size() == categories.size()) {
                categories.clear();
                categories.addAll(reordered);
                WorldShop.LOGGER.info("[CAT_ORDER] SUCCESS: Applied persisted category order ({} categories)", categories.size());
                // Log final order
                StringBuilder orderStr = new StringBuilder();
                for (int i = 0; i < categories.size(); i++) {
                    if (i > 0) orderStr.append(", ");
                    orderStr.append(i).append(":").append(categories.get(i).getName());
                }
                WorldShop.LOGGER.info("[CAT_ORDER] Final category order: [{}]", orderStr.toString());
            } else {
                WorldShop.LOGGER.warn("[CAT_ORDER] Category count mismatch: saved {} names, but have {} categories. Not applying order.",
                    nameOrder.size(), categories.size());
            }
        } catch (Exception e) {
            WorldShop.LOGGER.error("[CAT_ORDER] Could not apply persisted category order: {}", e.getMessage());
            e.printStackTrace();
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
