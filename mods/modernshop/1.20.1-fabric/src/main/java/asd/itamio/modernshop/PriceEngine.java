package asd.itamio.modernshop;

import net.minecraft.client.Minecraft;
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
    public static final double MIN_BASE_PRICE = 1200.0;
    public static final double BASE_MATERIAL_PRICE = 1200.0;
    public static final double UNCRAFTABLE_PRICE = 1200.0;
    public static final double BUY_MULTIPLIER = 1.2;
    public static final double SELL_MULTIPLIER = 0.8;

    private final Map<String, Double> priceCache = new HashMap<>();
    private final Set<String> computing = new HashSet<>();
    private PriceConfig priceConfig;

    /**
     * Initialize with a PriceConfig for persistent price storage.
     * Call this on both client and server sides.
     */
    public void setPriceConfig(PriceConfig config) {
        this.priceConfig = config;
    }

    /**
     * Client-side price estimate.
     * Attempts to use the client's recipe manager (if a world is loaded)
     * for accurate pricing. Falls back to PriceConfig, then flat pricing.
     */
    public double getBuyPrice(ItemStack stack) {
        double base = getBasePriceClient(stack);
        return Math.round(base * BUY_MULTIPLIER * 100.0) / 100.0;
    }

    public double getSellPrice(ItemStack stack) {
        double base = getBasePriceClient(stack);
        return Math.round(base * SELL_MULTIPLIER * 100.0) / 100.0;
    }

    /**
     * Client-side base price: try recipe manager, then config file, then flat price.
     */
    private double getBasePriceClient(ItemStack stack) {
        // First try using the client's recipe manager (world must be loaded)
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                RecipeManager recipeManager = mc.level.getRecipeManager();
                RegistryAccess registryAccess = mc.level.registryAccess();
                if (recipeManager != null && registryAccess != null) {
                    return getBasePrice(stack, recipeManager, registryAccess);
                }
            }
        } catch (Exception ignored) {
        }

        // Fall back to PriceConfig
        if (priceConfig != null && priceConfig.hasPrice(stack)) {
            double configPrice = priceConfig.getPrice(stack);
            if (configPrice >= 0) {
                return configPrice;
            }
        }

        // Last resort: flat pricing
        return UNCRAFTABLE_PRICE;
    }

    /**
     * Server-side price calculation with recipe manager access.
     * Prices are saved to PriceConfig for persistence and client use.
     */
    public double getBuyPrice(ItemStack stack, @Nullable RecipeManager recipeManager, @Nullable RegistryAccess registryAccess) {
        double base = getBasePrice(stack, recipeManager, registryAccess);
        return Math.round(base * BUY_MULTIPLIER * 100.0) / 100.0;
    }

    public double getSellPrice(ItemStack stack, @Nullable RecipeManager recipeManager, @Nullable RegistryAccess registryAccess) {
        double base = getBasePrice(stack, recipeManager, registryAccess);
        return Math.round(base * SELL_MULTIPLIER * 100.0) / 100.0;
    }

    /**
     * Get the base price of an item, with recipe manager access.
     * Calculated prices are saved to the config file for future use.
     */
    public double getBasePrice(ItemStack stack, @Nullable RecipeManager recipeManager, @Nullable RegistryAccess registryAccess) {
        if (stack == null || stack.isEmpty()) {
            return 0.0;
        }
        String key = getItemKey(stack);

        // Check in-memory cache first
        if (priceCache.containsKey(key)) {
            return priceCache.get(key);
        }

        // Check config file for a previously saved price
        if (priceConfig != null && priceConfig.hasPrice(stack)) {
            double configPrice = priceConfig.getPrice(stack);
            if (configPrice >= 0) {
                priceCache.put(key, configPrice);
                return configPrice;
            }
        }

        // Calculate price from recipes
        double price = computeBasePrice(stack, recipeManager, registryAccess);

        // Cache the result
        priceCache.put(key, price);

        // Save to config file for persistence and client access
        if (priceConfig != null) {
            priceConfig.setPrice(stack, price);
        }

        return price;
    }

    public double getBasePrice(ItemStack stack) {
        return getBasePriceClient(stack);
    }

    private double computeBasePrice(ItemStack stack, @Nullable RecipeManager recipeManager, @Nullable RegistryAccess registryAccess) {
        String key = getItemKey(stack);
        if (computing.contains(key)) {
            // Circular dependency detected — treat this ingredient as base material
            return BASE_MATERIAL_PRICE;
        }
        computing.add(key);
        try {
            // If no recipe manager is available, return flat pricing
            if (recipeManager == null || registryAccess == null) {
                return UNCRAFTABLE_PRICE;
            }

            // Try to find a recipe whose output matches this item
            List<Recipe<?>> recipes = findRecipesFor(stack, recipeManager, registryAccess);
            if (recipes.isEmpty()) {
                // No recipe found — item is uncraftable (e.g., diamond ore, raw materials)
                // Check if this item is a base crafting material by seeing if it's used as an ingredient
                if (isIngredientInAnyRecipe(stack, recipeManager, registryAccess)) {
                    return BASE_MATERIAL_PRICE;
                }
                return UNCRAFTABLE_PRICE;
            }

            // Find the cheapest recipe for this item
            double cheapestPrice = Double.MAX_VALUE;
            boolean foundRecipe = false;

            for (Recipe<?> recipe : recipes) {
                double totalIngredientPrice = 0.0;
                boolean hasIngredients = false;
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient == Ingredient.EMPTY) continue;
                    ItemStack[] matchingStacks = ingredient.getItems();
                    if (matchingStacks == null || matchingStacks.length == 0) continue;
                    // Use the cheapest matching item for this ingredient slot
                    double cheapestIngredient = Double.MAX_VALUE;
                    for (ItemStack ingredientStack : matchingStacks) {
                        if (ingredientStack == null || ingredientStack.isEmpty()) continue;
                        double ingredientPrice = getBasePrice(ingredientStack, recipeManager, registryAccess);
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

                int outputCount = recipe.getResultItem(registryAccess).getCount();
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
                return UNCRAFTABLE_PRICE;
            }

            // Enforce minimum price of $1200 for ALL items
            double finalPrice = Math.max(MIN_BASE_PRICE, cheapestPrice);
            return Math.round(finalPrice * 100.0) / 100.0;
        } finally {
            computing.remove(key);
        }
    }

    /**
     * Check if an item is used as an ingredient in any recipe.
     * This helps identify base materials vs truly uncraftable items.
     */
    private boolean isIngredientInAnyRecipe(ItemStack stack, RecipeManager recipeManager, RegistryAccess registryAccess) {
        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient == Ingredient.EMPTY) continue;
                ItemStack[] matchingStacks = ingredient.getItems();
                if (matchingStacks == null || matchingStacks.length == 0) continue;
                for (ItemStack match : matchingStacks) {
                    if (match != null && !match.isEmpty() && match.getItem() == stack.getItem()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<Recipe<?>> findRecipesFor(ItemStack stack, RecipeManager recipeManager, RegistryAccess registryAccess) {
        List<Recipe<?>> result = new ArrayList<>();
        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            ItemStack output = recipe.getResultItem(registryAccess);
            if (output == null || output.isEmpty()) continue;
            if (isSameItem(output, stack)) {
                result.add(recipe);
            }
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

    /**
     * Get the PriceConfig instance, if set.
     */
    @Nullable
    public PriceConfig getPriceConfig() {
        return priceConfig;
    }
}
