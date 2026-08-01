package asd.itamio.modernshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;

/**
 * Persistent mod configuration stored in config/modernshop_config.json.
 * Controls mod settings like sellhand confirmation behavior.
 */
public class ShopConfig {
    private static final String FILE_NAME = "modernshop_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File configFile;

    // Whether /sellhand requires confirmation before executing
    private boolean sellhandConfirmation = true;

    public ShopConfig(File configDir) {
        this.configFile = new File(configDir, FILE_NAME);
        load();
    }

    public ShopConfig() {
        File configDir = findConfigDir();
        this.configFile = configDir != null ? new File(configDir, FILE_NAME) : null;
        if (configFile != null) {
            load();
        }
    }

    private static File findConfigDir() {
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

    public static ShopConfig forServer(MinecraftServer server) {
        File configDir = new File(server.getServerDirectory(), "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return new ShopConfig(configDir);
    }

    public static ShopConfig forClient() {
        return new ShopConfig();
    }

    public synchronized void load() {
        if (configFile == null || !configFile.exists()) {
            // Set defaults
            this.sellhandConfirmation = true;
            return;
        }
        try (FileReader reader = new FileReader(configFile)) {
            Type type = new TypeToken<ConfigModel>() {}.getType();
            ConfigModel loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                this.sellhandConfirmation = loaded.sellhandConfirmation;
            }
        } catch (Exception e) {
            ModernShop.LOGGER.warn("Could not load shop config from {}: {}", configFile.getAbsolutePath(), e.getMessage());
        }
    }

    public synchronized void save() {
        if (configFile == null) return;
        try {
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            ConfigModel model = new ConfigModel();
            model.sellhandConfirmation = this.sellhandConfirmation;
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(model, writer);
                writer.flush();
            }
        } catch (Exception e) {
            ModernShop.LOGGER.warn("Could not save shop config to {}: {}", configFile.getAbsolutePath(), e.getMessage());
        }
    }

    private static class ConfigModel {
        boolean sellhandConfirmation = true;
    }

    public boolean isSellhandConfirmation() {
        return sellhandConfirmation;
    }

    public void setSellhandConfirmation(boolean enabled) {
        this.sellhandConfirmation = enabled;
        save();
    }
}