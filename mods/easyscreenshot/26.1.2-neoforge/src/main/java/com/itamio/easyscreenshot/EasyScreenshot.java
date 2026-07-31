package com.itamio.easyscreenshot;

import com.itamio.ModInfoPrinter;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(EasyScreenshot.MOD_ID)
public class EasyScreenshot {
    public static final String MOD_ID = "easyscreenshot";
    public static final String MOD_NAME = "Easy Screenshot";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public EasyScreenshot(IEventBus modEventBus) {
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);

        ScreenshotConfig.load();

        EasyScreenshotCommands.register();

        LOGGER.info("Easy Screenshot initialized");
    }
}
