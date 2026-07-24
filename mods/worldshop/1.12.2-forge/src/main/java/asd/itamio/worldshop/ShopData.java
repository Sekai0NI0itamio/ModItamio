package asd.itamio.worldshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Persistent shop customization data.
 * Stores hidden items/categories, custom categories, item overrides, and
 * the persistent category order. Saved to config/shop_data.json.
 */
public class ShopData {
    private static final String FILE_NAME = "shop_data.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File configFile;

    private final Set<String> hiddenItems = new HashSet<>();
    private final Set<String> hiddenCategories = new HashSet<>();
    private final Map<String, CustomCategory> customCategories = new LinkedHashMap<>();
    private final Map<String, ItemOverride> itemOverrides = new LinkedHashMap<>();
    private final List<String> categoryOrder = new ArrayList<>();

    public ShopData(File configDir) {
        this.configFile = new File(configDir, FILE_NAME);
        load();
    }

    public static ShopData forServer(MinecraftServer server) {
        File configDir = new File(server.getDataDirectory(), "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return new ShopData(configDir);
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
        hiddenItems.clear();
        hiddenCategories.clear();
        customCategories.clear();
        itemOverrides.clear();
        categoryOrder.clear();

        if (configFile == null || !configFile.exists()) return;

        try (FileReader reader = new FileReader(configFile)) {
            Type type = new TypeToken<DataModel>() {}.getType();
            DataModel loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                if (loaded.hiddenItems != null) hiddenItems.addAll(loaded.hiddenItems);
                if (loaded.hiddenCategories != null) hiddenCategories.addAll(loaded.hiddenCategories);
                if (loaded.customCategories != null) customCategories.putAll(loaded.customCategories);
                if (loaded.itemOverrides != null) itemOverrides.putAll(loaded.itemOverrides);
                if (loaded.categoryOrder != null) categoryOrder.addAll(loaded.categoryOrder);
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

    public boolean isCategoryHidden(String categoryName) {
        return hiddenCategories.contains(categoryName);
    }

    public Map<String, CustomCategory> getCustomCategories() {
        return Collections.unmodifiableMap(customCategories);
    }

    public ItemOverride getItemOverride(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return itemOverrides.get(getItemKey(stack));
    }

    public List<String> getCategoryOrder() {
        return new ArrayList<>(categoryOrder);
    }

    public void setCategoryOrder(List<String> order) {
        categoryOrder.clear();
        categoryOrder.addAll(order);
        save();
    }

    public static String getItemKey(ItemStack stack) {
        return stack.getItem().getRegistryName().toString();
    }

    public static Item itemFromKey(String key) {
        try {
            return Item.getByNameOrId(key);
        } catch (Exception e) {
            return null;
        }
    }
}
