package asd.itamio.modernshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Reusable screen navigation system that ensures proper screen transitions
 * and prevents visual overlaps when opening sub-screens.
 */
public class ScreenManager {

    /**
     * Open a screen, ensuring the previous screen is fully replaced
     * and no visual overlap occurs.
     */
    public static void open(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    /**
     * Open a popup screen from a parent screen.
     * The parent reference is stored in the popup for back-navigation.
     * The popup should have a fully opaque background to prevent overlaps.
     */
    public static void openAsPopup(Screen parent, Screen popup) {
        Minecraft.getInstance().setScreen(popup);
    }

    /**
     * Close the current screen and return to the parent.
     * Re-initializes the parent to ensure clean rendering.
     */
    public static void closeToParent(Screen parent) {
        if (parent != null) {
            Minecraft.getInstance().setScreen(parent);
            // Force re-initialization for clean rendering
            parent.init(parent.width, parent.height);
        } else {
            Minecraft.getInstance().setScreen(null);
        }
    }

    /**
     * A popup screen that stores its parent for back-navigation.
     * All popup screens should extend this to ensure consistent behavior.
     */
    public static abstract class PopupScreen extends Screen {
        protected final Screen parent;

        protected PopupScreen(Screen parent, net.minecraft.network.chat.Component title) {
            super(title);
            this.parent = parent;
        }

        /**
         * Go back to the parent screen.
         */
        protected void closeToParent() {
            ScreenManager.closeToParent(parent);
        }
    }
}
