package asd.itamio.optimizeddaycounter;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(OptimizedDayCounter.MOD_ID)
public class OptimizedDayCounter {

    public static final String MOD_ID = "optimizeddaycounter";
    public static final String MOD_NAME = "Optimized Day Counter";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static DayCounterConfig config;

    public OptimizedDayCounter(IEventBus modEventBus) {
        try {
            java.nio.file.Path configDir = FMLPaths.CONFIGDIR.get();
            config = new DayCounterConfig(configDir.resolve("optimizeddaycounter.txt").toFile());
            config.load();
            LOGGER.info("Optimized Day Counter config loaded from: " + configDir.resolve("optimizeddaycounter.txt"));
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to load configuration: " + e.getMessage());
            e.printStackTrace();
            config = new DayCounterConfig(
                FMLPaths.CONFIGDIR.get().resolve("optimizeddaycounter.txt").toFile()
            );
        }

        // Print the info card
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);

        // Register client-side handler (only on physical client)
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                MinecraftForge.EVENT_BUS.register(new DayCounterClientHandler());
                LOGGER.info("Optimized Day Counter HUD handler registered");
            } catch (Exception e) {
                System.err.println("[MODAPP-ERROR] Failed to register event handlers: " + e.getMessage());
                e.printStackTrace();
            }
        });

        LOGGER.info("Optimized Day Counter initialized — showing world day and time on the HUD");
    }
}
