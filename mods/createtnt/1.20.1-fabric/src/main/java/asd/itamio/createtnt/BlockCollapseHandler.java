package asd.itamio.createtnt;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Post-explosion structural support check.
 *
 * <p>After an explosion, blocks adjacent to the crater are queued for a
 * support check. A block STAYS if it is still connected to a real support:
 * a flood-fill through face-connected solid blocks (any direction — sideways,
 * up, down; distance does not matter) searches for an <b>anchor block</b>.
 * Only if the entire connected component contains no anchor does the block
 * fall as an {@link EntityEnhancedFallingBlock}.</p>
 *
 * <p>An anchor is:</p>
 * <ul>
 *   <li>bedrock, or any structural block at the world bottom (y ≤ 1), or</li>
 *   <li>a structural block backed by a solid column of
 *       {@code collapseGroundColumnDepth} blocks directly below it — i.e.
 *       something standing on real terrain.</li>
 * </ul>
 *
 * <p><b>Support levels (cantilever strength):</b> the flood-fill is limited
 * to the origin block's own <i>support range</i> — how many blocks that
 * material can extend past a support point. Dirt has range 1: one dirt block
 * hanging off a supported dirt block stays, but a second dirt block extended
 * out from it falls. Harder materials bridge further (stone 2, wood 2, iron
 * blocks 5, obsidian 16). The range comes from an optional per-block config
 * override ({@code supportRange.<modid:block>}) and otherwise defaults to
 * the block's rounded blast hardness clamped to
 * {@code [1, collapseSupportRangeMax]}.</p>
 *
 * <p>The flood-fill is bounded by {@code collapseSupportScanBudget} visited
 * blocks. If the budget is exhausted before the component is fully explored,
 * the block is conservatively treated as SUPPORTED — the system never
 * demolishes something it could not fully verify. When a block falls, its
 * neighbors are queued (cascade, up to {@code collapseMaxDepth} generations)
 * so severed structures crumble over successive ticks, and at most
 * {@code collapseMaxPerTick} blocks are processed per tick to avoid lag.</p>
 */
public class BlockCollapseHandler {

    private static final Deque<CollapseTask> queue = new ArrayDeque<>();
    private static final int MAX_QUEUE_SIZE = 6000;

    /**
     * Grace period (ticks) during which a freshly settled block cannot be
     * re-collapsed. Without this, an unsupported block that lands on a static
     * block right below it gets re-placed by the settle logic and then
     * instantly re-queued by the cascade — falling and re-placing forever,
     * flickering as an entity that "never becomes solid again". 40 ticks is
     * far longer than the few ticks a cascade loop needs to re-check a
     * position, so it reliably breaks the cycle while still allowing
     * genuinely new explosions shortly after to collapse the block again.
     */
    private static final long SETTLED_GRACE_TICKS = 40L;
    private static final java.util.Map<Level, java.util.Map<BlockPos, Long>> recentlySettled =
        new java.util.HashMap<>();

    /** Marks a freshly placed debris block as off-limits for collapse checks. */
    public static void markSettled(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        java.util.Map<BlockPos, Long> map = recentlySettled.computeIfAbsent(level, w -> new java.util.HashMap<>());
        if (map.size() > 4096) {
            // Purge expired entries so the map can't grow without bound.
            long now = level.getGameTime();
            map.values().removeIf(expiry -> now > expiry.longValue());
        }
        map.put(pos.immutable(), level.getGameTime() + SETTLED_GRACE_TICKS);
    }

    private static boolean isRecentlySettled(Level level, BlockPos pos) {
        java.util.Map<BlockPos, Long> map = recentlySettled.get(level);
        if (map == null) return false;
        Long expiry = map.get(pos);
        if (expiry == null) return false;
        if (level.getGameTime() > expiry.longValue()) {
            map.remove(pos);
            return false;
        }
        return true;
    }

    /** 6-connectivity, DOWN first so grounded structures anchor quickly. */
    private static final int[][] NEIGHBORS = {
        { 0,-1, 0},
        { 1, 0, 0}, {-1, 0, 0},
        { 0, 0, 1}, { 0, 0,-1},
        { 0, 1, 0}
    };

    private static class CollapseTask {
        final Level level;
        final BlockPos pos;
        final int generation;

        CollapseTask(Level level, BlockPos pos, int generation) {
            this.level = level;
            this.pos = pos.immutable();
            this.generation = generation;
        }
    }

    /**
     * Queues a block for a support check. Called after an explosion removes
     * adjacent blocks. Only structural (solid, movement-blocking) blocks are
     * queued.
     */
    public static void queueCollapseCheck(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        if (queue.size() >= MAX_QUEUE_SIZE) return;
        if (isRecentlySettled(level, pos)) return;
        if (!isStructural(level.getBlockState(pos))) return;
        queue.add(new CollapseTask(level, pos, 1));
    }

    /** Called from the Fabric server-tick callback (end of tick). */
    public void onServerTick() {

        int processed = 0;
        while (!queue.isEmpty() && processed < ModConfig.collapseMaxPerTick) {
            CollapseTask task = queue.poll();
            if (task == null) break;
            checkAndCollapse(task);
            processed++;
        }
    }

