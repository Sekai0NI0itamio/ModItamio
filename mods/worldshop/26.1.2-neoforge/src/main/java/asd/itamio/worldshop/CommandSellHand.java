package asd.itamio.worldshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class CommandSellHand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sellhand")
                .executes(ctx -> execute(ctx))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.literal("\u00a7cYou are not holding any item."));
            return 0;
        }

        PriceEngine priceEngine = WorldShop.getPriceEngine();
        double sellPricePerItem = priceEngine.getSellPrice(held);
        int totalSold = 0;

        var invItems = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < invItems.size(); i++) {
            ItemStack slot = invItems.get(i);
            if (slot.isEmpty() || !isSameItem(slot, held)) continue;
            totalSold += slot.getCount();
            invItems.set(i, ItemStack.EMPTY);
        }

        if (totalSold == 0) {
            player.sendSystemMessage(Component.literal("\u00a7cNo items found to sell."));
            return 0;
        }

        double totalEarnings = sellPricePerItem * (double) totalSold;
        EconomyData economy = EconomyData.get(player.level());
        economy.addBalance(player.getUUID(), totalEarnings);

        player.sendSystemMessage(Component.literal("\u00a7aSold " + totalSold + "x " + held.getHoverName().getString() + " for $" + String.format("%.2f", totalEarnings) + "!"));
        player.sendSystemMessage(Component.literal("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(player.getUUID()))));
        player.containerMenu.broadcastChanges();

        return 1;
    }

    private static boolean isSameItem(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b);
    }
}
