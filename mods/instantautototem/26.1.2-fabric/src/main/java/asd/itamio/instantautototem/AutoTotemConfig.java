package asd.itamio.instantautototem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AutoTotemConfig {

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("instantautototem.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Expose
    public boolean enableAutoTotem = true;
    @Expose
    public boolean showMessages = true;

    /** Default constructor used when config loading fails */
    public AutoTotemConfig() {
        this.enableAutoTotem = true;
        this.showMessages = true;
    }

    public void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                AutoTotemConfig loaded = GSON.fromJson(json, AutoTotemConfig.class);
                if (loaded != null) {
                    this.enableAutoTotem = loaded.enableAutoTotem;
                    this.showMessages = loaded.showMessages;
                }
            } else {
                save();
            }
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to load config: " + e.getMessage());
            e.printStackTrace();
            // Fall back to defaults — already set in field initializers
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            String json = GSON.toJson(this);
            Files.writeString(CONFIG_PATH, json);
        } catch (IOException e) {
            System.err.println("[MODAPP-ERROR] Failed to save config: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
