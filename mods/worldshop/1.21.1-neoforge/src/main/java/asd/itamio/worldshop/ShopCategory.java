package asd.itamio.worldshop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
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

    public CreativeModeTab getTab() {
        return tab;
    }

    public List<ItemStack> getItems() {
        if (!populated) {
            populateItems();
        }
        return items;
    }

    /**
     * Lazily populate items from the creative tab's display items.
     * In NeoForge 1.21.1, getDisplayItems() works directly without ItemDisplayParameters.
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
     */
    public void populateItemsServer(ServerLevel level) {
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
