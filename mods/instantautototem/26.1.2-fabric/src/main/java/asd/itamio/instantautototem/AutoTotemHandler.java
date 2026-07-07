package asd.itamio.instantautototem;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

public class AutoTotemHandler {

    public static void onPlayerTick(ServerPlayer player) {
        if (!InstantAutoTotem.config.enableAutoTotem) return;

        // Check if offhand has a totem
        ItemStack offhand = player.getOffhandItem();

        // If no totem in offhand, try to equip one
        if (offhand.isEmpty() || offhand.getItem() != Items.TOTEM_OF_UNDYING) {
            equipTotemIfAvailable(player);
        }
    }

    private static void equipTotemIfAvailable(ServerPlayer player) {
        try {
            var inventory = player.getInventory();

            // Search inventory for totem
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);

                if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                    // Found a totem! Swap it to offhand
                    ItemStack oldOffhand = player.getOffhandItem().copy();

                    // Set totem to offhand
                    player.setItemSlot(EquipmentSlot.OFFHAND, stack.copy());

                    // Put old offhand item back in the slot where totem was
                    if (!oldOffhand.isEmpty()) {
                        inventory.setItem(i, oldOffhand);
                    } else {
                        inventory.setItem(i, ItemStack.EMPTY);
                    }

                    // Show message
                    if (InstantAutoTotem.config.showMessages) {
                        player.sendSystemMessage(Component.literal("§6[Auto Totem] §aTotem equipped"));
                    }

                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error equipping totem: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
