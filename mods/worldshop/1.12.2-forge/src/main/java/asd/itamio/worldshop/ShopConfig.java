package asd.itamio.worldshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;

/**
 * Persistent mod configuration stored in config/worldshop_config.json.
 * Controls mod settings like sellhand confirmation behavior.
 */
public class ShopConfig {
    private static final String FILE_NAME = "worldshop_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File configFile;
    private boolean sellhandConfirmation = true;

    public ShopConfig(File configDir) {
        this.configFile = new File(configDir, FILE_NAME);
        load();
    }

    public static ShopConfig forServer(MinecraftServer server) {
        File configDir = new File(server.getDataDirectory(), "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return new ShopConfig(configDir);
    }

    public synchronized void load() {
        if (configFile == null || !configFile.exists()) {
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
            WorldShop.LOGGER.warn("Could not load shop config from {}: {}", configFile.getAbsolutePath(), e.getMessage());
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
            WorldShop.LOGGER.warn("Could not save shop config to {}: {}", configFile.getAbsolutePath(), e.getMessage());
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
