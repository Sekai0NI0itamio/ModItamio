package asd.itamio.buildabridge;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

/**
 * Defines bridge presets. Each preset specifies the road block, rail block (if any),
 * and support beam block.
 */
public enum BridgePresets {
    RAILROAD("railroad", Blocks.COBBLESTONE.getDefaultState(), Blocks.RAIL.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());

    private final String name;
    private final IBlockState roadBlock;
    private final IBlockState railBlock; // null means no rail
    private final IBlockState beamBlock;

    BridgePresets(String name, IBlockState roadBlock, IBlockState railBlock, IBlockState beamBlock) {
        this.name = name;
        this.roadBlock = roadBlock;
        this.railBlock = railBlock;
        this.beamBlock = beamBlock;
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
}
