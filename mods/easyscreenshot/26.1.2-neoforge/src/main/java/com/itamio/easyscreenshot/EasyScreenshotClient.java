package com.itamio.easyscreenshot;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = EasyScreenshot.MOD_ID, value = Dist.CLIENT)
public class EasyScreenshotClient {
    private static KeyMapping galleryKey;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.register(new ScreenshotEventListener());
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        galleryKey = new KeyMapping(
                "key.easyscreenshot.gallery",
                GLFW.GLFW_KEY_G,
                new KeyMapping.Category(Identifier.fromNamespaceAndPath("easyscreenshot", "gallery"))
        );
        event.register(galleryKey);
    }

    public static KeyMapping getGalleryKey() {
        return galleryKey;
    }
}
