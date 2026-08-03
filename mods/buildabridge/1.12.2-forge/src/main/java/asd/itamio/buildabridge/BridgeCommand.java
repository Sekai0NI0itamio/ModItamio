package asd.itamio.buildabridge;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class BridgeCommand extends CommandBase {

    @Override
    public String getName() {
        return "bridge";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/bridge <length> <preset> [x y z] OR /bridge custom <length> <roadBlock> <beamBlock> [x y z]";
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

        if (args.length < 1) {
            sendUsage(sender);
            return;
        }

        // Check for custom template subcommand
        if (args[0].equalsIgnoreCase("custom")) {
            executeCustom(server, sender, player, args);
            return;
        }

        // Standard preset bridge: /bridge <length> <preset> [x y z]
        executePreset(server, sender, player, args);
    }

    /**
     * Execute a standard preset bridge command.
     */
    private void executePreset(MinecraftServer server, ICommandSender sender, EntityPlayerMP player, String[] args) {
        // Parse length
        int length = parseLength(sender, args, 0);
        if (length < 0) return;

        // Parse preset
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString("\u00a7cUsage: /bridge <length> <preset> [x y z]"));
            sender.sendMessage(new TextComponentString("\u00a77Available presets: " + BridgePresets.getPresetList()));
            return;
        }

        String presetName = args[1];
        BridgePresets preset = BridgePresets.fromName(presetName);
        if (preset == null) {
            sender.sendMessage(new TextComponentString("\u00a7cUnknown preset: " + presetName));
            sender.sendMessage(new TextComponentString("\u00a77Available presets: " + BridgePresets.getPresetList()));
            return;
        }

        // Parse starting position
        BlockPos startPos = parsePosition(sender, player, args, 2);
        if (startPos == null) return;

        // Determine direction from player yaw
        EnumFacing direction = getPlayerDirection(player);
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
     * Execute a custom template bridge command.
     * Usage: /bridge custom <length> <roadBlock> <beamBlock> [x y z]
     */
    private void executeCustom(MinecraftServer server, ICommandSender sender, EntityPlayerMP player, String[] args) {
        // args[0] = "custom", args[1] = length, args[2] = roadBlock, args[3] = beamBlock, args[4..6] = x y z
        if (args.length < 4) {
            sender.sendMessage(new TextComponentString("\u00a7cUsage: /bridge custom <length> <roadBlock> <beamBlock> [x y z]"));
            sender.sendMessage(new TextComponentString("\u00a77Example: /bridge custom 50 minecraft:stonebrick minecraft:planks"));
            return;
        }

        // Parse length
        int length = parseLength(sender, args, 1);
        if (length < 0) return;

        // Parse road block
        IBlockState roadState = parseBlock(sender, args[2]);
        if (roadState == null) return;

        // Parse beam block
        IBlockState beamState = parseBlock(sender, args[3]);
        if (beamState == null) return;

        // Parse starting position
        BlockPos startPos = parsePosition(sender, player, args, 4);
        if (startPos == null) return;

        // Determine direction from player yaw
        EnumFacing direction = getPlayerDirection(player);
        if (direction == null) {
            sender.sendMessage(new TextComponentString("\u00a7cCannot determine viewing direction. Look horizontally."));
            return;
        }

        // Build the bridge
        try {
            BridgeBuilder.buildBridge(player.world, startPos, direction, length,
                    roadState, null, beamState, false, false);
            sender.sendMessage(new TextComponentString(
                "\u00a7aBuilt a \u00a7f" + length + "\u00a7a-block \u00a7fcustom" +
                "\u00a7a bridge going \u00a7f" + direction.getName() + "\u00a7a from \u00a7f" +
                startPos.getX() + " " + startPos.getY() + " " + startPos.getZ()));
        } catch (Exception e) {
            Buildabridge.LOGGER.error("[MODAPP-ERROR] Failed to build custom bridge: {}", e.getMessage(), e);
            sender.sendMessage(new TextComponentString("\u00a7cFailed to build bridge: " + e.getMessage()));
        }
    }

    /**
     * Parse and validate a length argument. Returns -1 on failure (error already sent).
     */
    private int parseLength(ICommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            sender.sendMessage(new TextComponentString("\u00a7cMissing length argument."));
            return -1;
        }
        int length;
        try {
            length = Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            sender.sendMessage(new TextComponentString("\u00a7cInvalid length: " + args[index]));
            return -1;
        }
        if (length <= 0) {
            sender.sendMessage(new TextComponentString("\u00a7cLength must be a positive number."));
            return -1;
        }
        if (length > 10000) {
            sender.sendMessage(new TextComponentString("\u00a7cLength cannot exceed 10000."));
            return -1;
        }
        return length;
    }

    /**
     * Parse a block from a resource location string (e.g. "minecraft:cobblestone" or "cobblestone").
     * Returns null on failure (error already sent).
     */
    private IBlockState parseBlock(ICommandSender sender, String blockName) {
        // Add minecraft: prefix if missing
        if (!blockName.contains(":")) {
            blockName = "minecraft:" + blockName;
        }
        ResourceLocation location = new ResourceLocation(blockName);
        Block block = ForgeRegistries.BLOCKS.getValue(location);
        if (block == null || block == Blocks.AIR) {
            sender.sendMessage(new TextComponentString("\u00a7cUnknown block: " + blockName));
            return null;
        }
        return block.getDefaultState();
    }

    /**
     * Parse position from args starting at the given index.
     * If not enough args, defaults to one block below the player's feet.
     * Returns null on parse failure (error already sent).
     */
    private BlockPos parsePosition(ICommandSender sender, EntityPlayerMP player, String[] args, int startIndex) {
        if (args.length >= startIndex + 3) {
            try {
                int x = Integer.parseInt(args[startIndex]);
                int y = Integer.parseInt(args[startIndex + 1]);
                int z = Integer.parseInt(args[startIndex + 2]);
                return new BlockPos(x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage(new TextComponentString("\u00a7cInvalid coordinates: " + args[startIndex] + " " + args[startIndex + 1] + " " + args[startIndex + 2]));
                return null;
            }
        } else {
            // Default: one block below the player so the bridge isn't inside them
            return player.getPosition().down();
        }
    }

    /**
     * Get the cardinal direction the player is facing.
     */
    private EnumFacing getPlayerDirection(EntityPlayerMP player) {
        float yaw = player.rotationYaw;
        while (yaw < 0) yaw += 360;
        while (yaw >= 360) yaw -= 360;
        return getCardinalDirection(yaw);
    }

    private void sendUsage(ICommandSender sender) {
        sender.sendMessage(new TextComponentString("\u00a7cUsage: /bridge <length> <preset> [x y z]"));
        sender.sendMessage(new TextComponentString("\u00a7c   OR: /bridge custom <length> <roadBlock> <beamBlock> [x y z]"));
        sender.sendMessage(new TextComponentString("\u00a77Available presets: " + BridgePresets.getPresetList()));
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
