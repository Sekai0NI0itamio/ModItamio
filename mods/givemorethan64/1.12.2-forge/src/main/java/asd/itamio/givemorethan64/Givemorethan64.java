package asd.itamio.givemorethan64;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import asd.itamio.ModInfoPrinter;
import org.apache.logging.log4j.Logger;

@Mod(modid = Givemorethan64.MOD_ID, name = Givemorethan64.MOD_NAME, version = Givemorethan64.VERSION)
public class Givemorethan64 {

    public static final String MOD_ID = "givemorethan64";
    public static final String MOD_NAME = "give more than 64";
    public static final String VERSION = "1.0.0";

    public static Logger LOGGER;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new CommandGiveHandler());
        LOGGER.info("GiveMoreThan64 initialized - /give command now supports amounts above stack limit");
    }
}
