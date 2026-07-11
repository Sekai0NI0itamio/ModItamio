package asd.itamio.instantautototem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class AutoTotemConfig {
    @SerializedName("enable_auto_totem")
    public boolean enableAutoTotem;

    @SerializedName("show_messages")
    public boolean showMessages;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private File configFile;

    /** Default constructor used when config loading fails */
    public AutoTotemConfig() {
        this.enableAutoTotem = true;
        this.showMessages = true;
    }

    public AutoTotemConfig(File configFile) {
        this();
        this.configFile = configFile;
        load();
    }

    private void load() {
        try {
            if (configFile.exists()) {
                try (FileReader reader = new FileReader(configFile)) {
                    AutoTotemConfig loaded = GSON.fromJson(reader, AutoTotemConfig.class);
                    if (loaded != null) {
                        this.enableAutoTotem = loaded.enableAutoTotem;
                        this.showMessages = loaded.showMessages;
                    }
                }
            } else {
                save();
            }
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to load config: " + e.getMessage());
            e.printStackTrace();
            // Fall back to defaults
            this.enableAutoTotem = true;
            this.showMessages = true;
        }
    }

    private void save() {
        try {
            if (configFile != null) {
                File parent = configFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try (FileWriter writer = new FileWriter(configFile)) {
                    GSON.toJson(this, writer);
                }
            }
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to save config: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
