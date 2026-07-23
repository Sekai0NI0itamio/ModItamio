package asd.itamio.worldshop;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

import java.util.List;
import java.util.UUID;

public class ShopPacketHandler implements IPayloadHandler<ShopPacket> {
    @Override
    public void handle(ShopPacket message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            try {
                switch (message.getType()) {
                    case ShopPacket.BUY_ITEM:
                        handleBuy(player, message.getCategoryIndex(), message.getItemIndex(), message.getQuantity());
                        break;
                    case ShopPacket.SELL_HAND:
                        handleSellHand(player);
                        break;
                    case ShopPacket.SELL_GUI_ITEMS:
                        handleSellGuiItems(player, message.getItems());
                        break;
                    case ShopPacket.REMOVE_ITEM:
                        handleRemoveItem(player, message.getCategoryIndex(), message.getItemIndex());
                        break;
                    case ShopPacket.REMOVE_CATEGORY:
                        handleRemoveCategory(player, message.getCategoryIndex());
                        break;
                    case ShopPacket.ADD_CATEGORY:
                        handleAddCategory(player, message.getStringData1(), message.getStringData2());
                        break;
                    case ShopPacket.ADD_ITEM:
                        handleAddItem(player, message.getCategoryIndex(), message.getStringData1());
                        break;
                    case ShopPacket.EDIT_ITEM:
                        handleEditItem(player, message.getCategoryIndex(), message.getStringData1(), message.getStringData2(), message.getStringData3(), message.getDoubleData1(), message.getDoubleData2());
                        break;
                    case ShopPacket.REORDER_CATEGORIES:
                        handleReorderCategories(player, message.getStringArrayData());
                        break;
                    case ShopPacket.RESET_CATEGORY_ORDER:
                        handleResetCategoryOrder(player);
                        break;
                    case ShopPacket.RESET_ALL_PRICES:
                        handleResetAllPrices(player);
                        break;
                    case ShopPacket.RECALCULATE_CATEGORY:
                        handleRecalculateCategory(player, message.getCategoryIndex());
                        break;
                    case ShopPacket.RESET_CATEGORY:
                        handleResetCategory(player, message.getCategoryIndex());
                        break;
                    case ShopPacket.RECALCULATE_BLOCK:
                        handleRecalculateBlock(player, message.getStringData1());
                        break;
                    case ShopPacket.RESET_BLOCK:
                        handleResetBlock(player, message.getStringData1());
                        break;
                    case ShopPacket.SAVE_CONFIG:
                        handleSaveConfig(player, message.getDoubleData1() > 0.0);
                        break;
                    default:
                        player.sendSystemMessage(Component.literal("\u00a7cUnknown packet type."));
                }
            } catch (Exception e) {
                WorldShop.LOGGER.error("Error handling World Shop packet type {}: {}", message.getType(), e.getMessage(), e);
            }
        });
    }

    private static void handleBuy(ServerPlayer player, int categoryIndex, int itemIndex, int quantity) {
        try {
            List<ShopCategory> categories = WorldShop.getCategories();
            if (categoryIndex < 0 || categoryIndex >= categories.size()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid category."));
                return;
            }
            ShopCategory category = categories.get(categoryIndex);
            List<ItemStack> items = category.getItems();
            if (itemIndex < 0 || itemIndex >= items.size()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid item."));
                return;
            }
            ItemStack itemStack = items.get(itemIndex);
            RecipeManager recipeManager = player.serverLevel().getRecipeManager();
            RegistryAccess registryAccess = player.serverLevel().registryAccess();
            double pricePerItem = WorldShop.getPriceEngine().getBuyPrice(itemStack, recipeManager, registryAccess);
            double totalCost = pricePerItem * (double) quantity;
            EconomyProvider economy = WorldShop.getEconomyProvider(player.serverLevel());
            UUID uuid = player.getUUID();

            if (!economy.subtractBalance(player.serverLevel(), uuid, totalCost)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need $" + String.format("%.2f", totalCost) + " but have $" + String.format("%.2f", economy.getBalance(player.serverLevel(), uuid)) + "."));
                return;
            }

            int maxStackSize = itemStack.getMaxStackSize();
            for (int remaining = quantity; remaining > 0; remaining -= maxStackSize) {
                int stackSize = Math.min(remaining, maxStackSize);
                ItemStack toGive = itemStack.copy();
                toGive.setCount(stackSize);
                if (!player.getInventory().add(toGive)) {
                    player.drop(toGive, false);
                }
            }

            player.sendSystemMessage(Component.literal("\u00a7aBought " + quantity + "x " + itemStack.getHoverName().getString() + " for $" + String.format("%.2f", totalCost) + "!"));
            player.sendSystemMessage(Component.literal("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(player.serverLevel(), uuid))));
        } catch (Exception e) {
            WorldShop.LOGGER.error("Error handling buy: {}", e.getMessage(), e);
        }
    }

    public static int handleSellHand(ServerPlayer player) {
        try {
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00a7cYou are not holding any item."));
                return 0;
            }

            PriceEngine priceEngine = WorldShop.getPriceEngine();
            RecipeManager recipeManager = player.serverLevel().getRecipeManager();
            RegistryAccess registryAccess = player.serverLevel().registryAccess();
            double sellPricePerItem = priceEngine.getSellPrice(held, recipeManager, registryAccess);
            int totalSold = 0;

            for (int i = 0; i < player.getInventory().items.size(); i++) {
                ItemStack slot = player.getInventory().items.get(i);
                if (slot.isEmpty() || slot.getItem() != held.getItem()) continue;
                totalSold += slot.getCount();
                player.getInventory().items.set(i, ItemStack.EMPTY);
            }

            if (totalSold == 0) {
                player.sendSystemMessage(Component.literal("\u00a7cNo items found to sell."));
                return 0;
            }

            double totalEarnings = sellPricePerItem * (double) totalSold;
            EconomyProvider economy = WorldShop.getEconomyProvider(player.serverLevel());
            economy.addBalance(player.serverLevel(), player.getUUID(), totalEarnings);

            player.sendSystemMessage(Component.literal("\u00a7aSold " + totalSold + "x " + held.getHoverName().getString() + " for $" + String.format("%.2f", totalEarnings) + "!"));
            player.sendSystemMessage(Component.literal("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(player.serverLevel(), player.getUUID()))));
            player.getInventory().setChanged();
            return 1;
        } catch (Exception e) {
            WorldShop.LOGGER.error("Error handling sell hand: {}", e.getMessage(), e);
            return 0;
        }
    }

    private static void handleSellGuiItems(ServerPlayer player, List<ItemStack> items) {
        try {
            if (items.isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00a7cNo items to sell."));
                return;
            }

            PriceEngine priceEngine = WorldShop.getPriceEngine();
            RecipeManager recipeManager = player.serverLevel().getRecipeManager();
            RegistryAccess registryAccess = player.serverLevel().registryAccess();
            double totalEarnings = 0.0;
            int totalSold = 0;

            for (ItemStack sellStack : items) {
                if (sellStack == null || sellStack.isEmpty()) continue;
                double sellPrice = priceEngine.getSellPrice(sellStack, recipeManager, registryAccess);
                totalEarnings += sellPrice * (double) sellStack.getCount();
                totalSold += sellStack.getCount();
            }

            if (totalSold == 0) {
                player.sendSystemMessage(Component.literal("\u00a7cNo items to sell."));
                return;
            }

            for (ItemStack sellStack : items) {
                if (sellStack == null || sellStack.isEmpty()) continue;
                int remaining = sellStack.getCount();
                for (int i = 0; i < player.getInventory().items.size() && remaining > 0; i++) {
                    ItemStack invStack = player.getInventory().items.get(i);
                    if (invStack.isEmpty() || invStack.getItem() != sellStack.getItem()) continue;
                    int toRemove = Math.min(remaining, invStack.getCount());
                    invStack.shrink(toRemove);
                    remaining -= toRemove;
                    if (invStack.isEmpty()) {
                        player.getInventory().items.set(i, ItemStack.EMPTY);
                    }
                }
            }

            EconomyProvider economy = WorldShop.getEconomyProvider(player.serverLevel());
            economy.addBalance(player.serverLevel(), player.getUUID(), totalEarnings);

            player.sendSystemMessage(Component.literal("\u00a7aSold " + totalSold + " items for $" + String.format("%.2f", totalEarnings) + "!"));
            player.sendSystemMessage(Component.literal("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(player.serverLevel(), player.getUUID()))));
            player.getInventory().setChanged();
        } catch (Exception e) {
            WorldShop.LOGGER.error("Error handling sell GUI items: {}", e.getMessage(), e);
        }
    }

    private static void handleRemoveItem(ServerPlayer player, int categoryIndex, int itemIndex) {
        try {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            List<ShopCategory> categories = WorldShop.getCategories();
            if (categoryIndex < 0 || categoryIndex >= categories.size()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid category."));
                return;
            }
            ShopCategory category = categories.get(categoryIndex);
            List<ItemStack> items = category.getItems();
            if (itemIndex < 0 || itemIndex >= items.size()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid item."));
                return;
            }
            ItemStack removed = items.remove(itemIndex);
            player.sendSystemMessage(Component.literal("\u00a7aRemoved " + removed.getHoverName().getString() + " from category \"" + category.getName() + "\"."));
        } catch (Exception e) {
            WorldShop.LOGGER.error("Error handling remove item: {}", e.getMessage(), e);
        }
    }

    private static void handleRemoveCategory(ServerPlayer player, int categoryIndex) {
        try {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            List<ShopCategory> categories = WorldShop.getCategories();
            if (categoryIndex < 0 || categoryIndex >= categories.size()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid category."));
                return;
            }
            ShopCategory removed = categories.remove(categoryIndex);
            player.sendSystemMessage(Component.literal("\u00a7aRemoved category \"" + removed.getName() + "\"."));
        } catch (Exception e) {
            WorldShop.LOGGER.error("Error handling remove category: {}", e.getMessage(), e);
        }
    }

    private static void handleAddCategory(ServerPlayer player, String name, String iconItemId) {
        try {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            if (name == null || name.trim().isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00a7cCategory name cannot be empty."));
                return;
            }
            if (iconItemId == null || iconItemId.trim().isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00a7cIcon item ID cannot be empty."));
                return;
            }
            ResourceLocation iconId = ResourceLocation.tryParse(iconItemId);
            if (iconId == null) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid item ID: " + iconItemId));
                return;
            }
            Item iconItem = BuiltInRegistries.ITEM.get(iconId);
            if (iconItem == null || iconItem == net.minecraft.world.item.Items.AIR) {
                player.sendSystemMessage(Component.literal("\u00a7cUnknown item: " + iconItemId));
                return;
            }
            ItemStack iconStack = new ItemStack(iconItem);
            ShopCategory newCategory = new ShopCategory(name.trim(), iconStack, null);
            WorldShop.getCategories().add(newCategory);
            player.sendSystemMessage(Component.literal("\u00a7aAdded category \"" + name + "\" with icon " + iconItemId + "."));
        } catch (Exception e) {
            WorldShop.LOGGER.error("Error handling add category: {}", e.getMessage(), e);
        }
    }

    private static void handleAddItem(ServerPlayer player, int categoryIndex, String itemId) {
        try {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            if (itemId == null || itemId.trim().isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00a7cItem ID cannot be empty."));
                return;
            }
            List<ShopCategory> categories = WorldShop.getCategories();
            if (categoryIndex < 0 || categoryIndex >= categories.size()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid category."));
                return;
            }
            ResourceLocation itemResource = ResourceLocation.tryParse(itemId);
            if (itemResource == null) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid item ID: " + itemId));
                return;
            }
            Item item = BuiltInRegistries.ITEM.get(itemResource);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                player.sendSystemMessage(Component.literal("\u00a7cUnknown item: " + itemId));
                return;
            }
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00a7cItem " + itemId + " cannot be obtained."));
                return;
            }
            ShopCategory category = categories.get(categoryIndex);
            category.addItem(stack.copy());
            player.sendSystemMessage(Component.literal("\u00a7aAdded " + stack.getHoverName().getString() + " to category \"" + category.getName() + "\"."));
        } catch (Exception e) {
            WorldShop.LOGGER.error("Error handling add item: {}", e.getMessage(), e);
        }
    }

    private static void handleEditItem(ServerPlayer player, int categoryIndex, String itemId, String displayName, String iconId, double buyPrice, double sellPrice) {
        try {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            if (itemId == null || itemId.trim().isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00a7cItem ID cannot be empty."));
                return;
            }
            List<ShopCategory> categories = WorldShop.getCategories();
            if (categoryIndex < 0 || categoryIndex >= categories.size()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid category."));
                return;
            }
            ShopCategory category = categories.get(categoryIndex);
            List<ItemStack> items = category.getItems();

            ResourceLocation targetId = ResourceLocation.tryParse(itemId);
            if (targetId == null) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid item ID: " + itemId));
                return;
            }

            ItemStack targetStack = null;
            int targetIndex = -1;
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack != null && !stack.isEmpty()) {
                    ResourceLocation stackId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (stackId.equals(targetId)) {
                        targetStack = stack;
                        targetIndex = i;
                        break;
                    }
                }
            }

            if (targetStack == null) {
                player.sendSystemMessage(Component.literal("\u00a7cItem " + itemId + " not found in this category."));
                return;
            }

            PriceConfig priceConfig = WorldShop.getPriceEngine().getPriceConfig();
            if (priceConfig != null) {
                if (buyPrice > 0) {
                    double basePrice = buyPrice / 1.2;
                    priceConfig.setPrice(targetStack, basePrice);
                    player.sendSystemMessage(Component.literal("\u00a77Set custom buy price to $" + String.format("%.2f", buyPrice)));
                }
                if (sellPrice > 0) {
                    double basePrice = sellPrice / 0.8;
                    priceConfig.setPrice(targetStack, basePrice);
                    player.sendSystemMessage(Component.literal("\u00a77Set custom sell price to $" + String.format("%.2f", sellPrice)));
                }
            }

            if (displayName != null && !displayName.trim().isEmpty() && !displayName.equals(targetStack.getHoverName().getString())) {
                ItemStack renamedStack = targetStack.copy();
                renamedStack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName.trim()));
                items.set(targetIndex, renamedStack);
                player.sendSystemMessage(Component.literal("\u00a7aSet display name to \"" + displayName.trim() + "\"."));
            }

            if (iconId != null && !iconId.trim().isEmpty()) {
                ResourceLocation iconResource = ResourceLocation.tryParse(iconId);
                if (iconResource != null) {
                    Item iconItem = BuiltInRegistries.ITEM.get(iconResource);
                    if (iconItem != null && iconItem != net.minecraft.world.item.Items.AIR) {
                        String currentIconId = BuiltInRegistries.ITEM.getKey(targetStack.getItem()).toString();
                        if (!iconId.equals(currentIconId)) {
                            ItemStack iconStack = new ItemStack(iconItem);
                            if (displayName != null && !displayName.trim().isEmpty()) {
                                iconStack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName.trim()));
                            }
                            items.set(targetIndex, iconStack);
                            player.sendSystemMessage(Component.literal("\u00a7aSet icon to " + iconId + "."));
                        }
                    }
                }
            }

            player.sendSystemMessage(Component.literal("\u00a7aItem \"" + targetStack.getHoverName().getString() + "\" updated."));
        } catch (Exception e) {
            WorldShop.LOGGER.error("Error handling edit item: {}", e.getMessage(), e);
        }
    }

    private static void handleReorderCategories(ServerPlayer player, String[] categoryNames) {
        try {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            if (categoryNames == null || categoryNames.length == 0) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid reorder data."));
                return;
            }
            List<ShopCategory> categories = WorldShop.getCategories();
            if (categoryNames.length != categories.size()) {
                player.sendSystemMessage(Component.literal("\u00a7cReorder data size mismatch."));
                return;
            }
            List<ShopCategory> reordered = new java.util.ArrayList<>();
            for (String name : categoryNames) {
                boolean found = false;
                for (ShopCategory cat : categories) {
                    if (cat.getName().equals(name) && !reordered.contains(cat)) {
                        reordered.add(cat);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    player.sendSystemMessage(Component.literal("\u00a7cUnknown category: " + name));
                    return;
                }
            }
            for (ShopCategory cat : categories) {
                if (!reordered.contains(cat)) {
                    reordered.add(cat);
                }
            }
            categories.clear();
            categories.addAll(reordered);

            WorldShop.saveCategoryOrder(reordered);
            player.sendSystemMessage(Component.literal("\u00a77Category order saved."));
            player.sendSystemMessage(Component.literal("\u00a7aCategories reordered successfully."));
        } catch (Exception e) {
            WorldShop.LOGGER.error("Error handling reorder categories: {}", e.getMessage(), e);
        }
    }

    private static void handleResetCategoryOrder(ServerPlayer player) {
        try {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            MinecraftServer server = WorldShop.getCurrentServer();
            if (server != null) {
                ShopData shopData = ShopData.forServer(server);
                shopData.setCategoryOrder(new java.util.ArrayList<>());
            }
            player.sendSystemMessage(Component.literal("\u00a7aCategory order has been reset to default."));
        } catch (Exception e) {
            WorldShop.LOGGER.error("Error resetting category order: {}", e.getMessage(), e);
        }
    }

    private static void handleResetAllPrices(ServerPlayer player) {
        try {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            PriceConfig priceConfig = WorldShop.getPriceEngine().getPriceConfig();
            if (priceConfig != null) {
                priceConfig.clearAll();
                player.sendSystemMessage(Component.literal("\u00a7aAll item prices have been reset. Prices will be recalculated on next query."));
            } else {
                player.sendSystemMessage(Component.literal("\u00a7cPrice config not available."));
            }
        } catch (Exception e) {
            WorldShop.LOGGER.error("Error resetting all prices: {}", e.getMessage(), e);
        }
    }

    private static void handleRecalculateCategory(ServerPlayer player, int categoryIndex) {
        try {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            List<ShopCategory> categories = WorldShop.getCategories();
            if (categoryIndex < 0 || categoryIndex >= categories.size()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid category index."));
                return;
            }
            ShopCategory category = categories.get(categoryIndex);
            PriceConfig priceConfig = WorldShop.getPriceEngine().getPriceConfig();
            if (priceConfig != null) {
                int recalculated = 0;
                for (ItemStack item : category.getItems()) {
                    if (item != null && !item.isEmpty()) {
                        priceConfig.removePrice(item);
                        recalculated++;
                    }
                }
                player.sendSystemMessage(Component.literal("\u00a7aCleared cached prices for " + recalculated + " items in category \"" + category.getName() + "\". Prices will be recalculated on next query."));
            } else {
                player.sendSystemMessage(Component.literal("\u00a7cPrice config not available."));
            }
        } catch (Exception e) {
            WorldShop.LOGGER.error("Error recalculating category: {}", e.getMessage(), e);
        }
    }

    private static void handleResetCategory(ServerPlayer player, int categoryIndex) {
        try {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            List<ShopCategory> categories = WorldShop.getCategories();
            if (categoryIndex < 0 || categoryIndex >= categories.size()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid category index."));
                return;
            }
            ShopCategory category = categories.get(categoryIndex);
            PriceConfig priceConfig = WorldShop.getPriceEngine().getPriceConfig();
            if (priceConfig != null) {
                for (ItemStack item : category.getItems()) {
                    if (item != null && !item.isEmpty()) {
                        priceConfig.removePrice(item);
                    }
                }
            }
            player.sendSystemMessage(Component.literal("\u00a7aCategory \"" + category.getName() + "\" has been reset. Prices cleared."));
        } catch (Exception e) {
            WorldShop.LOGGER.error("Error resetting category: {}", e.getMessage(), e);
        }
    }

    private static void handleRecalculateBlock(ServerPlayer player, String itemId) {
        try {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            if (itemId == null || itemId.trim().isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid item ID."));
                return;
            }
            ResourceLocation itemResource = ResourceLocation.tryParse(itemId);
            if (itemResource == null) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid item ID: " + itemId));
                return;
            }
            Item item = BuiltInRegistries.ITEM.get(itemResource);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                player.sendSystemMessage(Component.literal("\u00a7cUnknown item: " + itemId));
                return;
            }
            ItemStack stack = new ItemStack(item);
            PriceConfig priceConfig = WorldShop.getPriceEngine().getPriceConfig();
            if (priceConfig != null) {
                priceConfig.removePrice(stack);
                player.sendSystemMessage(Component.literal("\u00a7aPrice for \"" + stack.getHoverName().getString() + "\" cleared. Will recalculate on next query."));
            } else {
                player.sendSystemMessage(Component.literal("\u00a7cPrice config not available."));
            }
        } catch (Exception e) {
            WorldShop.LOGGER.error("Error recalculating block: {}", e.getMessage(), e);
        }
    }

    private static void handleResetBlock(ServerPlayer player, String itemId) {
        try {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            if (itemId == null || itemId.trim().isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid item ID."));
                return;
            }
            ResourceLocation itemResource = ResourceLocation.tryParse(itemId);
            if (itemResource == null) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid item ID: " + itemId));
                return;
            }
            Item item = BuiltInRegistries.ITEM.get(itemResource);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                player.sendSystemMessage(Component.literal("\u00a7cUnknown item: " + itemId));
                return;
            }
            ItemStack stack = new ItemStack(item);
            PriceConfig priceConfig = WorldShop.getPriceEngine().getPriceConfig();
            if (priceConfig != null) {
                priceConfig.removePrice(stack);
                MinecraftServer server = WorldShop.getCurrentServer();
                if (server != null) {
                    ShopData shopData = ShopData.forServer(server);
                }
                player.sendSystemMessage(Component.literal("\u00a7aBlock \"" + stack.getHoverName().getString() + "\" reset to default price."));
            } else {
                player.sendSystemMessage(Component.literal("\u00a7cPrice config not available."));
            }
        } catch (Exception e) {
            WorldShop.LOGGER.error("Error resetting block: {}", e.getMessage(), e);
        }
    }

    private static void handleSaveConfig(ServerPlayer player, boolean sellhandConfirmation) {
        try {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            ShopConfig config = WorldShop.getShopConfig();
            if (config != null) {
                config.setSellhandConfirmation(sellhandConfirmation);
                config.save();
                String status = sellhandConfirmation ? "enabled" : "disabled";
                player.sendSystemMessage(Component.literal("\u00a7aSellhand confirmation " + status + "."));
            } else {
                player.sendSystemMessage(Component.literal("\u00a7cConfig not available."));
            }
        } catch (Exception e) {
            WorldShop.LOGGER.error("Error saving config: {}", e.getMessage(), e);
        }
    }
}
