package asd.itamio.modernshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent shop customization data.
 * Stores hidden items/categories, custom categories, and item overrides.
 * Saved to config/shop_data.json.
 */
public class ShopData {
    private static final String FILE_NAME = "shop_data.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File configFile;

    private final Set<String> hiddenItems = ConcurrentHashMap.newKeySet();
    private final Set<String> hiddenCategories = ConcurrentHashMap.newKeySet();
    private final Map<String, CustomCategory> customCategories = new ConcurrentHashMap<>();
    private final Map<String, ItemOverride> itemOverrides = new ConcurrentHashMap<>();
    private final List<String> categoryOrder = Collections.synchronizedList(new ArrayList<>());
    private DataModel cachedData = new DataModel();

    public ShopData(File configDir) {
        this.configFile = new File(configDir, FILE_NAME);
        load();
    }

    public ShopData() {
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

    public static ShopData forServer(MinecraftServer server) {
        File configDir = new File(server.getServerDirectory().toFile(), "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return new ShopData(configDir);
    }

    public static ShopData forClient() {
        return new ShopData();
    }

    private static class DataModel {
        Set<String> hiddenItems = new HashSet<>();
        Set<String> hiddenCategories = new HashSet<>();
        Map<String, CustomCategory> customCategories = new LinkedHashMap<>();
        Map<String, ItemOverride> itemOverrides = new LinkedHashMap<>();
        List<String> categoryOrder = new ArrayList<>();
    }

    public static class CustomCategory {
        String name;
        String iconItemId;
        List<String> itemIds = new ArrayList<>();

        public CustomCategory() {}

        public CustomCategory(String name, String iconItemId) {
            this.name = name;
            this.iconItemId = iconItemId;
        }
    }

    public static class ItemOverride {
        String displayName;
        String iconItemId;
        Double buyPrice;
        Double sellPrice;

        public ItemOverride() {}
    }

    public synchronized void load() {
        cachedData = new DataModel();
        hiddenItems.clear();
        hiddenCategories.clear();
        customCategories.clear();
        itemOverrides.clear();

        if (configFile == null || !configFile.exists()) return;

        try (FileReader reader = new FileReader(configFile)) {
            Type type = new TypeToken<DataModel>() {}.getType();
            DataModel loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                cachedData = loaded;
                hiddenItems.addAll(loaded.hiddenItems);
                hiddenCategories.addAll(loaded.hiddenCategories);
                if (loaded.customCategories != null) customCategories.putAll(loaded.customCategories);
                if (loaded.itemOverrides != null) itemOverrides.putAll(loaded.itemOverrides);
                if (loaded.categoryOrder != null) {
                    categoryOrder.clear();
                    categoryOrder.addAll(loaded.categoryOrder);
                }
            }
        } catch (Exception e) {
            ModernShop.LOGGER.warn("Could not load shop data from {}: {}", configFile.getAbsolutePath(), e.getMessage());
        }
    }

    public synchronized void save() {
        if (configFile == null) return;
        try {
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            DataModel model = new DataModel();
            model.hiddenItems = new HashSet<>(hiddenItems);
            model.hiddenCategories = new HashSet<>(hiddenCategories);
            model.customCategories = new LinkedHashMap<>(customCategories);
            model.itemOverrides = new LinkedHashMap<>(itemOverrides);
            model.categoryOrder = new ArrayList<>(categoryOrder);

            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(model, writer);
                writer.flush();
            }
        } catch (Exception e) {
            ModernShop.LOGGER.warn("Could not save shop data to {}: {}", configFile.getAbsolutePath(), e.getMessage());
        }
    }

    // ========== Hidden items/categories ==========

    public boolean isItemHidden(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String key = getItemKey(stack);
        return hiddenItems.contains(key);
    }

    public boolean isCategoryHidden(String categoryName) {
        return hiddenCategories.contains(categoryName);
    }

    public void hideItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        hiddenItems.add(getItemKey(stack));
        save();
    }

    public void unhideItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        hiddenItems.remove(getItemKey(stack));
        save();
    }

    public void hideCategory(String name) {
        hiddenCategories.add(name);
        save();
    }

    public void unhideCategory(String name) {
        hiddenCategories.remove(name);
        save();
    }

    // ========== Custom categories ==========

    public void addCustomCategory(String name, String iconItemId) {
        customCategories.put(name, new CustomCategory(name, iconItemId));
        save();
    }

    public void removeCustomCategory(String name) {
        customCategories.remove(name);
        save();
    }

    public Map<String, CustomCategory> getCustomCategories() {
        return customCategories;
    }

    // ========== Item overrides ==========

    public ItemOverride getItemOverride(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return itemOverrides.get(getItemKey(stack));
    }

    public void setItemOverride(ItemStack stack, String displayName, String iconItemId, Double buyPrice, Double sellPrice) {
        if (stack == null || stack.isEmpty()) return;
        ItemOverride override = new ItemOverride();
        override.displayName = displayName;
        override.iconItemId = iconItemId;
        override.buyPrice = buyPrice;
        override.sellPrice = sellPrice;
        itemOverrides.put(getItemKey(stack), override);
        save();
    }

    public void removeItemOverride(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        itemOverrides.remove(getItemKey(stack));
        save();
    }

    // ========== Category order ==========

    public List<String> getCategoryOrder() {
        return categoryOrder;
    }

    public void setCategoryOrder(List<String> order) {
        categoryOrder.clear();
        categoryOrder.addAll(order);
        save();
    }

    // ========== Helpers ==========

    private static String getItemKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).getRegisteredName();
    }

    public static Item getItemById(String id) {
        if (id == null || id.isEmpty()) return null;
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) return null;
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        if (item == null) return null;
        return item;
    }

    public static String getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).getRegisteredName();
    }
}
