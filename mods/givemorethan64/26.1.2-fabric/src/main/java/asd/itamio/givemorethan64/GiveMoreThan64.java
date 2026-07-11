package asd.itamio.givemorethan64;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GiveMoreThan64 implements ModInitializer {

    public static final String MOD_ID = "givemorethan64";
    public static final String MOD_NAME = "give more than 64";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("GiveMoreThan64 initialized - /give command now supports amounts above stack limit");
    }
}
