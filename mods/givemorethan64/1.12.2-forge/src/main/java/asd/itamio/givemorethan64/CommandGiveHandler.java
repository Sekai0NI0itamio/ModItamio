package asd.itamio.givemorethan64;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandGive;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Handles the /give command to allow amounts exceeding an item's natural stack limit.
 * When a player uses /give with an amount larger than the item's stack limit (e.g.,
 * /give @s egg 100), this handler cancels the vanilla execution and distributes the
 * items across multiple stacks that respect the item's stack limit.
 */
public class CommandGiveHandler {

    /**
     * Intercepts the CommandEvent before /give executes.
     * If the command is /give with an amount exceeding the item's max stack size,
     * cancels the vanilla execution and runs the multi-stack distribution logic.
     */
    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        ICommand command = event.getCommand();
        if (!(command instanceof CommandGive)) {
            return;
        }

        String[] args = event.getParameters();
        ICommandSender sender = event.getSender();

        // Need at least: /give <player> <item> [amount]
        if (args.length < 2) {
            return;
        }

        // Get the server from the sender's world
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return;
        }

        // Try to parse the amount - if out of range, let vanilla handle it (will show error)
        int requestedAmount;
        try {
            requestedAmount = Integer.parseInt(args[2]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return; // Let vanilla handle invalid amounts (will show error)
        }

        // Resolve the player and item using CommandBase's static methods
        EntityPlayer entityplayer;
        Item item;
        try {
            entityplayer = CommandBase.getPlayer(server, sender, args[0]);
            item = CommandBase.getItemByText(sender, args[1]);
        } catch (CommandException e) {
            return; // Let vanilla handle if player/item not found
        }

        // If the amount is within stack limit, let vanilla handle it normally
        int stackLimit = item.getItemStackLimit();
        if (requestedAmount <= stackLimit) {
            return;
        }

        // We need to handle this ourselves - cancel the vanilla execution
        event.setCanceled(true);

        // Parse damage value (optional 4th arg)
        int damage = 0;
        if (args.length >= 4) {
            try {
                damage = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                // Invalid damage value, default to 0
            }
        }

        // Parse NBT data if present (5th arg onwards)
        net.minecraft.nbt.NBTTagCompound nbtTag = null;
        if (args.length >= 5) {
            StringBuilder sb = new StringBuilder();
            for (int i = 4; i < args.length; i++) {
                if (i > 4) sb.append(" ");
                sb.append(args[i]);
            }
            try {
                nbtTag = JsonToNBT.getTagFromJson(sb.toString());
            } catch (NBTException nbtexception) {
                TextComponentTranslation errorMsg = new TextComponentTranslation("commands.give.tagError", nbtexception.getMessage());
                sender.sendMessage(errorMsg);
                return;
            }
        }

        int remaining = requestedAmount;

        // Create stacks in batches of the stack limit
        while (remaining > 0) {
            int stackSize = Math.min(remaining, stackLimit);
            ItemStack itemstack = new ItemStack(item, stackSize, damage);
            if (nbtTag != null) {
                itemstack.setTagCompound(nbtTag.copy());
            }

            // Try to add to inventory
            boolean addedToInventory = entityplayer.inventory.addItemStackToInventory(itemstack);

            if (addedToInventory) {
                entityplayer.world.playSound(null, entityplayer.posX, entityplayer.posY, entityplayer.posZ,
                        SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS,
                        0.2F, ((entityplayer.getRNG().nextFloat() - entityplayer.getRNG().nextFloat()) * 0.7F + 1.0F) * 2.0F);
            }

            // If not fully added to inventory (or partially), drop remainder
            if (!itemstack.isEmpty()) {
                EntityItem entityitem = entityplayer.dropItem(itemstack, false);
                if (entityitem != null) {
                    entityitem.setNoPickupDelay();
                    entityitem.setOwner(entityplayer.getName());
                }
            }

            remaining -= stackSize;
        }

        entityplayer.inventoryContainer.detectAndSendChanges();

        // Send success message using the requested amount
        TextComponentTranslation successMsg = new TextComponentTranslation("commands.give.success",
                new ItemStack(item, 1, damage).getTextComponent(), requestedAmount, entityplayer.getName());
        sender.sendMessage(successMsg);

        // Set command stat to total affected items
        sender.setCommandStat(CommandResultStats.Type.AFFECTED_ITEMS, requestedAmount);
    }
}
