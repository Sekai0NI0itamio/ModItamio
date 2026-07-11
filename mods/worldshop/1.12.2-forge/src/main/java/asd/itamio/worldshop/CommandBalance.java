package asd.itamio.worldshop;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

public class CommandBalance extends CommandBase {
    @Override
    public String getName() {
        return "balance";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/balance";
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
        EconomyData economy = EconomyData.get(player.getEntityWorld());
        double balance = economy.getBalance(player.getUniqueID());
        player.sendMessage(new TextComponentString("\u00a7aBalance: $" + String.format("%.2f", balance)));
    }
}
