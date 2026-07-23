package asd.itamio.worldshop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopCategory {
    private final String name;
    private final ItemStack icon;
    private final CreativeModeTab tab;
    private final List<ItemStack> items = new ArrayList<>();
    private boolean populated = false;

    public ShopCategory(String name, ItemStack icon) {
        this(name, icon, null);
    }

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
        return items;
    }

    public void addItem(ItemStack stack) {
        items.add(stack);
    }

    /**
     * Populate items from the creative tab on the server side.
     * In 26.1.2, items are populated eagerly during buildFromItemGroups,
     * so this is a no-op if already populated.
     */
    public void populateItemsServer(ServerLevel level) {
        if (populated || tab == null) return;
        populated = true;
        for (ItemStack displayItem : tab.getDisplayItems()) {
            if (displayItem == null || displayItem.isEmpty()) continue;
            items.add(displayItem.copy());
        }
    }

    /**
     * Lazily populate items from the creative tab's display items (client-side).
     */
    public void populateItems() {
        if (populated || tab == null) return;
        populated = true;
        for (ItemStack displayItem : tab.getDisplayItems()) {
            if (displayItem == null || displayItem.isEmpty()) continue;
            items.add(displayItem.copy());
        }
    }

    public static List<ShopCategory> buildFromItemGroups(MinecraftServer server) {
        return buildFromCreativeTabs();
    }

    public static List<ShopCategory> buildFromCreativeTabs() {
        List<ShopCategory> categories = new ArrayList<>();
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab == null) continue;
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
            if (tab == CreativeModeTabs.searchTab()) continue;
            if (tab == CreativeModeTabs.getDefaultTab()) continue;

            ItemStack icon = tab.getIconItem();
            if (icon == null || icon.isEmpty()) continue;

            String tabName = tab.getDisplayName().getString();
            ShopCategory category = new ShopCategory(tabName, icon.copy(), tab);

            for (ItemStack displayItem : tab.getDisplayItems()) {
                if (displayItem == null || displayItem.isEmpty()) continue;
                category.addItem(displayItem.copy());
            }
            category.populated = true;

            if (category.getItems().isEmpty()) continue;
            categories.add(category);
        }
        return categories;
    }
}
