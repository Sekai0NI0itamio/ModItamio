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
    private static int giveItem(CommandSourceStack commandSourceStack, ItemInput itemInput, Collection<ServerPlayer> collection, int i) throws CommandSyntaxException {
        ItemStack itemStack = itemInput.createItemStack(1, false);
        int j = itemStack.getMaxStackSize();

        for (ServerPlayer serverPlayer : collection) {
            int l = i;

            while (l > 0) {
                int m = Math.min(j, l);
                l -= m;
                ItemStack itemStack2 = itemInput.createItemStack(m, false);
                boolean bl = serverPlayer.getInventory().add(itemStack2);
                if (bl && itemStack2.isEmpty()) {
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
                () -> Component.translatable("commands.give.success.single", i, itemStack.getDisplayName(), collection.iterator().next().getDisplayName()), true
            );
        } else {
            commandSourceStack.sendSuccess(() -> Component.translatable("commands.give.success.single", i, itemStack.getDisplayName(), collection.size()), true);
        }

        return collection.size();
    }
}
