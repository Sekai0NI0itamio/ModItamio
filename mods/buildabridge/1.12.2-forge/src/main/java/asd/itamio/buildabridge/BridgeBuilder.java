package asd.itamio.buildabridge;

import net.minecraft.block.BlockRailBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Core bridge building logic.
 * Builds a bridge with road, optional rail, and periodic support beams.
 */
public class BridgeBuilder {

    private static final Random RANDOM = new Random();
    private static final int MIN_BEAM_INTERVAL = 15;
    private static final int MAX_BEAM_INTERVAL = 20;
    private static final int BEAM_GROUND_PENETRATION = 5;
    private static final int MAX_BEAM_DEPTH = 256; // safety limit

    /**
     * Build a bridge in the given direction.
     *
     * @param world     the world
     * @param startPos  starting position (the first road block goes here)
     * @param direction the horizontal direction to extend
     * @param length    total bridge length in blocks
     * @param preset    the bridge preset to use
     */
    public static void buildBridge(World world, BlockPos startPos, EnumFacing direction, int length, BridgePresets preset) {
        IBlockState roadState = preset.getRoadBlock();
        IBlockState railState = preset.getRailBlock();
        IBlockState beamState = preset.getBeamBlock();

        // Determine the rail direction property based on bridge direction
        BlockRailBase.EnumRailDirection railDirection = getRailDirection(direction);

        int nextBeamAt = 0; // first beam at block 0

        for (int i = 0; i < length; i++) {
            BlockPos roadPos = startPos.offset(direction, i);

            // Place road block
            world.setBlockState(roadPos, roadState, 2);

            // Place rail block on top of road (if preset has one)
            if (preset.hasRail() && railState != null) {
                BlockPos railPos = roadPos.up();
                IBlockState placedRail = railState;
                // Set rail direction if the block is a BlockRail
                if (railState.getBlock() == Blocks.RAIL) {
                    placedRail = railState.withProperty(
                        net.minecraft.block.BlockRail.SHAPE,
                        railDirection
                    );
                }
                world.setBlockState(railPos, placedRail, 2);
            }

            // Support beam check
            if (i == nextBeamAt) {
                buildSupportBeam(world, roadPos, beamState);
                nextBeamAt = i + MIN_BEAM_INTERVAL + RANDOM.nextInt(MAX_BEAM_INTERVAL - MIN_BEAM_INTERVAL + 1);
            }
        }
    }

    /**
     * Build a support beam downward from the given position.
     * Phase 1: Descend through air/water/lava/glass, replacing with beam blocks,
     *          until a solid block is hit.
     * Phase 2: Replace the solid block and extend BEAM_GROUND_PENETRATION blocks deeper.
     */
    private static void buildSupportBeam(World world, BlockPos topPos, IBlockState beamState) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(topPos);
        int depth = 0;

        // Phase 1: descend through non-solid blocks
        while (depth < MAX_BEAM_DEPTH) {
            pos.setY(pos.getY() - 1);
            depth++;

            IBlockState currentState = world.getBlockState(pos);

            if (isReplaceable(currentState)) {
                world.setBlockState(pos, beamState, 2);
            } else {
                // Hit a solid block — replace it and continue to Phase 2
                world.setBlockState(pos, beamState, 2);
                break;
            }
        }

        // Phase 2: go BEAM_GROUND_PENETRATION blocks deeper into solid ground
        for (int i = 0; i < BEAM_GROUND_PENETRATION && depth < MAX_BEAM_DEPTH; i++) {
            pos.setY(pos.getY() - 1);
            depth++;
            world.setBlockState(pos, beamState, 2);
        }
    }

    /**
     * Check if a block can be replaced by the support beam (air, water, lava, glass).
     */
    private static boolean isReplaceable(IBlockState state) {
        return state.getBlock() == Blocks.AIR
                || state.getBlock() == Blocks.WATER
                || state.getBlock() == Blocks.FLOWING_WATER
                || state.getBlock() == Blocks.LAVA
                || state.getBlock() == Blocks.FLOWING_LAVA
                || state.getBlock() == Blocks.GLASS;
    }

    /**
     * Get the rail direction for the bridge direction.
     */
    private static BlockRailBase.EnumRailDirection getRailDirection(EnumFacing facing) {
        switch (facing) {
            case NORTH:
            case SOUTH:
                return BlockRailBase.EnumRailDirection.NORTH_SOUTH;
            case EAST:
            case WEST:
                return BlockRailBase.EnumRailDirection.EAST_WEST;
            default:
                return BlockRailBase.EnumRailDirection.NORTH_SOUTH;
        }
    }
}
