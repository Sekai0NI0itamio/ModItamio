package asd.itamio.instantautototem;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class InstantAutoTotemClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        try {
            AutoTotemKeyHandler.register();
            InstantAutoTotem.LOGGER.info("Instant Auto Totem client key handler registered");
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to register client key handler: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
