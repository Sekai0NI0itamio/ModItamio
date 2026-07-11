package asd.itamio.givemorethan64;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Collection;

/**
 * Handles the /give command to allow amounts exceeding an item's natural stack limit.
 * Intercepts CommandEvent before /give executes. If the amount exceeds (maxStackSize * 100),
 * cancels the vanilla execution and runs the multi-stack distribution logic without the cap.
 */
public class CommandGiveHandler {

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> results = event.getParseResults();

        // Check if this is the "give" command by examining the parsed nodes
        var nodes = results.getContext().getNodes();
        if (nodes.isEmpty()) return;

        String commandName = nodes.get(0).getNode().getName();
        if (!commandName.equals("give")) return;

        // Build the command context to access arguments
        String input = results.getReader().getString();
        CommandContext<CommandSourceStack> ctx = results.getContext().build(input);

        try {
            int count = ctx.getArgument("count", Integer.class);
            ItemInput itemInput = ctx.getArgument("item", ItemInput.class);
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
            CommandSourceStack source = results.getContext().getSource();

            int maxStackSize = itemInput.getItem().getMaxStackSize();
            int vanillaLimit = maxStackSize * 100;

            // If count is within the vanilla limit, let vanilla handle it
            if (count <= vanillaLimit) return;

            // Cancel the vanilla execution
            event.setCanceled(true);

            // Execute our own give logic without the cap
            giveItems(source, itemInput, targets, count);

        } catch (Exception e) {
            // If anything goes wrong, let vanilla handle it
        }
    }

    /**
     * Gives items to players, distributing across multiple stacks as needed.
     * Similar to GiveCommand.giveItem but without the MAX_ALLOWED_ITEMSTACKS cap.
     */
    private static void giveItems(CommandSourceStack source, ItemInput itemInput, Collection<ServerPlayer> targets, int count) {
        int maxStackSize = itemInput.getItem().getMaxStackSize();

        for (ServerPlayer serverPlayer : targets) {
            int remaining = count;

            while (remaining > 0) {
                int stackSize = Math.min(maxStackSize, remaining);
                remaining -= stackSize;
                ItemStack itemStack;
                try {
                    itemStack = itemInput.createItemStack(stackSize, false);
                } catch (CommandSyntaxException e) {
                    break;
                }
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

        // Send success message
        ItemStack singleStack;
        try {
            singleStack = itemInput.createItemStack(1, false);
        } catch (CommandSyntaxException e) {
            singleStack = null;
        }

        if (singleStack == null) {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.literal("Gave " + count + " items to " + targets.iterator().next().getDisplayName().getString()), true
                );
            } else {
                source.sendSuccess(
                    () -> Component.literal("Gave " + count + " items to " + targets.size() + " players"), true
                );
            }
        } else if (targets.size() == 1) {
            Component displayName = singleStack.getDisplayName();
            source.sendSuccess(
                () -> Component.translatable("commands.give.success.single", count, displayName, targets.iterator().next().getDisplayName()), true
            );
        } else {
            Component displayName = singleStack.getDisplayName();
            source.sendSuccess(
                () -> Component.translatable("commands.give.success.single", count, displayName, targets.size()), true
            );
        }
    }
}
