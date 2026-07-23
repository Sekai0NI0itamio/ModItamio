package asd.itamio.worldshop;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import asd.itamio.ModInfoPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.List;

@Mod(WorldShop.MOD_ID)
public class WorldShop {
    public static final String MOD_ID = "worldshop";
    public static final String MOD_NAME = "Modern Shop";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static List<ShopCategory> categories = Collections.emptyList();
    private static PriceEngine priceEngine = new PriceEngine();
    private static MinecraftServer currentServer = null;
    /** Grid slot -> category index (-1 = empty). Persists layout positions. */
    private static int[] categorySlotPositions = null;
    private static ShopConfig shopConfig = null;
    private static EconomyProvider externalEconomyProvider = null;

    public WorldShop(IEventBus modEventBus) {
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("Modern Shop mod initializing...");

        // Register payload handlers (networking)
        modEventBus.addListener(this::onRegisterPayloadHandlers);

        // Register event bus handlers
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);

        // Common setup
        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // Initialize a client-side PriceConfig so pricing works in single-player
        // dev environments. The server-side config is created in onPlayerLogin /
        // onServerStarting when the server is fully available.
        try {
            PriceConfig clientConfig = PriceConfig.forClient();
            if (clientConfig != null) {
                priceEngine.setPriceConfig(clientConfig);
                LOGGER.info("Modern Shop price config initialized: {}", clientConfig.getConfigFilePath());
            }
        } catch (Throwable t) {
            LOGGER.warn("Could not initialize client PriceConfig (likely dedicated server side): {}", t.getMessage());
        }

