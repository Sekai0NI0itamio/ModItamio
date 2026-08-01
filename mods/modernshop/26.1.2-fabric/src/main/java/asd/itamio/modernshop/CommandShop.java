package asd.itamio.modernshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

/**
 * /shop - Opens the shop GUI (not available in this version - shows category list in chat)
 * /shop search <query> - Search categories
 * /shop list <categoryId> [search] - List items in a category (optionally filtered by search)
 */
public class CommandShop {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shop")
                .executes(CommandShop::execute)
                .then(Commands.literal("search")
                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                .executes(ctx -> executeSearch(ctx, StringArgumentType.getString(ctx, "query")))))
                .then(Commands.literal("list")
                        .then(Commands.argument("category", IntegerArgumentType.integer(0))
                                .executes(ctx -> executeList(ctx, IntegerArgumentType.getInteger(ctx, "category"), null))
                                .then(Commands.argument("search", StringArgumentType.greedyString())
                                        .executes(ctx -> executeList(ctx, IntegerArgumentType.getInteger(ctx, "category"),
                                                StringArgumentType.getString(ctx, "search"))))))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        List<ShopCategory> categories = ModernShop.getCategories();
        if (categories.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cNo shop categories available."));
            return 0;
        }

        // Display available categories in chat
        player.sendSystemMessage(Component.literal("§6§l=== Modern Shop - Categories ==="));
        player.sendSystemMessage(Component.literal("§7Use §e/shop list <id> [search] §7to browse items"));
        player.sendSystemMessage(Component.literal("§7Use §e/shop search <query> §7to search categories"));
        player.sendSystemMessage(Component.literal("§7Use §e/shop buy <category> <item> <quantity> §7to purchase"));
        player.sendSystemMessage(Component.literal(""));

        int i = 0;
        for (ShopCategory cat : categories) {
            player.sendSystemMessage(Component.literal("§e" + i + ". §f" + cat.getName() + " §7(" + cat.getItems().size() + " items)"));
            i++;
        }
        return 1;
    }

    private static int executeSearch(CommandContext<CommandSourceStack> ctx, String query) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        String lowerQuery = query.toLowerCase();
        List<ShopCategory> allCategories = ModernShop.getCategories();
        List<ShopCategory> filtered = allCategories.stream()
                .filter(cat -> cat.getName().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cNo categories found matching: " + query));
            return 0;
        }

        player.sendSystemMessage(Component.literal("§6§l=== Shop Search: " + query + " ==="));
        int i = 0;
        for (ShopCategory cat : filtered) {
            int originalIndex = allCategories.indexOf(cat);
            player.sendSystemMessage(Component.literal("§e" + originalIndex + ". §f" + cat.getName() + " §7(" + cat.getItems().size() + " items)"));
            i++;
        }
        player.sendSystemMessage(Component.literal("§7Found " + i + " categories"));
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx, int categoryIndex, String searchQuery) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        List<ShopCategory> categories = ModernShop.getCategories();
        if (categoryIndex < 0 || categoryIndex >= categories.size()) {
            player.sendSystemMessage(Component.literal("§cInvalid category index. Use /shop to see available categories."));
            return 0;
        }

        ShopCategory category = categories.get(categoryIndex);
        List<ItemStack> items = category.getItems();

        // Filter by search query if provided
        List<ItemStack> filteredItems;
        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            String lowerQuery = searchQuery.toLowerCase();
            filteredItems = items.stream()
                    .filter(item -> item.getDisplayName().getString().toLowerCase().contains(lowerQuery))
                    .collect(Collectors.toList());
        } else {
            filteredItems = items;
        }

        if (filteredItems.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cNo items found in category '" + category.getName() + "'" +
                    (searchQuery != null ? " matching: " + searchQuery : "")));
            return 0;
        }

        PriceEngine priceEngine = ModernShop.getPriceEngine();
        player.sendSystemMessage(Component.literal("§6§l=== " + category.getName() + " ==="));
        player.sendSystemMessage(Component.literal("§7Use §e/shop buy " + categoryIndex + " <itemId> <quantity> §7to purchase"));

        int maxShow = Math.min(filteredItems.size(), 20);
        for (int i = 0; i < maxShow; i++) {
            ItemStack item = filteredItems.get(i);
            int originalIndex = items.indexOf(item);
            double buyPrice = priceEngine.getBuyPrice(item);
            player.sendSystemMessage(Component.literal("§e" + originalIndex + ". §f" + item.getDisplayName().getString()
                    + " §7- §a$" + String.format("%.2f", buyPrice) + " each"));
        }

        if (filteredItems.size() > maxShow) {
            player.sendSystemMessage(Component.literal("§7... and " + (filteredItems.size() - maxShow) + " more items"));
        }
        return 1;
    }
}
