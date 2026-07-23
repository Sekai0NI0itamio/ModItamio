package asd.itamio.worldshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class CommandShop {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shop")
                .executes(ctx -> execute(ctx))
                .then(Commands.literal("player")
                        .executes(ctx -> executePlayer(ctx))
                )
                .then(Commands.literal("player-no-mod")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> executePlayerNoMod(ctx))
                )
                .then(Commands.literal("search")
                        .then(Commands.literal("all")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> executeSearchAll(ctx))
                                )
                        )
                        .then(Commands.literal("category")
                                .then(Commands.argument("category", StringArgumentType.string())
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> executeSearchCategory(ctx))
                                        )
                                )
                        )
                )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
            return 0;
        }
        PacketDistributor.sendToPlayer(player, ShopPacket.openShop());
        return 1;
    }

    private static int executePlayer(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
            return 0;
        }
        PacketDistributor.sendToPlayer(player, ShopPacket.openPlayerShop());
        return 1;
    }

    private static int executePlayerNoMod(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
            return 0;
        }
        VanillaShopContainer.open(player);
        return 1;
    }

    private static int executeSearchAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
            return 0;
        }

        String searchName = StringArgumentType.getString(ctx, "name").toLowerCase();
        ServerLevel level = player.serverLevel();
        RecipeManager recipeManager = level.getRecipeManager();
        RegistryAccess registryAccess = level.registryAccess();

        source.sendSuccess(() -> Component.literal("\u00a76\u00a7lSearch results for '\u00a7f" + searchName + "\u00a76\u00a7l':"), false);

        int totalFound = 0;
        List<ShopCategory> categories = WorldShop.getCategories();
        for (ShopCategory category : categories) {
            category.populateItemsServer(level);
            for (ItemStack item : category.getItems()) {
                String itemName = item.getHoverName().getString().toLowerCase();
                if (itemName.contains(searchName)) {
                    double buyPrice = WorldShop.getPriceEngine().getBuyPrice(item, recipeManager, registryAccess);
                    double sellPrice = WorldShop.getPriceEngine().getSellPrice(item, recipeManager, registryAccess);
                    String display = "\u00a7e" + item.getHoverName().getString()
                            + " \u00a77[" + category.getName() + "]"
                            + " \u00a7aBuy: $" + String.format("%.2f", buyPrice)
                            + " \u00a7cSell: $" + String.format("%.2f", sellPrice);
                    source.sendSuccess(() -> Component.literal(display), false);
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

    private static int executeSearchCategory(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
            return 0;
        }

        String categoryName = StringArgumentType.getString(ctx, "category");
        String searchName = StringArgumentType.getString(ctx, "name").toLowerCase();
        ServerLevel level = player.serverLevel();
        RecipeManager recipeManager = level.getRecipeManager();
        RegistryAccess registryAccess = level.registryAccess();

        ShopCategory targetCategory = null;
        List<ShopCategory> categories = WorldShop.getCategories();
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
                double buyPrice = WorldShop.getPriceEngine().getBuyPrice(item, recipeManager, registryAccess);
                double sellPrice = WorldShop.getPriceEngine().getSellPrice(item, recipeManager, registryAccess);
                String display = "\u00a7e" + item.getHoverName().getString()
                        + " \u00a7aBuy: $" + String.format("%.2f", buyPrice)
                        + " \u00a7cSell: $" + String.format("%.2f", sellPrice);
                source.sendSuccess(() -> Component.literal(display), false);
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
