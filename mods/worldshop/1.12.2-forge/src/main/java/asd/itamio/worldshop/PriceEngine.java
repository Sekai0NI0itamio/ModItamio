package asd.itamio.worldshop;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;

import java.util.*;

public class PriceEngine {
    public static final double BASE_MATERIAL_PRICE = 2.0;
    public static final double UNCRAFTABLE_PRICE = 1000.0;
    public static final double BUY_MULTIPLIER = 1.2;
    public static final double SELL_MULTIPLIER = 0.8;

    private final Map<String, Double> priceCache = new HashMap<>();
    private final Set<String> computing = new HashSet<>();

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
        String key = getItemKey(stack);
        if (computing.contains(key)) {
            return BASE_MATERIAL_PRICE;
        }
        computing.add(key);
        try {
            List<IRecipe> recipes = findRecipesFor(stack);
            if (recipes.isEmpty()) {
                return UNCRAFTABLE_PRICE;
            }
            IRecipe recipe = recipes.get(0);
            double totalIngredientPrice = 0.0;
            boolean hasIngredients = false;
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient == Ingredient.EMPTY) continue;
                ItemStack[] matchingStacks = ingredient.getMatchingStacks();
                if (matchingStacks == null || matchingStacks.length == 0) continue;
                ItemStack ingredientStack = matchingStacks[0];
                double ingredientPrice = getBasePrice(ingredientStack);
                totalIngredientPrice += ingredientPrice;
                hasIngredients = true;
            }
            if (!hasIngredients) {
                return UNCRAFTABLE_PRICE;
            }
            int outputCount = recipe.getRecipeOutput().getCount();
            if (outputCount <= 0) {
                outputCount = 1;
            }
            double perItemPrice = totalIngredientPrice / (double) outputCount;
            return Math.round(perItemPrice * 100.0) / 100.0;
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
        if (a.getItem() != b.getItem()) {
            return false;
        }
        return a.getItemDamage() == b.getItemDamage() || a.getItemDamage() == Short.MAX_VALUE || b.getItemDamage() == Short.MAX_VALUE;
    }

    private String getItemKey(ItemStack stack) {
        return stack.getItem().getRegistryName() + ":" + stack.getItemDamage();
    }

    public void clearCache() {
        priceCache.clear();
        computing.clear();
    }
}
