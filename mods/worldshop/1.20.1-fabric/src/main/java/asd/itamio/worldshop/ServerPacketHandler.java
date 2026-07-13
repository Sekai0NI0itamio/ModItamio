package asd.itamio.worldshop;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

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
                default:
                    player.sendSystemMessage(Component.literal("\u00a7cUnknown packet type."));
            }
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error handling World Shop packet type " + packet.getType() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleBuy(ServerPlayer player, int categoryIndex, int itemIndex, int quantity) {
        try {
            List<ShopCategory> categories = WorldShop.getCategories();
            if (categoryIndex < 0 || categoryIndex >= categories.size()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid category."));
                System.err.println("[MODAPP-ERROR] Buy: invalid category index " + categoryIndex);
                return;
            }
            ShopCategory category = categories.get(categoryIndex);
            List<ItemStack> items = category.getItems();
            if (itemIndex < 0 || itemIndex >= items.size()) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid item."));
                System.err.println("[MODAPP-ERROR] Buy: invalid item index " + itemIndex + " in category " + categoryIndex);
                return;
            }
            ItemStack itemStack = items.get(itemIndex);
            RecipeManager recipeManager = player.serverLevel().getRecipeManager();
            RegistryAccess registryAccess = player.serverLevel().registryAccess();
            double pricePerItem = WorldShop.getPriceEngine().getBuyPrice(itemStack, recipeManager, registryAccess);
            double totalCost = pricePerItem * (double) quantity;
            EconomyData economy = EconomyData.get(player.serverLevel());
            UUID uuid = player.getUUID();

            if (!economy.subtractBalance(uuid, totalCost)) {
                player.sendSystemMessage(Component.literal("\u00a7cYou need $" + String.format("%.2f", totalCost) + " but have $" + String.format("%.2f", economy.getBalance(uuid)) + "."));
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
            player.sendSystemMessage(Component.literal("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(uuid))));
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error handling buy: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleSellHand(ServerPlayer player) {
        try {
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00a7cYou are not holding any item."));
                return;
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
                return;
            }

            double totalEarnings = sellPricePerItem * (double) totalSold;
            EconomyData economy = EconomyData.get(player.serverLevel());
            economy.addBalance(player.getUUID(), totalEarnings);

            player.sendSystemMessage(Component.literal("\u00a7aSold " + totalSold + "x " + held.getHoverName().getString() + " for $" + String.format("%.2f", totalEarnings) + "!"));
            player.sendSystemMessage(Component.literal("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(player.getUUID()))));
            player.getInventory().setChanged();
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error handling sell hand: " + e.getMessage());
            e.printStackTrace();
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

            // Remove items from inventory
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

            EconomyData economy = EconomyData.get(player.serverLevel());
            economy.addBalance(player.getUUID(), totalEarnings);

            player.sendSystemMessage(Component.literal("\u00a7aSold " + totalSold + " items for $" + String.format("%.2f", totalEarnings) + "!"));
            player.sendSystemMessage(Component.literal("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(player.getUUID()))));
            player.getInventory().setChanged();
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error handling sell GUI items: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ========== Admin OP-level handler methods ==========

    /**
     * Remove an item from a category by index.
     * This is an OP-only operation for shop management.
     */
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
            System.err.println("[MODAPP-ERROR] Error handling remove item: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Remove an entire category by index.
     * This is an OP-only operation for shop management.
     */
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
            System.err.println("[MODAPP-ERROR] Error handling remove category: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Add a new category with the given display name and icon item ID.
     * This is an OP-only operation for shop management.
     */
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
            System.err.println("[MODAPP-ERROR] Error handling add category: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Add an item to a category by its registry ID.
     * This is an OP-only operation for shop management.
     */
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
            System.err.println("[MODAPP-ERROR] Error handling add item: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Edit an item's display name, icon, buy price, and/or sell price.
     * This is an OP-only operation for shop management.
     * If buyPrice or sellPrice is > 0, a custom price override is saved to PriceConfig.
     */
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

            // Find the item in the category by ID
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

            // Apply custom buy/sell price overrides via PriceConfig
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

            // Apply display name change
            if (displayName != null && !displayName.trim().isEmpty() && !displayName.equals(targetStack.getHoverName().getString())) {
                ItemStack renamedStack = targetStack.copy();
                renamedStack.setHoverName(Component.literal(displayName.trim()));
                items.set(targetIndex, renamedStack);
                player.sendSystemMessage(Component.literal("\u00a7aSet display name to \"" + displayName.trim() + "\"."));
            }

            // Apply icon change
            if (iconId != null && !iconId.trim().isEmpty()) {
                ResourceLocation iconResource = ResourceLocation.tryParse(iconId);
                if (iconResource != null) {
                    Item iconItem = BuiltInRegistries.ITEM.get(iconResource);
                    if (iconItem != null && iconItem != net.minecraft.world.item.Items.AIR) {
                        String currentIconId = BuiltInRegistries.ITEM.getKey(targetStack.getItem()).toString();
                        if (!iconId.equals(currentIconId)) {
                            ItemStack iconStack = new ItemStack(iconItem);
                            if (displayName != null && !displayName.trim().isEmpty()) {
                                iconStack.setHoverName(Component.literal(displayName.trim()));
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
}
