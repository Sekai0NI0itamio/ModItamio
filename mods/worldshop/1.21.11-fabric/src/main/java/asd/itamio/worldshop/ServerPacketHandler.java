package asd.itamio.worldshop;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ServerPacketHandler {
    public static void handle(ShopPayload.ShopMessage msg, ServerPlayer player) {
        try {
            switch (msg.getType()) {
                case ShopPayload.ShopMessage.BUY_ITEM:
                    handleBuy(player, msg.getCategoryIndex(), msg.getItemIndex(), msg.getQuantity());
                    break;
                case ShopPayload.ShopMessage.SELL_HAND:
                    handleSellHand(player);
                    break;
                case ShopPayload.ShopMessage.SELL_GUI_ITEMS:
                    handleSellGuiItems(player, msg.getSellSlotEntries());
                    break;
                case ShopPayload.ShopMessage.REMOVE_ITEM:
                    handleRemoveItem(player, msg.getCategoryIndex(), msg.getItemIndex());
                    break;
                case ShopPayload.ShopMessage.REMOVE_CATEGORY:
                    handleRemoveCategory(player, msg.getCategoryIndex());
                    break;
                case ShopPayload.ShopMessage.ADD_CATEGORY:
                    handleAddCategory(player, msg.getStringData1(), msg.getStringData2());
                    break;
                case ShopPayload.ShopMessage.ADD_ITEM:
                    handleAddItem(player, msg.getCategoryIndex(), msg.getStringData1());
                    break;
                case ShopPayload.ShopMessage.EDIT_ITEM:
                    handleEditItem(player, msg.getCategoryIndex(), msg.getStringData1(), msg.getStringData2(), msg.getStringData3(), msg.getDoubleData1(), msg.getDoubleData2());
                    break;
                case ShopPayload.ShopMessage.REORDER_CATEGORIES:
                    handleReorderCategories(player, msg.getStringArrayData());
                    break;
                case ShopPayload.ShopMessage.RESET_CATEGORY_ORDER:
                    handleResetCategoryOrder(player);
                    break;
                case ShopPayload.ShopMessage.RESET_ALL_PRICES:
                    handleResetAllPrices(player);
                    break;
                case ShopPayload.ShopMessage.RECALCULATE_CATEGORY:
                    handleRecalculateCategory(player, msg.getCategoryIndex());
                    break;
                case ShopPayload.ShopMessage.RESET_CATEGORY:
                    handleResetCategory(player, msg.getCategoryIndex());
                    break;
                case ShopPayload.ShopMessage.RECALCULATE_BLOCK:
                    handleRecalculateBlock(player, msg.getStringData1());
                    break;
                case ShopPayload.ShopMessage.RESET_BLOCK:
                    handleResetBlock(player, msg.getStringData1());
                    break;
                case ShopPayload.ShopMessage.SAVE_CONFIG:
                    handleSaveConfig(player, msg.getDoubleData1() > 0.0);
                    break;
                default:
                    player.sendSystemMessage(Component.literal("\u00a7cUnknown packet type."));
            }
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error handling Modern Shop packet type " + msg.getType() + ": " + e.getMessage());
            e.printStackTrace();
        }
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
            RecipeManager recipeManager = player.level().getServer().getRecipeManager();
            RegistryAccess registryAccess = player.level().registryAccess();
            double pricePerItem = WorldShop.getPriceEngine().getBuyPrice(itemStack, recipeManager, registryAccess);
            double totalCost = pricePerItem * (double) quantity;
            EconomyProvider economy = WorldShop.getEconomyProvider(player.level());
            UUID uuid = player.getUUID();

            if (!economy.subtractBalance(player.level(), uuid, totalCost)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need $" + String.format("%.2f", totalCost) + " but have $" + String.format("%.2f", economy.getBalance(player.level(), uuid)) + "."));
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
            player.sendSystemMessage(Component.literal("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(player.level(), uuid))));
            player.containerMenu.broadcastChanges();
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error handling buy: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle the /sellhand command — sell all matching items in the player's inventory.
     * @return 1 on success, 0 on failure
     */
    public static int handleSellHand(ServerPlayer player) {
        try {
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00a7cYou are not holding any item."));
                return 0;
            }

            PriceEngine priceEngine = WorldShop.getPriceEngine();
            RecipeManager recipeManager = player.level().getServer().getRecipeManager();
            RegistryAccess registryAccess = player.level().registryAccess();
            double sellPricePerItem = priceEngine.getSellPrice(held, recipeManager, registryAccess);
            int totalSold = 0;

            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack slot = player.getInventory().getItem(i);
                if (slot.isEmpty() || slot.getItem() != held.getItem()) continue;
                totalSold += slot.getCount();
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }

            if (totalSold == 0) {
                player.sendSystemMessage(Component.literal("\u00a7cNo items found to sell."));
                return 0;
            }

            double totalEarnings = sellPricePerItem * (double) totalSold;
            EconomyProvider economy = WorldShop.getEconomyProvider(player.level());
            economy.addBalance(player.level(), player.getUUID(), totalEarnings);

            player.sendSystemMessage(Component.literal("\u00a7aSold " + totalSold + "x " + held.getHoverName().getString() + " for $" + String.format("%.2f", totalEarnings) + "!"));
            player.sendSystemMessage(Component.literal("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(player.level(), player.getUUID()))));
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            return 1;
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error handling sell hand: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    private static void handleSellGuiItems(ServerPlayer player, List<ShopPayload.ShopMessage.SellSlotEntry> entries) {
        try {
            if (entries == null || entries.isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00a7cNo items to sell."));
                return;
            }

            PriceEngine priceEngine = WorldShop.getPriceEngine();
            RecipeManager recipeManager = player.level().getServer().getRecipeManager();
            RegistryAccess registryAccess = player.level().registryAccess();
            double totalEarnings = 0.0;
            int totalSold = 0;

            // Convert sell slot entries to ItemStacks from the player's inventory
            List<ItemStack> itemsToSell = new ArrayList<>();
            for (ShopPayload.ShopMessage.SellSlotEntry entry : entries) {
                if (entry.slotIndex < 0 || entry.slotIndex >= player.getInventory().getContainerSize()) continue;
                ItemStack invStack = player.getInventory().getItem(entry.slotIndex);
                if (invStack.isEmpty()) continue;
                int qty = Math.min(entry.quantity, invStack.getCount());
                if (qty <= 0) continue;
                ItemStack sellStack = invStack.copy();
                sellStack.setCount(qty);
                itemsToSell.add(sellStack);
                double sellPrice = priceEngine.getSellPrice(sellStack, recipeManager, registryAccess);
                totalEarnings += sellPrice * (double) qty;
                totalSold += qty;
            }

            if (totalSold == 0) {
                player.sendSystemMessage(Component.literal("\u00a7cNo items to sell."));
                return;
            }

            // Remove items from inventory
            for (ShopPayload.ShopMessage.SellSlotEntry entry : entries) {
                if (entry.slotIndex < 0 || entry.slotIndex >= player.getInventory().getContainerSize()) continue;
                ItemStack invStack = player.getInventory().getItem(entry.slotIndex);
                if (invStack.isEmpty()) continue;
                int toRemove = Math.min(entry.quantity, invStack.getCount());
                invStack.shrink(toRemove);
                if (invStack.isEmpty()) {
                    player.getInventory().setItem(entry.slotIndex, ItemStack.EMPTY);
                }
            }

            EconomyProvider economy = WorldShop.getEconomyProvider(player.level());
            economy.addBalance(player.level(), player.getUUID(), totalEarnings);

            player.sendSystemMessage(Component.literal("\u00a7aSold " + totalSold + " items for $" + String.format("%.2f", totalEarnings) + "!"));
            player.sendSystemMessage(Component.literal("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(player.level(), player.getUUID()))));
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error handling sell GUI items: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ========== Admin OP-level handler methods ==========

    private static void handleRemoveItem(ServerPlayer player, int categoryIndex, int itemIndex) {
        try {
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
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
            System.err.println("[MODAPP-ERROR] Error handling remove item: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleRemoveCategory(ServerPlayer player, int categoryIndex) {
        try {
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
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
            System.err.println("[MODAPP-ERROR] Error handling remove category: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleAddCategory(ServerPlayer player, String name, String iconItemId) {
        try {
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
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
            Identifier iconId = Identifier.tryParse(iconItemId);
            if (iconId == null) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid item ID: " + iconItemId));
                return;
            }
            Item iconItem = BuiltInRegistries.ITEM.getValue(iconId);
            if (iconItem == null || iconItem == net.minecraft.world.item.Items.AIR) {
                player.sendSystemMessage(Component.literal("\u00a7cUnknown item: " + iconItemId));
                return;
            }
            ItemStack iconStack = new ItemStack(iconItem);
            ShopCategory newCategory = new ShopCategory(name.trim(), iconStack, null);
            WorldShop.getCategories().add(newCategory);
            player.sendSystemMessage(Component.literal("\u00a7aAdded category \"" + name + "\" with icon " + iconItemId + "."));
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error handling add category: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleAddItem(ServerPlayer player, int categoryIndex, String itemId) {
        try {
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
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
            Identifier itemResource = Identifier.tryParse(itemId);
            if (itemResource == null) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid item ID: " + itemId));
                return;
            }
            Item item = BuiltInRegistries.ITEM.getValue(itemResource);
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
            System.err.println("[MODAPP-ERROR] Error handling add item: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleEditItem(ServerPlayer player, int categoryIndex, String itemId, String displayName, String iconId, double buyPrice, double sellPrice) {
        try {
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
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

            Identifier targetId = Identifier.tryParse(itemId);
            if (targetId == null) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid item ID: " + itemId));
                return;
            }

            ItemStack targetStack = null;
            int targetIndex = -1;
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack != null && !stack.isEmpty()) {
                    Identifier stackId = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
                Identifier iconResource = Identifier.tryParse(iconId);
                if (iconResource != null) {
                    Item iconItem = BuiltInRegistries.ITEM.getValue(iconResource);
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
            System.err.println("[MODAPP-ERROR] Error handling edit item: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleReorderCategories(ServerPlayer player, String[] categoryNames) {
        try {
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
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
            System.err.println("[MODAPP-ERROR] Error handling reorder categories: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ========== Settings operation handlers ==========

    private static void handleResetCategoryOrder(ServerPlayer player) {
        try {
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
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
            System.err.println("[MODAPP-ERROR] Error resetting category order: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleResetAllPrices(ServerPlayer player) {
        try {
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            PriceConfig priceConfig = WorldShop.getPriceEngine().getPriceConfig();
            if (priceConfig != null) {
                priceConfig.clearAllPrices();
                player.sendSystemMessage(Component.literal("\u00a7aAll item prices have been reset. Prices will be recalculated on next query."));
            } else {
                player.sendSystemMessage(Component.literal("\u00a7cPrice config not available."));
            }
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error resetting all prices: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleRecalculateCategory(ServerPlayer player, int categoryIndex) {
        try {
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
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
            System.err.println("[MODAPP-ERROR] Error recalculating category: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleResetCategory(ServerPlayer player, int categoryIndex) {
        try {
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
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
            System.err.println("[MODAPP-ERROR] Error resetting category: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleRecalculateBlock(ServerPlayer player, String itemId) {
        try {
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            if (itemId == null || itemId.trim().isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid item ID."));
                return;
            }
            Identifier itemResource = Identifier.tryParse(itemId);
            if (itemResource == null) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid item ID: " + itemId));
                return;
            }
            Item item = BuiltInRegistries.ITEM.getValue(itemResource);
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
            System.err.println("[MODAPP-ERROR] Error recalculating block: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleResetBlock(ServerPlayer player, String itemId) {
        try {
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            if (itemId == null || itemId.trim().isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid item ID."));
                return;
            }
            Identifier itemResource = Identifier.tryParse(itemId);
            if (itemResource == null) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid item ID: " + itemId));
                return;
            }
            Item item = BuiltInRegistries.ITEM.getValue(itemResource);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                player.sendSystemMessage(Component.literal("\u00a7cUnknown item: " + itemId));
                return;
            }
            ItemStack stack = new ItemStack(item);
            PriceConfig priceConfig = WorldShop.getPriceEngine().getPriceConfig();
            if (priceConfig != null) {
                priceConfig.removePrice(stack);
                player.sendSystemMessage(Component.literal("\u00a7aBlock \"" + stack.getHoverName().getString() + "\" reset to default price."));
            } else {
                player.sendSystemMessage(Component.literal("\u00a7cPrice config not available."));
            }
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error resetting block: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleSaveConfig(ServerPlayer player, boolean sellhandConfirmation) {
        try {
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
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
            System.err.println("[MODAPP-ERROR] Error saving config: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
