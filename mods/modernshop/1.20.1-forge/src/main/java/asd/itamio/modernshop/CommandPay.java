package asd.itamio.modernshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CommandPay {
    private static final Map<UUID, PendingPayment> pendingPayments = new HashMap<>();
    private static final long CONFIRM_TIMEOUT_MS = 30000L;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pay")
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                .executes(context -> executePay(context, StringArgumentType.getString(context, "player"), DoubleArgumentType.getDouble(context, "amount")))
                        )
                )
                .then(Commands.literal("confirm")
                        .executes(context -> executeConfirm(context))
                )
        );
    }

    private static int executePay(CommandContext<CommandSourceStack> context, String targetName, double amount) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
            return 0;
        }

        UUID senderUuid = player.getUUID();

        if (amount <= 0.0) {
            source.sendFailure(Component.literal("\u00a7cAmount must be greater than 0."));
            return 0;
        }

        if (targetName.equalsIgnoreCase(player.getScoreboardName())) {
            boolean isOp = player.hasPermissions(2);
            boolean isCreative = player.isCreative();
            if (!isOp && !isCreative) {
                source.sendFailure(Component.literal("\u00a7cYou can't pay yourself. (OP/Creative only)"));
                return 0;
            }
        }

        EconomyData economy = EconomyData.get(player.serverLevel());
        UUID targetUuid = resolveTargetUuid(player.getServer(), economy, targetName);
        if (targetUuid == null) {
            source.sendFailure(Component.literal("\u00a7cPlayer not found: " + targetName));
            return 0;
        }

        double balance = economy.getBalance(senderUuid);
        if (balance < amount) {
            source.sendFailure(Component.literal("\u00a7cYou don't have enough money. You have $" + String.format("%.2f", balance) + " but need $" + String.format("%.2f", amount) + "."));
            return 0;
        }

        pendingPayments.put(senderUuid, new PendingPayment(targetName, amount));
        source.sendSuccess(() -> Component.literal("\u00a7eConfirm payment of $" + String.format("%.2f", amount) + " to " + targetName + "?"), false);
        source.sendSuccess(() -> Component.literal("\u00a7aType \u00a7f/pay confirm \u00a7ato confirm, or wait 30 seconds to cancel."), false);
        return 1;
    }

    private static int executeConfirm(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
            return 0;
        }

        UUID senderUuid = player.getUUID();
        PendingPayment pending = pendingPayments.get(senderUuid);

        if (pending == null || pending.isExpired()) {
            pendingPayments.remove(senderUuid);
            source.sendFailure(Component.literal("\u00a7cNo pending payment to confirm. Use /pay <player> <amount> first."));
            return 0;
        }

        EconomyData economy = EconomyData.get(player.serverLevel());
        if (!economy.subtractBalance(senderUuid, pending.amount)) {
            source.sendFailure(Component.literal("\u00a7cYou don't have enough money. You have $" + String.format("%.2f", economy.getBalance(senderUuid)) + " but need $" + String.format("%.2f", pending.amount) + "."));
            pendingPayments.remove(senderUuid);
            return 0;
        }

        UUID targetUuid = resolveTargetUuid(player.getServer(), economy, pending.targetName);
        if (targetUuid == null) {
            economy.addBalance(senderUuid, pending.amount);
            source.sendFailure(Component.literal("\u00a7cCould not find player: " + pending.targetName));
            pendingPayments.remove(senderUuid);
            return 0;
        }

        economy.addBalance(targetUuid, pending.amount);
        pendingPayments.remove(senderUuid);
        source.sendSuccess(() -> Component.literal("\u00a7aPaid $" + String.format("%.2f", pending.amount) + " to " + pending.targetName + "!"), false);
        source.sendSuccess(() -> Component.literal("\u00a77Your balance: $" + String.format("%.2f", economy.getBalance(senderUuid))), false);

        ServerPlayer targetPlayer = player.getServer().getPlayerList().getPlayerByName(pending.targetName);
        if (targetPlayer != null) {
            targetPlayer.sendSystemMessage(Component.literal("\u00a7aYou received $" + String.format("%.2f", pending.amount) + " from " + player.getScoreboardName() + "!"));
            targetPlayer.sendSystemMessage(Component.literal("\u00a77Your balance: $" + String.format("%.2f", economy.getBalance(targetUuid))));
        }

        return 1;
    }

    private static UUID resolveTargetUuid(net.minecraft.server.MinecraftServer server, EconomyData economy, String name) {
        ServerPlayer onlinePlayer = server.getPlayerList().getPlayerByName(name);
        if (onlinePlayer != null) {
            economy.registerPlayer(name, onlinePlayer.getUUID());
            return onlinePlayer.getUUID();
        }
        return economy.getUuidByName(name);
    }

    private static class PendingPayment {
        final String targetName;
        final double amount;
        final long timestamp;

        PendingPayment(String targetName, double amount) {
            this.targetName = targetName;
            this.amount = amount;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CONFIRM_TIMEOUT_MS;
        }
    }
}
