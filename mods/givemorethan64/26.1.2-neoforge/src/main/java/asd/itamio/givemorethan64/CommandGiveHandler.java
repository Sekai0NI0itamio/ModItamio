package asd.itamio.givemorethan64;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;

import java.util.Collection;

/**
 * Handles the /give command to allow amounts exceeding the vanilla 100-stack limit.
 * In modern Minecraft (1.13+), vanilla /give already handles multi-stack distribution,
 * but caps at 100 stacks (maxStackSize * 100). This handler removes that cap,
 * allowing any amount to be given.
 */
public class CommandGiveHandler {

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        try {
            ParseResults<CommandSourceStack> parse = event.getParseResults();
            CommandContextBuilder<CommandSourceStack> contextBuilder = parse.getContext();

            // Check if this is a "give" command by looking at the first literal node
            String commandName = null;
            for (ParsedCommandNode<CommandSourceStack> node : contextBuilder.getNodes()) {
                commandName = node.getNode().getName();
                break;
            }

            if (!"give".equals(commandName)) {
                return;
            }

            // Build the full command context to access all parsed arguments
            CommandContext<CommandSourceStack> context = contextBuilder.build(parse.getReader().getString());

            // Check if "count" argument exists using the builder's arguments map
            if (!contextBuilder.getArguments().containsKey("count")) {
                return; // No count specified, let vanilla handle it (defaults to 1)
            }

            // Get the count argument value
            int count = context.getArgument("count", Integer.class);

            // Get the item input
            ItemInput itemInput = context.getArgument("item", ItemInput.class);

            // Get the targets
            Collection<ServerPlayer> targets;
            try {
                targets = EntityArgument.getPlayers(context, "targets");
            } catch (CommandSyntaxException e) {
                return; // Invalid targets, let vanilla handle the error
            }

            // Build a prototype stack (count=1) to get the max stack size
            ItemStack prototypeStack = itemInput.createItemStack(1);
            int maxStackSize = prototypeStack.getMaxStackSize();
            int vanillaMaxAllowed = maxStackSize * 100;

            // If count is within vanilla limits, let vanilla handle it
            if (count <= vanillaMaxAllowed) {
                return;
            }

            // We need to handle this oversized give ourselves — cancel vanilla execution
            event.setCanceled(true);

            CommandSourceStack source = context.getSource();

            // Give items to each target in batches of maxStackSize
            for (ServerPlayer player : targets) {
                int remaining = count;

                while (remaining > 0) {
                    int size = Math.min(maxStackSize, remaining);
                    remaining -= size;
                    // Create ItemStack directly (bypassing createItemStack validation)
                    ItemStack stackToGive = new ItemStack(itemInput.item(), size, itemInput.components());

                    // Try to add to inventory
                    boolean added = player.getInventory().add(stackToGive);
                    if (added && stackToGive.isEmpty()) {
                        // Items were fully added to inventory
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
                    } else if (!stackToGive.isEmpty()) {
                        // Items couldn't fit in inventory, drop the remainder
                        ItemEntity drop = player.drop(stackToGive, false);
                        if (drop != null) {
                            drop.setNoPickUpDelay();
                            drop.setTarget(player.getUUID());
                        }
                    }
                }

                // Send success message
                source.sendSuccess(
                        () -> Component.translatable(
                                "commands.give.success.single", count, prototypeStack.getDisplayName(), player.getDisplayName()
                        ),
                        true
                );
            }
        } catch (Exception e) {
            Givemorethan64.LOGGER.error("Error in CommandGiveHandler.onCommand: {}", e.getMessage());
            // If something goes wrong, do nothing — vanilla command will execute normally
        }
    }
}
