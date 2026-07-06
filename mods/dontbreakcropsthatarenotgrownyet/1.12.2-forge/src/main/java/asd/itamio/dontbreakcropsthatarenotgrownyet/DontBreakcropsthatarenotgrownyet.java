package asd.itamio.dontbreakcropsthatarenotgrownyet;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = DontBreakcropsthatarenotgrownyet.MOD_ID, name = DontBreakcropsthatarenotgrownyet.MOD_NAME, version = DontBreakcropsthatarenotgrownyet.VERSION)
public class DontBreakcropsthatarenotgrownyet {

    public static final String MOD_ID = "dontbreakcropsthatarenotgrownyet";
    public static final String MOD_NAME = "Dont Break crops that are not grown yet (1.12.2 Forge)";
    public static final String VERSION = "1.0.0";
    public static Logger LOGGER;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("Dont Break crops initialized — unripe crops are protected from player breaks (sneak to bypass)");
    }
}
