package asd.itamio.buildabridge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import asd.itamio.ModInfoPrinter;
import org.apache.logging.log4j.Logger;

@Mod(modid = Buildabridge.MOD_ID, name = Buildabridge.MOD_NAME, version = Buildabridge.VERSION, acceptedMinecraftVersions = "[1.12.2]")
public class Buildabridge {

    public static final String MOD_ID = "buildabridge";
    public static final String MOD_NAME = "Build a bridge";
    public static final String VERSION = "1.0.0";

    public static Logger LOGGER;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("Build a bridge mod initialized. Use /bridge <length> <preset> [x y z] to build!");
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new BridgeCommand());
        LOGGER.info("Registered /bridge command");
    }
}
