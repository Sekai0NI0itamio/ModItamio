package asd.itamio.worldshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Persists item base prices to a JSON config file at config/worldshop_prices.json.
 * <p>
 * The prices are stored by item registry ID (e.g. "minecraft:diamond" -> 256.0).
 * Both the server-side price engine and the client-side GUI can read this file.
 * The server writes calculated prices to the config so they persist across restarts
 * and are available to the client for display.
 */
public class PriceConfig {
    private static final String FILE_NAME = "worldshop_prices.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File configFile;
    private final Map<String, Double> prices = new HashMap<>();

    /**
     * Create a PriceConfig in the given config directory.
     *
     * @param configDir the Minecraft config directory (e.g. server.getServerDirectory()/config or Minecraft.getInstance().gameDirectory/config)
     */
    public PriceConfig(File configDir) {
        this.configFile = new File(configDir, FILE_NAME);
        load();
    }

    /**
     * Create a PriceConfig relative to the Minecraft run directory.
     * Tries to find the config directory automatically.
     */
    public PriceConfig() {
        File configDir = findConfigDir();
        this.configFile = configDir != null ? new File(configDir, FILE_NAME) : null;
        if (configFile != null) {
            load();
        }
    }

    /**
     * Try to locate the Minecraft config directory from either the client or server.
     */
    private static File findConfigDir() {
        // Try client first
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameDirectory != null) {
                File configDir = new File(mc.gameDirectory, "config");
                if (configDir.exists() || configDir.mkdirs()) {
                    return configDir;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Create a PriceConfig from a MinecraftServer instance (server-side).
     */
    public static PriceConfig forServer(MinecraftServer server) {
        File configDir = new File(server.getServerDirectory(), "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return new PriceConfig(configDir);
    }

    /**
     * Create a PriceConfig from the client's game directory.
     */
    public static PriceConfig forClient() {
        return new PriceConfig();
    }

    /**
     * Load prices from the config file.
     */
    public void load() {
        prices.clear();
        if (configFile == null || !configFile.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(configFile)) {
            Type type = new TypeToken<Map<String, Double>>() {}.getType();
            Map<String, Double> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                prices.putAll(loaded);
            }
        } catch (Exception e) {
            WorldShop.LOGGER.warn("Could not load shop prices from {}: {}", configFile.getAbsolutePath(), e.getMessage());
        }
    }

    /**
     * Save the current prices to the config file.
     */
    public void save() {
        if (configFile == null) return;
        try {
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(prices, writer);
                writer.flush();
            }
        } catch (Exception e) {
            WorldShop.LOGGER.warn("Could not save shop prices to {}: {}", configFile.getAbsolutePath(), e.getMessage());
        }
    }

    /**
     * Get the stored base price for an item.
     *
     * @param stack the item stack
     * @return the base price, or -1 if not found
     */
    public double getPrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1.0;
        String key = getItemKey(stack);
        return prices.getOrDefault(key, -1.0);
    }

    /**
     * Set the stored base price for an item and immediately save.
     *
     * @param stack the item stack
     * @param price the base price
     */
    public void setPrice(ItemStack stack, double price) {
        if (stack == null || stack.isEmpty()) return;
        String key = getItemKey(stack);
        // Only save if the price has changed
        Double existing = prices.get(key);
        if (existing == null || Math.abs(existing - price) > 0.001) {
            prices.put(key, price);
            save();
        }
    }

    /**
     * Check if a price is stored for this item.
     */
    public boolean hasPrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return prices.containsKey(getItemKey(stack));
    }

    /**
     * Get the item registry ID as a string key.
     */
    private static String getItemKey(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /**
     * Get the underlying map (for bulk operations).
     */
    public Map<String, Double> getAllPrices() {
        return prices;
    }

    /**
     * Get the config file path for logging.
     */
    public String getConfigFilePath() {
        return configFile != null ? configFile.getAbsolutePath() : "unknown";
    }
}
