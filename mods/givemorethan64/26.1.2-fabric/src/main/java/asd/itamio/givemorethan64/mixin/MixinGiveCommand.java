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
 * Mixin for GiveCommand that removes the maximum item stack count cap,
 * allowing /give to distribute any number of items across multiple stacks.
 */
@Mixin(GiveCommand.class)
public abstract class MixinGiveCommand {

    /**
     * @author Itamio
     * @reason Remove the max stack size cap (maxStackSize * 100) from the /give command,
     * allowing players to request any number of items. Items are distributed across
     * multiple stacks respecting each item's natural stack limit.
     */
    @Overwrite
    private static int giveItem(final CommandSourceStack source, final ItemInput input, final Collection<ServerPlayer> players, final int count) throws CommandSyntaxException {
        ItemStack prototypeItemStack = input.createItemStack(1);
        int maxStackSize = prototypeItemStack.getMaxStackSize();

        for (ServerPlayer player : players) {
            int remaining = count;

            while (remaining > 0) {
                int size = Math.min(maxStackSize, remaining);
                remaining -= size;
                ItemStack copyToDrop = prototypeItemStack.copyWithCount(size);
                boolean added = player.getInventory().add(copyToDrop);
                if (added && copyToDrop.isEmpty()) {
                    ItemEntity drop = player.drop(prototypeItemStack.copy(), false);
                    if (drop != null) {
                        drop.makeFakeItem();
                    }

                    player.level()
                        .playSound(
                            null,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            SoundEvents.ITEM_PICKUP,
                            SoundSource.PLAYERS,
                            0.2F,
                            ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F
                        );
                    player.containerMenu.broadcastChanges();
                } else {
                    ItemEntity drop = player.drop(copyToDrop, false);
                    if (drop != null) {
                        drop.setNoPickUpDelay();
                        drop.setTarget(player.getUUID());
                    }
                }
            }
        }

        if (players.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable("commands.give.success.single", count, prototypeItemStack.getDisplayName(), players.iterator().next().getDisplayName()), true
            );
        } else {
            source.sendSuccess(() -> Component.translatable("commands.give.success.single", count, prototypeItemStack.getDisplayName(), players.size()), true);
        }

        return players.size();
    }
}
