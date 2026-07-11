package asd.itamio.worldshop;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

public class CommandSellHand extends CommandBase {
    @Override
    public String getName() {
        return "sellhand";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/sellhand";
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
}
