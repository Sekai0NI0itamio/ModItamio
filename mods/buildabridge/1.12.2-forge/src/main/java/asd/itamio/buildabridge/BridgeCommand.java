package asd.itamio.buildabridge;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

public class BridgeCommand extends CommandBase {

    @Override
    public String getName() {
        return "bridge";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/bridge <length> <preset> [x y z]";
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

        // Parse length
        if (args.length < 1) {
            sender.sendMessage(new TextComponentString("\u00a7cUsage: /bridge <length> <preset> [x y z]"));
            sender.sendMessage(new TextComponentString("\u00a77Available presets: railroad"));
            return;
        }

        int length;
        try {
            length = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(new TextComponentString("\u00a7cInvalid length: " + args[0]));
            return;
        }

        if (length <= 0) {
            sender.sendMessage(new TextComponentString("\u00a7cLength must be a positive number."));
            return;
        }
        if (length > 10000) {
            sender.sendMessage(new TextComponentString("\u00a7cLength cannot exceed 10000."));
            return;
        }

        // Parse preset
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString("\u00a7cUsage: /bridge <length> <preset> [x y z]"));
            sender.sendMessage(new TextComponentString("\u00a77Available presets: railroad"));
            return;
        }

        String presetName = args[1];
        BridgePresets preset = BridgePresets.fromName(presetName);
        if (preset == null) {
            sender.sendMessage(new TextComponentString("\u00a7cUnknown preset: " + presetName));
            sender.sendMessage(new TextComponentString("\u00a77Available presets: railroad"));
            return;
        }

        // Parse starting position
        BlockPos startPos;
        if (args.length >= 5) {
            try {
                int x = Integer.parseInt(args[2]);
                int y = Integer.parseInt(args[3]);
                int z = Integer.parseInt(args[4]);
                startPos = new BlockPos(x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage(new TextComponentString("\u00a7cInvalid coordinates: " + args[2] + " " + args[3] + " " + args[4]));
                return;
            }
        } else {
            startPos = player.getPosition();
        }

        // Determine direction from player yaw
        float yaw = player.rotationYaw;
        // Normalize yaw to 0-360
        while (yaw < 0) yaw += 360;
        while (yaw >= 360) yaw -= 360;

        EnumFacing direction = getCardinalDirection(yaw);
        if (direction == null) {
            sender.sendMessage(new TextComponentString("\u00a7cCannot determine viewing direction. Look horizontally."));
            return;
        }

        // Build the bridge
        try {
            BridgeBuilder.buildBridge(player.world, startPos, direction, length, preset);
            sender.sendMessage(new TextComponentString(
                "\u00a7aBuilt a \u00a7f" + length + "\u00a7a-block \u00a7f" + preset.getName() +
                "\u00a7a bridge going \u00a7f" + direction.getName() + "\u00a7a from \u00a7f" +
                startPos.getX() + " " + startPos.getY() + " " + startPos.getZ()));
        } catch (Exception e) {
            Buildabridge.LOGGER.error("[MODAPP-ERROR] Failed to build bridge: {}", e.getMessage(), e);
            sender.sendMessage(new TextComponentString("\u00a7cFailed to build bridge: " + e.getMessage()));
        }
    }

    /**
     * Convert yaw angle to cardinal direction.
     * Minecraft yaw: 0=south, 90=west, 180=north, 270=east
     */
    private static EnumFacing getCardinalDirection(float yaw) {
        // Add 45 degrees so the boundaries fall between directions
        int index = (int) Math.floor((yaw + 45.0) / 90.0) % 4;
        switch (index) {
            case 0: return EnumFacing.SOUTH;   // 315-45 degrees
            case 1: return EnumFacing.WEST;    // 45-135 degrees
            case 2: return EnumFacing.NORTH;   // 135-225 degrees
            case 3: return EnumFacing.EAST;    // 225-315 degrees
            default: return null;
        }
    }
}
