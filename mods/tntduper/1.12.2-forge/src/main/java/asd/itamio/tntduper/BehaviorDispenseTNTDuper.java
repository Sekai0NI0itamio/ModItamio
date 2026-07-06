package asd.itamio.tntduper;

import net.minecraft.block.BlockDispenser;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BehaviorDispenseTNTDuper extends BehaviorDefaultDispenseItem {

    @Override
    protected ItemStack dispenseStack(IBlockSource source, ItemStack stack) {
        try {
            World world = source.getWorld();
            BlockPos blockpos = source.getBlockPos().offset(source.getBlockState().getValue(BlockDispenser.FACING));
            EntityTNTPrimed entitytntprimed = new EntityTNTPrimed(
                world,
                (double) blockpos.getX() + 0.5D,
                (double) blockpos.getY(),
                (double) blockpos.getZ() + 0.5D,
                null // No living entity thrower
            );
            world.spawnEntity(entitytntprimed);
            world.playSound(
                null,
                entitytntprimed.posX,
                entitytntprimed.posY,
                entitytntprimed.posZ,
                SoundEvents.ENTITY_TNT_PRIMED,
                SoundCategory.BLOCKS,
                1.0F,
                1.0F
            );
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] TNT Duper failed to spawn primed TNT: " + e.getMessage());
            e.printStackTrace();
        }

        // Return the stack unchanged — this is the "duping" behavior.
        // The TNT item is NOT consumed, allowing infinite dispensing.
        return stack;
    }
}
