package asd.itamio.dontbreakcropsthatarenotgrownyet;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(DontBreakcropsthatarenotgrownyet.MOD_ID)
public class DontBreakcropsthatarenotgrownyet {

    public static final String MOD_ID = "dontbreakcropsthatarenotgrownyet";
    public static final String MOD_NAME = "Dont Break crops that are not grown yet (1.20.1 Forge)";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DontBreakcropsthatarenotgrownyet() {
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("Dont Break crops initialized — unripe crops are protected from player breaks (sneak to bypass)");
    }
}
