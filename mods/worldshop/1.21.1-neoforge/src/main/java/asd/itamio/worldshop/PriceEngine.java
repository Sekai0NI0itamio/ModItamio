package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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

    public double getBuyPrice(ItemStack stack) {
        double base = getBasePriceClient(stack);
        return Math.round(base * BUY_MULTIPLIER * 100.0) / 100.0;
    }

    public double getSellPrice(ItemStack stack) {
        double base = getBasePriceClient(stack);
        return Math.round(base * SELL_MULTIPLIER * 100.0) / 100.0;
    }

    private double getBasePriceClient(ItemStack stack) {
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

        if (priceConfig != null && priceConfig.hasPrice(stack)) {
            double configPrice = priceConfig.getPrice(stack);
            if (configPrice >= 0) {
                return configPrice;
            }
        }

        return UNCRAFTABLE_PRICE * ItemRarity.getRarityMultiplier(stack) * ItemRarity.getBulkDiscountMultiplier(stack);
    }

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

        if (priceConfig != null && priceConfig.hasPrice(stack)) {
            double configPrice = priceConfig.getPrice(stack);
            if (configPrice >= 0) {
                priceCache.put(key, configPrice);
                return configPrice;
            }
        }

        double price = computeBasePrice(stack, recipeManager, registryAccess);

        priceCache.put(key, price);

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
            return BASE_MATERIAL_PRICE * ItemRarity.getRarityMultiplier(stack) * ItemRarity.getBulkDiscountMultiplier(stack);
        }
        computing.add(key);
        try {
            if (recipeManager == null || registryAccess == null) {
                return UNCRAFTABLE_PRICE * ItemRarity.getRarityMultiplier(stack) * ItemRarity.getBulkDiscountMultiplier(stack);
            }

            List<Recipe<?>> recipes = findRecipesFor(stack, recipeManager, registryAccess);
            if (recipes.isEmpty()) {
                return UNCRAFTABLE_PRICE * ItemRarity.getRarityMultiplier(stack) * ItemRarity.getBulkDiscountMultiplier(stack);
            }

            double cheapestPrice = Double.MAX_VALUE;
            boolean foundRecipe = false;

            for (Recipe<?> recipe : recipes) {
                double totalIngredientPrice = 0.0;
                boolean hasIngredients = false;
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient == Ingredient.EMPTY) continue;
                    ItemStack[] matchingStacks = ingredient.getItems();
                    if (matchingStacks == null || matchingStacks.length == 0) continue;
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
                return UNCRAFTABLE_PRICE * ItemRarity.getRarityMultiplier(stack) * ItemRarity.getBulkDiscountMultiplier(stack);
            }

            double rarityAdjustedPrice = cheapestPrice * ItemRarity.getCraftedRarityMultiplier(stack);
            double finalPrice = Math.max(MIN_BASE_PRICE, rarityAdjustedPrice);
            return Math.round(finalPrice * 100.0) / 100.0;
        } finally {
            computing.remove(key);
        }
    }

    private List<Recipe<?>> findRecipesFor(ItemStack stack, RecipeManager recipeManager, RegistryAccess registryAccess) {
        List<Recipe<?>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            try {
                Recipe<?> recipe = holder.value();
                ItemStack output = recipe.getResultItem(registryAccess);
                if (output == null || output.isEmpty()) continue;
                if (isSameItem(output, stack)) {
                    result.add(recipe);
                }
            } catch (Exception ignored) {
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

    @Nullable
    public PriceConfig getPriceConfig() {
        return priceConfig;
    }
}
