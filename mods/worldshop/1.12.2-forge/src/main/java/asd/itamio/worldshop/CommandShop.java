package asd.itamio.worldshop;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.List;

public class CommandShop extends CommandBase {
    private static final int SEARCH_RESULT_LIMIT = 50;

    @Override
    public String getName() {
        return "shop";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/shop [player|search all <name>|search category <category> <name>]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString("\u00a7cOnly players can use this command."));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;

        // /shop player — open the shop in player mode
        if (args.length > 0 && args[0].equalsIgnoreCase("player")) {
            WorldShop.NETWORK.sendTo(ShopPacket.openPlayerShop(), player);
            return;
        }

        // /shop search all <name> — search all items across all categories
        if (args.length >= 2 && args[0].equalsIgnoreCase("search") && args[1].equalsIgnoreCase("all")) {
            String searchName = joinArgs(args, 2).toLowerCase();
            executeSearchAll(sender, searchName);
            return;
        }

        // /shop search category <category> <name> — search within a specific category
        if (args.length >= 3 && args[0].equalsIgnoreCase("search") && args[1].equalsIgnoreCase("category")) {
            String categoryName = args[2];
            String searchName = joinArgs(args, 3).toLowerCase();
            executeSearchCategory(sender, categoryName, searchName);
            return;
        }

        // /shop search with no/invalid sub-arguments — show usage
        if (args.length > 0 && args[0].equalsIgnoreCase("search")) {
            sender.sendMessage(new TextComponentString("\u00a7cUsage: /shop search all <name> | /shop search category <category> <name>"));
            return;
        }

        // /shop — open the shop
        WorldShop.NETWORK.sendTo(ShopPacket.openShop(), player);
    }

    /**
     * /shop search all <name> — search all items across all categories.
     * Returns matching items via chat with name, category, buy/sell prices.
     */
    private void executeSearchAll(ICommandSender sender, String searchName) {
        if (searchName.isEmpty()) {
            sender.sendMessage(new TextComponentString("\u00a7cUsage: /shop search all <name>"));
            return;
        }

        sender.sendMessage(new TextComponentString("\u00a76\u00a7lSearch results for '\u00a7f" + searchName + "\u00a76\u00a7l':"));

        PriceEngine priceEngine = WorldShop.getPriceEngine();
        List<ShopCategory> categories = WorldShop.getCategories();
        int totalFound = 0;

        for (ShopCategory category : categories) {
            for (ItemStack item : category.getItems()) {
                if (item == null || item.isEmpty()) continue;
                String itemName = item.getDisplayName().toLowerCase();
                if (!itemName.contains(searchName)) continue;

                double buyPrice = priceEngine.getBuyPrice(item);
                double sellPrice = priceEngine.getSellPrice(item);
                sender.sendMessage(new TextComponentString("\u00a7e" + item.getDisplayName()
                        + " \u00a77[" + category.getName() + "]"
                        + " \u00a7aBuy: $" + String.format("%.2f", buyPrice)
                        + " \u00a7cSell: $" + String.format("%.2f", sellPrice)));
                totalFound++;
                if (totalFound >= SEARCH_RESULT_LIMIT) {
                    sender.sendMessage(new TextComponentString("\u00a77...showing first " + SEARCH_RESULT_LIMIT + " results. Refine your search for more specific results."));
                    return;
                }
            }
        }

        if (totalFound == 0) {
            sender.sendMessage(new TextComponentString("\u00a7cNo items found matching '" + searchName + "'."));
        } else {
            sender.sendMessage(new TextComponentString("\u00a7aFound " + totalFound + " item(s)."));
        }
    }

    /**
     * /shop search category <category> <name> — search within a specific category.
     */
    private void executeSearchCategory(ICommandSender sender, String categoryName, String searchName) {
        if (searchName.isEmpty()) {
            sender.sendMessage(new TextComponentString("\u00a7cUsage: /shop search category <category> <name>"));
            return;
        }

        // Find the category by fuzzy name match
        ShopCategory targetCategory = null;
        List<ShopCategory> categories = WorldShop.getCategories();
        for (ShopCategory category : categories) {
            if (category.getName().toLowerCase().contains(categoryName.toLowerCase())) {
                targetCategory = category;
                break;
            }
        }

        if (targetCategory == null) {
            sender.sendMessage(new TextComponentString("\u00a7cCategory not found: " + categoryName
                    + ". Use /shop to see available categories."));
            return;
        }

        sender.sendMessage(new TextComponentString("\u00a76\u00a7lSearch in \u00a7f" + targetCategory.getName()
                + "\u00a76\u00a7l for '\u00a7f" + searchName + "\u00a76\u00a7l':"));

        PriceEngine priceEngine = WorldShop.getPriceEngine();
        int totalFound = 0;

        for (ItemStack item : targetCategory.getItems()) {
            if (item == null || item.isEmpty()) continue;
            String itemName = item.getDisplayName().toLowerCase();
            if (!itemName.contains(searchName)) continue;

            double buyPrice = priceEngine.getBuyPrice(item);
            double sellPrice = priceEngine.getSellPrice(item);
            sender.sendMessage(new TextComponentString("\u00a7e" + item.getDisplayName()
                    + " \u00a7aBuy: $" + String.format("%.2f", buyPrice)
                    + " \u00a7cSell: $" + String.format("%.2f", sellPrice)));
            totalFound++;
            if (totalFound >= SEARCH_RESULT_LIMIT) {
                sender.sendMessage(new TextComponentString("\u00a77...showing first " + SEARCH_RESULT_LIMIT + " results. Refine your search for more specific results."));
                return;
            }
        }

        if (totalFound == 0) {
            sender.sendMessage(new TextComponentString("\u00a7cNo items found matching '" + searchName + "' in " + targetCategory.getName() + "."));
        } else {
            sender.sendMessage(new TextComponentString("\u00a7aFound " + totalFound + " item(s) in " + targetCategory.getName() + "."));
        }
    }

    /** Join args[startIndex..] with spaces to support multi-word search terms. */
    private String joinArgs(String[] args, int startIndex) {
        if (startIndex >= args.length) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
