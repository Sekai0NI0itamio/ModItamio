package asd.itamio.worldshop;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public class CommandShopReset extends CommandBase {
    @Override
    public String getName() {
        return "shopreset";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/shopreset";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return sender.canUseCommand(2, getName());
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        WorldShop.getPriceEngine().clearCache();
        WorldShop.buildShopCategories();
        sender.sendMessage(new TextComponentString("\u00a7aShop prices have been reset and recalculated!"));
        WorldShop.LOGGER.info("Shop prices reset by {}", sender.getName());
    }
}
