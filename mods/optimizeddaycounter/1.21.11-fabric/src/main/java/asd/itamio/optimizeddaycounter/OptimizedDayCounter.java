package asd.itamio.optimizeddaycounter;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class OptimizedDayCounter implements ModInitializer {

    public static final String MOD_ID = "optimizeddaycounter";
    public static final String MOD_NAME = "Optimized Day Counter";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static DayCounterConfig config;

    @Override
    public void onInitialize() {
        // Load configuration from the game directory
        try {
            File configDir = new File("config");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            File configFile = new File(configDir, "optimizeddaycounter.txt");
            config = new DayCounterConfig(configFile);
            config.load();
            LOGGER.info("Optimized Day Counter config loaded from: {}", configFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to load configuration: " + e.getMessage());
            e.printStackTrace();
            config = new DayCounterConfig(new File("config", "optimizeddaycounter.txt"));
        }

        // Print the info card
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("Optimized Day Counter initialized — showing world day and time on the HUD");
    }
}
