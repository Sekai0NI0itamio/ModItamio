package asd.itamio.instantautototem;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(InstantAutoTotem.MOD_ID)
public class InstantAutoTotem {

    public static final String MOD_ID = "instantautototem";
    public static final String MOD_NAME = "Instant Auto Totem";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static AutoTotemConfig config = new AutoTotemConfig(); // Default instance

    public InstantAutoTotem(IEventBus modEventBus) {
        try {
            // Register configuration (will bake into the static config instance)
            AutoTotemConfig.init(modEventBus);
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to initialize configuration: " + e.getMessage());
            e.printStackTrace();
            // config already has defaults
        }

        // Register server event handlers
        try {
            NeoForge.EVENT_BUS.register(new AutoTotemHandler());
            LOGGER.info("Instant Auto Totem event handlers registered");
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to register event handlers: " + e.getMessage());
            e.printStackTrace();
        }

        // Register client event handlers
        try {
            modEventBus.addListener(AutoTotemKeyHandler::onRegisterKeyMappings);
            NeoForge.EVENT_BUS.addListener(AutoTotemKeyHandler::onClientTick);
            LOGGER.info("Instant Auto Totem client key handler registered");
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to register client event handlers: " + e.getMessage());
            e.printStackTrace();
        }

        // Print the info card
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("Instant Auto Totem initialized — automatically keeps a Totem of Undying in your offhand");
    }
}
