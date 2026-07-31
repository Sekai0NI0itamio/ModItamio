package com.itamio.easyscreenshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ScreenshotConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILENAME = "easyscreenshot.json";
    private static Path configPath;

    public boolean toastEnabled = true;
    public boolean autoOpenFolder = false;
    public String buttonOrder = "COPY,OPEN,OPENFOLDER,RENAME,DELETE";
    public String copyColor = "aqua";
    public String openColor = "green";
    public String openFolderColor = "yellow";
    public String renameColor = "light_purple";
    public String deleteColor = "red";

    public static List<String> getButtonOrder() {
        String order = getInstance().buttonOrder;
        if (order == null || order.isEmpty()) {
            return Arrays.asList("COPY", "OPEN", "OPENFOLDER", "RENAME", "DELETE");
        }
        return new ArrayList<>(Arrays.asList(order.split(",")));
    }

    public static ScreenshotConfig getInstance() {
        return Holder.INSTANCE;
    }

    public static void load() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve(FILENAME);
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                Type type = new TypeToken<ScreenshotConfig>() {}.getType();
                ScreenshotConfig loaded = GSON.fromJson(json, type);
                if (loaded != null) {
                    Holder.INSTANCE = loaded;
                }
            } catch (IOException e) {
                EasyScreenshot.LOGGER.warn("Failed to load config, using defaults", e);
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(Holder.INSTANCE);
            Files.writeString(configPath, json);
        } catch (IOException e) {
            EasyScreenshot.LOGGER.warn("Failed to save config", e);
        }
    }

    private static class Holder {
        static ScreenshotConfig INSTANCE = new ScreenshotConfig();
    }
}
