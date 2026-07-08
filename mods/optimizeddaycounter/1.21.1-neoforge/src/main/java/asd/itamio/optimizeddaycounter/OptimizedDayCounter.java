package asd.itamio.optimizeddaycounter;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

@Mod(OptimizedDayCounter.MOD_ID)
public class OptimizedDayCounter {

    public static final String MOD_ID = "optimizeddaycounter";
    public static final String MOD_NAME = "Optimized Day Counter";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static DayCounterConfig config;

    public OptimizedDayCounter(IEventBus modEventBus) {
        try {
            // Load configuration from the default config directory
            String configDir = System.getProperty("user.dir") + "/config";
            File configFile = new File(configDir, "optimizeddaycounter.txt");
            config = new DayCounterConfig(configFile);
            config.load();
            LOGGER.info("Optimized Day Counter config loaded from: " + configFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to load configuration: " + e.getMessage());
            e.printStackTrace();
            String configDir = System.getProperty("user.dir") + "/config";
            config = new DayCounterConfig(new File(configDir, "optimizeddaycounter.txt"));
        }

        // Register client event handlers
        try {
            NeoForge.EVENT_BUS.register(new DayCounterClientHandler());
            LOGGER.info("Optimized Day Counter HUD handler registered");
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to register event handlers: " + e.getMessage());
            e.printStackTrace();
        }

        // Print the info card
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("Optimized Day Counter initialized — showing world day and time on the HUD");
    }
}
