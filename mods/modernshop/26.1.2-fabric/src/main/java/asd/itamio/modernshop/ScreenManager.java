package asd.itamio.modernshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Reusable screen navigation system that ensures proper screen transitions
 * and prevents visual overlaps when opening sub-screens.
 */
public class ScreenManager {

    public static void open(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    public static void openAsPopup(Screen parent, Screen popup) {
        Minecraft.getInstance().setScreen(popup);
    }

    public static void closeToParent(Screen parent) {
        if (parent != null) {
            Minecraft.getInstance().setScreen(parent);
            parent.init(parent.width, parent.height);
        } else {
            Minecraft.getInstance().setScreen(null);
        }
    }

    public static abstract class PopupScreen extends Screen {
        protected final Screen parent;

        protected PopupScreen(Screen parent, net.minecraft.network.chat.Component title) {
            super(title);
            this.parent = parent;
        }

        protected void closeToParent() {
            ScreenManager.closeToParent(parent);
        }
    }
}
