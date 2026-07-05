package asd.itamio.smoothcontainer;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(SmoothContainer.MOD_ID)
public class SmoothContainer {

    public static final String MOD_ID = "smoothcontainer";
    public static final String MOD_NAME = "SmoothContainer (1.20.1 Forge)";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SmoothContainer() {
        LOGGER.info(ModInfoPrinter.build(MOD_NAME, VERSION));
        LOGGER.info("SmoothContainer initialized — container GUI stutter mitigation active");
    }
}
