package asd.itamio.tntduper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;

public class BehaviorDispenseTNTDuper extends DefaultDispenseItemBehavior {

    @Override
    protected ItemStack execute(BlockSource source, ItemStack stack) {
        try {
            Direction direction = source.getBlockState().getValue(DispenserBlock.FACING);
            BlockPos blockpos = source.getPos().relative(direction);
            PrimedTnt primedTnt = new PrimedTnt(
                source.getLevel(),
                (double) blockpos.getX() + 0.5D,
                (double) blockpos.getY(),
                (double) blockpos.getZ() + 0.5D,
                null
            );
            source.getLevel().addFreshEntity(primedTnt);
            source.getLevel().playSound(
                null,
                primedTnt.getX(),
                primedTnt.getY(),
                primedTnt.getZ(),
                SoundEvents.TNT_PRIMED,
                SoundSource.BLOCKS,
                1.0F,
                1.0F
            );
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] TNT Duper failed to spawn primed TNT: " + e.getMessage());
            e.printStackTrace();
        }

        // Return the stack unchanged — this is the "duping" behavior.
        return stack;
    }
}
