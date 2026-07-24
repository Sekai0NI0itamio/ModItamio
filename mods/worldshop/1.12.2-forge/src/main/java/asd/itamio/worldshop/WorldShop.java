package asd.itamio.worldshop;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import asd.itamio.ModInfoPrinter;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Mod(modid = WorldShop.MOD_ID, name = WorldShop.MOD_NAME, version = WorldShop.VERSION, acceptedMinecraftVersions = "[1.12.2]")
public class WorldShop {
    public static final String MOD_ID = "worldshop";
    public static final String MOD_NAME = "Modern Shop";
    public static final String VERSION = "1.0.0";

    @Instance(MOD_ID)
    public static WorldShop instance;

    public static Logger LOGGER;
    public static SimpleNetworkWrapper NETWORK;

    private static List<ShopCategory> categories = new ArrayList<>();
    private static PriceEngine priceEngine = new PriceEngine();
    private static MinecraftServer currentServer = null;
    private static ShopConfig shopConfig = null;
    private static EconomyProvider externalEconomyProvider = null;
    /** Grid slot -> category index (-1 = empty). Persists layout positions. */
    private static int[] categorySlotPositions = null;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("Modern Shop mod initializing...");

        NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MOD_ID);
        NETWORK.registerMessage(ServerPacketHandler.class, ShopPacket.class, 0, Side.SERVER);
        NETWORK.registerMessage(ClientPacketHandler.class, ShopPacket.class, 1, Side.CLIENT);
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new ShopGuiHandler());
        MinecraftForge.EVENT_BUS.register(this);

        // Initialize PriceConfig from the client config directory (server-side
        // config is re-initialized later when the server starts).
        PriceConfig clientConfig = PriceConfig.forClient();
        if (clientConfig != null) {
            priceEngine.setPriceConfig(clientConfig);
            LOGGER.info("Modern Shop price config initialized: {}", clientConfig.getConfigFilePath());
        }
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        buildShopCategories();
        LOGGER.info("Modern Shop initialized with {} categories", categories.size());
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandShop());
        event.registerServerCommand(new CommandSellHand());
        event.registerServerCommand(new CommandSellGui());
        event.registerServerCommand(new CommandBalance());
        event.registerServerCommand(new CommandPay());
        // Register /bal alias as a separate command instance
        event.registerServerCommand(new CommandBalance() {
            @Override
            public String getName() {
                return "bal";
            }
        });
        event.registerServerCommand(new CommandShopReset());

        currentServer = event.getServer();
        // Initialize PriceConfig and ShopConfig from the server's config directory
        PriceConfig serverConfig = PriceConfig.forServer(currentServer);
        priceEngine.setPriceConfig(serverConfig);
        LOGGER.info("Modern Shop server price config initialized: {}", serverConfig.getConfigFilePath());
        shopConfig = ShopConfig.forServer(currentServer);
        LOGGER.info("Modern Shop config initialized: sellhandConfirmation={}", shopConfig.isSellhandConfirmation());

        buildShopCategories();
        applyPersistedCategoryOrder();
        LOGGER.info("Modern Shop commands registered");
    }

    @EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        // Re-apply persisted category order after the server is fully started
        applyPersistedCategoryOrder();
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            World world = player.getEntityWorld();
            EconomyProvider economy = getEconomyProvider(world);
            economy.registerPlayer(world, player.getName(), player.getUniqueID());
            LOGGER.info("Registered player {} -> {}", player.getName(), player.getUniqueID());
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

    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }

    public static ShopConfig getShopConfig() {
        return shopConfig;
    }

    /**
     * Get the economy provider for the given world. If an external provider
     * has been registered via {@link #setEconomyProvider(EconomyProvider)},
     * it is used; otherwise the built-in {@link EconomyData} for the world
     * is used.
     */
    public static EconomyProvider getEconomyProvider(World world) {
        if (externalEconomyProvider != null) {
            return externalEconomyProvider;
        }
        return EconomyData.get(world);
    }

    /**
     * Register an external economy provider to override the built-in
     * EconomyData. Other mods should call this during their init to plug
     * in their own economy system.
     */
    public static void setEconomyProvider(EconomyProvider provider) {
        externalEconomyProvider = provider;
        LOGGER.info("External economy provider registered: {}", provider == null ? "null" : provider.getClass().getName());
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
     * Categories cluster around the center of the visible rows. Overflow
     * extends DOWNWARD below the visible viewport.
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

        List<int[]> slots = new ArrayList<>();
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
        Arrays.fill(layout, -1);
        for (int i = 0; i < numCategories && i < slots.size(); i++) {
            layout[slots.get(i)[0]] = i;
        }

        LOGGER.info("[LAYOUT] Built center-sphere layout: {} categories in {}x{} grid (visibleRows={}, center={},{}), gridSize={}",
                numCategories, columns, totalRows, visibleRows, centerCol, centerRow, gridSize);
        return layout;
    }

    /**
     * Save the current category order to config/worldshop_category_order.json.
     * Called from ServerPacketHandler.handleReorderCategories.
     */
    public static void saveCategoryOrder(List<ShopCategory> orderedCategories) {
        if (currentServer == null) {
            LOGGER.warn("[CAT_ORDER] Cannot save category order: no server instance");
            return;
        }
        try {
            File configDir = new File(currentServer.getDataDirectory(), "config");
            if (!configDir.exists()) configDir.mkdirs();
            File orderFile = new File(configDir, "worldshop_category_order.json");

            List<String> names = new ArrayList<>();
            for (ShopCategory cat : orderedCategories) {
                names.add(cat.getName());
            }

            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(names);
            try (java.io.FileWriter writer = new java.io.FileWriter(orderFile)) {
                writer.write(json);
                writer.flush();
            }
            LOGGER.info("[CAT_ORDER] Saved category order to {} ({} categories)", orderFile.getAbsolutePath(), names.size());
        } catch (Exception e) {
            LOGGER.error("[CAT_ORDER] Failed to save category order: {}", e.getMessage());
        }
    }

    /**
     * Apply the persisted category order from worldshop_category_order.json.
     * Called when the server is fully started.
     */
    public static void applyPersistedCategoryOrder() {
        if (currentServer == null) {
            LOGGER.warn("[CAT_ORDER] Cannot apply category order: currentServer is null");
            return;
        }
        try {
            File configDir = new File(currentServer.getDataDirectory(), "config");
            if (!configDir.exists()) {
                LOGGER.info("[CAT_ORDER] Config dir does not exist, cannot load category order");
                return;
            }
            File orderFile = new File(configDir, "worldshop_category_order.json");
            if (!orderFile.exists()) {
                LOGGER.info("[CAT_ORDER] No saved category order file, using default order");
                return;
            }

            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(orderFile))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                String json = sb.toString().trim();
                if (json.isEmpty()) {
                    LOGGER.info("[CAT_ORDER] Category order file is empty, using default order");
                    return;
                }
                com.google.gson.Gson gson = new com.google.gson.Gson();
                String[] names = gson.fromJson(json, String[].class);
                if (names == null || names.length == 0) {
                    LOGGER.info("[CAT_ORDER] Category order file has no entries, using default order");
                    return;
                }

                // Reorder categories to match the saved order. Categories
                // not in the saved list keep their relative order at the end.
                List<ShopCategory> reordered = new ArrayList<>();
                List<ShopCategory> remaining = new ArrayList<>(categories);
                for (String name : names) {
                    for (int i = 0; i < remaining.size(); i++) {
                        if (remaining.get(i).getName().equals(name)) {
                            reordered.add(remaining.remove(i));
                            break;
                        }
                    }
                }
                reordered.addAll(remaining);
                categories = reordered;
                LOGGER.info("[CAT_ORDER] Applied persisted category order: {} categories", reordered.size());
            }
        } catch (Exception e) {
            LOGGER.warn("[CAT_ORDER] Could not apply persisted category order: {}", e.getMessage());
        }
    }
}
