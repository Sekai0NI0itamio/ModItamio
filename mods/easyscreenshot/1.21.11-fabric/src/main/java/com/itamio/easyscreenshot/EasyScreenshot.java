package com.itamio.easyscreenshot;

import com.itamio.ModInfoPrinter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EasyScreenshot implements ModInitializer {
    public static final String MOD_ID = "easyscreenshot";
    public static final String MOD_NAME = "Easy Screenshot";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);

        ScreenshotConfig.load();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            EasyScreenshotCommands.register(dispatcher);
        });

        LOGGER.info("Easy Screenshot initialized");
    }
}
