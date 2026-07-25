package asd.itamio.worldshop;

import asd.itamio.ModInfoPrinter;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@Mod(WorldShop.MOD_ID)
public class WorldShop {
    public static final String MOD_ID = "worldshop";
    public static final String MOD_NAME = "Modern Shop";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final String PROTOCOL_VERSION = "1";
    public static SimpleChannel NETWORK;

    private static List<ShopCategory> categories = Collections.emptyList();
    private static PriceEngine priceEngine = new PriceEngine();
    private static MinecraftServer currentServer = null;
    /** Grid slot -> category index (-1 = empty). Persists layout positions. */
    private static int[] categorySlotPositions = null;
    private static ShopConfig shopConfig = null;
    private static EconomyProvider externalEconomyProvider = null;

    public WorldShop() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("Modern Shop mod initializing...");

        // Initialize a client-side PriceConfig so the client can read cached
        // prices for display before the server pushes a server-side config.
        PriceConfig clientConfig = PriceConfig.forClient();
        if (clientConfig != null) {
            priceEngine.setPriceConfig(clientConfig);
            LOGGER.info("Modern Shop price config initialized: {}", clientConfig.getConfigFilePath());
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        NETWORK = NetworkRegistry.newSimpleChannel(
                new net.minecraft.resources.ResourceLocation(MOD_ID, "main"),
                () -> PROTOCOL_VERSION,
                s -> true,
                s -> true
        );

        NETWORK.registerMessage(0, ShopPacket.class,
                ShopPacket::toBytes,
                ShopPacket::new,
                WorldShop::handlePacket
        );

        LOGGER.info("Modern Shop network channel registered");

        // Build initial shop categories from creative tabs.
        event.enqueueWork(WorldShop::buildShopCategories);
    }

    private static void handlePacket(ShopPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            try {
                if (ctx.getDirection() == NetworkDirection.PLAY_TO_SERVER) {
                    ServerPlayer player = ctx.getSender();
                    if (player != null) {
                        ServerPacketHandler.handle(packet, player);
                    }
                } else {
                    ClientPacketHandler.handle(packet);
                }
            } catch (Exception e) {
                System.err.println("[MODAPP-ERROR] Error handling World Shop packet type " + packet.getType() + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
        ctx.setPacketHandled(true);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
        LOGGER.info("Modern Shop commands registered");
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            try {
                ServerLevel level = serverPlayer.serverLevel();
                EconomyProvider economy = getEconomyProvider(level);
                economy.registerPlayer(level, serverPlayer.getScoreboardName(), serverPlayer.getUUID());
                LOGGER.info("Registered player {} -> {}", serverPlayer.getScoreboardName(), serverPlayer.getUUID());

                // Initialize PriceConfig and ShopConfig from the server's config
                // directory the first time a player joins (server is now available).
                if (currentServer == null || currentServer != serverPlayer.getServer()) {
                    currentServer = serverPlayer.getServer();
                    PriceConfig serverConfig = PriceConfig.forServer(currentServer);
                    priceEngine.setPriceConfig(serverConfig);
                    LOGGER.info("Modern Shop server price config initialized: {}", serverConfig.getConfigFilePath());
                    shopConfig = ShopConfig.forServer(currentServer);
                    LOGGER.info("Modern Shop config initialized: sellhandConfirmation={}", shopConfig.isSellhandConfirmation());
                    applyPersistedCategoryOrder();
                }
            } catch (Exception e) {
                System.err.println("[MODAPP-ERROR] Failed to register player on login: " + e.getMessage());
            }
        }
    }

    public static void buildShopCategories() {
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
     * Prefer the 3-arg overload that takes the actual visible row count.
     */
    public static int[] buildCenterSphereLayout(int numCategories, int columns) {
        return buildCenterSphereLayout(numCategories, columns, 3);
    }

    /**
     * Build a center-sphere default layout centered on the VISIBLE viewport.
     *
     * <p>Categories cluster around the center of the visible rows (the area
     * the user sees without scrolling). Overflow extends DOWNWARD below the
     * visible viewport so the user sees a dense center cluster first.
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
     * Save the current category order to a JSON file in the server config directory.
     * Called from ServerPacketHandler.handleReorderCategories.
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
            e.printStackTrace();
        }
    }

    /**
     * Apply the persisted category order from worldshop_category_order.json.
     * Called when the server config is first available. Does NOT replace the
     * existing order if the saved data is corrupted or incomplete.
     */
    public static void applyPersistedCategoryOrder() {
        WorldShop.LOGGER.info("[CAT_ORDER] applyPersistedCategoryOrder() called — currentServer={}", currentServer != null ? currentServer.getServerDirectory().getAbsolutePath() : "null");
        if (currentServer == null) {
            WorldShop.LOGGER.warn("[CAT_ORDER] Cannot apply category order: currentServer is null");
            return;
        }
        try {
            File configDir = new File(currentServer.getServerDirectory(), "config");
            if (!configDir.exists()) {
                WorldShop.LOGGER.info("[CAT_ORDER] Config dir does not exist, cannot load category order");
                return;
            }
            File orderFile = new File(configDir, "worldshop_category_order.json");
            if (!orderFile.exists()) {
                WorldShop.LOGGER.info("[CAT_ORDER] No persisted category order file found");
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
                WorldShop.LOGGER.info("[CAT_ORDER] Category order file is empty or corrupted, not applying");
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
                WorldShop.LOGGER.info("[CAT_ORDER] Parsed nameOrder is null or empty, not applying");
                return;
            }

            // Validate: every name must exist in current categories
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

            WorldShop.LOGGER.info("[CAT_ORDER] Loaded {} valid category names from file", nameOrder.size());

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
                WorldShop.LOGGER.info("[CAT_ORDER] SUCCESS: Applied persisted category order ({} categories)", categories.size());
            } else {
                WorldShop.LOGGER.warn("[CAT_ORDER] Category count mismatch: saved {} names, but have {} categories. Not applying order.",
                    nameOrder.size(), categories.size());
            }
        } catch (Exception e) {
            WorldShop.LOGGER.error("[CAT_ORDER] Could not apply persisted category order: {}", e.getMessage());
            e.printStackTrace();
        }
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
     * Other mods can call this during initialization to use their own economy system.
     */
    public static void setEconomyProvider(EconomyProvider provider) {
        externalEconomyProvider = provider;
        LOGGER.info("External economy provider registered: {}", provider.getClass().getName());
    }

    /**
     * Get the active economy provider.
     * Returns the external provider if one was registered, otherwise falls back to EconomyData.
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
