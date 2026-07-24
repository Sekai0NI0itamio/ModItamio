package asd.itamio.worldshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Persists item base prices to a JSON config file at config/worldshop_prices.json.
 * A version file tracks the pricing system version; when it changes the cached
 * prices are cleared so they recalculate with the new system.
 */
public class PriceConfig {
    private static final String FILE_NAME = "worldshop_prices.json";
    private static final String VERSION_FILE_NAME = "worldshop_price_version.txt";
    /**
     * Pricing system version. Increment when base prices, rarity multipliers,
     * or recipe pricing logic changes so old cached prices are cleared.
     */
    static final int PRICE_VERSION = 6;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File configFile;
    private final File versionFile;
    private final Map<String, Double> prices = new HashMap<>();

    public PriceConfig(File configDir) {
        this.configFile = new File(configDir, FILE_NAME);
        this.versionFile = new File(configDir, VERSION_FILE_NAME);
        load();
    }

    public static PriceConfig forServer(MinecraftServer server) {
        File configDir = new File(getServerDataDir(server), "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return new PriceConfig(configDir);
    }

    /**
     * Create a PriceConfig for the client side using the client's game
     * directory. Returns null on a dedicated server (where Minecraft client
     * classes are not loaded) so the caller can skip client-only init.
     */
    public static PriceConfig forClient() {
        File configDir = findClientConfigDir();
        if (configDir == null) return null;
        return new PriceConfig(configDir);
    }

    private static File findClientConfigDir() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc != null && mc.gameDir != null) {
                File configDir = new File(mc.gameDir, "config");
                if (configDir.exists() || configDir.mkdirs()) {
                    return configDir;
                }
            }
        } catch (Throwable ignored) {
            // Dedicated server — Minecraft client class is not loaded
        }
        return null;
    }

    private static File getServerDataDir(MinecraftServer server) {
        try {
            return server.getDataDirectory();
        } catch (Exception e) {
            return new File(".");
        }
    }

    public void load() {
        prices.clear();
        if (!checkVersion()) {
            WorldShop.LOGGER.info("Price system version changed (expected v{}), clearing cached prices", PRICE_VERSION);
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
            WorldShop.LOGGER.warn("Could not load shop prices from {}: {}", configFile.getAbsolutePath(), e.getMessage());
        }
    }

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
            WorldShop.LOGGER.warn("Could not write price version file: {}", e.getMessage());
        }
    }

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

    public double getPrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1.0;
        return prices.getOrDefault(getItemKey(stack), -1.0);
    }

    public void setPrice(ItemStack stack, double price) {
        if (stack == null || stack.isEmpty()) return;
        String key = getItemKey(stack);
        Double existing = prices.get(key);
        if (existing == null || Math.abs(existing - price) > 0.001) {
            prices.put(key, price);
            save();
        }
    }

    public boolean hasPrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return prices.containsKey(getItemKey(stack));
    }

    private static String getItemKey(ItemStack stack) {
        return stack.getItem().getRegistryName().toString();
    }

    public Map<String, Double> getAllPrices() {
        return prices;
    }

    public String getConfigFilePath() {
        return configFile != null ? configFile.getAbsolutePath() : "unknown";
    }

    public void clearAllPrices() {
        prices.clear();
        if (configFile != null && configFile.exists()) {
            configFile.delete();
        }
        save();
        writeVersionFile();
        WorldShop.LOGGER.info("All shop prices cleared from config file");
    }

    public void clearAll() {
        clearAllPrices();
    }

    public void removePrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        String key = getItemKey(stack);
        if (prices.containsKey(key)) {
            prices.remove(key);
            save();
        }
    }
}
