package asd.itamio.modernshop;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PriceEngine {
    // Starter prices at the $1 level. Rarity multipliers (from ItemRarity)
    // are applied on top of these base prices so rare items cost more.
    //
    // Pricing flow:
    // 1. Uncraftable items (no recipe): $1 * rarity_multiplier * bulk_discount
    //    - Diamond (RARE): $1 * 30 = $30
    //    - Enchanted golden apple (LEGENDARY): $1 * 2,000 = $2,000
    // 2. Crafted items: recipe_price * crafted_rarity_multiplier
    //    - Recipe price = sum of ingredient prices (each already includes rarity)
    //    - Crafted multiplier is SMALLER (1.0x-5.0x) to avoid double-counting
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

    /**
     * Client-side price estimate.
     */
    public double getBuyPrice(ItemStack stack) {
        double base = getBasePriceClient(stack);
        return Math.round(base * BUY_MULTIPLIER * 100.0) / 100.0;
    }

    public double getSellPrice(ItemStack stack) {
        double base = getBasePriceClient(stack);
        return Math.round(base * SELL_MULTIPLIER * 100.0) / 100.0;
    }

    private double getBasePriceClient(ItemStack stack) {
        // In MC 1.21.11 the client no longer exposes a RecipeManager (only a
        // limited RecipeAccess via ClientPacketListener.recipes() which has no
        // getRecipes() method). Recipe-based pricing is therefore computed
        // server-side and persisted to PriceConfig; the client reads from there.

        // Fall back to PriceConfig
        if (priceConfig != null && priceConfig.hasPrice(stack)) {
            double configPrice = priceConfig.getPrice(stack);
            if (configPrice >= 0) {
                return configPrice;
            }
        }

        // Last resort: flat pricing with rarity multiplier
        return UNCRAFTABLE_PRICE * ItemRarity.getRarityMultiplier(stack) * ItemRarity.getBulkDiscountMultiplier(stack);
    }

    /**
     * Server-side price calculation with recipe manager access.
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
            // Circular dependency detected - treat as base material
            return BASE_MATERIAL_PRICE * ItemRarity.getRarityMultiplier(stack) * ItemRarity.getBulkDiscountMultiplier(stack);
        }
        computing.add(key);
        try {
            // If no recipe manager is available, return flat pricing with rarity
            if (recipeManager == null || registryAccess == null) {
                return UNCRAFTABLE_PRICE * ItemRarity.getRarityMultiplier(stack) * ItemRarity.getBulkDiscountMultiplier(stack);
            }

            // Try to find recipes whose output matches this item
            List<RecipeHolder<?>> recipes = findRecipesFor(stack, recipeManager);
            if (recipes.isEmpty()) {
                // No recipe found - item is uncraftable. Apply the rarity multiplier
                return UNCRAFTABLE_PRICE * ItemRarity.getRarityMultiplier(stack) * ItemRarity.getBulkDiscountMultiplier(stack);
            }

            // Find the cheapest recipe for this item
            double cheapestPrice = Double.MAX_VALUE;
            boolean foundRecipe = false;

            for (RecipeHolder<?> recipeHolder : recipes) {
                Recipe<?> recipe = recipeHolder.value();
                if (!(recipe instanceof CraftingRecipe)) continue;

                List<Ingredient> ingredients = recipe.placementInfo().ingredients();
                double totalIngredientPrice = 0.0;
                boolean hasIngredients = false;

                for (Ingredient ingredient : ingredients) {
                    if (ingredient == null || ingredient.isEmpty()) continue;
                    Iterator<Holder<Item>> it = ingredient.items().iterator();
                    if (!it.hasNext()) continue;
                    // Use the cheapest matching item for this ingredient slot
                    double cheapestIngredient = Double.MAX_VALUE;
                    while (it.hasNext()) {
                        ItemStack ingredientStack = new ItemStack(it.next());
                        if (ingredientStack.isEmpty()) continue;
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

                int outputCount = getRecipeOutputCount(recipe);
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

            // Apply the crafted item's OWN rarity multiplier on top of the recipe price.
            // Uses the CRAFTED multiplier (smaller than the uncraftable multiplier).
            double rarityAdjustedPrice = cheapestPrice * ItemRarity.getCraftedRarityMultiplier(stack);
            double finalPrice = Math.max(MIN_BASE_PRICE, rarityAdjustedPrice);
            return Math.round(finalPrice * 100.0) / 100.0;
        } finally {
            computing.remove(key);
        }
    }

    private int getRecipeOutputCount(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            ItemStack result = shaped.assemble(CraftingInput.EMPTY, null);
            return result.getCount();
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            ItemStack result = shapeless.assemble(CraftingInput.EMPTY, null);
            return result.getCount();
        }

        // Fall back to recipe displays
        try {
            List<RecipeDisplay> displays = recipe.display();
            if (!displays.isEmpty()) {
                SlotDisplay resultSlot = displays.get(0).result();
                if (resultSlot instanceof SlotDisplay.ItemStackSlotDisplay itemStackSlot) {
                    return itemStackSlot.stack().getCount();
                }
            }
        } catch (Exception ignored) {
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
        if (recipe instanceof ShapedRecipe shaped) {
            return shaped.assemble(CraftingInput.EMPTY, null);
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            return shapeless.assemble(CraftingInput.EMPTY, null);
        }

        try {
            List<RecipeDisplay> displays = recipe.display();
            if (!displays.isEmpty()) {
                SlotDisplay resultSlot = displays.get(0).result();
                if (resultSlot instanceof SlotDisplay.ItemStackSlotDisplay itemStackSlot) {
                    return itemStackSlot.stack();
                }
            }
        } catch (Exception ignored) {
        }

        return ItemStack.EMPTY;
    }

    private boolean isSameItem(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem();
    }

    private String getItemKey(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
