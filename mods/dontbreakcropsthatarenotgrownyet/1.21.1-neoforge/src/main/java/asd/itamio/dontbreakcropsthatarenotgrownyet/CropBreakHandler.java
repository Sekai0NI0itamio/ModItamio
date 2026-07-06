package asd.itamio.dontbreakcropsthatarenotgrownyet;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Cancels {@link BlockEvent.BreakEvent} when the broken block is a crop that
 * has not yet reached its maximum growth stage. Covers every {@link CropBlock}
 * subclass (wheat, carrots, potatoes, beetroots, torchflower crop, pitcher
 * crop) via {@link CropBlock#isMaxAge}, plus {@link NetherWartBlock} and
 * {@link SweetBerryBushBlock} which use a separate age property with max 3.
 *
 * <p>Side feature: a sneaking player bypasses the protection so unripe crops
 * can still be removed intentionally (misplanted seeds, farm reorganisation)
 * without breaking the farmland underneath.</p>
 *
 * <p>Registered on the NeoForge game event bus via {@link EventBusSubscriber}
 * without a {@code Dist} restriction — the break check runs on the server
 * side, so cancelling on both sides keeps integrated-server single-player
 * consistent with dedicated servers.</p>
 */
@EventBusSubscriber(modid = DontBreakcropsthatarenotgrownyet.MOD_ID)
public final class CropBreakHandler {

    private static final int NETHER_WART_MAX_AGE = 3;
    private static final int SWEET_BERRY_MAX_AGE = 3;

    private CropBreakHandler() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        try {
            // Sneak to bypass: lets players intentionally remove unripe crops.
            if (event.getPlayer().isShiftKeyDown()) {
                return;
            }
            if (isUnripeCrop(event.getState())) {
                event.setCanceled(true);
            }
        } catch (Throwable t) {
            DontBreakcropsthatarenotgrownyet.LOGGER.error(
                    "[MODAPP-ERROR] Failed to evaluate crop break at pos={} state={}",
                    event.getPos(), event.getState(), t);
        }
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
