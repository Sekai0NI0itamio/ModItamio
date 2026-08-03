package asd.itamio.buildabridge;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

/**
 * Defines bridge presets. Each preset specifies the road block, rail block (if any),
 * support beam block, and whether torch platforms are placed on pillars.
 */
public enum BridgePresets {
    RAILROAD("railroad",
            Blocks.COBBLESTONE.getDefaultState(),
            Blocks.RAIL.getDefaultState(),
            Blocks.COBBLESTONE.getDefaultState(),
            true),

    PLAIN("plain",
            Blocks.COBBLESTONE.getDefaultState(),
            null,
            Blocks.COBBLESTONE.getDefaultState(),
            false),

    STONE_BRICK("stone_brick",
            Blocks.STONEBRICK.getDefaultState(),
            null,
            Blocks.STONEBRICK.getDefaultState(),
            false),

    WOODEN("wooden",
            Blocks.PLANKS.getDefaultState(),
            null,
            Blocks.PLANKS.getDefaultState(),
            false);

    private final String name;
    private final IBlockState roadBlock;
    private final IBlockState railBlock; // null means no rail
    private final IBlockState beamBlock;
    private final boolean torchPlatform;

    BridgePresets(String name, IBlockState roadBlock, IBlockState railBlock, IBlockState beamBlock, boolean torchPlatform) {
        this.name = name;
        this.roadBlock = roadBlock;
        this.railBlock = railBlock;
        this.beamBlock = beamBlock;
        this.torchPlatform = torchPlatform;
    }

    public String getName() {
        return name;
    }

    public IBlockState getRoadBlock() {
        return roadBlock;
    }

    public IBlockState getRailBlock() {
        return railBlock;
    }

    public IBlockState getBeamBlock() {
        return beamBlock;
    }

    public boolean hasRail() {
        return railBlock != null;
    }

    public boolean hasTorchPlatform() {
        return torchPlatform;
    }

    /**
     * Look up a preset by name (case-insensitive).
     * Returns null if not found.
     */
    public static BridgePresets fromName(String name) {
        for (BridgePresets preset : values()) {
            if (preset.name.equalsIgnoreCase(name)) {
                return preset;
            }
        }
        return null;
    }

    /**
     * Get a comma-separated list of all preset names.
     */
    public static String getPresetList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values().length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(values()[i].name);
        }
        return sb.toString();
    }
}
