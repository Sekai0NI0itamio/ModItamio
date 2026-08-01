package asd.itamio.modernshop;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
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
        // Trigger lazy population if not done yet
        if (!populated) {
            populateItems();
        }
        return items;
    }

    /**
     * Lazily populate items from the creative tab's display items.
     * This must be called when a Minecraft client world is loaded, because
     * it needs FeatureFlagSet and HolderLookup.Provider from the level.
     */
    public void populateItems() {
        if (populated || tab == null) return;
        populated = true;

        // We need a loaded client level to get feature flags and registry access
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            // No world loaded yet — can't populate, items stay empty
            return;
        }

        FeatureFlagSet features = mc.level.enabledFeatures();
        boolean hasPermissions = mc.player != null && mc.player.hasPermissions(2);
        net.minecraft.core.HolderLookup.Provider holders = mc.level.registryAccess();

        CreativeModeTab.ItemDisplayParameters params = new CreativeModeTab.ItemDisplayParameters(features, hasPermissions, holders);
        tab.buildContents(params);

        for (ItemStack item : tab.getDisplayItems()) {
            if (item == null || item.isEmpty()) continue;
            items.add(item.copy());
        }
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
            // Build category with tab reference (items populated lazily when GUI opens)
            ShopCategory category = new ShopCategory(tabName, icon.copy(), tab);
            categories.add(category);
        }
        return categories;
    }
}
