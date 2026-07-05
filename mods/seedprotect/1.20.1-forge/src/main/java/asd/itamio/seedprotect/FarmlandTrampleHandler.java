package asd.itamio.seedprotect;

import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Cancels {@link BlockEvent.FarmlandTrampleEvent} so neither players nor mobs can
 * trample farmland. When trampling is cancelled the farmland block stays as
 * farmland and any crop planted on it remains intact.
 *
 * <p>The handler is registered on the FORGE event bus via
 * {@link Mod.EventBusSubscriber} and runs on both client and server sides —
 * this matches the original 1.12.2 mod's behaviour (server-side required,
 * client-side unsupported, but cancelling on both sides is harmless and keeps
 * integrated-server single-player consistent with dedicated servers).</p>
 */
@Mod.EventBusSubscriber(modid = SeedProtect.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
