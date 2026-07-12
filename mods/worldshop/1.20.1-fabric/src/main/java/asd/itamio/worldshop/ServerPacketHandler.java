package asd.itamio.worldshop;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.core.RegistryAccess;

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
}
