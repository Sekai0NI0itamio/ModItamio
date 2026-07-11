package asd.itamio.worldshop;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

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
        for (CreativeTabs tab : CreativeTabs.CREATIVE_TAB_ARRAY) {
            if (tab == null || tab == CreativeTabs.SEARCH) continue;
            ItemStack icon;
            try {
                icon = tab.getIcon();
            } catch (Exception e) {
                continue;
            }
            if (icon == null || icon.isEmpty()) continue;
            String tabName = tab.getTabLabel();
            ShopCategory category = new ShopCategory(tabName, icon.copy());
            NonNullList<ItemStack> tabItems = NonNullList.create();
            try {
                tab.displayAllRelevantItems(tabItems);
            } catch (Exception e) {
                continue;
            }
            for (ItemStack item : tabItems) {
                if (item == null || item.isEmpty()) continue;
                category.addItem(item.copy());
            }
            if (category.getItems().isEmpty()) continue;
            categories.add(category);
        }
        return categories;
    }
}
