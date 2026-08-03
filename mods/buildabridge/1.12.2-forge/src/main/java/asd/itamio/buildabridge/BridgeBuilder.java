package asd.itamio.buildabridge;

import net.minecraft.block.BlockRailBase;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Core bridge building logic.
 * Builds a bridge with road, optional rail, periodic support beams,
 * headroom clearing, and optional torch platforms on pillars.
 */
public class BridgeBuilder {

    private static final Random RANDOM = new Random();
    private static final int MIN_BEAM_INTERVAL = 25;
    private static final int MAX_BEAM_INTERVAL = 30;
    private static final int BEAM_GROUND_PENETRATION = 5;
    private static final int MAX_BEAM_DEPTH = 256; // safety limit
    private static final int HEADROOM_BLOCKS = 2;  // blocks to clear above the top bridge block

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
        buildBridge(world, startPos, direction, length,
                preset.getRoadBlock(), preset.getRailBlock(), preset.getBeamBlock(),
                preset.hasRail(), preset.hasTorchPlatform());
    }

    /**
     * Build a bridge with custom block types.
     *
     * @param world          the world
     * @param startPos       starting position (the first road block goes here)
     * @param direction      the horizontal direction to extend
     * @param length         total bridge length in blocks
     * @param roadState      block for the road surface
     * @param railState      block for the rail (may be null)
     * @param beamState      block for support beams
     * @param hasRail        whether to place rails
     * @param torchPlatform  whether to place torch platforms on pillars
     */
    public static void buildBridge(World world, BlockPos startPos, EnumFacing direction, int length,
                                   IBlockState roadState, IBlockState railState, IBlockState beamState,
                                   boolean hasRail, boolean torchPlatform) {
        // Determine the rail direction property based on bridge direction
        BlockRailBase.EnumRailDirection railDirection = getRailDirection(direction);

        // Perpendicular direction for torch platforms (right side of travel)
        EnumFacing perpDirection = direction.rotateY();

        int nextBeamAt = 0; // first beam at block 0

        for (int i = 0; i < length; i++) {
            BlockPos roadPos = startPos.offset(direction, i);

            // Place road block
            world.setBlockState(roadPos, roadState, 2);

            // Place rail block on top of road (if preset has one)
            BlockPos topBridgePos = roadPos;
            if (hasRail && railState != null) {
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
                topBridgePos = railPos;
            }

            // Clear headroom: ensure at least HEADROOM_BLOCKS air above the top bridge block
            clearHeadroom(world, topBridgePos);

            // Support beam check
            if (i == nextBeamAt) {
                buildSupportBeam(world, roadPos, beamState);

                // Torch platform on pillar
                if (torchPlatform) {
                    buildTorchPlatform(world, roadPos, perpDirection, beamState);
                }

                nextBeamAt = i + MIN_BEAM_INTERVAL + RANDOM.nextInt(MAX_BEAM_INTERVAL - MIN_BEAM_INTERVAL + 1);
            }
        }
    }

    /**
     * Clear blocks above the given position to ensure HEADROOM_BLOCKS of air.
     * Replaces any non-air, non-rail block with air.
     */
    private static void clearHeadroom(World world, BlockPos topPos) {
        for (int j = 1; j <= HEADROOM_BLOCKS; j++) {
            BlockPos clearPos = topPos.up(j);
            IBlockState state = world.getBlockState(clearPos);
            if (state.getBlock() != Blocks.AIR) {
                world.setBlockToAir(clearPos);
            }
        }
    }

    /**
     * Build a torch platform adjacent to a support beam.
     * Places a platform block next to the beam at road level, then a torch on top.
     */
    private static void buildTorchPlatform(World world, BlockPos roadPos, EnumFacing perpDirection, IBlockState beamState) {
        // Platform block is adjacent to the road, at the same Y level
        BlockPos platformPos = roadPos.offset(perpDirection);
        world.setBlockState(platformPos, beamState, 2);

        // Torch on top of the platform block
        BlockPos torchPos = platformPos.up();
        IBlockState torchState = Blocks.TORCH.getDefaultState()
                .withProperty(BlockTorch.FACING, EnumFacing.UP);
        world.setBlockState(torchPos, torchState, 2);
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
