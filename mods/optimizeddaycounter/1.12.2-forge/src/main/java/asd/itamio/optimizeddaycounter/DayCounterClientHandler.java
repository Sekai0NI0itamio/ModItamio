package asd.itamio.optimizeddaycounter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Renders the day/time HUD overlay on the screen.
 * Registered on the Forge EVENT_BUS on the client side only.
 */
public class DayCounterClientHandler {

    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.player == null || minecraft.world == null || minecraft.gameSettings.hideGUI) {
            return;
        }

        try {
            config.reloadIfChanged();

            String text = DayCounterFormatter.format(
                minecraft.world.getTotalWorldTime(),
                minecraft.world.getWorldTime(),
                config.getDisplayMode()
            );
            if (text.isEmpty()) {
                return;
            }

            FontRenderer fontRenderer = minecraft.fontRenderer;
            ScaledResolution resolution = event.getResolution();
            int width = fontRenderer.getStringWidth(text);
            int x = config.getAnchor().resolveX(resolution.getScaledWidth(), width, config.getOffsetX());
            int y = config.getAnchor().resolveY(resolution.getScaledHeight(), fontRenderer.FONT_HEIGHT, config.getOffsetY());

            fontRenderer.drawStringWithShadow(text, x, y, 0xFFFFFF);
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error rendering day counter HUD: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
