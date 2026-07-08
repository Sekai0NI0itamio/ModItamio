package asd.itamio.optimizeddaycounter;

import java.io.File;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = OptimizedDayCounter.MOD_ID,
    name = OptimizedDayCounter.MOD_NAME,
    version = OptimizedDayCounter.VERSION,
    clientSideOnly = true,
    acceptableRemoteVersions = "*"
)
public class OptimizedDayCounter {

    public static final String MOD_ID = "optimizeddaycounter";
    public static final String MOD_NAME = "Optimized Day Counter";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    private static DayCounterConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        try {
            // Load configuration
            File configFile = new File(event.getModConfigurationDirectory(), "optimizeddaycounter.txt");
            config = new DayCounterConfig(configFile);
            config.load();
            LOGGER.info("Optimized Day Counter config loaded from: " + configFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to load configuration: " + e.getMessage());
            e.printStackTrace();
            config = new DayCounterConfig(new File(event.getModConfigurationDirectory(), "optimizeddaycounter.txt"));
        }

        // Print the info card
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("Optimized Day Counter initialized — showing world day and time on the HUD");
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        try {
            if (event.getSide().isClient()) {
                MinecraftForge.EVENT_BUS.register(new DayCounterClientHandler(config));
                LOGGER.info("Optimized Day Counter HUD handler registered");
            }
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to register event handlers: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
