package asd.itamio.modernshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registers all Modern Shop commands.
 *
 * <p>Commands:
 * <ul>
 *   <li>/shop — open the modded shop GUI (admin mode if op)</li>
 *   <li>/shop player — open the shop GUI in player mode (force)</li>
 *   <li>/shop player-no-mod — open the vanilla container (admin only, for vanilla clients)</li>
 *   <li>/shop reset — reset all prices and recalculate from recipes (admin)</li>
 *   <li>/shop search all &lt;name&gt; — search items across all categories</li>
 *   <li>/shop search category &lt;category&gt; &lt;name&gt; — search within a category</li>
 *   <li>/sellhand — sell all matching items in hand (with optional confirmation)</li>
 *   <li>/sellhand confirm — confirm a pending sell</li>
 *   <li>/sellgui — open the sell GUI</li>
 *   <li>/balance (or /bal) — check your balance</li>
 *   <li>/pay &lt;player&gt; &lt;amount&gt; — pay another player (with confirmation)</li>
 *   <li>/pay confirm — confirm a pending payment</li>
 * </ul>
 */
public class ModCommands {
    private static final Map<UUID, PendingPayment> pendingPayments = new HashMap<>();
    private static final Map<UUID, PendingSell> pendingSells = new HashMap<>();
    private static final long CONFIRM_TIMEOUT_MS = 30000L;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /shop
        dispatcher.register(Commands.literal("shop")
                .executes(context -> openShop(context, false))
                .then(Commands.literal("player")
                        .executes(context -> openShop(context, true))
                )
                .then(Commands.literal("player-no-mod")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            if (source.getEntity() instanceof ServerPlayer player) {
                                VanillaShopContainer.open(player);
                            } else {
                                source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
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
                            source.sendSuccess(() -> Component.literal("\u00a7aShop prices have been reset and recalculated from recipes!"), true);
                            ModernShop.LOGGER.info("Shop prices reset by {} (config file cleared)", source.getTextName());
                            return 1;
                        })
                )
                // /shop search all <name> — search all items across all categories
                .then(Commands.literal("search")
                        .then(Commands.literal("all")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ModCommands::executeSearchAll)
                                )
                        )
                        .then(Commands.literal("category")
                                .then(Commands.argument("category", StringArgumentType.string())
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ModCommands::executeSearchCategory)
                                        )
                                )
                        )
                )
        );

        // /sellhand with optional confirmation
        dispatcher.register(Commands.literal("sellhand")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (source.getEntity() instanceof ServerPlayer player) {
                        ShopConfig config = ModernShop.getShopConfig();
                        if (config != null && config.isSellhandConfirmation()) {
                            // Show confirmation message with item details
                            executeSellhandPreview(player, source);
                        } else {
                            // Sell immediately
                            ServerPacketHandler.handleSellHand(player);
                        }
                    } else {
                        source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
                    }
                    return 1;
                })
                .then(Commands.literal("confirm")
                        .executes(ModCommands::executeSellhandConfirm)
                )
        );

        // /sellgui
        dispatcher.register(Commands.literal("sellgui")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (source.getEntity() instanceof ServerPlayer player) {
                        ModernShop.NETWORK.send(PacketDistributor.PLAYER.with(() -> player), ShopPacket.openSellGui());
                    } else {
                        source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
                    }
                    return 1;
                })
        );

        // /balance and /bal
        dispatcher.register(Commands.literal("balance")
                .executes(context -> showBalance(context))
        );
        dispatcher.register(Commands.literal("bal")
                .executes(context -> showBalance(context))
        );

        // /pay
        dispatcher.register(Commands.literal("pay")
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                .executes(context -> executePay(context, StringArgumentType.getString(context, "player"), DoubleArgumentType.getDouble(context, "amount")))
                        )
                )
                .then(Commands.literal("confirm")
                        .executes(ModCommands::executeConfirm)
                )
        );
    }

    /**
     * Open the shop GUI for the player. In player mode, the GUI is forced
     * into player mode (no admin controls) regardless of permissions.
     */
    private static int openShop(CommandContext<CommandSourceStack> context, boolean playerMode) {
        CommandSourceStack source = context.getSource();
        if (source.getEntity() instanceof ServerPlayer player) {
            // Forge requires the mod on both client and server, so always use
            // the modded GUI. For vanilla clients, use /shop player-no-mod.
            ShopPacket packet = playerMode ? ShopPacket.openPlayerShop() : ShopPacket.openShop();
            ModernShop.NETWORK.send(PacketDistributor.PLAYER.with(() -> player), packet);
        } else {
            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
        }
        return 1;
    }

    private static int showBalance(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (source.getEntity() instanceof ServerPlayer player) {
            EconomyProvider economy = ModernShop.getEconomyProvider(player.serverLevel());
            double balance = economy.getBalance(player.serverLevel(), player.getUUID());
            source.sendSuccess(() -> Component.literal("\u00a7aBalance: $" + String.format("%.2f", balance)), false);
        } else {
            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
        }
        return 1;
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

        EconomyProvider economy = ModernShop.getEconomyProvider(player.serverLevel());
        UUID targetUuid = resolveTargetUuid(player.getServer(), economy, targetName);
        if (targetUuid == null) {
            source.sendFailure(Component.literal("\u00a7cPlayer not found: " + targetName));
            return 0;
        }

        double balance = economy.getBalance(player.serverLevel(), senderUuid);
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

        EconomyProvider economy = ModernShop.getEconomyProvider(player.serverLevel());
        if (!economy.subtractBalance(player.serverLevel(), senderUuid, pending.amount)) {
            source.sendFailure(Component.literal("\u00a7cYou don't have enough money. You have $" + String.format("%.2f", economy.getBalance(player.serverLevel(), senderUuid)) + " but need $" + String.format("%.2f", pending.amount) + "."));
            pendingPayments.remove(senderUuid);
            return 0;
        }

        UUID targetUuid = resolveTargetUuid(player.getServer(), economy, pending.targetName);
        if (targetUuid == null) {
            economy.addBalance(player.serverLevel(), senderUuid, pending.amount);
            source.sendFailure(Component.literal("\u00a7cCould not find player: " + pending.targetName));
            pendingPayments.remove(senderUuid);
            return 0;
        }

        economy.addBalance(player.serverLevel(), targetUuid, pending.amount);
        pendingPayments.remove(senderUuid);
        source.sendSuccess(() -> Component.literal("\u00a7aPaid $" + String.format("%.2f", pending.amount) + " to " + pending.targetName + "!"), false);
        source.sendSuccess(() -> Component.literal("\u00a77Your balance: $" + String.format("%.2f", economy.getBalance(player.serverLevel(), senderUuid))), false);

        ServerPlayer targetPlayer = player.getServer().getPlayerList().getPlayerByName(pending.targetName);
        if (targetPlayer != null) {
            targetPlayer.sendSystemMessage(Component.literal("\u00a7aYou received $" + String.format("%.2f", pending.amount) + " from " + player.getScoreboardName() + "!"));
            targetPlayer.sendSystemMessage(Component.literal("\u00a77Your balance: $" + String.format("%.2f", economy.getBalance(player.serverLevel(), targetUuid))));
        }

        return 1;
    }

    private static UUID resolveTargetUuid(MinecraftServer server, EconomyProvider economy, String name) {
        ServerPlayer onlinePlayer = server.getPlayerList().getPlayerByName(name);
        if (onlinePlayer != null) {
            economy.registerPlayer(server.overworld(), name, onlinePlayer.getUUID());
            return onlinePlayer.getUUID();
        }
        return economy.getUuidByName(server.overworld(), name);
    }

    /**
     * Show a preview of what /sellhand will sell and ask for confirmation.
     */
    private static void executeSellhandPreview(ServerPlayer player, CommandSourceStack source) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("\u00a7cYou are not holding any item."));
            return;
        }

        // Count matching items in inventory
        final int totalCount;
        {
            int count = 0;
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                ItemStack slot = player.getInventory().items.get(i);
                if (!slot.isEmpty() && slot.getItem() == held.getItem()) {
                    count += slot.getCount();
                }
            }
            totalCount = count;
        }

        if (totalCount == 0) {
            source.sendFailure(Component.literal("\u00a7cNo items found to sell."));
            return;
        }

        // Calculate sell price
        RecipeManager recipeManager = player.serverLevel().getRecipeManager();
        RegistryAccess registryAccess = player.serverLevel().registryAccess();
        double sellPricePerItem = ModernShop.getPriceEngine().getSellPrice(held, recipeManager, registryAccess);
        double totalEarnings = sellPricePerItem * (double) totalCount;

        // Register pending sell
        pendingSells.put(player.getUUID(), new PendingSell(System.currentTimeMillis()));

        // Send preview messages
        source.sendSuccess(() -> Component.literal("\u00a7e=== Sell Hand Preview ==="), false);
        source.sendSuccess(() -> Component.literal("\u00a77Item: \u00a7f" + held.getHoverName().getString()), false);
        source.sendSuccess(() -> Component.literal("\u00a77Count: \u00a7f" + totalCount + "x"), false);
        source.sendSuccess(() -> Component.literal("\u00a77Price: \u00a7a$" + String.format("%.2f", sellPricePerItem) + " each = \u00a7e$" + String.format("%.2f", totalEarnings)), false);
        source.sendSuccess(() -> Component.literal("\u00a7aType \u00a7f/sellhand confirm \u00a7ato confirm, or wait 30 seconds to cancel."), false);
    }

    /**
     * Execute the confirmed /sellhand sale.
     */
    private static int executeSellhandConfirm(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
            return 0;
        }

        UUID playerUuid = player.getUUID();
        PendingSell pending = pendingSells.get(playerUuid);

        if (pending == null || pending.isExpired()) {
            pendingSells.remove(playerUuid);
            source.sendFailure(Component.literal("\u00a7cNo pending sell to confirm. Use /sellhand first."));
            return 0;
        }

        // Execute the sell
        pendingSells.remove(playerUuid);
        return ServerPacketHandler.handleSellHand(player);
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

    private static class PendingSell {
        final long timestamp;

        PendingSell(long timestamp) {
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CONFIRM_TIMEOUT_MS;
        }
    }

    // ========== Search Commands ==========

    /**
     * /shop search all <name> — search all items across all categories.
     * Returns matching items via chat with name, category, buy/sell prices.
     */
    private static int executeSearchAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
            return 0;
        }

        String searchName = StringArgumentType.getString(context, "name").toLowerCase();
        ServerLevel level = player.serverLevel();
        RecipeManager recipeManager = level.getRecipeManager();
        RegistryAccess registryAccess = level.registryAccess();

        source.sendSuccess(() -> Component.literal("\u00a76\u00a7lSearch results for '\u00a7f" + searchName + "\u00a76\u00a7l':"), false);

        int totalFound = 0;
        List<ShopCategory> categories = ModernShop.getCategories();
        for (ShopCategory category : categories) {
            category.populateItemsServer(level);
            for (ItemStack item : category.getItems()) {
                String itemName = item.getHoverName().getString().toLowerCase();
                if (itemName.contains(searchName)) {
                    double buyPrice = ModernShop.getPriceEngine().getBuyPrice(item, recipeManager, registryAccess);
                    double sellPrice = ModernShop.getPriceEngine().getSellPrice(item, recipeManager, registryAccess);
                    source.sendSuccess(() -> Component.literal("\u00a7e" + item.getHoverName().getString()
                            + " \u00a77[" + category.getName() + "]"
                            + " \u00a7aBuy: $" + String.format("%.2f", buyPrice)
                            + " \u00a7cSell: $" + String.format("%.2f", sellPrice)), false);
                    totalFound++;
                    if (totalFound >= 50) {
                        source.sendSuccess(() -> Component.literal("\u00a77...showing first 50 results. Refine your search for more specific results."), false);
                        return 1;
                    }
                }
            }
        }

        if (totalFound == 0) {
            source.sendSuccess(() -> Component.literal("\u00a7cNo items found matching '" + searchName + "'."), false);
        } else {
            final int count = totalFound;
            source.sendSuccess(() -> Component.literal("\u00a7aFound " + count + " item(s)."), false);
        }
        return 1;
    }

    /**
     * /shop search category <category> <name> — search within a specific category.
     */
    private static int executeSearchCategory(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
            return 0;
        }

        String categoryName = StringArgumentType.getString(context, "category");
        String searchName = StringArgumentType.getString(context, "name").toLowerCase();
        ServerLevel level = player.serverLevel();
        RecipeManager recipeManager = level.getRecipeManager();
        RegistryAccess registryAccess = level.registryAccess();

        // Find the category
        ShopCategory targetCategory = null;
        List<ShopCategory> categories = ModernShop.getCategories();
        for (ShopCategory category : categories) {
            if (category.getName().toLowerCase().contains(categoryName.toLowerCase())) {
                targetCategory = category;
                break;
            }
        }

        if (targetCategory == null) {
            source.sendFailure(Component.literal("\u00a7cCategory not found: " + categoryName
                    + ". Use /shop to see available categories."));
            return 0;
        }

        final ShopCategory foundCategory = targetCategory;
        foundCategory.populateItemsServer(level);

        source.sendSuccess(() -> Component.literal("\u00a76\u00a7lSearch in \u00a7f" + foundCategory.getName()
                + "\u00a76\u00a7l for '\u00a7f" + searchName + "\u00a76\u00a7l':"), false);

        int totalFound = 0;
        for (ItemStack item : foundCategory.getItems()) {
            String itemName = item.getHoverName().getString().toLowerCase();
            if (itemName.contains(searchName)) {
                double buyPrice = ModernShop.getPriceEngine().getBuyPrice(item, recipeManager, registryAccess);
                double sellPrice = ModernShop.getPriceEngine().getSellPrice(item, recipeManager, registryAccess);
                source.sendSuccess(() -> Component.literal("\u00a7e" + item.getHoverName().getString()
                        + " \u00a7aBuy: $" + String.format("%.2f", buyPrice)
                        + " \u00a7cSell: $" + String.format("%.2f", sellPrice)), false);
                totalFound++;
                if (totalFound >= 50) {
                    source.sendSuccess(() -> Component.literal("\u00a77...showing first 50 results. Refine your search for more specific results."), false);
                    return 1;
                }
            }
        }

        if (totalFound == 0) {
            source.sendSuccess(() -> Component.literal("\u00a7cNo items found matching '" + searchName + "' in " + foundCategory.getName() + "."), false);
        } else {
            final int count = totalFound;
            source.sendSuccess(() -> Component.literal("\u00a7aFound " + count + " item(s) in " + foundCategory.getName() + "."), false);
        }
        return 1;
    }
}
