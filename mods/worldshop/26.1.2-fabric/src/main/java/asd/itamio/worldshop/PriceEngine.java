package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Rarity-first pricing engine for 26.1.2.
 *
 * Pricing flow:
 * 1. Uncraftable items (no recipe): $1 * rarity_multiplier * bulk_discount
 * 2. Crafted items: recipe_price * crafted_rarity_multiplier
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
    private MinecraftServer server;
    private int recipeCount = 0;
    private boolean loggedRecipeInfo = false;

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public void setPriceConfig(PriceConfig config) {
        this.priceConfig = config;
    }

    @Nullable
    public PriceConfig getPriceConfig() {
        return priceConfig;
    }

    // ========== Client-side methods ==========

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
                return getBasePrice(stack, (RecipeManager) mc.level.recipeAccess(), mc.level.registryAccess());
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

    // ========== Server-side methods ==========

    public double getBuyPrice(ItemStack stack, @Nullable RecipeManager recipeManager, @Nullable RegistryAccess registryAccess) {
        double base = getBasePrice(stack, recipeManager, registryAccess);
        return Math.round(base * BUY_MULTIPLIER * 100.0) / 100.0;
    }

    public double getSellPrice(ItemStack stack, @Nullable RecipeManager recipeManager, @Nullable RegistryAccess registryAccess) {
        double base = getBasePrice(stack, recipeManager, registryAccess);
        return Math.round(base * SELL_MULTIPLIER * 100.0) / 100.0;
    }

    public double getBasePrice(ItemStack stack) {
        return getBasePriceClient(stack);
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

    private double computeBasePrice(ItemStack stack, @Nullable RecipeManager recipeManager, @Nullable RegistryAccess registryAccess) {
        String key = getItemKey(stack);
        if (computing.contains(key)) {
            return BASE_MATERIAL_PRICE * ItemRarity.getRarityMultiplier(stack) * ItemRarity.getBulkDiscountMultiplier(stack);
        }
        computing.add(key);
        try {
            if (recipeManager == null || registryAccess == null) {
                if (server == null) {
                    return UNCRAFTABLE_PRICE * ItemRarity.getRarityMultiplier(stack) * ItemRarity.getBulkDiscountMultiplier(stack);
                }
                recipeManager = server.getRecipeManager();
            }

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
                return UNCRAFTABLE_PRICE * ItemRarity.getRarityMultiplier(stack) * ItemRarity.getBulkDiscountMultiplier(stack);
            }

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

                    double cheapestIngredient = Double.MAX_VALUE;
                    while (it.hasNext()) {
                        Holder<Item> itemHolder = it.next();
                        ItemStack ingredientStack = new ItemStack(itemHolder);
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

                if (!hasIngredients) continue;

                int outputCount = getRecipeOutputCount(recipe);
                if (outputCount <= 0) outputCount = 1;

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

    private int getRecipeOutputCount(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            ItemStack result = shaped.assemble(CraftingInput.EMPTY);
            return result.getCount();
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            ItemStack result = shapeless.assemble(CraftingInput.EMPTY);
            return result.getCount();
        }

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
        if (recipe instanceof ShapedRecipe shaped) {
            return shaped.assemble(CraftingInput.EMPTY);
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            return shapeless.assemble(CraftingInput.EMPTY);
        }

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
        return a.getItem() == b.getItem();
    }

    private String getItemKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).getRegisteredName();
    }

    public void clearCache() {
        priceCache.clear();
        computing.clear();
    }
}
