package asd.itamio.modernshop;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CommandBalance {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("balance")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (source.getEntity() instanceof ServerPlayer player) {
                        EconomyData economy = EconomyData.get(player.serverLevel());
                        double balance = economy.getBalance(player.getUUID());
                        source.sendSuccess(() -> Component.literal("\u00a7aBalance: $" + String.format("%.2f", balance)), false);
                    } else {
                        source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
                    }
                    return 1;
                })
        );

        dispatcher.register(Commands.literal("bal")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (source.getEntity() instanceof ServerPlayer player) {
                        EconomyData economy = EconomyData.get(player.serverLevel());
                        double balance = economy.getBalance(player.getUUID());
                        source.sendSuccess(() -> Component.literal("\u00a7aBalance: $" + String.format("%.2f", balance)), false);
                    } else {
                        source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
                    }
                    return 1;
                })
        );
    }
}
