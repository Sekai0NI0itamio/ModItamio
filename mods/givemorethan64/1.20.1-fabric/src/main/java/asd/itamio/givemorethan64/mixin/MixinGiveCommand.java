package asd.itamio.givemorethan64.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.GiveCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Collection;

/**
 * Modifies the /give command to allow amounts exceeding an item's natural stack limit.
 * The vanilla implementation caps the total at (stack limit * 100), preventing
 * commands like /give @s egg 10000. This overwrite removes that cap entirely.
 */
@Mixin(GiveCommand.class)
public abstract class MixinGiveCommand {

    /**
     * @author Itamio
     * @reason Allow /give with any amount by removing the MAX_ALLOWED_ITEMSTACKS cap.
     * The original method handles multi-stack distribution correctly; we just remove
     * the limit check so any amount is allowed.
     */
    @Overwrite
    private static int giveItem(CommandSourceStack commandSourceStack, ItemInput itemInput, Collection<ServerPlayer> collection, int i) throws CommandSyntaxException {
        int maxStackSize = itemInput.getItem().getMaxStackSize();

        for (ServerPlayer serverPlayer : collection) {
            int remaining = i;

            while (remaining > 0) {
                int stackSize = Math.min(maxStackSize, remaining);
                remaining -= stackSize;
                ItemStack itemStack = itemInput.createItemStack(stackSize, false);
                boolean addedToInventory = serverPlayer.getInventory().add(itemStack);

                if (addedToInventory && itemStack.isEmpty()) {
                    itemStack.setCount(1);
                    ItemEntity itemEntity = serverPlayer.drop(itemStack, false);
                    if (itemEntity != null) {
                        itemEntity.makeFakeItem();
                    }

                    serverPlayer.level()
                        .playSound(
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
                    ItemEntity itemEntity = serverPlayer.drop(itemStack, false);
                    if (itemEntity != null) {
                        itemEntity.setNoPickUpDelay();
                        itemEntity.setTarget(serverPlayer.getUUID());
                    }
                }
            }
        }

        if (collection.size() == 1) {
            commandSourceStack.sendSuccess(
                () -> {
                    try {
                        return Component.translatable("commands.give.success.single", i, itemInput.createItemStack(1, false).getDisplayName(), collection.iterator().next().getDisplayName());
                    } catch (CommandSyntaxException e) {
                        return Component.literal("Gave " + i + " items");
                    }
                }, true
            );
        } else {
            commandSourceStack.sendSuccess(
                () -> {
                    try {
                        return Component.translatable("commands.give.success.single", i, itemInput.createItemStack(1, false).getDisplayName(), collection.size());
                    } catch (CommandSyntaxException e) {
                        return Component.literal("Gave " + i + " items to " + collection.size() + " players");
                    }
                }, true
            );
        }

        return collection.size();
    }
}
