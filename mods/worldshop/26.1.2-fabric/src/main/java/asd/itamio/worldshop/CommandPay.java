package asd.itamio.worldshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
                                .executes(CommandPay::execute)))
                .then(Commands.literal("confirm")
                        .executes(CommandPay::confirm)));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        UUID senderUuid = player.getUUID();
        String targetName = StringArgumentType.getString(ctx, "player");
        double amount;
        try {
            amount = DoubleArgumentType.getDouble(ctx, "amount");
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal("§cInvalid amount."));
            return 0;
        }

        if (amount <= 0.0) {
            player.sendSystemMessage(Component.literal("§cAmount must be greater than 0."));
            return 0;
        }

        if (targetName.equalsIgnoreCase(player.getScoreboardName())) {
            boolean isOp = source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
            boolean isCreative = player.isCreative();
            if (!isOp && !isCreative) {
                player.sendSystemMessage(Component.literal("§cYou can't pay yourself. (OP/Creative only)"));
                return 0;
            }
        }

        EconomyData economy = EconomyData.get(player.level());
        UUID targetUuid = resolveTargetUuid(source.getServer(), economy, targetName);
        if (targetUuid == null) {
            player.sendSystemMessage(Component.literal("§cPlayer not found: " + targetName));
            return 0;
        }

        double balance = economy.getBalance(senderUuid);
        if (balance < amount) {
            player.sendSystemMessage(Component.literal("§cYou don't have enough money. You have $" + String.format("%.2f", balance) + " but need $" + String.format("%.2f", amount) + "."));
            return 0;
        }

        pendingPayments.put(senderUuid, new PendingPayment(targetName, amount));
        player.sendSystemMessage(Component.literal("§eConfirm payment of $" + String.format("%.2f", amount) + " to " + targetName + "?"));
        player.sendSystemMessage(Component.literal("§aType §f/pay confirm §ato confirm, or wait 30 seconds to cancel."));
        return 1;
    }

    private static int confirm(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        UUID senderUuid = player.getUUID();
        PendingPayment pending = pendingPayments.get(senderUuid);

        if (pending == null || pending.isExpired()) {
            pendingPayments.remove(senderUuid);
            player.sendSystemMessage(Component.literal("§cNo pending payment to confirm. Use /pay <player> <amount> first."));
            return 0;
        }

        EconomyData economy = EconomyData.get(player.level());
        if (!economy.subtractBalance(senderUuid, pending.amount)) {
            player.sendSystemMessage(Component.literal("§cYou don't have enough money. You have $" + String.format("%.2f", economy.getBalance(senderUuid)) + " but need $" + String.format("%.2f", pending.amount) + "."));
            pendingPayments.remove(senderUuid);
            return 0;
        }

        UUID targetUuid = resolveTargetUuid(source.getServer(), economy, pending.targetName);
        if (targetUuid == null) {
            economy.addBalance(senderUuid, pending.amount);
            player.sendSystemMessage(Component.literal("§cCould not find player: " + pending.targetName));
            pendingPayments.remove(senderUuid);
            return 0;
        }

        economy.addBalance(targetUuid, pending.amount);
        pendingPayments.remove(senderUuid);
        player.sendSystemMessage(Component.literal("§aPaid $" + String.format("%.2f", pending.amount) + " to " + pending.targetName + "!"));
        player.sendSystemMessage(Component.literal("§7Your balance: $" + String.format("%.2f", economy.getBalance(senderUuid))));

        ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayerByName(pending.targetName);
        if (targetPlayer != null) {
            targetPlayer.sendSystemMessage(Component.literal("§aYou received $" + String.format("%.2f", pending.amount) + " from " + player.getScoreboardName() + "!"));
            targetPlayer.sendSystemMessage(Component.literal("§7Your balance: $" + String.format("%.2f", economy.getBalance(targetUuid))));
        }
        return 1;
    }

    private static UUID resolveTargetUuid(net.minecraft.server.MinecraftServer server, EconomyData economy, String name) {
        ServerPlayer onlinePlayer = server.getPlayerList().getPlayerByName(name);
        if (onlinePlayer != null) {
            economy.registerPlayer(onlinePlayer.getScoreboardName(), onlinePlayer.getUUID());
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
