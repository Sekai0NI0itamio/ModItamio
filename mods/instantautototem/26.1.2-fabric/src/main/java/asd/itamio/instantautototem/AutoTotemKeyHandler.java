package asd.itamio.instantautototem;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class AutoTotemKeyHandler {

    private static KeyMapping toggleKey;

    public static void register() {
        try {
            toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "Toggle Auto Totem",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_O,
                    KeyMapping.Category.MISC
            ));

            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (toggleKey != null && toggleKey.consumeClick()) {
                    try {
                        // Toggle the mod
                        InstantAutoTotem.config.enableAutoTotem = !InstantAutoTotem.config.enableAutoTotem;

                        // Save config
                        InstantAutoTotem.config.save();

                        // Get player
                        var player = Minecraft.getInstance().player;
                        if (player != null) {
                            String status = InstantAutoTotem.config.enableAutoTotem ? "§aEnabled" : "§cDisabled";
                            player.sendSystemMessage(Component.literal("§6[Auto Totem] " + status));
                        }
                    } catch (Exception e) {
                        System.err.println("[MODAPP-ERROR] Error in key handler: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to register keybinding: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
