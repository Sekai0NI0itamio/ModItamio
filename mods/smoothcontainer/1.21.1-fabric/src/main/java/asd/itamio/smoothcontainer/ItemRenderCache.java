package asd.itamio.smoothcontainer;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

public class ItemRenderCache {

    private static final Set<Item> WARMED_ITEMS = new HashSet<>();

    private ItemRenderCache() {}

    public static void preWarm(AbstractContainerMenu menu) {
        if (menu == null) {
            return;
        }

        int warmed = 0;
        for (Slot slot : menu.slots) {
            if (slot.hasItem()) {
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    Item item = stack.getItem();
                    if (WARMED_ITEMS.add(item)) {
                        warmed++;
                    }
                }
            }
        }

        if (warmed > 0) {
            SmoothContainer.LOGGER.debug("Pre-warmed {} new item models for render cache", warmed);
        }
    }

    public static void preWarmItem(Item item) {
        WARMED_ITEMS.add(item);
    }

    public static boolean isWarmed(Item item) {
        return WARMED_ITEMS.contains(item);
    }

    public static void clear() {
        WARMED_ITEMS.clear();
        SmoothContainer.LOGGER.debug("Item render cache cleared");
    }
}
