package asd.itamio.worldshop;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PriceEngine {
    public static final double BASE_MATERIAL_PRICE = 2.0;
    public static final double UNCRAFTABLE_PRICE = 1000.0;
    public static final double BUY_MULTIPLIER = 1.2;
    public static final double SELL_MULTIPLIER = 0.8;

    private final Map<String, Double> priceCache = new HashMap<>();
    private final Set<String> computing = new HashSet<>();

    /**
     * Client-side price estimate (flat pricing — no recipe lookup available).
     */
    public double getBuyPrice(ItemStack stack) {
        double base = getBasePrice(stack);
        return Math.round(base * BUY_MULTIPLIER * 100.0) / 100.0;
    }

    public double getSellPrice(ItemStack stack) {
        double base = getBasePrice(stack);
        return Math.round(base * SELL_MULTIPLIER * 100.0) / 100.0;
    }

    public double getBasePrice(ItemStack stack) {
        return getBasePrice(stack, null, null);
    }

    /**
     * Server-side price calculation with recipe manager access.
     * Uses recipe ingredient tree to compute fair prices.
     */
    public double getBuyPrice(ItemStack stack, @Nullable RecipeManager recipeManager, @Nullable RegistryAccess registryAccess) {
        double base = getBasePrice(stack, recipeManager, registryAccess);
        return Math.round(base * BUY_MULTIPLIER * 100.0) / 100.0;
    }

    public double getSellPrice(ItemStack stack, @Nullable RecipeManager recipeManager, @Nullable RegistryAccess registryAccess) {
        double base = getBasePrice(stack, recipeManager, registryAccess);
        return Math.round(base * SELL_MULTIPLIER * 100.0) / 100.0;
    }

    public double getBasePrice(ItemStack stack, @Nullable RecipeManager recipeManager, @Nullable RegistryAccess registryAccess) {
        if (stack == null || stack.isEmpty()) {
            return 0.0;
        }
        String key = getItemKey(stack);
        if (priceCache.containsKey(key)) {
            return priceCache.get(key);
        }
        double price = computeBasePrice(stack, recipeManager, registryAccess);
        priceCache.put(key, price);
        return price;
    }

    private double computeBasePrice(ItemStack stack, @Nullable RecipeManager recipeManager, @Nullable RegistryAccess registryAccess) {
        String key = getItemKey(stack);
        if (computing.contains(key)) {
            return BASE_MATERIAL_PRICE;
        }
        computing.add(key);
        try {
            // If no recipe manager is available (client-side), return flat pricing
            if (recipeManager == null || registryAccess == null) {
                return UNCRAFTABLE_PRICE;
            }

            List<Recipe<?>> recipes = findRecipesFor(stack, recipeManager, registryAccess);
            if (recipes.isEmpty()) {
                return UNCRAFTABLE_PRICE;
            }
            Recipe<?> recipe = recipes.get(0);
            double totalIngredientPrice = 0.0;
            boolean hasIngredients = false;
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient == Ingredient.EMPTY) continue;
                ItemStack[] matchingStacks = ingredient.getItems();
                if (matchingStacks == null || matchingStacks.length == 0) continue;
                ItemStack ingredientStack = matchingStacks[0];
                // Recursively price each ingredient
                double ingredientPrice = getBasePrice(ingredientStack, recipeManager, registryAccess);
                totalIngredientPrice += ingredientPrice;
                hasIngredients = true;
            }
            if (!hasIngredients) {
                return UNCRAFTABLE_PRICE;
            }
            int outputCount = recipe.getResultItem(registryAccess).getCount();
            if (outputCount <= 0) {
                outputCount = 1;
            }
            double perItemPrice = totalIngredientPrice / (double) outputCount;
            return Math.round(perItemPrice * 100.0) / 100.0;
        } finally {
            computing.remove(key);
        }
    }

    private List<Recipe<?>> findRecipesFor(ItemStack stack, RecipeManager recipeManager, RegistryAccess registryAccess) {
        List<Recipe<?>> result = new ArrayList<>();
        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            ItemStack output = recipe.getResultItem(registryAccess);
            if (output == null || output.isEmpty() || !isSameItem(output, stack)) continue;
            result.add(recipe);
        }
        return result;
    }

    private boolean isSameItem(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem();
    }

    private String getItemKey(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null ? id.toString() : "unknown";
    }

    public void clearCache() {
        priceCache.clear();
        computing.clear();
    }
}
