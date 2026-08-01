package asd.itamio.modernshop;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.List;
import java.util.UUID;

public class ServerPacketHandler {
    public static void handle(ShopPacket packet, ServerPlayer player) {
        try {
            switch (packet.getType()) {
                case ShopPacket.BUY_ITEM:
                    handleBuy(player, packet.getCategoryIndex(), packet.getItemIndex(), packet.getQuantity());
                    break;
                case ShopPacket.SELL_HAND:
                    handleSellHand(player);
                    break;
                case ShopPacket.SELL_GUI_ITEMS:
                    handleSellGuiItems(player, packet.getItems());
                    break;
                case ShopPacket.REMOVE_ITEM:
                    handleRemoveItem(player, packet.getCategoryIndex(), packet.getItemIndex());
                    break;
                case ShopPacket.REMOVE_CATEGORY:
                    handleRemoveCategory(player, packet.getCategoryIndex());
                    break;
                case ShopPacket.ADD_CATEGORY:
                    handleAddCategory(player, packet.getStringData1(), packet.getStringData2());
                    break;
                case ShopPacket.ADD_ITEM:
                    handleAddItem(player, packet.getCategoryIndex(), packet.getStringData1());
                    break;
                case ShopPacket.EDIT_ITEM:
                    handleEditItem(player, packet.getCategoryIndex(), packet.getStringData1(), packet.getStringData2(), packet.getStringData3(), packet.getDoubleData1(), packet.getDoubleData2());
                    break;
                case ShopPacket.REORDER_CATEGORIES:
                    handleReorderCategories(player, packet.getStringArrayData());
                    break;
                case ShopPacket.RESET_CATEGORY_ORDER:
                    handleResetCategoryOrder(player);
                    break;
                case ShopPacket.RESET_ALL_PRICES:
                    handleResetAllPrices(player);
                    break;
                case ShopPacket.RECALCULATE_CATEGORY:
                    handleRecalculateCategory(player, packet.getCategoryIndex());
                    break;
                case ShopPacket.RESET_CATEGORY:
                    handleResetCategory(player, packet.getCategoryIndex());
                    break;
                case ShopPacket.RECALCULATE_BLOCK:
                    handleRecalculateBlock(player, packet.getStringData1());
                    break;
                case ShopPacket.RESET_BLOCK:
                    handleResetBlock(player, packet.getStringData1());
                    break;
                case ShopPacket.SAVE_CONFIG:
                    handleSaveConfig(player, packet.getDoubleData1() > 0.0);
                    break;
                default:
                    player.sendSystemMessage(Component.literal("\u00a7cUnknown packet type."));
            }
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error handling Modern Shop packet type " + packet.getType() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleBuy(ServerPlayer player, int categoryIndex, int itemIndex, int quantity) {
        try {
            List<ShopCategory> categories = ModernShop.getCategories();
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
            double pricePerItem = ModernShop.getPriceEngine().getBuyPrice(itemStack, recipeManager, registryAccess);
            double totalCost = pricePerItem * (double) quantity;
            EconomyProvider economy = ModernShop.getEconomyProvider(player.level());
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
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error handling buy: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static int handleSellHand(ServerPlayer player) {
        try {
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00a7cYou are not holding any item."));
                return 0;
            }

            PriceEngine priceEngine = ModernShop.getPriceEngine();
            RecipeManager recipeManager = player.level().getServer().getRecipeManager();
            RegistryAccess registryAccess = player.level().registryAccess();
            double sellPricePerItem = priceEngine.getSellPrice(held, recipeManager, registryAccess);
            int totalSold = 0;

            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack slot = inventory.getItem(i);
                if (slot.isEmpty() || !ItemStack.isSameItem(slot, held)) continue;
                totalSold += slot.getCount();
                inventory.setItem(i, ItemStack.EMPTY);
            }

            if (totalSold == 0) {
                player.sendSystemMessage(Component.literal("\u00a7cNo items found to sell."));
                return 0;
            }

            double totalEarnings = sellPricePerItem * (double) totalSold;
            EconomyProvider economy = ModernShop.getEconomyProvider(player.level());
            economy.addBalance(player.level(), player.getUUID(), totalEarnings);

            player.sendSystemMessage(Component.literal("\u00a7aSold " + totalSold + "x " + held.getHoverName().getString() + " for $" + String.format("%.2f", totalEarnings) + "!"));
            player.sendSystemMessage(Component.literal("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(player.level(), player.getUUID()))));
            inventory.setChanged();
            return 1;
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error handling sell hand: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    private static void handleSellGuiItems(ServerPlayer player, List<ItemStack> items) {
        try {
            if (items.isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00a7cNo items to sell."));
                return;
            }

            PriceEngine priceEngine = ModernShop.getPriceEngine();
            RecipeManager recipeManager = player.level().getServer().getRecipeManager();
            RegistryAccess registryAccess = player.level().registryAccess();
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

            var inventory = player.getInventory();
            for (ItemStack sellStack : items) {
                if (sellStack == null || sellStack.isEmpty()) continue;
                int remaining = sellStack.getCount();
                for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
                    ItemStack invStack = inventory.getItem(i);
                    if (invStack.isEmpty() || !ItemStack.isSameItem(invStack, sellStack)) continue;
                    int toRemove = Math.min(remaining, invStack.getCount());
                    invStack.shrink(toRemove);
                    remaining -= toRemove;
                    if (invStack.isEmpty()) {
                        inventory.setItem(i, ItemStack.EMPTY);
                    }
                }
            }

            EconomyProvider economy = ModernShop.getEconomyProvider(player.level());
            economy.addBalance(player.level(), player.getUUID(), totalEarnings);

            player.sendSystemMessage(Component.literal("\u00a7aSold " + totalSold + " items for $" + String.format("%.2f", totalEarnings) + "!"));
            player.sendSystemMessage(Component.literal("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(player.level(), player.getUUID()))));
            inventory.setChanged();
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error handling sell GUI items: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ========== Admin handlers ==========

    private static void handleRemoveItem(ServerPlayer player, int categoryIndex, int itemIndex) {
        try {
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            List<ShopCategory> categories = ModernShop.getCategories();
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
            List<ShopCategory> categories = ModernShop.getCategories();
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
            Item iconItem = ShopData.getItemById(iconItemId);
            if (iconItem == null || iconItem == Items.AIR) {
                player.sendSystemMessage(Component.literal("\u00a7cUnknown item: " + iconItemId));
                return;
            }
            ItemStack iconStack = new ItemStack(iconItem);
            ShopCategory newCategory = new ShopCategory(name.trim(), iconStack);
            ModernShop.getCategories().add(newCategory);
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
            List<ShopCategory> categories = ModernShop.getCategories();
            if (categoryIndex < 0 || categoryIndex >= categories.size()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid category."));
                return;
            }
            Item item = ShopData.getItemById(itemId);
            if (item == null || item == Items.AIR) {
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
            List<ShopCategory> categories = ModernShop.getCategories();
            if (categoryIndex < 0 || categoryIndex >= categories.size()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid category."));
                return;
            }
            ShopCategory category = categories.get(categoryIndex);
            List<ItemStack> items = category.getItems();

            ItemStack targetStack = null;
            int targetIndex = -1;
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack != null && !stack.isEmpty()) {
                    String stackId = ShopData.getItemId(stack);
                    if (stackId.equals(itemId)) {
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

            PriceConfig priceConfig = ModernShop.getPriceEngine().getPriceConfig();
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

            if (displayName != null && !displayName.trim().isEmpty()) {
                ItemStack renamedStack = targetStack.copy();
                renamedStack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName.trim()));
                items.set(targetIndex, renamedStack);
                player.sendSystemMessage(Component.literal("\u00a7aSet display name to \"" + displayName.trim() + "\"."));
            }

            if (iconId != null && !iconId.trim().isEmpty()) {
                Item iconItem = ShopData.getItemById(iconId);
                if (iconItem != null && iconItem != Items.AIR) {
                    String currentIconId = ShopData.getItemId(targetStack);
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

            player.sendSystemMessage(Component.literal("\u00a7aItem updated."));
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
            List<ShopCategory> categories = ModernShop.getCategories();
            List<ShopCategory> reordered = new java.util.ArrayList<>();
            for (String name : categoryNames) {
                for (ShopCategory cat : categories) {
                    if (cat.getName().equals(name) && !reordered.contains(cat)) {
                        reordered.add(cat);
                        break;
                    }
                }
            }
            for (ShopCategory cat : categories) {
                if (!reordered.contains(cat)) {
                    reordered.add(cat);
                }
            }
            categories.clear();
            categories.addAll(reordered);
            ModernShop.saveCategoryOrder(reordered);
            player.sendSystemMessage(Component.literal("\u00a7aCategories reordered successfully."));
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error handling reorder categories: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ========== Settings handlers ==========

    private static void handleResetCategoryOrder(ServerPlayer player) {
        try {
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need OP level 2 to manage the shop."));
                return;
            }
            MinecraftServer server = ModernShop.getCurrentServer();
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
            PriceConfig priceConfig = ModernShop.getPriceEngine().getPriceConfig();
            if (priceConfig != null) {
                priceConfig.clearAll();
                player.sendSystemMessage(Component.literal("\u00a7aAll item prices have been reset."));
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
            List<ShopCategory> categories = ModernShop.getCategories();
            if (categoryIndex < 0 || categoryIndex >= categories.size()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid category index."));
                return;
            }
            ShopCategory category = categories.get(categoryIndex);
            PriceConfig priceConfig = ModernShop.getPriceEngine().getPriceConfig();
            if (priceConfig != null) {
                int recalculated = 0;
                for (ItemStack item : category.getItems()) {
                    if (item != null && !item.isEmpty()) {
                        priceConfig.removePrice(item);
                        recalculated++;
                    }
                }
                player.sendSystemMessage(Component.literal("\u00a7aCleared cached prices for " + recalculated + " items in category \"" + category.getName() + "\"."));
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
            List<ShopCategory> categories = ModernShop.getCategories();
            if (categoryIndex < 0 || categoryIndex >= categories.size()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid category index."));
                return;
            }
            ShopCategory category = categories.get(categoryIndex);
            PriceConfig priceConfig = ModernShop.getPriceEngine().getPriceConfig();
            if (priceConfig != null) {
                for (ItemStack item : category.getItems()) {
                    if (item != null && !item.isEmpty()) {
                        priceConfig.removePrice(item);
                    }
                }
            }
            player.sendSystemMessage(Component.literal("\u00a7aCategory \"" + category.getName() + "\" has been reset."));
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
            Item item = ShopData.getItemById(itemId);
            if (item == null || item == Items.AIR) {
                player.sendSystemMessage(Component.literal("\u00a7cUnknown item: " + itemId));
                return;
            }
            ItemStack stack = new ItemStack(item);
            PriceConfig priceConfig = ModernShop.getPriceEngine().getPriceConfig();
            if (priceConfig != null) {
                priceConfig.removePrice(stack);
                player.sendSystemMessage(Component.literal("\u00a7aPrice for \"" + stack.getHoverName().getString() + "\" cleared."));
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
            Item item = ShopData.getItemById(itemId);
            if (item == null || item == Items.AIR) {
                player.sendSystemMessage(Component.literal("\u00a7cUnknown item: " + itemId));
                return;
            }
            ItemStack stack = new ItemStack(item);
            PriceConfig priceConfig = ModernShop.getPriceEngine().getPriceConfig();
            if (priceConfig != null) {
                priceConfig.removePrice(stack);
                player.sendSystemMessage(Component.literal("\u00a7aBlock \"" + stack.getHoverName().getString() + "\" reset to default price."));
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
            ShopConfig config = ModernShop.getShopConfig();
            if (config != null) {
                config.setSellhandConfirmation(sellhandConfirmation);
                config.save();
                String status = sellhandConfirmation ? "enabled" : "disabled";
                player.sendSystemMessage(Component.literal("\u00a7aSellhand confirmation " + status + "."));
            }
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error saving config: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
