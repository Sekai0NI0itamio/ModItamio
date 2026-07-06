package asd.itamio.dontbreakcropsthatarenotgrownyet;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DontBreakcropsthatarenotgrownyet implements ModInitializer {

    public static final String MOD_ID = "dontbreakcropsthatarenotgrownyet";
    public static final String MOD_NAME = "Dont Break crops that are not grown yet (26.1.2 Fabric)";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CropBreakHandler.register();
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("Dont Break crops initialized — unripe crops are protected from player breaks (sneak to bypass)");
    }
}
