package asd.itamio.worldshop;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.List;
import java.util.UUID;

/**
 * Server-side handler for all shop packets including admin operations.
 */
public class ServerPacketHandler implements IMessageHandler<ShopPacket, IMessage> {
    @Override
    public IMessage onMessage(ShopPacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        MinecraftServer server = player.getServer();
        World world = player.getEntityWorld();
        server.addScheduledTask(() -> handle(message, player, world));
        return null;
    }

    public static void handle(ShopPacket packet, EntityPlayerMP player, World world) {
        try {
            switch (packet.getType()) {
                case ShopPacket.BUY_ITEM:
                    handleBuy(player, world, packet.getCategoryIndex(), packet.getItemIndex(), packet.getQuantity());
                    break;
                case ShopPacket.SELL_HAND:
                    handleSellHand(player, world);
                    break;
                case ShopPacket.SELL_GUI_ITEMS:
                    handleSellGuiItems(player, world, packet.getItems());
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
                    player.sendMessage(new TextComponentString("\u00a7cUnknown packet type."));
            }
        } catch (Exception e) {
            WorldShop.LOGGER.error("Error handling World Shop packet type " + packet.getType() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleBuy(EntityPlayerMP player, World world, int categoryIndex, int itemIndex, int quantity) {
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
        double pricePerItem = WorldShop.getPriceEngine().getBuyPrice(itemStack);
        double totalCost = pricePerItem * (double) quantity;
        EconomyProvider economy = WorldShop.getEconomyProvider(world);
        UUID uuid = player.getUniqueID();

        if (!economy.subtractBalance(world, uuid, totalCost)) {
            player.sendMessage(new TextComponentString("\u00a7cYou need $" + String.format("%.2f", totalCost) + " but have $" + String.format("%.2f", economy.getBalance(world, uuid)) + "."));
            return;
        }

        int maxStackSize = itemStack.getMaxStackSize();
        for (int remaining = quantity; remaining > 0; remaining -= maxStackSize) {
            int stackSize = Math.min(remaining, maxStackSize);
            ItemStack toGive = itemStack.copy();
            toGive.setCount(stackSize);
            if (!player.inventory.addItemStackToInventory(toGive)) {
                player.dropItem(toGive, false);
            }
        }

        player.sendMessage(new TextComponentString("\u00a7aBought " + quantity + "x " + itemStack.getDisplayName() + " for $" + String.format("%.2f", totalCost) + "!"));
        player.sendMessage(new TextComponentString("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(world, uuid))));
        player.openContainer.detectAndSendChanges();
    }

    public static int handleSellHand(EntityPlayerMP player, World world) {
        ItemStack held = player.getHeldItemMainhand();
        if (held.isEmpty()) {
            player.sendMessage(new TextComponentString("\u00a7cYou are not holding any item."));
            return 0;
        }

        PriceEngine priceEngine = WorldShop.getPriceEngine();
        double sellPricePerItem = priceEngine.getSellPrice(held);
        int totalSold = 0;

        for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
            ItemStack slot = player.inventory.mainInventory.get(i);
            if (slot.isEmpty() || slot.getItem() != held.getItem()) continue;
            totalSold += slot.getCount();
            player.inventory.mainInventory.set(i, ItemStack.EMPTY);
        }

        if (totalSold == 0) {
            player.sendMessage(new TextComponentString("\u00a7cNo items found to sell."));
            return 0;
        }

        double totalEarnings = sellPricePerItem * (double) totalSold;
        EconomyProvider economy = WorldShop.getEconomyProvider(world);
        economy.addBalance(world, player.getUniqueID(), totalEarnings);

        player.sendMessage(new TextComponentString("\u00a7aSold " + totalSold + "x " + held.getDisplayName() + " for $" + String.format("%.2f", totalEarnings) + "!"));
        player.sendMessage(new TextComponentString("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(world, player.getUniqueID()))));
        player.openContainer.detectAndSendChanges();
        return 1;
    }

    private static void handleSellGuiItems(EntityPlayerMP player, World world, List<ItemStack> items) {
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

        for (ItemStack sellStack : items) {
            if (sellStack == null || sellStack.isEmpty()) continue;
            int remaining = sellStack.getCount();
            for (int i = 0; i < player.inventory.mainInventory.size() && remaining > 0; i++) {
                ItemStack invStack = player.inventory.mainInventory.get(i);
                if (invStack.isEmpty() || invStack.getItem() != sellStack.getItem()) continue;
                int toRemove = Math.min(remaining, invStack.getCount());
                invStack.shrink(toRemove);
                remaining -= toRemove;
                if (invStack.isEmpty()) {
                    player.inventory.mainInventory.set(i, ItemStack.EMPTY);
                }
            }
        }

        EconomyProvider economy = WorldShop.getEconomyProvider(world);
        economy.addBalance(world, player.getUniqueID(), totalEarnings);

        player.sendMessage(new TextComponentString("\u00a7aSold " + totalSold + " items for $" + String.format("%.2f", totalEarnings) + "!"));
        player.sendMessage(new TextComponentString("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(world, player.getUniqueID()))));
        player.openContainer.detectAndSendChanges();
    }

    // ========== Admin operations ==========

    private static boolean isOp(EntityPlayerMP player) {
        return player.canUseCommand(2, "");
    }

    private static void handleRemoveItem(EntityPlayerMP player, int categoryIndex, int itemIndex) {
        if (!isOp(player)) {
            player.sendMessage(new TextComponentString("\u00a7cYou need OP level 2 to manage the shop."));
            return;
        }
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
        ItemStack removed = items.remove(itemIndex);
        player.sendMessage(new TextComponentString("\u00a7aRemoved " + removed.getDisplayName() + " from category \"" + category.getName() + "\"."));
    }

    private static void handleRemoveCategory(EntityPlayerMP player, int categoryIndex) {
        if (!isOp(player)) {
            player.sendMessage(new TextComponentString("\u00a7cYou need OP level 2 to manage the shop."));
            return;
        }
        List<ShopCategory> categories = WorldShop.getCategories();
        if (categoryIndex < 0 || categoryIndex >= categories.size()) {
            player.sendMessage(new TextComponentString("\u00a7cInvalid category."));
            return;
        }
        ShopCategory removed = categories.remove(categoryIndex);
        player.sendMessage(new TextComponentString("\u00a7aRemoved category \"" + removed.getName() + "\"."));
    }

    private static void handleAddCategory(EntityPlayerMP player, String name, String iconItemId) {
        if (!isOp(player)) {
            player.sendMessage(new TextComponentString("\u00a7cYou need OP level 2 to manage the shop."));
            return;
        }
        if (name == null || name.trim().isEmpty()) {
            player.sendMessage(new TextComponentString("\u00a7cCategory name cannot be empty."));
            return;
        }
        Item iconItem = Item.getByNameOrId(iconItemId);
        if (iconItem == null) {
            player.sendMessage(new TextComponentString("\u00a7cUnknown item: " + iconItemId));
            return;
        }
        ItemStack iconStack = new ItemStack(iconItem);
        ShopCategory newCategory = new ShopCategory(name.trim(), iconStack);
        WorldShop.getCategories().add(newCategory);
        player.sendMessage(new TextComponentString("\u00a7aAdded category \"" + name + "\" with icon " + iconItemId + "."));
    }

    private static void handleAddItem(EntityPlayerMP player, int categoryIndex, String itemId) {
        if (!isOp(player)) {
            player.sendMessage(new TextComponentString("\u00a7cYou need OP level 2 to manage the shop."));
            return;
        }
        List<ShopCategory> categories = WorldShop.getCategories();
        if (categoryIndex < 0 || categoryIndex >= categories.size()) {
            player.sendMessage(new TextComponentString("\u00a7cInvalid category."));
            return;
        }
        Item item = Item.getByNameOrId(itemId);
        if (item == null) {
            player.sendMessage(new TextComponentString("\u00a7cUnknown item: " + itemId));
            return;
        }
        ItemStack stack = new ItemStack(item);
        if (stack.isEmpty()) {
            player.sendMessage(new TextComponentString("\u00a7cItem " + itemId + " cannot be obtained."));
            return;
        }
        ShopCategory category = categories.get(categoryIndex);
        category.addItem(stack.copy());
        player.sendMessage(new TextComponentString("\u00a7aAdded " + stack.getDisplayName() + " to category \"" + category.getName() + "\"."));
    }

    private static void handleEditItem(EntityPlayerMP player, int categoryIndex, String itemId, String displayName, String iconId, double buyPrice, double sellPrice) {
        if (!isOp(player)) {
            player.sendMessage(new TextComponentString("\u00a7cYou need OP level 2 to manage the shop."));
            return;
        }
        List<ShopCategory> categories = WorldShop.getCategories();
        if (categoryIndex < 0 || categoryIndex >= categories.size()) {
            player.sendMessage(new TextComponentString("\u00a7cInvalid category."));
            return;
        }
        ShopCategory category = categories.get(categoryIndex);
        List<ItemStack> items = category.getItems();

        ItemStack targetStack = null;
        int targetIndex = -1;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack != null && !stack.isEmpty()) {
                String stackId = stack.getItem().getRegistryName().toString();
                if (stackId.equals(itemId)) {
                    targetStack = stack;
                    targetIndex = i;
                    break;
                }
            }
        }

        if (targetStack == null) {
            player.sendMessage(new TextComponentString("\u00a7cItem " + itemId + " not found in this category."));
            return;
        }

        PriceConfig priceConfig = WorldShop.getPriceEngine().getPriceConfig();
        if (priceConfig != null) {
            if (buyPrice > 0) {
                double basePrice = buyPrice / 1.2;
                priceConfig.setPrice(targetStack, basePrice);
                player.sendMessage(new TextComponentString("\u00a77Set custom buy price to $" + String.format("%.2f", buyPrice)));
            }
            if (sellPrice > 0) {
                double basePrice = sellPrice / 0.8;
                priceConfig.setPrice(targetStack, basePrice);
                player.sendMessage(new TextComponentString("\u00a77Set custom sell price to $" + String.format("%.2f", sellPrice)));
            }
        }

        if (displayName != null && !displayName.trim().isEmpty()) {
            ItemStack renamedStack = targetStack.copy();
            renamedStack.setStackDisplayName(displayName.trim());
            items.set(targetIndex, renamedStack);
            player.sendMessage(new TextComponentString("\u00a7aSet display name to \"" + displayName.trim() + "\"."));
        }

        player.sendMessage(new TextComponentString("\u00a7aItem updated."));
    }

    private static void handleReorderCategories(EntityPlayerMP player, String[] categoryNames) {
        if (!isOp(player)) {
            player.sendMessage(new TextComponentString("\u00a7cYou need OP level 2 to manage the shop."));
            return;
        }
        if (categoryNames == null || categoryNames.length == 0) {
            player.sendMessage(new TextComponentString("\u00a7cInvalid reorder data."));
            return;
        }
        List<ShopCategory> categories = WorldShop.getCategories();
        java.util.List<ShopCategory> reordered = new java.util.ArrayList<>();
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
        WorldShop.saveCategoryOrder(reordered);
        player.sendMessage(new TextComponentString("\u00a7aCategories reordered successfully."));
    }

    private static void handleResetCategoryOrder(EntityPlayerMP player) {
        if (!isOp(player)) {
            player.sendMessage(new TextComponentString("\u00a7cYou need OP level 2 to manage the shop."));
            return;
        }
        MinecraftServer server = WorldShop.getCurrentServer();
        if (server != null) {
            ShopData shopData = ShopData.forServer(server);
            shopData.setCategoryOrder(new java.util.ArrayList<>());
        }
        player.sendMessage(new TextComponentString("\u00a7aCategory order has been reset to default."));
    }

    private static void handleResetAllPrices(EntityPlayerMP player) {
        if (!isOp(player)) {
            player.sendMessage(new TextComponentString("\u00a7cYou need OP level 2 to manage the shop."));
            return;
        }
        PriceConfig priceConfig = WorldShop.getPriceEngine().getPriceConfig();
        if (priceConfig != null) {
            priceConfig.clearAll();
            WorldShop.getPriceEngine().clearCache();
            player.sendMessage(new TextComponentString("\u00a7aAll item prices have been reset."));
        } else {
            player.sendMessage(new TextComponentString("\u00a7cPrice config not available."));
        }
    }

    private static void handleRecalculateCategory(EntityPlayerMP player, int categoryIndex) {
        if (!isOp(player)) {
            player.sendMessage(new TextComponentString("\u00a7cYou need OP level 2 to manage the shop."));
            return;
        }
        List<ShopCategory> categories = WorldShop.getCategories();
        if (categoryIndex < 0 || categoryIndex >= categories.size()) {
            player.sendMessage(new TextComponentString("\u00a7cInvalid category index."));
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
            WorldShop.getPriceEngine().clearCache();
            player.sendMessage(new TextComponentString("\u00a7aCleared cached prices for " + recalculated + " items in category \"" + category.getName() + "\"."));
        } else {
            player.sendMessage(new TextComponentString("\u00a7cPrice config not available."));
        }
    }

    private static void handleResetCategory(EntityPlayerMP player, int categoryIndex) {
        if (!isOp(player)) {
            player.sendMessage(new TextComponentString("\u00a7cYou need OP level 2 to manage the shop."));
            return;
        }
        List<ShopCategory> categories = WorldShop.getCategories();
        if (categoryIndex < 0 || categoryIndex >= categories.size()) {
            player.sendMessage(new TextComponentString("\u00a7cInvalid category index."));
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
            WorldShop.getPriceEngine().clearCache();
        }
        player.sendMessage(new TextComponentString("\u00a7aCategory \"" + category.getName() + "\" has been reset."));
    }

    private static void handleRecalculateBlock(EntityPlayerMP player, String itemId) {
        if (!isOp(player)) {
            player.sendMessage(new TextComponentString("\u00a7cYou need OP level 2 to manage the shop."));
            return;
        }
        Item item = Item.getByNameOrId(itemId);
        if (item == null) {
            player.sendMessage(new TextComponentString("\u00a7cUnknown item: " + itemId));
            return;
        }
        ItemStack stack = new ItemStack(item);
        PriceConfig priceConfig = WorldShop.getPriceEngine().getPriceConfig();
        if (priceConfig != null) {
            priceConfig.removePrice(stack);
            WorldShop.getPriceEngine().clearCache();
            player.sendMessage(new TextComponentString("\u00a7aPrice for \"" + stack.getDisplayName() + "\" cleared."));
        } else {
            player.sendMessage(new TextComponentString("\u00a7cPrice config not available."));
        }
    }

    private static void handleResetBlock(EntityPlayerMP player, String itemId) {
        handleRecalculateBlock(player, itemId);
    }

    private static void handleSaveConfig(EntityPlayerMP player, boolean sellhandConfirmation) {
        if (!isOp(player)) {
            player.sendMessage(new TextComponentString("\u00a7cYou need OP level 2 to manage the shop."));
            return;
        }
        ShopConfig config = WorldShop.getShopConfig();
        if (config != null) {
            config.setSellhandConfirmation(sellhandConfirmation);
            String status = sellhandConfirmation ? "enabled" : "disabled";
            player.sendMessage(new TextComponentString("\u00a7aSellhand confirmation " + status + "."));
        } else {
            player.sendMessage(new TextComponentString("\u00a7cConfig not available."));
        }
    }
}
