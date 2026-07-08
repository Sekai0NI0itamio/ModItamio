package asd.itamio.dontbreakcropsthatarenotgrownyet;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Cancels {@link BlockEvent.BreakEvent} when the broken block is a crop that
 * has not yet reached its maximum growth stage. Covers every
 * {@link BlockCrops} subclass (wheat, carrots, potatoes, beetroots) via
 * {@link BlockCrops#isMaxAge(IBlockState)}, plus {@link BlockNetherWart} which
 * uses a separate age property with max 3.
 *
 * <p>Note: sweet berry bushes, torchflower crop, and pitcher crop do not exist
 * in 1.12.2, so they are not handled here.</p>
 *
 * <p>Side feature: a sneaking player bypasses the protection so unripe crops
 * can still be removed intentionally (misplanted seeds, farm reorganisation)
 * without breaking the farmland underneath.</p>
 *
 * <p>Registered on the FORGE event bus via {@link Mod.EventBusSubscriber} —
 * the break check runs on the server side, so cancelling on both sides keeps
 * integrated-server single-player consistent with dedicated servers.</p>
 */
@Mod.EventBusSubscriber(modid = DontBreakcropsthatarenotgrownyet.MOD_ID)
public final class CropBreakHandler {

    private static final int NETHER_WART_MAX_AGE = 3;

    private CropBreakHandler() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        try {
            // Sneak to bypass: lets players intentionally remove unripe crops.
            // Cast to EntityLivingBase to avoid MCP SRG remapping issues with
            // EntityPlayer.isSneaking() in stable_39 mappings.
            if (((EntityLivingBase) event.getPlayer()).isSneaking()) {
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

    private static boolean isUnripeCrop(IBlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockCrops) {
            BlockCrops crop = (BlockCrops) block;
            return !crop.isMaxAge(state);
        }
        if (block instanceof BlockNetherWart) {
            return ((Integer) state.getValue(BlockNetherWart.AGE)) < NETHER_WART_MAX_AGE;
        }
        return false;
    }
}
