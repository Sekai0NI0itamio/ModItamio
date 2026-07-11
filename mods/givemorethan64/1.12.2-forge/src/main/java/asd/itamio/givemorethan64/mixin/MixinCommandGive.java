package asd.itamio.givemorethan64.mixin;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandGive;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

@Mixin(CommandGive.class)
public abstract class MixinCommandGive extends CommandBase {

    /**
     * @author Itamio
     * @reason Allow /give with amounts exceeding item stack limit by distributing across multiple stacks.
     * The vanilla implementation caps the count at item.getItemStackLimit(), preventing
     * commands like /give @s egg 100. This overwrite removes that cap and handles
     * multi-stack distribution.
     */
    @Overwrite
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("commands.give.usage");
        }

        EntityPlayer entityplayer = getPlayer(server, sender, args[0]);
        Item item = getItemByText(sender, args[1]);

        // Parse the requested amount - no cap at item stack limit anymore
        int requestedAmount = args.length >= 3 ? parseInt(args[2], 1, Integer.MAX_VALUE) : 1;
        int damage = args.length >= 4 ? parseInt(args[3]) : 0;

        // Parse NBT data if present
        net.minecraft.nbt.NBTTagCompound nbtTag = null;
        if (args.length >= 5) {
            String s = buildString(args, 4);
            try {
                nbtTag = JsonToNBT.getTagFromJson(s);
            } catch (NBTException nbtexception) {
                throw new CommandException("commands.give.tagError", nbtexception.getMessage());
            }
        }

        int stackLimit = item.getItemStackLimit();
        int remaining = requestedAmount;
        int totalGiven = 0;

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

            totalGiven += stackSize;
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

    /**
     * @author Itamio
     * @reason Keep tab completions consistent with vanilla behavior.
     */
    @Overwrite
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        } else {
            return args.length == 2 ? getListOfStringsMatchingLastWord(args, Item.REGISTRY.getKeys()) : Collections.emptyList();
        }
    }

    /**
     * @author Itamio
     * @reason Keep username index check consistent with vanilla behavior.
     */
    @Overwrite
    public boolean isUsernameIndex(String[] args, int index) {
        return index == 0;
    }
}
