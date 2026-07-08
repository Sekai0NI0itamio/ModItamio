package asd.itamio.instantautototem;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

public class AutoTotemKeyHandler {

    private static KeyMapping toggleKey;

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        try {
            toggleKey = new KeyMapping(
                    "Toggle Auto Totem",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_O,
                    "Instant Auto Totem"
            );
            event.register(toggleKey);
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to register keybinding: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        try {
            if (toggleKey != null && toggleKey.consumeClick()) {
                // Toggle the mod
                InstantAutoTotem.config.enableAutoTotem = !InstantAutoTotem.config.enableAutoTotem;

                // Get player
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    String status = InstantAutoTotem.config.enableAutoTotem ? "§aEnabled" : "§cDisabled";
                    mc.player.sendSystemMessage(Component.literal("§6[Auto Totem] " + status));
                }
            }
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error in key handler: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
