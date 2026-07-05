package asd.itamio.seedprotect;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SeedProtect implements ModInitializer {

    public static final String MOD_ID = "seedprotect";
    public static final String MOD_NAME = "Seed Protect (1.20.1 Fabric)";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("Seed Protect initialized — farmland trampling is cancelled for players and mobs");
    }
}
