package asd.itamio.worldshop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopCategory {
    private final String name;
    private final ItemStack icon;
    private final List<ItemStack> items = new ArrayList<>();

    public ShopCategory(String name, ItemStack icon) {
        this.name = name;
        this.icon = icon;
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

    public static List<ShopCategory> buildFromItemGroups(MinecraftServer server) {
        List<ShopCategory> categories = new ArrayList<>();
        // We can't easily build tabs without the display parameters on the server side
        // Instead, iterate creative mode tabs from the registry and try to get their display items
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab == null) continue;
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
            if (tab == CreativeModeTabs.searchTab()) continue;
            if (tab == CreativeModeTabs.getDefaultTab()) continue;

            ItemStack icon = tab.getIconItem();
            if (icon == null || icon.isEmpty()) continue;

            String tabName = tab.getDisplayName().getString();
            ShopCategory category = new ShopCategory(tabName, icon.copy());

            // Get display items from the tab
            for (ItemStack displayItem : tab.getDisplayItems()) {
                if (displayItem == null || displayItem.isEmpty()) continue;
                category.addItem(displayItem.copy());
            }

            if (category.getItems().isEmpty()) continue;
            categories.add(category);
        }
        return categories;
    }
}
