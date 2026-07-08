package asd.itamio.optimizeddaycounter;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders the day/time HUD overlay on the screen.
 * Registered on the Fabric client side only via ClientModInitializer.
 */
public class DayCounterClientHandler implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        try {
            HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft == null || minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
                    return;
                }

                try {
                    if (OptimizedDayCounter.config != null) {
                        OptimizedDayCounter.config.reloadIfChanged();

                        String text = DayCounterFormatter.format(
                            minecraft.level.getDayTime(),
                            minecraft.level.getDayTime(),
                            OptimizedDayCounter.config.getDisplayMode()
                        );
                        if (text.isEmpty()) {
                            return;
                        }

                        Font fontRenderer = minecraft.font;
                        int width = fontRenderer.width(text);
                        int x = OptimizedDayCounter.config.getAnchor().resolveX(
                            drawContext.guiWidth(), width, OptimizedDayCounter.config.getOffsetX());
                        int y = OptimizedDayCounter.config.getAnchor().resolveY(
                            drawContext.guiHeight(), fontRenderer.lineHeight, OptimizedDayCounter.config.getOffsetY());

                        drawContext.drawString(fontRenderer, text, x, y, 0xFFFFFF, true);
                    }
                } catch (Exception e) {
                    System.err.println("[MODAPP-ERROR] Error rendering day counter HUD: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            OptimizedDayCounter.LOGGER.info("Optimized Day Counter HUD handler registered");
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to register HUD render callback: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
