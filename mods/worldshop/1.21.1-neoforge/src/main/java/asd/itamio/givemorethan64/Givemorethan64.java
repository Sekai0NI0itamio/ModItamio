package asd.itamio.givemorethan64;

import asd.itamio.ModInfoPrinter;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Givemorethan64.MOD_ID)
public class Givemorethan64 {

    public static final String MOD_ID = "givemorethan64";
    public static final String MOD_NAME = "give more than 64";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public Givemorethan64() {
        // Print the info card
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("GiveMoreThan64 initialized — /give command now supports amounts above stack limit");
    }
}
