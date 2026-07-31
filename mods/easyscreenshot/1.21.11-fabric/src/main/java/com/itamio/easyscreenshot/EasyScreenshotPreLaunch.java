package com.itamio.easyscreenshot;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class EasyScreenshotPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        System.setProperty("java.awt.headless", "false");
    }
}
