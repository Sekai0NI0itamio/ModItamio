package asd.itamio.instantautototem;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class AutoTotemHandler {

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        try {
            if (!InstantAutoTotem.config.enableAutoTotem) return;

            Player player = event.getEntity();
            if (player.level().isClientSide()) return; // Server-side only

            // Check if offhand has a totem
            ItemStack offhand = player.getOffhandItem();

            // If no totem in offhand, try to equip one
            if (offhand.isEmpty() || offhand.getItem() != Items.TOTEM_OF_UNDYING) {
                equipTotemIfAvailable(player);
            }
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error in AutoTotemHandler.onPlayerTick: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void equipTotemIfAvailable(Player player) {
        try {
            Inventory inventory = player.getInventory();

            // Search inventory for totem (slots 0-35 = main inventory + hotbar, skip armor and offhand)
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);

                if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                    // Found a totem! Swap it to offhand
                    ItemStack oldOffhand = player.getOffhandItem().copy();

                    // Set totem to offhand (slot 40 in player inventory is offhand)
                    inventory.setItem(Inventory.SLOT_OFFHAND, stack.copy());

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
