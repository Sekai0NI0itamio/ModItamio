package asd.itamio.optimizeddaycounter;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptimizedDayCounter implements ModInitializer {

    public static final String MOD_ID = "optimizeddaycounter";
    public static final String MOD_NAME = "Optimized Day Counter";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static DayCounterConfig config;

    @Override
    public void onInitialize() {
        try {
            java.nio.file.Path configDir = FabricLoader.getInstance().getConfigDir();
            config = new DayCounterConfig(configDir.resolve("optimizeddaycounter.txt").toFile());
            config.load();
            LOGGER.info("Optimized Day Counter config loaded from: " + configDir.resolve("optimizeddaycounter.txt"));
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to load configuration: " + e.getMessage());
            e.printStackTrace();
            config = new DayCounterConfig(
                FabricLoader.getInstance().getConfigDir().resolve("optimizeddaycounter.txt").toFile()
            );
        }

        // Print the info card
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("Optimized Day Counter initialized — showing world day and time on the HUD");
    }
}
