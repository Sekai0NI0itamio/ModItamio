package asd.itamio.worldshop;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
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
    private int recipeCount = 0;
    private boolean loggedRecipeInfo = false;

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

            // Log recipe info once for debugging
            if (!loggedRecipeInfo) {
                recipeCount = 0;
                for (RecipeHolder<?> ignored : recipeManager.getRecipes()) {
                    recipeCount++;
                }
                WorldShop.LOGGER.info("PriceEngine: " + recipeCount + " total recipes available");
                loggedRecipeInfo = true;
            }

            List<RecipeHolder<?>> recipes = findRecipesFor(stack, recipeManager);
            if (recipes.isEmpty()) {
                return UNCRAFTABLE_PRICE;
            }
            RecipeHolder<?> recipeHolder = recipes.get(0);
            Recipe<?> recipe = recipeHolder.value();

            if (!(recipe instanceof CraftingRecipe)) {
                return UNCRAFTABLE_PRICE;
            }

            // Get ingredients from placement info
            List<Ingredient> ingredients = recipe.placementInfo().ingredients();
            double totalIngredientPrice = 0.0;
            boolean hasIngredients = false;

            for (Ingredient ingredient : ingredients) {
                if (ingredient == null || ingredient.isEmpty()) continue;
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

            // Get the output count using the properly supported API
            int outputCount = getRecipeOutputCount(recipe);
            if (outputCount <= 0) {
                outputCount = 1;
            }

            double perItemPrice = totalIngredientPrice / (double) outputCount;
            return Math.round(perItemPrice * 100.0) / 100.0;
        } finally {
            computing.remove(key);
        }
    }

    private int getRecipeOutputCount(Recipe<?> recipe) {
        // Method 1: Try assemble (returns result.copy() for ShapedRecipe/ShapelessRecipe)
        if (recipe instanceof ShapedRecipe shaped) {
            ItemStack result = shaped.assemble(CraftingInput.EMPTY);
            return result.getCount();
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            ItemStack result = shapeless.assemble(CraftingInput.EMPTY);
            return result.getCount();
        }

        // Method 2: Try getting result from recipe displays
        try {
            List<RecipeDisplay> displays = recipe.display();
            if (!displays.isEmpty()) {
                SlotDisplay resultSlot = displays.get(0).result();
                if (resultSlot instanceof SlotDisplay.ItemStackSlotDisplay itemStackSlot) {
                    return itemStackSlot.stack().count();
                }
            }
        } catch (Exception e) {
            // Fallback
        }

        return 1;
    }

    private List<RecipeHolder<?>> findRecipesFor(ItemStack stack, RecipeManager recipeManager) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof CraftingRecipe)) continue;
            ItemStack output = tryGetRecipeOutput(recipe);
            if (output == null || output.isEmpty() || !isSameItem(output, stack)) continue;
            result.add(holder);
        }
        return result;
    }

    private ItemStack tryGetRecipeOutput(Recipe<?> recipe) {
        // Method 1: assemble (returns result.copy() for ShapedRecipe/ShapelessRecipe)
        if (recipe instanceof ShapedRecipe shaped) {
            return shaped.assemble(CraftingInput.EMPTY);
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            return shapeless.assemble(CraftingInput.EMPTY);
        }

        // Method 2: Try recipe displays
        try {
            List<RecipeDisplay> displays = recipe.display();
            if (!displays.isEmpty()) {
                SlotDisplay resultSlot = displays.get(0).result();
                if (resultSlot instanceof SlotDisplay.ItemStackSlotDisplay itemStackSlot) {
                    return itemStackSlot.stack().create();
                }
            }
        } catch (Exception e) {
            // Fallback
        }

        return ItemStack.EMPTY;
    }

    private boolean isSameItem(ItemStack a, ItemStack b) {
        if (a.getItem() != b.getItem()) {
            return false;
        }
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
