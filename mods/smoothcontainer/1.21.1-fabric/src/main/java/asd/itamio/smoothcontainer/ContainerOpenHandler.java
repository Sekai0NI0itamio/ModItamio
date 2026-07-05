package asd.itamio.smoothcontainer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class ContainerOpenHandler {

    private static AbstractContainerScreen<?> deferredScreen = null;
    private static int deferredContainerId = -1;
    private static int deferTicks = 0;
    private static Minecraft minecraft;
    private static boolean isSettingDeferred = false;

    static final int DEFER_FRAMES = 3;

    private ContainerOpenHandler() {}

    public static boolean tryDefer(Screen screen) {
        if (deferTicks > 0) {
            return false;
        }
        if (isSettingDeferred) {
            return false;
        }
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return false;
        }

        deferredScreen = containerScreen;
        deferredContainerId = containerScreen.getMenu().containerId;
        minecraft = mc;
        deferTicks = DEFER_FRAMES;

        ItemRenderCache.preWarm(containerScreen.getMenu());
        return true;
    }

    public static void onClientTick(Minecraft mc) {
        if (deferTicks <= 0 || deferredScreen == null) {
            return;
        }

        deferTicks--;

        switch (deferTicks) {
            case 2:
                if (minecraft != null && minecraft.getWindow() != null) {
                    deferredScreen.init(
                            minecraft,
                            minecraft.getWindow().getGuiScaledWidth(),
                            minecraft.getWindow().getGuiScaledHeight()
                    );
                }
                break;

            case 1:
                break;

            case 0:
                if (isContainerStillValid()) {
                    isSettingDeferred = true;
                    minecraft.setScreen(deferredScreen);
                    isSettingDeferred = false;
                }
                deferredScreen = null;
                deferredContainerId = -1;
                minecraft = null;
                break;
        }
    }

    private static boolean isContainerStillValid() {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        if (minecraft.player.isRemoved()) {
            return false;
        }
        AbstractContainerMenu currentMenu = minecraft.player.containerMenu;
        return currentMenu != null && currentMenu.containerId == deferredContainerId;
    }

    public static void cleanup() {
        deferredScreen = null;
        deferredContainerId = -1;
        deferTicks = 0;
        minecraft = null;
        isSettingDeferred = false;
    }
}
