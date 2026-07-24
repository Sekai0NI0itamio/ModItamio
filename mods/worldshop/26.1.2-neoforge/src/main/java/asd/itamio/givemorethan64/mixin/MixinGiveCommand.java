package asd.itamio.givemorethan64.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.server.commands.GiveCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Collection;

@Mixin(GiveCommand.class)
public abstract class MixinGiveCommand {

    /**
     * @author Itamio
     * @reason Remove the item count cap in /give command to allow amounts above stack limit.
     * Vanilla caps at itemStack.getMaxStackSize() * 100, which prevents large give amounts.
     * This removes the cap entirely and distributes items across multiple stacks.
     */
    @Overwrite
    private static int giveItem(CommandSourceStack commandSourceStack, ItemInput itemInput, Collection<ServerPlayer> collection, int count) throws CommandSyntaxException {
        ItemStack itemStack = itemInput.createItemStack(1);
        int maxStackSize = itemStack.getMaxStackSize();

        for (ServerPlayer serverPlayer : collection) {
            int remaining = count;

            while (remaining > 0) {
                int batchSize = Math.min(maxStackSize, remaining);
                remaining -= batchSize;
                ItemStack itemStack2 = itemInput.createItemStack(batchSize);
                boolean addedToInventory = serverPlayer.getInventory().add(itemStack2);
                if (addedToInventory && itemStack2.isEmpty()) {
                    serverPlayer.level().playSound(
                            null,
                            serverPlayer.getX(),
                            serverPlayer.getY(),
                            serverPlayer.getZ(),
                            SoundEvents.ITEM_PICKUP,
                            SoundSource.PLAYERS,
                            0.2F,
                            ((serverPlayer.getRandom().nextFloat() - serverPlayer.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F
                    );
                    serverPlayer.containerMenu.broadcastChanges();
                } else {
                    ItemEntity itemEntity = serverPlayer.drop(itemStack2, false);
                    if (itemEntity != null) {
                        itemEntity.setNoPickUpDelay();
                        itemEntity.setTarget(serverPlayer.getUUID());
                    }
                }
            }
        }

        if (collection.size() == 1) {
            commandSourceStack.sendSuccess(
                    () -> net.minecraft.network.chat.Component.translatable("commands.give.success.single", count, itemStack.getDisplayName(), collection.iterator().next().getDisplayName()),
                    true
            );
        } else {
            commandSourceStack.sendSuccess(
                    () -> net.minecraft.network.chat.Component.translatable("commands.give.success.single", count, itemStack.getDisplayName(), collection.size()),
                    true
            );
        }

        return collection.size();
    }
}
