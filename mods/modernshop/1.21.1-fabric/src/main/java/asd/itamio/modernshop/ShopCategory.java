package asd.itamio.modernshop;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopCategory {
    private final String name;
    private final ItemStack icon;
    private final CreativeModeTab tab;
    private final List<ItemStack> items = new ArrayList<>();
    private boolean populated = false;

    public ShopCategory(String name, ItemStack icon, CreativeModeTab tab) {
        this.name = name;
        this.icon = icon;
        this.tab = tab;
    }

    public String getName() {
        return name;
    }

    public ItemStack getIcon() {
        return icon;
    }

    public List<ItemStack> getItems() {
        if (!populated) {
            populateItems();
        }
        return items;
    }

    /**
     * Lazily populate items from the creative tab's display items.
     * Called client-side; requires a loaded client world.
     */
    public void populateItems() {
        if (populated || tab == null) return;
        populated = true;

        try {
            for (ItemStack item : tab.getDisplayItems()) {
                if (item == null || item.isEmpty()) continue;
                items.add(item.copy());
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Populate items from the creative tab on the server side.
     * Must be called from a server thread with a valid ServerLevel.
     */
    public void populateItemsServer(ServerLevel level) {
        if (populated || tab == null) return;
        populated = true;

        try {
            FeatureFlagSet features = level.enabledFeatures();
            boolean hasPermissions = true;
            HolderLookup.Provider holders = level.registryAccess();

            CreativeModeTab.ItemDisplayParameters params = new CreativeModeTab.ItemDisplayParameters(features, hasPermissions, holders);
            tab.buildContents(params);

            for (ItemStack item : tab.getDisplayItems()) {
                if (item == null || item.isEmpty()) continue;
                items.add(item.copy());
            }
        } catch (Exception ignored) {
            // Fall back to direct display items if buildContents is unavailable
            try {
                for (ItemStack item : tab.getDisplayItems()) {
                    if (item == null || item.isEmpty()) continue;
                    items.add(item.copy());
                }
            } catch (Exception ignored2) {
            }
        }
    }

    public void addItem(ItemStack stack) {
        items.add(stack);
    }

    public static List<ShopCategory> buildFromCreativeTabs() {
        List<ShopCategory> categories = new ArrayList<>();
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab == null) continue;
            CreativeModeTab.Type type = tab.getType();
            if (type == CreativeModeTab.Type.SEARCH || type == CreativeModeTab.Type.HOTBAR || type == CreativeModeTab.Type.INVENTORY) continue;
            ItemStack icon;
            try {
                icon = tab.getIconItem();
            } catch (Exception e) {
                continue;
            }
            if (icon == null || icon.isEmpty()) continue;
            String tabName = tab.getDisplayName().getString();
            ShopCategory category = new ShopCategory(tabName, icon.copy(), tab);
            categories.add(category);
        }
        return categories;
    }
}
