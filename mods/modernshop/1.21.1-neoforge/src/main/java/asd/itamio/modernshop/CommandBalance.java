package asd.itamio.modernshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CommandBalance {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("balance")
                .executes(ctx -> execute(ctx))
        );
    }

    public static void registerAlias(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bal")
                .executes(ctx -> execute(ctx))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
            return 0;
        }
        EconomyData economy = EconomyData.get(player.serverLevel());
        double balance = economy.getBalance(player.getUUID());
        player.sendSystemMessage(Component.literal("\u00a7aBalance: $" + String.format("%.2f", balance)));
        return 1;
    }
}
