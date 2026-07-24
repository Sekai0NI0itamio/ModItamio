package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
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
                try {
                    RecipeManager recipeManager = (RecipeManager) mc.level.recipeAccess();
                    RegistryAccess registryAccess = mc.level.registryAccess();
                    if (recipeManager != null && registryAccess != null) {
                        return getBasePrice(stack, recipeManager, registryAccess);
                    }
                } catch (ClassCastException ignored) {
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

    @SuppressWarnings({"rawtypes", "unchecked"})
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

            List<Recipe<?>> recipes = findRecipesFor(stack, recipeManager);
            if (recipes.isEmpty()) {
                return UNCRAFTABLE_PRICE * ItemRarity.getRarityMultiplier(stack) * ItemRarity.getBulkDiscountMultiplier(stack);
            }

            double cheapestPrice = Double.MAX_VALUE;
            boolean foundRecipe = false;

            for (Recipe<?> recipe : recipes) {
                double totalIngredientPrice = 0.0;
                boolean hasIngredients = false;

                List<Ingredient> ingredients = recipe.placementInfo().ingredients();
                for (Ingredient ingredient : ingredients) {
                    if (ingredient == null || ingredient.isEmpty()) continue;
                    Iterator<Holder<Item>> it = ingredient.items().iterator();
                    if (!it.hasNext()) continue;

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
            if (displays != null && !displays.isEmpty()) {
                SlotDisplay resultSlot = displays.get(0).result();
                if (resultSlot instanceof SlotDisplay.ItemStackSlotDisplay itemStackSlot) {
                    return itemStackSlot.stack().count();
                }
            }
        } catch (Exception ignored) {
        }

        return 1;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Recipe<?>> findRecipesFor(ItemStack stack, RecipeManager recipeManager) {
        List<Recipe<?>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            try {
                Recipe<?> recipe = holder.value();
                if (!(recipe instanceof CraftingRecipe)) continue;
                ItemStack output = tryGetRecipeOutput(recipe);
                if (output == null || output.isEmpty() || !isSameItem(output, stack)) continue;
                result.add(recipe);
            } catch (Exception ignored) {
            }
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
            if (displays != null && !displays.isEmpty()) {
                SlotDisplay resultSlot = displays.get(0).result();
                if (resultSlot instanceof SlotDisplay.ItemStackSlotDisplay itemStackSlot) {
                    return itemStackSlot.stack().create();
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
        return BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).getRegisteredName();
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
