package asd.itamio.modernshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ModCommands {
    private static final Map<UUID, PendingPayment> pendingPayments = new HashMap<>();
    private static final long CONFIRM_TIMEOUT_MS = 30000L;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /shop
        dispatcher.register(Commands.literal("shop")
                .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
                    @Override
                    public int run(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                        CommandSourceStack source = context.getSource();
                        if (source.getEntity() instanceof ServerPlayer player) {
                            FriendlyByteBuf buf = PacketByteBufs.create();
                            ShopPacket.write(ShopPacket.openShop(), buf);
                            ServerPlayNetworking.send(player, ShopPacket.PACKET_ID, buf);
                        } else {
                            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
                        }
                        return 1;
                    }
                })
                .then(Commands.literal("player")
                        .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
                            @Override
                            public int run(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                                CommandSourceStack source = context.getSource();
                                if (source.getEntity() instanceof ServerPlayer player) {
                                    FriendlyByteBuf buf = PacketByteBufs.create();
                                    ShopPacket.write(ShopPacket.openPlayerShop(), buf);
                                    ServerPlayNetworking.send(player, ShopPacket.PACKET_ID, buf);
                                } else {
                                    source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
                                }
                                return 1;
                            }
                        })
                )
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
                            @Override
                            public int run(CommandContext<CommandSourceStack> context) {
                                CommandSourceStack source = context.getSource();
                                // Clear the in-memory price cache
                                ModernShop.getPriceEngine().clearCache();
                                // Clear the persistent price config file so prices are recalculated fresh
                                PriceConfig config = ModernShop.getPriceEngine().getPriceConfig();
                                if (config != null) {
                                    config.clearAllPrices();
                                }
                                // Rebuild shop categories
                                ModernShop.buildShopCategories();
                                ModernShop.applyPersistedCategoryOrder();
                                source.sendSuccess(new java.util.function.Supplier<Component>() {
                                    @Override
                                    public Component get() {
                                        return Component.literal("\u00a7aShop prices have been reset and recalculated from recipes!");
                                    }
                                }, true);
                                ModernShop.LOGGER.info("Shop prices reset by {} (config file cleared)", source.getTextName());
                                return 1;
                            }
                        })
                )
        );

        // /sellhand
        dispatcher.register(Commands.literal("sellhand")
                .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
                    @Override
                    public int run(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                        CommandSourceStack source = context.getSource();
                        if (source.getEntity() instanceof ServerPlayer player) {
                            FriendlyByteBuf buf = PacketByteBufs.create();
                            ShopPacket.write(ShopPacket.sellHand(), buf);
                            ServerPlayNetworking.send(player, ShopPacket.PACKET_ID, buf);
                        } else {
                            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
                        }
                        return 1;
                    }
                })
        );

        // /sellgui
        dispatcher.register(Commands.literal("sellgui")
                .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
                    @Override
                    public int run(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                        CommandSourceStack source = context.getSource();
                        if (source.getEntity() instanceof ServerPlayer player) {
                            FriendlyByteBuf buf = PacketByteBufs.create();
                            ShopPacket.write(ShopPacket.openSellGui(), buf);
                            ServerPlayNetworking.send(player, ShopPacket.PACKET_ID, buf);
                        } else {
                            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
                        }
                        return 1;
                    }
                })
        );

        // /balance and /bal
        dispatcher.register(Commands.literal("balance")
                .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
                    @Override
                    public int run(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                        CommandSourceStack source = context.getSource();
                        if (source.getEntity() instanceof ServerPlayer player) {
                            EconomyData economy = EconomyData.get(player.serverLevel());
                            double balance = economy.getBalance(player.getUUID());
                            source.sendSuccess(new java.util.function.Supplier<Component>() {
                                @Override
                                public Component get() {
                                    return Component.literal("\u00a7aBalance: $" + String.format("%.2f", balance));
                                }
                            }, false);
                        } else {
                            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
                        }
                        return 1;
                    }
                })
        );

        dispatcher.register(Commands.literal("bal")
                .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
                    @Override
                    public int run(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                        CommandSourceStack source = context.getSource();
                        if (source.getEntity() instanceof ServerPlayer player) {
                            EconomyData economy = EconomyData.get(player.serverLevel());
                            double balance = economy.getBalance(player.getUUID());
                            source.sendSuccess(new java.util.function.Supplier<Component>() {
                                @Override
                                public Component get() {
                                    return Component.literal("\u00a7aBalance: $" + String.format("%.2f", balance));
                                }
                            }, false);
                        } else {
                            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
                        }
                        return 1;
                    }
                })
        );

        // /pay
        dispatcher.register(Commands.literal("pay")
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
                                    @Override
                                    public int run(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                                        return executePay(context, StringArgumentType.getString(context, "player"), DoubleArgumentType.getDouble(context, "amount"));
                                    }
                                })
                        )
                )
                .then(Commands.literal("confirm")
                        .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
                            @Override
                            public int run(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                                return executeConfirm(context);
                            }
                        })
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
        source.sendSuccess(new java.util.function.Supplier<Component>() {
            @Override
            public Component get() {
                return Component.literal("\u00a7eConfirm payment of $" + String.format("%.2f", amount) + " to " + targetName + "?");
            }
        }, false);
        source.sendSuccess(new java.util.function.Supplier<Component>() {
            @Override
            public Component get() {
                return Component.literal("\u00a7aType \u00a7f/pay confirm \u00a7ato confirm, or wait 30 seconds to cancel.");
            }
        }, false);
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
        source.sendSuccess(new java.util.function.Supplier<Component>() {
            @Override
            public Component get() {
                return Component.literal("\u00a7aPaid $" + String.format("%.2f", pending.amount) + " to " + pending.targetName + "!");
            }
        }, false);
        source.sendSuccess(new java.util.function.Supplier<Component>() {
            @Override
            public Component get() {
                return Component.literal("\u00a77Your balance: $" + String.format("%.2f", economy.getBalance(senderUuid)));
            }
        }, false);

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
