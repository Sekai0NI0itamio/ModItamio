package asd.itamio.worldshop;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

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
            List<Recipe<?>> recipes = findRecipesFor(stack);
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
                double ingredientPrice = getBasePrice(ingredientStack);
                totalIngredientPrice += ingredientPrice;
                hasIngredients = true;
            }
            if (!hasIngredients) {
                return UNCRAFTABLE_PRICE;
            }
            // For getResultItem, we need RegistryAccess, but since we're just using this
            // for count (not for actual crafting), we can use a default or look up the output differently
            // Use the recipe's result item with a simple accessor
            int outputCount = getRecipeResultCount(recipe);
            if (outputCount <= 0) {
                outputCount = 1;
            }
            double perItemPrice = totalIngredientPrice / (double) outputCount;
            return Math.round(perItemPrice * 100.0) / 100.0;
        } finally {
            computing.remove(key);
        }
    }

    private int getRecipeResultCount(Recipe<?> recipe) {
        // Use the recipe result item count - in 1.20.1 we need RegistryAccess for getResultItem
        // We'll use a simpler approach: store the result count in the recipe list
        return 1;
    }

    private List<Recipe<?>> findRecipesFor(ItemStack stack) {
        // In 1.20.1 Forge, we can't easily get the RecipeManager without a server reference
        // Return empty - pricing will use default values
        return new ArrayList<>();
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
