package asd.itamio.worldshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ModCommands {

    private static final Map<UUID, PendingPayment> pendingPayments = new HashMap<>();
    private static final long CONFIRM_TIMEOUT_MS = 30000L;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shop")
                .executes(context -> executeShop(context.getSource().getPlayerOrException()))
        );

        dispatcher.register(Commands.literal("sellhand")
                .executes(context -> executeSellHand(context.getSource().getPlayerOrException()))
        );

        dispatcher.register(Commands.literal("sellgui")
                .executes(context -> executeSellGui(context.getSource().getPlayerOrException()))
        );

        dispatcher.register(Commands.literal("balance")
                .executes(context -> executeBalance(context.getSource().getPlayerOrException()))
        );

        dispatcher.register(Commands.literal("bal")
                .executes(context -> executeBalance(context.getSource().getPlayerOrException()))
        );

        dispatcher.register(Commands.literal("pay")
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                .executes(context -> executePay(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "player"),
                                        DoubleArgumentType.getDouble(context, "amount")
                                ))
                        )
                )
                .executes(context -> executePayConfirm(context.getSource().getPlayerOrException()))
        );

        // /pay confirm subcommand
        dispatcher.register(Commands.literal("pay")
                .then(Commands.literal("confirm")
                        .executes(context -> executePayConfirm(context.getSource().getPlayerOrException()))
                )
        );
    }

    private static int executeShop(ServerPlayer player) {
        if (player.connection != null) {
            ServerPlayNetworking.send(player, ShopPacket.openShop());
        }
        return 1;
    }

    private static int executeSellHand(ServerPlayer player) {
        WorldShop.getPriceEngine().setLevel(player.serverLevel());
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.literal("\u00a7cYou are not holding any item."));
            return 0;
        }

        PriceEngine priceEngine = WorldShop.getPriceEngine();
        double sellPricePerItem = priceEngine.getSellPrice(held);
        int totalSold = 0;

        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack slot = player.getInventory().items.get(i);
            if (slot.isEmpty() || slot.getItem() != held.getItem()) continue;
            totalSold += slot.getCount();
            player.getInventory().items.set(i, ItemStack.EMPTY);
        }

        if (totalSold == 0) {
            player.sendSystemMessage(Component.literal("\u00a7cNo items found to sell."));
            return 0;
        }

        double totalEarnings = sellPricePerItem * (double) totalSold;
        EconomyData economy = EconomyData.get(player.serverLevel());
        economy.addBalance(player.getUUID(), totalEarnings);

        player.sendSystemMessage(Component.literal("\u00a7aSold " + totalSold + "x " + held.getHoverName().getString() + " for $" + String.format("%.2f", totalEarnings) + "!"));
        player.sendSystemMessage(Component.literal("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(player.getUUID()))));
        player.containerMenu.broadcastChanges();
        return 1;
    }

    private static int executeSellGui(ServerPlayer player) {
        if (player.connection != null) {
            ServerPlayNetworking.send(player, ShopPacket.openSellGui());
        }
        return 1;
    }

    private static int executeBalance(ServerPlayer player) {
        EconomyData economy = EconomyData.get(player.serverLevel());
        double balance = economy.getBalance(player.getUUID());
        player.sendSystemMessage(Component.literal("\u00a7aBalance: $" + String.format("%.2f", balance)));
        return 1;
    }

    private static int executePay(ServerPlayer player, String targetName, double amount) {
        UUID senderUuid = player.getUUID();

        if (amount <= 0.0) {
            player.sendSystemMessage(Component.literal("\u00a7cAmount must be greater than 0."));
            return 0;
        }

        if (targetName.equalsIgnoreCase(player.getScoreboardName())) {
            if (!player.hasPermissions(2) && !player.isCreative()) {
                player.sendSystemMessage(Component.literal("\u00a7cYou can't pay yourself. (OP/Creative only)"));
                return 0;
            }
        }

        EconomyData economy = EconomyData.get(player.serverLevel());
        UUID targetUuid = resolveTargetUuid(player, economy, targetName);
        if (targetUuid == null) {
            player.sendSystemMessage(Component.literal("\u00a7cPlayer not found: " + targetName));
            return 0;
        }

        double balance = economy.getBalance(senderUuid);
        if (balance < amount) {
            player.sendSystemMessage(Component.literal("\u00a7cYou don't have enough money. You have $" + String.format("%.2f", balance) + " but need $" + String.format("%.2f", amount) + "."));
            return 0;
        }

        pendingPayments.put(senderUuid, new PendingPayment(targetName, amount));
        player.sendSystemMessage(Component.literal("\u00a7eConfirm payment of $" + String.format("%.2f", amount) + " to " + targetName + "?"));
        player.sendSystemMessage(Component.literal("\u00a7aType \u00a7f/pay confirm \u00a7ato confirm, or wait 30 seconds to cancel."));
        return 1;
    }

    private static int executePayConfirm(ServerPlayer player) {
        UUID senderUuid = player.getUUID();
        PendingPayment pending = pendingPayments.get(senderUuid);

        if (pending == null || pending.isExpired()) {
            pendingPayments.remove(senderUuid);
            player.sendSystemMessage(Component.literal("\u00a7cNo pending payment to confirm. Use /pay <player> <amount> first."));
            return 0;
        }

        EconomyData economy = EconomyData.get(player.serverLevel());
        if (!economy.subtractBalance(senderUuid, pending.amount)) {
            player.sendSystemMessage(Component.literal("\u00a7cYou don't have enough money. You have $" + String.format("%.2f", economy.getBalance(senderUuid)) + " but need $" + String.format("%.2f", pending.amount) + "."));
            pendingPayments.remove(senderUuid);
            return 0;
        }

        UUID targetUuid = resolveTargetUuid(player, economy, pending.targetName);
        if (targetUuid == null) {
            economy.addBalance(senderUuid, pending.amount);
            player.sendSystemMessage(Component.literal("\u00a7cCould not find player: " + pending.targetName));
            pendingPayments.remove(senderUuid);
            return 0;
        }

        economy.addBalance(targetUuid, pending.amount);
        pendingPayments.remove(senderUuid);
        player.sendSystemMessage(Component.literal("\u00a7aPaid $" + String.format("%.2f", pending.amount) + " to " + pending.targetName + "!"));
        player.sendSystemMessage(Component.literal("\u00a77Your balance: $" + String.format("%.2f", economy.getBalance(senderUuid))));

        ServerPlayer targetPlayer = player.getServer().getPlayerList().getPlayerByName(pending.targetName);
        if (targetPlayer != null) {
            targetPlayer.sendSystemMessage(Component.literal("\u00a7aYou received $" + String.format("%.2f", pending.amount) + " from " + player.getScoreboardName() + "!"));
            targetPlayer.sendSystemMessage(Component.literal("\u00a77Your balance: $" + String.format("%.2f", economy.getBalance(targetUuid))));
        }
        return 1;
    }

    private static UUID resolveTargetUuid(ServerPlayer player, EconomyData economy, String name) {
        ServerPlayer onlinePlayer = player.getServer().getPlayerList().getPlayerByName(name);
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
