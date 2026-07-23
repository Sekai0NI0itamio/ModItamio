package asd.itamio.worldshop;

import asd.itamio.ModInfoPrinter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class WorldShop implements ModInitializer {
    public static final String MOD_ID = "worldshop";
    public static final String MOD_NAME = "Modern Shop";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static List<ShopCategory> categories = Collections.emptyList();
    private static PriceEngine priceEngine = new PriceEngine();
    private static MinecraftServer currentServer = null;
    private static int[] categorySlotPositions = null;
    private static ShopConfig shopConfig = null;
    private static EconomyProvider externalEconomyProvider = null;

    @Override
    public void onInitialize() {
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("Modern Shop mod initializing...");

        // Initialize client-side PriceConfig
        try {
            PriceConfig clientConfig = PriceConfig.forClient();
            if (clientConfig != null) {
                priceEngine.setPriceConfig(clientConfig);
                LOGGER.info("Modern Shop price config initialized: {}", clientConfig.getConfigFilePath());
            }
        } catch (Exception e) {
            LOGGER.warn("Could not initialize client PriceConfig: {}", e.getMessage());
        }

        // Register packet types
        PayloadTypeRegistry.serverboundPlay().register(ShopPacket.PACKET_TYPE, ShopPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ShopPacket.PACKET_TYPE, ShopPacket.STREAM_CODEC);

        // Register server packet handler
        ServerPlayNetworking.registerGlobalReceiver(ShopPacket.PACKET_TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                ServerPacketHandler.handle(payload, player);
            });
        });

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModCommands.register(dispatcher);
        });

        // Register player join event
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            ServerLevel level = player.level();
            EconomyProvider economy = getEconomyProvider(level);
            economy.registerPlayer(level, player.getScoreboardName(), player.getUUID());
            LOGGER.info("Registered player {} -> {}", player.getScoreboardName(), player.getUUID());

            if (currentServer == null || currentServer != server) {
                currentServer = server;
                try {
                    PriceConfig serverConfig = PriceConfig.forServer(server);
                    priceEngine.setPriceConfig(serverConfig);
                    LOGGER.info("Modern Shop server price config initialized: {}", serverConfig.getConfigFilePath());
                    shopConfig = ShopConfig.forServer(server);
                    LOGGER.info("Modern Shop config initialized: sellhandConfirmation={}", shopConfig.isSellhandConfirmation());
                    applyPersistedCategoryOrder();
                } catch (Exception e) {
                    LOGGER.warn("Could not initialize server configs: {}", e.getMessage());
                }
            }
        });

        // Build shop categories on world load
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            priceEngine.setServer(server);
            buildShopCategories(server);
        });

        buildShopCategories();
        LOGGER.info("Modern Shop initialized with {} categories", categories.size());
    }

    public static void buildShopCategories() {
        categories = ShopCategory.buildFromCreativeTabs();
        priceEngine.clearCache();
        LOGGER.info("Built {} shop categories from creative tabs", categories.size());
    }

    public static void buildShopCategories(MinecraftServer server) {
        categories = ShopCategory.buildFromItemGroups(server);
        priceEngine.clearCache();
        LOGGER.info("Built {} shop categories from item groups", categories.size());
    }

    public static List<ShopCategory> getCategories() {
        return categories;
    }

    public static int[] getCategorySlotPositions() {
        return categorySlotPositions;
    }

    public static void setCategorySlotPositions(int[] positions) {
        categorySlotPositions = positions;
    }

    public static int[] buildCenterSphereLayout(int numCategories, int columns) {
        return buildCenterSphereLayout(numCategories, columns, 3);
    }

    public static int[] buildCenterSphereLayout(int numCategories, int columns, int visibleRows) {
        if (numCategories <= 0) return new int[0];
        if (columns <= 0) columns = 9;
        if (visibleRows <= 0) visibleRows = 3;

        int rowsNeeded = (int) Math.ceil((double) numCategories / columns);
        int totalRows = Math.max(visibleRows, rowsNeeded);
        int gridSize = totalRows * columns;

        int centerCol = columns / 2;
        int centerRow = visibleRows / 2;

        java.util.List<int[]> slots = new java.util.ArrayList<>();
        for (int row = 0; row < totalRows; row++) {
            for (int col = 0; col < columns; col++) {
                int slot = row * columns + col;
                double dx = col - centerCol;
                double dy = row - centerRow;
                int dist = (int) Math.round((dx * dx + dy * dy) * 100);
                slots.add(new int[]{slot, dist});
            }
        }

        slots.sort((a, b) -> Integer.compare(a[1], b[1]));

        int[] layout = new int[gridSize];
        java.util.Arrays.fill(layout, -1);
        for (int i = 0; i < numCategories && i < slots.size(); i++) {
            layout[slots.get(i)[0]] = i;
        }

        LOGGER.info("[LAYOUT] Built center-sphere layout: {} categories in {}x{} grid (visibleRows={}, center={},{}), gridSize={}",
                numCategories, columns, totalRows, visibleRows, centerCol, centerRow, gridSize);
        return layout;
    }

    public static void saveCategoryOrder(List<ShopCategory> orderedCategories) {
        if (currentServer == null) {
            WorldShop.LOGGER.warn("[CAT_ORDER] Cannot save category order: no server instance");
            return;
        }
        try {
            File configDir = new File(currentServer.getServerDirectory().toFile(), "config");
            if (!configDir.exists()) configDir.mkdirs();
            File orderFile = new File(configDir, "worldshop_category_order.json");

            List<String> names = new java.util.ArrayList<>();
            for (ShopCategory cat : orderedCategories) {
                names.add(cat.getName());
            }

            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(names);
            try (java.io.FileWriter writer = new java.io.FileWriter(orderFile)) {
                writer.write(json);
                writer.flush();
            }
            WorldShop.LOGGER.info("[CAT_ORDER] SAVED category order to {} ({} categories)", orderFile.getAbsolutePath(), names.size());
        } catch (Exception e) {
            WorldShop.LOGGER.error("[CAT_ORDER] Failed to save category order: {}", e.getMessage());
        }
    }

    public static void applyPersistedCategoryOrder() {
        if (currentServer == null) {
            return;
        }
        try {
            File configDir = new File(currentServer.getServerDirectory().toFile(), "config");
            if (!configDir.exists()) return;
            File orderFile = new File(configDir, "worldshop_category_order.json");
            if (!orderFile.exists()) return;

            StringBuilder content = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(orderFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
            }

            String rawContent = content.toString().trim();
            if (rawContent.isEmpty() || rawContent.equals("[]") || rawContent.equals("null")) return;

            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.List<String>>() {}.getType();
            java.util.List<String> nameOrder = gson.fromJson(rawContent, listType);
            if (nameOrder == null || nameOrder.isEmpty()) return;

            for (String name : nameOrder) {
                boolean found = false;
                for (ShopCategory cat : categories) {
                    if (cat.getName().equals(name)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return;
            }

            List<ShopCategory> reordered = new java.util.ArrayList<>();
            for (String name : nameOrder) {
                for (ShopCategory cat : categories) {
                    if (cat.getName().equals(name) && !reordered.contains(cat)) {
                        reordered.add(cat);
                        break;
                    }
                }
            }
            for (ShopCategory cat : categories) {
                if (!reordered.contains(cat)) {
                    reordered.add(cat);
                }
            }
            if (reordered.size() == categories.size()) {
                categories.clear();
                categories.addAll(reordered);
                WorldShop.LOGGER.info("[CAT_ORDER] Applied persisted category order ({} categories)", categories.size());
            }
        } catch (Exception e) {
            WorldShop.LOGGER.error("[CAT_ORDER] Could not apply persisted category order: {}", e.getMessage());
        }
    }

    public static PriceEngine getPriceEngine() {
        return priceEngine;
    }

    public static ShopConfig getShopConfig() {
        return shopConfig;
    }

    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }

    public static void setEconomyProvider(EconomyProvider provider) {
        externalEconomyProvider = provider;
        LOGGER.info("External economy provider registered: {}", provider.getClass().getName());
    }

    public static EconomyProvider getEconomyProvider(ServerLevel level) {
        if (externalEconomyProvider != null) {
            return externalEconomyProvider;
        }
        return EconomyData.get(level);
    }

    public static boolean hasExternalEconomyProvider() {
        return externalEconomyProvider != null;
    }
}