    private void checkAndCollapse(CollapseTask task) {
        Level level = task.level;
        BlockPos pos = task.pos;

        if (!level.isLoaded(pos)) return;
        if (isRecentlySettled(level, pos)) return;

        BlockState state = level.getBlockState(pos);
        if (!isStructural(state)) return;

        // Skip tile entities, TNT, and bedrock.
        if (level.getBlockEntity(pos) != null) return;
        if (state.getBlock() == Blocks.TNT) return;
        if (state.getBlock() == Blocks.BEDROCK) return;

        // The structural support check: stays if connected to an anchor
        // within this block's own support range.
        if (isSupported(level, pos, state)) return;

        // No support anywhere in the connected component — make it fall.
        // DO NOT set the block to air here: the falling block entity's
        // first-tick logic expects the block to still be present at its
        // position (it removes the block itself). If we clear it now, the
        // entity sees AIR on its first tick and kills itself.
        EntityEnhancedFallingBlock falling = new EntityEnhancedFallingBlock(level,
            pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, state);
        level.addFreshEntity(falling);

        // Queue neighbors for checking (cascade). Use a 2-block X/Z radius and
        // check 3 Y levels above so multiple layers collapse at once instead of
        // layer-by-layer. This makes tall structures crumble much faster.
        if (task.generation < ModConfig.collapseMaxDepth) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = 0; dy <= 3; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        if (queue.size() >= MAX_QUEUE_SIZE) return;
                        queue.add(new CollapseTask(level, pos.offset(dx, dy, dz), task.generation + 1));
                    }
                }
            }
        }
    }

    /**
     * Anchored connectivity check with per-block support range: layered BFS
     * from the origin through face-connected structural blocks (any
     * direction), looking for an anchor block (one backed by real ground).
     * The search extends at most {@code range} layers from the origin, where
     * {@code range} is the origin block's support level — so a dirt block
     * (range 1) hanging two blocks out from a support falls, while a stone
     * block (range 2) at the same spot stays. Returns true if an anchor is
     * reachable within the range, or if the scan budget is exhausted first
     * (conservative — never demolish what we cannot fully verify).
     */
    private boolean isSupported(Level level, BlockPos origin, BlockState originState) {
        // Fast path: standing directly on an anchored block.
        if (isAnchorBlock(level, origin.below())) {
            return true;
        }

        final int range = getSupportRange(level, origin, originState);
        if (range <= 0) {
            return false;
        }
        final int budget = ModConfig.collapseSupportScanBudget;

        Set<BlockPos> visited = new HashSet<>();
        java.util.List<BlockPos> currentLayer = new java.util.ArrayList<>();
        visited.add(origin);
        currentLayer.add(origin);

        for (int layer = 1; layer <= range; layer++) {
            java.util.List<BlockPos> nextLayer = new java.util.ArrayList<>();
            for (BlockPos current : currentLayer) {
                for (int[] dir : NEIGHBORS) {
                    BlockPos adj = current.offset(dir[0], dir[1], dir[2]);
                    if (!visited.add(adj)) {
                        continue;
                    }
                    if (visited.size() > budget) {
                        // Could not fully verify within the scan budget:
                        // conservatively treat as supported.
                        return true;
                    }
                    BlockState adjState = level.getBlockState(adj);
                    if (!isStructural(adjState)) {
                        continue;
                    }
                    if (isAnchorBlock(level, adj, adjState)) {
                        return true;
                    }
                    nextLayer.add(adj);
                }
            }
            if (nextLayer.isEmpty()) {
                // No more structural blocks to expand through: the connected
                // component was fully explored and contains no anchor in range.
                return false;
            }
            currentLayer = nextLayer;
        }
        // The anchor (if any) lies further away than this block's support
        // range: unsupported.
        return false;
    }

    /**
     * A block's support level — how many blocks it can extend past a support
     * point. Taken from the per-block config override if present
     * ({@code supportRange.<modid:block>}), otherwise derived from the
     * block's blast hardness (rounded, clamped to
     * {@code [1, collapseSupportRangeMax]}). Unbreakable blocks get the max.
     */
    private int getSupportRange(Level level, BlockPos pos, BlockState state) {
        ResourceLocation name = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (name != null) {
            Integer override = ModConfig.supportRangeOverrides.get(name.toString());
            if (override != null) {
                return override;
            }
        }
        int max = ModConfig.collapseSupportRangeMax;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) {
            return max; // unbreakable
        }
        return Mth.clamp(Math.round(hardness), 1, max);
    }

    /**
     * An anchor is a structural block that is unambiguously supported by the
     * world itself: bedrock, a block at the world bottom, or a block backed
     * by an unbroken solid column of {@code collapseGroundColumnDepth} blocks
     * straight down (real terrain / a load-bearing structure base).
     */
    private boolean isAnchorBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isStructural(state)) return false;
        return isAnchorBlock(level, pos, state);
    }

    private boolean isAnchorBlock(Level level, BlockPos pos, BlockState state) {
        if (!isStructural(state)) return false;
        if (state.getBlock() == Blocks.BEDROCK) return true;
        if (pos.getY() <= level.getMinBuildHeight() + 1) return true;
        int depth = ModConfig.collapseGroundColumnDepth;
        for (int i = 1; i <= depth; i++) {
            BlockPos below = pos.below(i);
            if (below.getY() <= level.getMinBuildHeight()) return true;
            if (!isStructural(level.getBlockState(below))) return false;
        }
        return true;
    }

    /**
     * A structural block participates in support connections: solid and
     * movement-blocking (stone, wood, glass, leaves...). Air, fluids, plants,
     * torches and similar non-solid blocks neither need support nor give it.
     */
    private static boolean isStructural(BlockState state) {
        return !state.isAir() && state.blocksMotion() && state.getFluidState().isEmpty();
    }
}
