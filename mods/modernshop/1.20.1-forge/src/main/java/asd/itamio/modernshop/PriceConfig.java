package asd.itamio.modernshop;

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
 * Persists item base prices to a JSON config file at config/modernshop_prices.json.
 * <p>
 * The prices are stored by item registry ID (e.g. "minecraft:diamond" -> 256.0).
 * Both the server-side price engine and the client-side GUI can read this file.
 * The server writes calculated prices to the config so they persist across restarts
 * and are available to the client for display.
 * <p>
 * A version file (modernshop_price_version.txt) tracks the pricing system version.
 * When the version changes (base prices, rarity multipliers, or recipe logic
 * change), the cached prices are automatically cleared so they recalculate with
 * the new system on the next query.
 */
public class PriceConfig {
    private static final String FILE_NAME = "modernshop_prices.json";
    private static final String VERSION_FILE_NAME = "modernshop_price_version.txt";
    /**
     * Pricing system version. Increment this whenever the base prices,
     * rarity multipliers, or recipe pricing logic changes so that old
     * cached prices are automatically cleared and recalculated.
     */
    static final int PRICE_VERSION = 6;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File configFile;
    private final File versionFile;
    private final Map<String, Double> prices = new HashMap<>();

    /**
     * Create a PriceConfig in the given config directory.
     *
     * @param configDir the Minecraft config directory (e.g. server.getServerDirectory()/config or Minecraft.getInstance().gameDirectory/config)
     */
    public PriceConfig(File configDir) {
        this.configFile = new File(configDir, FILE_NAME);
        this.versionFile = new File(configDir, VERSION_FILE_NAME);
        load();
    }

    /**
     * Create a PriceConfig relative to the Minecraft run directory.
     * Tries to find the config directory automatically.
     */
    public PriceConfig() {
        File configDir = findConfigDir();
        this.configFile = configDir != null ? new File(configDir, FILE_NAME) : null;
        this.versionFile = configDir != null ? new File(configDir, VERSION_FILE_NAME) : null;
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
     * If the pricing system version has changed, old cached prices are
     * cleared so they recalculate with the new system.
     */
    public void load() {
        prices.clear();

        // Check version file — if the pricing system version changed,
        // delete the old cached prices so they recalculate fresh.
        if (!checkVersion()) {
            ModernShop.LOGGER.info("Price system version changed (expected v{}), clearing cached prices", PRICE_VERSION);
            if (configFile != null && configFile.exists()) {
                configFile.delete();
            }
            writeVersionFile();
            return;
        }

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
            ModernShop.LOGGER.warn("Could not load shop prices from {}: {}", configFile.getAbsolutePath(), e.getMessage());
        }
    }

    /**
     * Check if the stored pricing system version matches the current version.
     */
    private boolean checkVersion() {
        if (versionFile == null || !versionFile.exists()) {
            return false;
        }
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(versionFile))) {
            String line = reader.readLine();
            if (line != null) {
                int stored = Integer.parseInt(line.trim());
                return stored == PRICE_VERSION;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Write the current pricing system version to the version file.
     */
    private void writeVersionFile() {
        if (versionFile == null) return;
        try {
            if (!versionFile.getParentFile().exists()) {
                versionFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(versionFile)) {
                writer.write(String.valueOf(PRICE_VERSION));
                writer.flush();
            }
        } catch (Exception e) {
            ModernShop.LOGGER.warn("Could not write price version file: {}", e.getMessage());
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
            ModernShop.LOGGER.warn("Could not save shop prices to {}: {}", configFile.getAbsolutePath(), e.getMessage());
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

    /**
     * Clear all stored prices and delete the config file.
     * This forces the price engine to recalculate all prices from scratch
     * using the current world's recipe manager on the next query.
     */
    public void clearAllPrices() {
        prices.clear();
        if (configFile != null && configFile.exists()) {
            configFile.delete();
        }
        save();
        writeVersionFile();
        ModernShop.LOGGER.info("All shop prices cleared from config file");
    }

    /**
     * Alias for clearAllPrices() — used by settings operations.
     */
    public void clearAll() {
        clearAllPrices();
    }

    /**
     * Remove a specific item's price from the config.
     *
     * @param stack the item stack to remove
     */
    public void removePrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        String key = getItemKey(stack);
        if (prices.containsKey(key)) {
            prices.remove(key);
            save();
        }
    }
}
