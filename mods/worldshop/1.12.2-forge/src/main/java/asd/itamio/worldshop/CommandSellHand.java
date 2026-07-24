package asd.itamio.worldshop;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CommandSellHand extends CommandBase {
    private static final Map<UUID, PendingSell> pendingSells = new HashMap<>();
    private static final long CONFIRM_TIMEOUT_MS = 30000L;

    @Override
    public String getName() {
        return "sellhand";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/sellhand [confirm]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString("\u00a7cOnly players can use this command."));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;

        // /sellhand confirm — execute a pending sell
        if (args.length > 0 && args[0].equalsIgnoreCase("confirm")) {
            executeSellhandConfirm(player);
            return;
        }

        // /sellhand — preview (if confirmation enabled) or sell immediately
        ShopConfig config = WorldShop.getShopConfig();
        if (config != null && config.isSellhandConfirmation()) {
            executeSellhandPreview(player);
        } else {
            sellImmediately(player);
        }
    }

    /**
     * Show a preview of what /sellhand will sell and ask for confirmation.
     */
    private void executeSellhandPreview(EntityPlayerMP player) {
        ItemStack held = player.getHeldItemMainhand();
        if (held.isEmpty()) {
            player.sendMessage(new TextComponentString("\u00a7cYou are not holding any item."));
            return;
        }

        // Count matching items in inventory (matches handleSellHand's behavior,
        // which matches by item type only).
        int totalCount = 0;
        for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
            ItemStack slot = player.inventory.mainInventory.get(i);
            if (!slot.isEmpty() && slot.getItem() == held.getItem()) {
                totalCount += slot.getCount();
            }
        }

        if (totalCount == 0) {
            player.sendMessage(new TextComponentString("\u00a7cNo items found to sell."));
            return;
        }

        double sellPricePerItem = WorldShop.getPriceEngine().getSellPrice(held);
        double totalEarnings = sellPricePerItem * (double) totalCount;

        // Register pending sell
        pendingSells.put(player.getUniqueID(), new PendingSell(System.currentTimeMillis()));

        // Send preview messages
        player.sendMessage(new TextComponentString("\u00a7e=== Sell Hand Preview ==="));
        player.sendMessage(new TextComponentString("\u00a77Item: \u00a7f" + held.getDisplayName()));
        player.sendMessage(new TextComponentString("\u00a77Count: \u00a7f" + totalCount + "x"));
        player.sendMessage(new TextComponentString("\u00a77Price: \u00a7a$" + String.format("%.2f", sellPricePerItem) + " each = \u00a7e$" + String.format("%.2f", totalEarnings)));
        player.sendMessage(new TextComponentString("\u00a7aType \u00a7f/sellhand confirm \u00a7ato confirm, or wait 30 seconds to cancel."));
    }

    /**
     * Execute the confirmed /sellhand sale.
     */
    private void executeSellhandConfirm(EntityPlayerMP player) {
        UUID playerUuid = player.getUniqueID();
        PendingSell pending = pendingSells.get(playerUuid);

        if (pending == null || pending.isExpired()) {
            pendingSells.remove(playerUuid);
            player.sendMessage(new TextComponentString("\u00a7cNo pending sell to confirm. Use /sellhand first."));
            return;
        }

        // Execute the sell
        pendingSells.remove(playerUuid);
        World world = player.getEntityWorld();
        ServerPacketHandler.handleSellHand(player, world);
    }

    /**
     * Sell immediately without confirmation (used when confirmation is disabled).
     * Preserves the original /sellhand behavior.
     */
    private void sellImmediately(EntityPlayerMP player) {
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

    private boolean isSameItem(ItemStack a, ItemStack b) {
        if (a.getItem() != b.getItem()) {
            return false;
        }
        return a.getItemDamage() == b.getItemDamage();
    }

    private static class PendingSell {
        final long timestamp;

        PendingSell(long timestamp) {
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CONFIRM_TIMEOUT_MS;
        }
    }
}
