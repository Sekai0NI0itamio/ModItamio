package asd.itamio.optimizeddaycounter;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Client initializer for the Optimized Day Counter mod.
 * Registers the HUD render callback.
 */
public class OptimizedDayCounterClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        try {
            HudRenderCallback.EVENT.register(new DayCounterClientHandler());
            OptimizedDayCounter.LOGGER.info("Optimized Day Counter HUD handler registered");
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to register HUD renderer: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
