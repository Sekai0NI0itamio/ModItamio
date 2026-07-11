package asd.itamio.worldshop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class PriceEngine {
    public static final double BASE_MATERIAL_PRICE = 2.0;
    public static final double UNCRAFTABLE_PRICE = 1000.0;
    public static final double BUY_MULTIPLIER = 1.2;
    public static final double SELL_MULTIPLIER = 0.8;

    private final Map<String, Double> priceCache = new HashMap<>();

    public double getBuyPrice(ItemStack stack) {
        double base = getBasePrice(stack);
        return Math.round(base * BUY_MULTIPLIER * 100.0) / 100.0;
    }

    public double getSellPrice(ItemStack stack) {
        double base = getBasePrice(stack);
        return Math.round(base * SELL_MULTIPLIER * 100.0) / 100.0;
    }

    public double getBasePrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0;
        }
        String key = getItemKey(stack);
        if (priceCache.containsKey(key)) {
            return priceCache.get(key);
        }
        double price = computeBasePrice(stack);
        priceCache.put(key, price);
        return price;
    }

    private double computeBasePrice(ItemStack stack) {
        // Simplified pricing: base material price for most items, higher for rare items
        int maxStack = stack.getMaxStackSize();
        if (maxStack >= 16) {
            // Common items (blocks, ingots, etc.)
            return BASE_MATERIAL_PRICE;
        } else {
            // Tools, weapons, armor (non-stackable)
            return UNCRAFTABLE_PRICE;
        }
    }

    private String getItemKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public void clearCache() {
        priceCache.clear();
    }
}
