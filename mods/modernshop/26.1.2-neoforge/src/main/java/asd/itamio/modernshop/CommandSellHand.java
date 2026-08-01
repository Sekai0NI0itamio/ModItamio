package asd.itamio.modernshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CommandSellHand {
    private static final Map<UUID, PendingSell> pendingSells = new HashMap<>();
    private static final long CONFIRM_TIMEOUT_MS = 30000L;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sellhand")
                .executes(ctx -> execute(ctx))
                .then(Commands.literal("confirm")
                        .executes(ctx -> executeConfirm(ctx))
                )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
            return 0;
        }
        ShopConfig config = ModernShop.getShopConfig();
        if (config != null && config.isSellhandConfirmation()) {
            executeSellhandPreview(player, source);
        } else {
            ShopPacketHandler.handleSellHand(player);
        }
        return 1;
    }

    private static int executeConfirm(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
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
        pendingSells.remove(playerUuid);
        return ShopPacketHandler.handleSellHand(player);
    }

    private static void executeSellhandPreview(ServerPlayer player, CommandSourceStack source) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("\u00a7cYou are not holding any item."));
            return;
        }

        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (!slot.isEmpty() && slot.getItem() == held.getItem()) {
                count += slot.getCount();
            }
        }

        if (count == 0) {
            source.sendFailure(Component.literal("\u00a7cNo items found to sell."));
            return;
        }

        RecipeManager recipeManager = player.level().recipeAccess();
        RegistryAccess registryAccess = player.level().registryAccess();
        double sellPricePerItem = ModernShop.getPriceEngine().getSellPrice(held, recipeManager, registryAccess);
        double totalEarnings = sellPricePerItem * (double) count;

        pendingSells.put(player.getUUID(), new PendingSell(System.currentTimeMillis()));

        String itemMsg = "\u00a77Item: \u00a7f" + held.getHoverName().getString();
        String countMsg = "\u00a77Count: \u00a7f" + count + "x";
        String priceMsg = "\u00a77Price: \u00a7a$" + String.format("%.2f", sellPricePerItem) + " each = \u00a7e$" + String.format("%.2f", totalEarnings);

        source.sendSuccess(() -> Component.literal("\u00a7e=== Sell Hand Preview ==="), false);
        source.sendSuccess(() -> Component.literal(itemMsg), false);
        source.sendSuccess(() -> Component.literal(countMsg), false);
        source.sendSuccess(() -> Component.literal(priceMsg), false);
        source.sendSuccess(() -> Component.literal("\u00a7aType \u00a7f/sellhand confirm \u00a7ato confirm, or wait 30 seconds to cancel."), false);
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
}
