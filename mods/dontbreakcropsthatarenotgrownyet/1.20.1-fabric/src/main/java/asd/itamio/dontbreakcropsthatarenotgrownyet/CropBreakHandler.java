package asd.itamio.dontbreakcropsthatarenotgrownyet;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Registers a {@link PlayerBlockBreakEvents#BEFORE} callback that cancels the
 * break when the targeted block is a crop that has not yet reached its maximum
 * growth stage. Covers every {@link CropBlock} subclass (wheat, carrots,
 * potatoes, beetroots, torchflower crop, pitcher crop) via
 * {@link CropBlock#isMaxAge}, plus {@link NetherWartBlock} and
 * {@link SweetBerryBushBlock} which use a separate age property with max 3.
 *
 * <p>Side feature: a sneaking player bypasses the protection so unripe crops
 * can still be removed intentionally (misplanted seeds, farm reorganisation)
 * without breaking the farmland underneath.</p>
 *
 * <p>Fabric API's BEFORE callback runs on both the client (integrated server)
 * and the dedicated server — the protection is enforced everywhere a player
 * can break blocks.</p>
 */
public final class CropBreakHandler {

    private static final int NETHER_WART_MAX_AGE = 3;
    private static final int SWEET_BERRY_MAX_AGE = 3;

    private CropBreakHandler() {
    }

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((Level world, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) -> {
            try {
                // Sneak to bypass: lets players intentionally remove unripe crops.
                if (player.isShiftKeyDown()) {
                    return true;
                }
                return !isUnripeCrop(state);
            } catch (Throwable t) {
                DontBreakcropsthatarenotgrownyet.LOGGER.error(
                        "[MODAPP-ERROR] Failed to evaluate crop break at pos={} state={}",
                        pos, state, t);
                return true; // fail-open: allow the break if we can't evaluate
            }
        });
    }

    private static boolean isUnripeCrop(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return !crop.isMaxAge(state);
        }
        if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) < NETHER_WART_MAX_AGE;
        }
        if (block instanceof SweetBerryBushBlock) {
            return state.getValue(SweetBerryBushBlock.AGE) < SWEET_BERRY_MAX_AGE;
        }
        return false;
    }
}
