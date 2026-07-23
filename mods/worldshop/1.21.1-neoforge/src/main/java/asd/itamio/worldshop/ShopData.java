package asd.itamio.worldshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
            WorldShop.LOGGER.warn("Could not load shop data from {}: {}", configFile.getAbsolutePath(), e.getMessage());
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
            WorldShop.LOGGER.warn("Could not save shop data to {}: {}", configFile.getAbsolutePath(), e.getMessage());
        }
    }

    public boolean isItemHidden(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return hiddenItems.contains(getItemKey(stack));
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

    public boolean isCategoryHidden(String categoryName) {
        return hiddenCategories.contains(categoryName);
    }

    public void hideCategory(String categoryName) {
        hiddenCategories.add(categoryName);
        save();
    }

    public void unhideCategory(String categoryName) {
        hiddenCategories.remove(categoryName);
        save();
    }

    public Map<String, CustomCategory> getCustomCategories() {
        return Collections.unmodifiableMap(customCategories);
    }

    public CustomCategory getCustomCategory(String key) {
        return customCategories.get(key);
    }

    public void addCustomCategory(String key, String name, String iconItemId) {
        CustomCategory cat = new CustomCategory(name, iconItemId);
        customCategories.put(key, cat);
        save();
    }

    public void removeCustomCategory(String key) {
        customCategories.remove(key);
        save();
    }

    public void addItemToCustomCategory(String key, String itemId) {
        CustomCategory cat = customCategories.get(key);
        if (cat != null && !cat.itemIds.contains(itemId)) {
            cat.itemIds.add(itemId);
            save();
        }
    }

    public void removeItemFromCustomCategory(String key, String itemId) {
        CustomCategory cat = customCategories.get(key);
        if (cat != null) {
            cat.itemIds.remove(itemId);
            save();
        }
    }

    public ItemOverride getItemOverride(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return itemOverrides.get(getItemKey(stack));
    }

    public void setItemOverride(ItemStack stack, String displayName, String iconItemId, Double buyPrice, Double sellPrice) {
        ItemOverride ov = new ItemOverride();
        ov.displayName = displayName;
        ov.iconItemId = iconItemId;
        ov.buyPrice = buyPrice;
        ov.sellPrice = sellPrice;
        itemOverrides.put(getItemKey(stack), ov);
        save();
    }

    public void removeItemOverride(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        itemOverrides.remove(getItemKey(stack));
        save();
    }

    public List<String> getCategoryOrder() {
        return new ArrayList<>(categoryOrder);
    }

    public void setCategoryOrder(List<String> order) {
        categoryOrder.clear();
        categoryOrder.addAll(order);
        save();
    }

    public synchronized ShopDataSnapshot getSnapshot() {
        ShopDataSnapshot snap = new ShopDataSnapshot();
        snap.hiddenItems = new HashSet<>(hiddenItems);
        snap.hiddenCategories = new HashSet<>(hiddenCategories);
        snap.customCategories = new LinkedHashMap<>(customCategories);
        snap.itemOverrides = new LinkedHashMap<>(itemOverrides);
        return snap;
    }

    public synchronized void applySnapshot(ShopDataSnapshot snap) {
        hiddenItems.clear();
        hiddenItems.addAll(snap.hiddenItems);
        hiddenCategories.clear();
        hiddenCategories.addAll(snap.hiddenCategories);
        customCategories.clear();
        customCategories.putAll(snap.customCategories);
        itemOverrides.clear();
        itemOverrides.putAll(snap.itemOverrides);
    }

    public static class ShopDataSnapshot {
        public Set<String> hiddenItems = new HashSet<>();
        public Set<String> hiddenCategories = new HashSet<>();
        public Map<String, CustomCategory> customCategories = new LinkedHashMap<>();
        public Map<String, ItemOverride> itemOverrides = new LinkedHashMap<>();
    }

    public static String getItemKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static String getItemKey(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    public static Item itemFromKey(String key) {
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id == null) return null;
        return BuiltInRegistries.ITEM.get(id);
    }

    public String getConfigFilePath() {
        return configFile != null ? configFile.getAbsolutePath() : "unknown";
    }
}
