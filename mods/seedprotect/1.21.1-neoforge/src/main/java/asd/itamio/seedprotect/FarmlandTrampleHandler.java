package asd.itamio.seedprotect;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Cancels {@link BlockEvent.FarmlandTrampleEvent} so neither players nor mobs can
 * trample farmland. When trampling is cancelled the farmland block stays as
 * farmland and any crop planted on it remains intact.
 *
 * <p>Registered on the NeoForge game event bus via {@link EventBusSubscriber}
 * without a {@code Dist} restriction — the trample check runs on the server
 * side, so cancelling on both sides keeps integrated-server single-player
 * consistent with dedicated servers.</p>
 */
@EventBusSubscriber(modid = SeedProtect.MOD_ID)
public final class FarmlandTrampleHandler {

    private FarmlandTrampleHandler() {
    }

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        try {
            event.setCanceled(true);
        } catch (Throwable t) {
            SeedProtect.LOGGER.error("[MODAPP-ERROR] Failed to cancel FarmlandTrampleEvent at pos={} entity={}",
                    event.getPos(), event.getEntity(), t);
        }
    }
}
