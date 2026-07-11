package asd.itamio.worldshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /shop - Opens the shop GUI (not available in this version - shows category list in chat)
 */
public class CommandShop {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shop")
                .executes(CommandShop::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        // Display available categories in chat
        player.sendSystemMessage(Component.literal("§6§l=== World Shop - Categories ==="));
        int i = 0;
        for (ShopCategory cat : WorldShop.getCategories()) {
            player.sendSystemMessage(Component.literal("§e" + i + ". §f" + cat.getName() + " §7(" + cat.getItems().size() + " items)"));
            i++;
        }
        player.sendSystemMessage(Component.literal("§7Use §e/shop buy <category> <item> <quantity> §7to purchase items"));
        player.sendSystemMessage(Component.literal("§7Use §e/sellhand §7to sell all matching items from your inventory"));
        return 1;
    }
}
