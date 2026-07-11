package asd.itamio.worldshop;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import java.util.*;

public class PriceEngine {
    public static final double BASE_MATERIAL_PRICE = 2.0;
    public static final double UNCRAFTABLE_PRICE = 1000.0;
    public static final double BUY_MULTIPLIER = 1.2;
    public static final double SELL_MULTIPLIER = 0.8;

    private final Map<String, Double> priceCache = new HashMap<>();
    private final Set<String> computing = new HashSet<>();
    private MinecraftServer server;

    public void setServer(MinecraftServer server) {
        this.server = server;
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
            if (server == null) {
                return UNCRAFTABLE_PRICE;
            }
            RecipeManager recipeManager = server.getRecipeManager();
            List<RecipeHolder<?>> recipes = findRecipesFor(stack, recipeManager);
            if (recipes.isEmpty()) {
                return UNCRAFTABLE_PRICE;
            }
            RecipeHolder<?> recipeHolder = recipes.get(0);
            Recipe<?> recipe = recipeHolder.value();

            if (!(recipe instanceof CraftingRecipe)) {
                return UNCRAFTABLE_PRICE;
            }

            List<Ingredient> ingredients = recipe.placementInfo().ingredients();
            double totalIngredientPrice = 0.0;
            boolean hasIngredients = false;

            for (Ingredient ingredient : ingredients) {
                if (ingredient == null || ingredient.isEmpty()) continue;
                // Get the first matching item stack from this ingredient
                Iterator<Holder<Item>> it = ingredient.items().iterator();
                if (!it.hasNext()) continue;
                ItemStack ingredientStack = new ItemStack(it.next());
                if (ingredientStack.isEmpty()) continue;
                double ingredientPrice = getBasePrice(ingredientStack);
                totalIngredientPrice += ingredientPrice;
                hasIngredients = true;
            }

            if (!hasIngredients) {
                return UNCRAFTABLE_PRICE;
            }

            // Get the output count - use the result from the recipe
            ItemStack output = getRecipeResult(recipe);
            int outputCount = output.getCount();
            if (outputCount <= 0) {
                outputCount = 1;
            }

            double perItemPrice = totalIngredientPrice / (double) outputCount;
            return Math.round(perItemPrice * 100.0) / 100.0;
        } finally {
            computing.remove(key);
        }
    }

    private ItemStack getRecipeResult(Recipe<?> recipe) {
        // Try to get result via display (modern approach)
        // For crafting recipes, assemble with empty input returns the result
        if (recipe instanceof ShapedRecipe shaped) {
            return shaped.assemble(CraftingInput.EMPTY, null);
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            return shapeless.assemble(CraftingInput.EMPTY, null);
        }
        // Fallback
        return ItemStack.EMPTY;
    }

    private List<RecipeHolder<?>> findRecipesFor(ItemStack stack, RecipeManager recipeManager) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof CraftingRecipe)) continue;
            ItemStack output = getRecipeResult(recipe);
            if (output == null || output.isEmpty() || !isSameItem(output, stack)) continue;
            result.add(holder);
        }
        return result;
    }

    private boolean isSameItem(ItemStack a, ItemStack b) {
        if (a.getItem() != b.getItem()) {
            return false;
        }
        // In modern Minecraft, damage value is part of components
        return true;
    }

    private String getItemKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).getRegisteredName();
    }

    public void clearCache() {
        priceCache.clear();
        computing.clear();
    }
}
