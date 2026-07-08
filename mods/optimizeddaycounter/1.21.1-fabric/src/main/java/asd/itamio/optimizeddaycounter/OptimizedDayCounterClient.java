package asd.itamio.optimizeddaycounter;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

@Environment(EnvType.CLIENT)
public class OptimizedDayCounterClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        try {
            HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
                renderDayCounter(guiGraphics);
            });
            OptimizedDayCounter.LOGGER.info("Optimized Day Counter HUD handler registered");
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to register HUD render callback: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void renderDayCounter(GuiGraphics guiGraphics) {
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
