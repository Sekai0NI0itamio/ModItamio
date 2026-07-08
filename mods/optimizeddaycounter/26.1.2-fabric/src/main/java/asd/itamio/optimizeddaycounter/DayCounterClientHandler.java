package asd.itamio.optimizeddaycounter;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;

/**
 * Renders the day/time HUD overlay on the screen.
 * Registered on the Fabric client side only via ClientModInitializer.
 */
public class DayCounterClientHandler implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        try {
            HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath("optimizeddaycounter", "day_counter"),
                (extractor, deltaTracker) -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft == null || minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
                        return;
                    }

                    try {
                        if (OptimizedDayCounter.config != null) {
                            OptimizedDayCounter.config.reloadIfChanged();

                            long totalTime = minecraft.level.getLevelData().getGameTime();
                            long dayTime = minecraft.level.getOverworldClockTime();

                            String text = DayCounterFormatter.format(
                                totalTime,
                                dayTime,
                                OptimizedDayCounter.config.getDisplayMode()
                            );
                            if (text.isEmpty()) {
                                return;
                            }

                            Font fontRenderer = minecraft.font;
                            int width = fontRenderer.width(text);
                            int x = OptimizedDayCounter.config.getAnchor().resolveX(
                                extractor.guiWidth(), width, OptimizedDayCounter.config.getOffsetX());
                            int y = OptimizedDayCounter.config.getAnchor().resolveY(
                                extractor.guiHeight(), fontRenderer.lineHeight, OptimizedDayCounter.config.getOffsetY());

                            extractor.text(fontRenderer, text, x, y, 0xFFFFFF);
                        }
                    } catch (Exception e) {
                        System.err.println("[MODAPP-ERROR] Error rendering day counter HUD: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            );
            OptimizedDayCounter.LOGGER.info("Optimized Day Counter HUD handler registered");
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to register HUD render callback: " + e.getMessage());
            e.printStackTrace();
        }
    }
}