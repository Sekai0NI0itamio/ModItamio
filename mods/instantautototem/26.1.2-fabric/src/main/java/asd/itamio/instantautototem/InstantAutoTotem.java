package asd.itamio.instantautototem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstantAutoTotem implements ModInitializer {

    public static final String MOD_ID = "instantautototem";
    public static final String MOD_NAME = "Instant Auto Totem";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static AutoTotemConfig config;

    @Override
    public void onInitialize() {
        try {
            // Load configuration
            config = new AutoTotemConfig();
            config.load();
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to load configuration: " + e.getMessage());
            e.printStackTrace();
            config = new AutoTotemConfig(); // Use defaults
        }

        try {
            // Register server tick event handler
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                    try {
                        AutoTotemHandler.onPlayerTick(player);
                    } catch (Exception e) {
                        System.err.println("[MODAPP-ERROR] Error in player tick: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });
            LOGGER.info("Registered server tick handler for auto totem");
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to register server tick handler: " + e.getMessage());
            e.printStackTrace();
        }

        // Print the info card
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("Instant Auto Totem initialized — automatically keeps a Totem of Undying in your offhand");
    }
}
