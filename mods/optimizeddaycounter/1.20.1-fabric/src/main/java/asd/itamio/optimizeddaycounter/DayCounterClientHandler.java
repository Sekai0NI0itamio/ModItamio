package asd.itamio.optimizeddaycounter;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders the day/time HUD overlay on the screen.
 * Registered via Fabric's HudRenderCallback on the client side only.
 */
public class DayCounterClientHandler implements HudRenderCallback {

    @Override
    public void onHudRender(GuiGraphics drawContext, float tickDelta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }

        try {
            // Access the static config from the main mod class
            DayCounterConfig config = OptimizedDayCounter.config;
            if (config == null) {
                return;
            }

            config.reloadIfChanged();

            String text = DayCounterFormatter.format(
                minecraft.level.getDayTime(),
                minecraft.level.getDayTime(),
                config.getDisplayMode()
            );
            if (text.isEmpty()) {
                return;
            }

            Font fontRenderer = minecraft.font;
            int width = fontRenderer.width(text);
            int screenWidth = minecraft.getWindow().getGuiScaledWidth();
            int screenHeight = minecraft.getWindow().getGuiScaledHeight();
            int x = config.getAnchor().resolveX(screenWidth, width, config.getOffsetX());
            int y = config.getAnchor().resolveY(screenHeight, fontRenderer.lineHeight, config.getOffsetY());

            drawContext.drawString(fontRenderer, text, x, y, 0xFFFFFF, true);
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error rendering day counter HUD: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
