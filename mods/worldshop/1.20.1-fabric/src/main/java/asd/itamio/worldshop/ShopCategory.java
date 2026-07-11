package asd.itamio.worldshop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
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

    public static List<ShopCategory> buildFromCreativeTabs() {
        List<ShopCategory> categories = new ArrayList<>();
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab == null || tab.getType() == CreativeModeTab.Type.SEARCH || tab.getType() == CreativeModeTab.Type.HOTBAR || tab.getType() == CreativeModeTab.Type.INVENTORY) continue;
            ItemStack icon = tab.getIconItem();
            if (icon == null || icon.isEmpty()) continue;
            String tabName = tab.getDisplayName().getString();
            ShopCategory category = new ShopCategory(tabName, icon.copy());
            categories.add(category);
        }
        return categories;
    }
}
