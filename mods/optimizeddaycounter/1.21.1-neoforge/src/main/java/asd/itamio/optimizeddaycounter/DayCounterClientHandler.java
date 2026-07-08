package asd.itamio.optimizeddaycounter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Renders the day/time HUD overlay on the screen.
 * Registered on the NeoForge EVENT_BUS on the client side only.
 */
public class DayCounterClientHandler {

    @SubscribeEvent
    public void onRenderOverlay(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }

        try {
            OptimizedDayCounter.config.reloadIfChanged();

            String text = DayCounterFormatter.format(
                minecraft.level.getGameTime(),
                minecraft.level.getDayTime(),
                OptimizedDayCounter.config.getDisplayMode()
            );
            if (text.isEmpty()) {
                return;
            }

            Font font = minecraft.font;
            GuiGraphics guiGraphics = event.getGuiGraphics();
            int screenWidth = minecraft.getWindow().getGuiScaledWidth();
            int screenHeight = minecraft.getWindow().getGuiScaledHeight();
            int width = font.width(text);
            int x = OptimizedDayCounter.config.getAnchor().resolveX(screenWidth, width, OptimizedDayCounter.config.getOffsetX());
            int y = OptimizedDayCounter.config.getAnchor().resolveY(screenHeight, font.lineHeight, OptimizedDayCounter.config.getOffsetY());

            guiGraphics.drawString(font, text, x, y, 0xFFFFFF, true);
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error rendering day counter HUD: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
