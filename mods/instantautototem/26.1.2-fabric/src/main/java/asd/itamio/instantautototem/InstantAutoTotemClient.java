package asd.itamio.instantautototem;

import net.fabricmc.api.ClientModInitializer;

public class InstantAutoTotemClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        try {
            AutoTotemKeyHandler.register();
            InstantAutoTotem.LOGGER.info("Registered client keybinding for auto totem toggle");
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to initialize client: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
