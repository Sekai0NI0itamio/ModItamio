package asd.itamio.worldshop;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.List;
import java.util.UUID;

public class ShopPacketHandler implements IMessageHandler<ShopPacket, IMessage> {
    @Override
    public IMessage onMessage(ShopPacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
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
            }
        });
        return null;
    }

    private void handleBuy(EntityPlayerMP player, int categoryIndex, int itemIndex, int quantity) {
        List<ShopCategory> categories = WorldShop.getCategories();
        if (categoryIndex < 0 || categoryIndex >= categories.size()) {
            player.sendMessage(new TextComponentString("\u00a7cInvalid category."));
            return;
        }
        ShopCategory category = categories.get(categoryIndex);
        List<ItemStack> items = category.getItems();
        if (itemIndex < 0 || itemIndex >= items.size()) {
            player.sendMessage(new TextComponentString("\u00a7cInvalid item."));
            return;
        }
        ItemStack itemStack = items.get(itemIndex);
        PriceEngine priceEngine = WorldShop.getPriceEngine();
        double pricePerItem = priceEngine.getBuyPrice(itemStack);
        double totalCost = pricePerItem * (double) quantity;
        EconomyData economy = EconomyData.get(player.getEntityWorld());
        UUID uuid = player.getUniqueID();

        if (!economy.subtractBalance(uuid, totalCost)) {
            player.sendMessage(new TextComponentString("\u00a7cYou need $" + String.format("%.2f", totalCost) + " but have $" + String.format("%.2f", economy.getBalance(uuid)) + "."));
            return;
        }

        int maxStackSize = itemStack.getMaxStackSize();
        for (int remaining = quantity; remaining > 0; remaining -= maxStackSize) {
            int stackSize = Math.min(remaining, maxStackSize);
            ItemStack toGive = itemStack.copy();
            toGive.setCount(stackSize);
            player.inventory.addItemStackToInventory(toGive);
        }

        player.sendMessage(new TextComponentString("\u00a7aBought " + quantity + "x " + itemStack.getDisplayName() + " for $" + String.format("%.2f", totalCost) + "!"));
        player.sendMessage(new TextComponentString("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(uuid))));
    }

    private void handleSellHand(EntityPlayerMP player) {
        ItemStack held = player.getHeldItemMainhand();
        if (held.isEmpty()) {
            player.sendMessage(new TextComponentString("\u00a7cYou are not holding any item."));
            return;
        }

        PriceEngine priceEngine = WorldShop.getPriceEngine();
        double sellPricePerItem = priceEngine.getSellPrice(held);
        int totalSold = 0;

        for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
            ItemStack slot = player.inventory.mainInventory.get(i);
            if (slot.isEmpty() || !isSameItem(slot, held)) continue;
            totalSold += slot.getCount();
            player.inventory.mainInventory.set(i, ItemStack.EMPTY);
        }

        if (totalSold == 0) {
            player.sendMessage(new TextComponentString("\u00a7cNo items found to sell."));
            return;
        }

        double totalEarnings = sellPricePerItem * (double) totalSold;
        EconomyData economy = EconomyData.get(player.getEntityWorld());
        economy.addBalance(player.getUniqueID(), totalEarnings);

        player.sendMessage(new TextComponentString("\u00a7aSold " + totalSold + "x " + held.getDisplayName() + " for $" + String.format("%.2f", totalEarnings) + "!"));
        player.sendMessage(new TextComponentString("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(player.getUniqueID()))));
        player.openContainer.detectAndSendChanges();
    }

    private void handleSellGuiItems(EntityPlayerMP player, List<ItemStack> items) {
        if (items.isEmpty()) {
            player.sendMessage(new TextComponentString("\u00a7cNo items to sell."));
            return;
        }

        PriceEngine priceEngine = WorldShop.getPriceEngine();
        double totalEarnings = 0.0;
        int totalSold = 0;

        for (ItemStack sellStack : items) {
            if (sellStack == null || sellStack.isEmpty()) continue;
            double sellPrice = priceEngine.getSellPrice(sellStack);
            totalEarnings += sellPrice * (double) sellStack.getCount();
            totalSold += sellStack.getCount();
        }

        if (totalSold == 0) {
            player.sendMessage(new TextComponentString("\u00a7cNo items to sell."));
            return;
        }

        // Remove items from inventory
        for (ItemStack sellStack : items) {
            if (sellStack == null || sellStack.isEmpty()) continue;
            int remaining = sellStack.getCount();
            for (int i = 0; i < player.inventory.mainInventory.size() && remaining > 0; i++) {
                ItemStack invStack = player.inventory.mainInventory.get(i);
                if (invStack.isEmpty() || !isSameItem(invStack, sellStack)) continue;
                int toRemove = Math.min(remaining, invStack.getCount());
                invStack.shrink(toRemove);
                remaining -= toRemove;
                if (invStack.isEmpty()) {
                    player.inventory.mainInventory.set(i, ItemStack.EMPTY);
                }
            }
        }

        EconomyData economy = EconomyData.get(player.getEntityWorld());
        economy.addBalance(player.getUniqueID(), totalEarnings);

        player.sendMessage(new TextComponentString("\u00a7aSold " + totalSold + " items for $" + String.format("%.2f", totalEarnings) + "!"));
        player.sendMessage(new TextComponentString("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(player.getUniqueID()))));
        player.openContainer.detectAndSendChanges();
    }

    private boolean isSameItem(ItemStack a, ItemStack b) {
        if (a.getItem() != b.getItem()) {
            return false;
        }
        return a.getItemDamage() == b.getItemDamage();
    }
}
