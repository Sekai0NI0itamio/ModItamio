package asd.itamio.worldshop;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;

import java.util.*;

/**
 * Rarity-first pricing engine with recipe cascade.
 *
 * Pricing flow:
 * 1. Uncraftable items (no recipe): $1 * rarity_multiplier * bulk_discount
 * 2. Crafted items: recipe_price * crafted_rarity_multiplier
 *    - Recipe price = sum of ingredient prices (each already includes rarity)
 *    - Crafted multiplier is smaller (1.0x-5.0x) to avoid double-counting
 */
public class PriceEngine {
    public static final double MIN_BASE_PRICE = 1.0;
    public static final double BASE_MATERIAL_PRICE = 1.0;
    public static final double UNCRAFTABLE_PRICE = 1.0;
    public static final double BUY_MULTIPLIER = 1.2;
    public static final double SELL_MULTIPLIER = 0.8;

    private final Map<String, Double> priceCache = new HashMap<>();
    private final Set<String> computing = new HashSet<>();
    private PriceConfig priceConfig;

    public void setPriceConfig(PriceConfig config) {
        this.priceConfig = config;
    }

    public PriceConfig getPriceConfig() {
        return priceConfig;
    }

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

        if (priceConfig != null && priceConfig.hasPrice(stack)) {
            double configPrice = priceConfig.getPrice(stack);
            if (configPrice >= 0) {
                priceCache.put(key, configPrice);
                return configPrice;
            }
        }

        double price = computeBasePrice(stack);
        priceCache.put(key, price);

        if (priceConfig != null) {
            priceConfig.setPrice(stack, price);
        }

        return price;
    }

    private double computeBasePrice(ItemStack stack) {
        String key = getItemKey(stack);
        if (computing.contains(key)) {
            return BASE_MATERIAL_PRICE * ItemRarity.getRarityMultiplier(stack) * ItemRarity.getBulkDiscountMultiplier(stack);
        }
        computing.add(key);
        try {
            List<IRecipe> recipes = findRecipesFor(stack);
            if (recipes.isEmpty()) {
                return UNCRAFTABLE_PRICE * ItemRarity.getRarityMultiplier(stack) * ItemRarity.getBulkDiscountMultiplier(stack);
            }

            double cheapestPrice = Double.MAX_VALUE;
            boolean foundRecipe = false;

            for (IRecipe recipe : recipes) {
                double totalIngredientPrice = 0.0;
                boolean hasIngredients = false;
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient == Ingredient.EMPTY) continue;
                    ItemStack[] matchingStacks = ingredient.getMatchingStacks();
                    if (matchingStacks == null || matchingStacks.length == 0) continue;
                    double cheapestIngredient = Double.MAX_VALUE;
                    for (ItemStack ingredientStack : matchingStacks) {
                        if (ingredientStack == null || ingredientStack.isEmpty()) continue;
                        double ingredientPrice = getBasePrice(ingredientStack);
                        if (ingredientPrice < cheapestIngredient) {
                            cheapestIngredient = ingredientPrice;
                        }
                    }
                    if (cheapestIngredient < Double.MAX_VALUE) {
                        totalIngredientPrice += cheapestIngredient;
                        hasIngredients = true;
                    }
                }

                if (!hasIngredients) {
                    continue;
                }

                int outputCount = recipe.getRecipeOutput().getCount();
                if (outputCount <= 0) {
                    outputCount = 1;
                }

                double perItemPrice = totalIngredientPrice / (double) outputCount;
                if (perItemPrice < cheapestPrice) {
                    cheapestPrice = perItemPrice;
                    foundRecipe = true;
                }
            }

            if (!foundRecipe) {
                return UNCRAFTABLE_PRICE * ItemRarity.getRarityMultiplier(stack) * ItemRarity.getBulkDiscountMultiplier(stack);
            }

            double rarityAdjustedPrice = cheapestPrice * ItemRarity.getCraftedRarityMultiplier(stack);
            double finalPrice = Math.max(MIN_BASE_PRICE, rarityAdjustedPrice);
            return Math.round(finalPrice * 100.0) / 100.0;
        } finally {
            computing.remove(key);
        }
    }

    private List<IRecipe> findRecipesFor(ItemStack stack) {
        List<IRecipe> result = new ArrayList<>();
        for (IRecipe recipe : CraftingManager.REGISTRY) {
            ItemStack output = recipe.getRecipeOutput();
            if (output == null || output.isEmpty() || !isSameItem(output, stack)) continue;
            result.add(recipe);
        }
        return result;
    }

    private boolean isSameItem(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem();
    }

    private String getItemKey(ItemStack stack) {
        return stack.getItem().getRegistryName().toString();
    }

    public void clearCache() {
        priceCache.clear();
        computing.clear();
    }
}
