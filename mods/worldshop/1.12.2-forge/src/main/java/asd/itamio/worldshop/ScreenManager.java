package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Reusable screen navigation system that ensures proper screen transitions
 * and prevents visual overlaps when opening sub-screens.
 */
@SideOnly(Side.CLIENT)
public class ScreenManager {

    /**
     * Open a screen, ensuring the previous screen is fully replaced
     * and no visual overlap occurs.
     */
    public static void open(GuiScreen screen) {
        Minecraft.getMinecraft().displayGuiScreen(screen);
    }

    /**
     * Close the current screen and return to the parent.
     * Re-initializes the parent to ensure clean rendering.
     */
    public static void closeToParent(GuiScreen parent) {
        if (parent != null) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            parent.initGui();
        } else {
            Minecraft.getMinecraft().displayGuiScreen(null);
        }
    }
}
