package com.itamio.easyscreenshot;

import com.itamio.ModInfoPrinter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class EasyScreenshotClient implements ClientModInitializer {
    private static KeyMapping galleryKey;

    @Override
    public void onInitializeClient() {
        ModInfoPrinter.print(org.slf4j.LoggerFactory.getLogger("easyscreenshot-client")::info,
                "Easy Screenshot Client", "1.0.0");

        galleryKey = new KeyMapping(
                "key.easyscreenshot.gallery",
                GLFW.GLFW_KEY_G,
                new KeyMapping.Category(Identifier.fromNamespaceAndPath("easyscreenshot", "gallery"))
        );
        KeyBindingHelper.registerKeyBinding(galleryKey);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (galleryKey.consumeClick() && client.player != null) {
                Minecraft.getInstance().setScreen(
                        new ScreenshotGalleryScreen(client.screen)
                );
            }
        });

        ScreenshotEventListener.register();
    }

    public static KeyMapping getGalleryKey() {
        return galleryKey;
    }
}