        buildShopCategories();
        LOGGER.info("Modern Shop initialized with {} categories", categories.size());
    }

    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // Server-bound: shop actions (buy, sell, etc.)
        registrar.playToServer(
                ShopPacket.TYPE,
                ShopPacket.STREAM_CODEC,
                new ShopPacketHandler()
        );
        // Client-bound: open GUIs
        registrar.playToClient(
                ShopPacket.TYPE,
                ShopPacket.STREAM_CODEC,
                new ShopClientPacketHandler()
        );
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        CommandShop.register(dispatcher);
        CommandSellHand.register(dispatcher);
        CommandSellGui.register(dispatcher);
        CommandBalance.register(dispatcher);
        CommandPay.register(dispatcher);
        CommandBalance.registerAlias(dispatcher);
        CommandShopReset.register(dispatcher);
        LOGGER.info("Modern Shop commands registered");
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            EconomyProvider economy = getEconomyProvider(level);
            economy.registerPlayer(level, player.getGameProfile().getName(), player.getUUID());
            LOGGER.info("Registered player {} -> {}", player.getGameProfile().getName(), player.getUUID());

            // Re-initialize PriceConfig and ShopConfig with the server's config
            // directory on first login (server may not be set yet during very
            // early connection events, so we guard with a null check).
            MinecraftServer server = player.getServer();
            if (server != null && (currentServer == null || currentServer != server)) {
                currentServer = server;
                PriceConfig serverConfig = PriceConfig.forServer(server);
                priceEngine.setPriceConfig(serverConfig);
                LOGGER.info("Modern Shop server price config initialized: {}", serverConfig.getConfigFilePath());
                shopConfig = ShopConfig.forServer(server);
                LOGGER.info("Modern Shop config initialized: sellhandConfirmation={}", shopConfig.isSellhandConfirmation());
                applyPersistedCategoryOrder();
            }
        }
    }

    private void onServerStarting(ServerStartingEvent event) {
        // Make sure currentServer is set so commands/handlers that need the
        // config directory can use it. The first player login also sets this,
        // but onServerStarting fires earlier and is a more reliable point.
        if (currentServer == null) {
            currentServer = event.getServer();
            PriceConfig serverConfig = PriceConfig.forServer(currentServer);
            priceEngine.setPriceConfig(serverConfig);
            LOGGER.info("Modern Shop server price config initialized (onServerStarting): {}", serverConfig.getConfigFilePath());
            shopConfig = ShopConfig.forServer(currentServer);
            LOGGER.info("Modern Shop config initialized (onServerStarting): sellhandConfirmation={}", shopConfig.isSellhandConfirmation());
            applyPersistedCategoryOrder();
        }
        LOGGER.info("Modern Shop server starting");
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
     * Build a center-sphere default layout: categories cluster in the center
     * of the grid, expanding outward in a filled-circle pattern.
     *
     * <p>This 2-arg overload assumes the legacy default of 3 visible rows.
     * Prefer the 3-arg overload that takes the actual visible row count so
     * the cluster is centered in the visible viewport rather than the full
     * scrollable area.
     *
     * @param numCategories Number of categories to place
     * @param columns       Grid column count (must match the rendering columns
     *                      used by GuiShopCategories, currently 9)
     * @return int[] of slotIndex -> categoryIndex (-1 = empty).
     */
    public static int[] buildCenterSphereLayout(int numCategories, int columns) {
        return buildCenterSphereLayout(numCategories, columns, 3);
    }

    /**
     * Build a center-sphere default layout centered on the VISIBLE viewport.
     *
     * <p>Categories cluster around the center of the visible rows (the area
     * the user sees without scrolling). If there are more categories than fit
     * in the visible area, overflow extends DOWNWARD below the visible
     * viewport — so the user sees a dense center cluster first, then scrolls
     * down for the remaining categories.
     *
     * @param numCategories Number of categories to place
     * @param columns       Grid column count (must match the rendering columns
     *                      used by GuiShopCategories, currently 9)
     * @param visibleRows   Number of rows visible without scrolling. The
     *                      sphere center is placed at row {@code visibleRows/2}
     *                      so the cluster appears centered in the viewport.
     * @return int[] of slotIndex -> categoryIndex (-1 = empty). Size is
     *         {@code totalRows * columns} where {@code totalRows} is the larger
     *         of {@code visibleRows} and the rows needed to hold all categories.
     */
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

    /**
     * Save the current category order to a simple JSON file in the server config directory.
     */
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
            WorldShop.LOGGER.info("[CAT_ORDER] Saved category order to {} ({} categories)", orderFile.getAbsolutePath(), names.size());
        } catch (Exception e) {
            WorldShop.LOGGER.error("[CAT_ORDER] Failed to save category order: {}", e.getMessage());
        }
    }

    /**
     * Apply the persisted category order from worldshop_category_order.json.
     * Called when the server is fully started and the config directory is available.
     */
    public static void applyPersistedCategoryOrder() {
        if (currentServer == null) {
            WorldShop.LOGGER.warn("[CAT_ORDER] Cannot apply category order: currentServer is null");
            return;
        }
        try {
            File configDir = new File(currentServer.getServerDirectory().toFile(), "config");
            if (!configDir.exists()) {
                return;
            }
            File orderFile = new File(configDir, "worldshop_category_order.json");
            if (!orderFile.exists()) {
                return;
            }

            StringBuilder content = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(orderFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
            }

            String rawContent = content.toString().trim();
            if (rawContent.isEmpty() || rawContent.equals("[]") || rawContent.equals("null")) {
                return;
            }

            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.List<String>>() {}.getType();
            java.util.List<String> nameOrder = null;
            try {
                nameOrder = gson.fromJson(rawContent, listType);
            } catch (Exception e) {
                WorldShop.LOGGER.warn("[CAT_ORDER] Category order file is corrupted (parse error: {}), not applying", e.getMessage());
                return;
            }

            if (nameOrder == null || nameOrder.isEmpty()) {
                return;
            }

            // Validate: every name in the saved order must exist in the current categories
            for (String name : nameOrder) {
                boolean found = false;
                for (ShopCategory cat : categories) {
                    if (cat.getName().equals(name)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    WorldShop.LOGGER.warn("[CAT_ORDER] Saved category '{}' not found in current categories. Not applying order.", name);
                    return;
                }
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
            } else {
                WorldShop.LOGGER.warn("[CAT_ORDER] Category count mismatch: saved {} names, but have {} categories. Not applying order.",
                    nameOrder.size(), categories.size());
            }
        } catch (Exception e) {
            WorldShop.LOGGER.error("[CAT_ORDER] Could not apply persisted category order: {}", e.getMessage());
        }
    }

    public static PriceEngine getPriceEngine() {
        return priceEngine;
    }

    /**
     * Get the current ShopConfig (may be null on client before server is initialized).
     */
    public static ShopConfig getShopConfig() {
        return shopConfig;
    }

    /**
     * Get the current server instance (may be null on client).
     */
    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }

    /**
     * Register an external economy provider to override the built-in economy.
     */
    public static void setEconomyProvider(EconomyProvider provider) {
        externalEconomyProvider = provider;
        LOGGER.info("External economy provider registered: {}", provider.getClass().getName());
    }

    /**
     * Get the active economy provider.
     * Returns the external provider if one was registered, otherwise falls back to the built-in EconomyData.
     */
    public static EconomyProvider getEconomyProvider(ServerLevel level) {
        if (externalEconomyProvider != null) {
            return externalEconomyProvider;
        }
        return EconomyData.get(level);
    }

    /**
     * Check if an external economy provider is currently registered.
     */
    public static boolean hasExternalEconomyProvider() {
        return externalEconomyProvider != null;
    }
}
