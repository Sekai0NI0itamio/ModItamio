package asd.itamio.modernshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class CommandSellGui {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sellgui")
                .executes(CommandSellGui::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        // In this version, /sellgui sells all items from inventory (no GUI available)
        PriceEngine priceEngine = ModernShop.getPriceEngine();
        double totalEarnings = 0.0;
        int totalSold = 0;

        var inventory = player.getInventory();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot.isEmpty()) continue;

            double sellPrice = priceEngine.getSellPrice(slot);
            totalEarnings += sellPrice * (double) slot.getCount();
            totalSold += slot.getCount();
            inventory.setItem(i, ItemStack.EMPTY);
        }

        if (totalSold == 0) {
            player.sendSystemMessage(Component.literal("§cNo items to sell."));
            return 0;
        }

        EconomyData economy = EconomyData.get(player.level());
        economy.addBalance(player.getUUID(), totalEarnings);

        player.sendSystemMessage(Component.literal("§aSold " + totalSold + " items for $" + String.format("%.2f", totalEarnings) + "!"));
        player.sendSystemMessage(Component.literal("§7Balance: $" + String.format("%.2f", economy.getBalance(player.getUUID()))));
        return 1;
    }
}
