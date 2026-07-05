package asd.itamio.smoothcontainer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = SmoothContainer.MOD_ID, value = Dist.CLIENT)
public class ContainerOpenHandler {

    private static AbstractContainerScreen<?> deferredScreen = null;
    private static int deferredContainerId = -1;
    private static int deferTicks = 0;
    private static Minecraft minecraft;
    private static boolean isSettingDeferred = false;

    static final int DEFER_FRAMES = 3;

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        if (deferTicks > 0) {
            return;
        }

        if (isSettingDeferred) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }

        deferredScreen = containerScreen;
        deferredContainerId = containerScreen.getMenu().containerId;
        minecraft = mc;
        deferTicks = DEFER_FRAMES;

        ItemRenderCache.preWarm(containerScreen.getMenu());

        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
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

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        cleanup();
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

    private static void cleanup() {
        deferredScreen = null;
        deferredContainerId = -1;
        deferTicks = 0;
        minecraft = null;
        isSettingDeferred = false;
    }
}
