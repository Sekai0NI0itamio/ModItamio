package asd.itamio.worldshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CommandBalance {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("balance")
                .executes(CommandBalance::execute));
        dispatcher.register(Commands.literal("bal")
                .executes(CommandBalance::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        EconomyData economy = EconomyData.get(player.level());
        double balance = economy.getBalance(player.getUUID());
        player.sendSystemMessage(Component.literal("§aBalance: $" + String.format("%.2f", balance)));
        return 1;
    }
}
