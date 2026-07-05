package asd.itamio.seedprotect;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(SeedProtect.MOD_ID)
public class SeedProtect {

    public static final String MOD_ID = "seedprotect";
    public static final String MOD_NAME = "Seed Protect (1.21.1 NeoForge)";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SeedProtect() {
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("Seed Protect initialized — farmland trampling is cancelled for players and mobs");
    }
}
