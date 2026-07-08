package asd.itamio.optimizeddaycounter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Renders the day/time HUD overlay on the screen.
 * Registered as a custom GUI overlay on the Forge mod event bus.
 */
@Mod.EventBusSubscriber(modid = OptimizedDayCounter.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class DayCounterClientHandler {

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(
            "day_counter",
            (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
                renderOverlay(guiGraphics);
            }
        );
    }

    private static void renderOverlay(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }

        try {
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

            guiGraphics.drawString(fontRenderer, text, x, y, 0xFFFFFF, true);
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error rendering day counter HUD: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
