package asd.itamio.modernshop;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public class ShopPacketHandler {
    public static void handle(ServerPlayer player, ShopPayload.ShopMessage message) {
        switch (message.getType()) {
            case ShopPayload.ShopMessage.BUY_ITEM:
                handleBuy(player, message.getCategoryIndex(), message.getItemIndex(), message.getQuantity());
                break;
            case ShopPayload.ShopMessage.SELL_HAND:
                handleSellHand(player);
                break;
            case ShopPayload.ShopMessage.SELL_GUI_ITEMS:
                handleSellGuiItems(player, message.getSellSlotEntries());
                break;
        }
    }

    private static void handleBuy(ServerPlayer player, int categoryIndex, int itemIndex, int quantity) {
        List<ShopCategory> categories = ModernShop.getCategories();
        if (categoryIndex < 0 || categoryIndex >= categories.size()) {
            player.sendSystemMessage(Component.literal("§cInvalid category."));
            return;
        }
        ShopCategory category = categories.get(categoryIndex);
        List<ItemStack> items = category.getItems();
        if (itemIndex < 0 || itemIndex >= items.size()) {
            player.sendSystemMessage(Component.literal("§cInvalid item."));
            return;
        }
        ItemStack itemStack = items.get(itemIndex);
        PriceEngine priceEngine = ModernShop.getPriceEngine();
        double pricePerItem = priceEngine.getBuyPrice(itemStack);
        double totalCost = pricePerItem * (double) quantity;
        EconomyData economy = EconomyData.get(player.level());
        UUID uuid = player.getUUID();

        if (!economy.subtractBalance(uuid, totalCost)) {
            player.sendSystemMessage(Component.literal("§cYou need $" + String.format("%.2f", totalCost) + " but have $" + String.format("%.2f", economy.getBalance(uuid)) + "."));
            return;
        }

        int maxStackSize = itemStack.getMaxStackSize();
        for (int remaining = quantity; remaining > 0; remaining -= maxStackSize) {
            int stackSize = Math.min(remaining, maxStackSize);
            ItemStack toGive = itemStack.copy();
            toGive.setCount(stackSize);
            player.getInventory().add(toGive);
        }

        player.sendSystemMessage(Component.literal("§aBought " + quantity + "x " + itemStack.getDisplayName().getString() + " for $" + String.format("%.2f", totalCost) + "!"));
        player.sendSystemMessage(Component.literal("§7Balance: $" + String.format("%.2f", economy.getBalance(uuid))));
    }

    private static void handleSellHand(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cYou are not holding any item."));
            return;
        }

        PriceEngine priceEngine = ModernShop.getPriceEngine();
        double sellPricePerItem = priceEngine.getSellPrice(held);
        int totalSold = 0;

        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot.isEmpty() || !ItemStack.isSameItem(slot, held)) continue;
            totalSold += slot.getCount();
            inventory.setItem(i, ItemStack.EMPTY);
        }

        if (totalSold == 0) {
            player.sendSystemMessage(Component.literal("§cNo items found to sell."));
            return;
        }

        double totalEarnings = sellPricePerItem * (double) totalSold;
        EconomyData economy = EconomyData.get(player.level());
        economy.addBalance(player.getUUID(), totalEarnings);

        player.sendSystemMessage(Component.literal("§aSold " + totalSold + "x " + held.getDisplayName().getString() + " for $" + String.format("%.2f", totalEarnings) + "!"));
        player.sendSystemMessage(Component.literal("§7Balance: $" + String.format("%.2f", economy.getBalance(player.getUUID()))));
        player.containerMenu.broadcastChanges();
    }

    private static void handleSellGuiItems(ServerPlayer player, List<ShopPayload.ShopMessage.SellSlotEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cNo items to sell."));
            return;
        }

        PriceEngine priceEngine = ModernShop.getPriceEngine();
        double totalEarnings = 0.0;
        int totalSold = 0;

        // Collect items from inventory slots
        var inventory = player.getInventory();
        for (ShopPayload.ShopMessage.SellSlotEntry entry : entries) {
            if (entry.slotIndex < 0 || entry.slotIndex >= inventory.getContainerSize()) continue;
            if (entry.quantity <= 0) continue;
            ItemStack slotStack = inventory.getItem(entry.slotIndex);
            if (slotStack.isEmpty()) continue;

            int toSell = Math.min(entry.quantity, slotStack.getCount());
            ItemStack sellStack = slotStack.copy();
            sellStack.setCount(toSell);

            double sellPrice = priceEngine.getSellPrice(sellStack);
            totalEarnings += sellPrice * (double) toSell;
            totalSold += toSell;
        }

        if (totalSold == 0) {
            player.sendSystemMessage(Component.literal("§cNo items to sell."));
            return;
        }

        // Remove items from inventory
        for (ShopPayload.ShopMessage.SellSlotEntry entry : entries) {
            if (entry.slotIndex < 0 || entry.slotIndex >= inventory.getContainerSize()) continue;
            if (entry.quantity <= 0) continue;
            ItemStack slotStack = inventory.getItem(entry.slotIndex);
            if (slotStack.isEmpty()) continue;
            int toRemove = Math.min(entry.quantity, slotStack.getCount());
            slotStack.shrink(toRemove);
            if (slotStack.isEmpty()) {
                inventory.setItem(entry.slotIndex, ItemStack.EMPTY);
            }
        }

        EconomyData economy = EconomyData.get(player.level());
        economy.addBalance(player.getUUID(), totalEarnings);

        player.sendSystemMessage(Component.literal("§aSold " + totalSold + " items for $" + String.format("%.2f", totalEarnings) + "!"));
        player.sendSystemMessage(Component.literal("§7Balance: $" + String.format("%.2f", economy.getBalance(player.getUUID()))));
        player.containerMenu.broadcastChanges();
    }
}
