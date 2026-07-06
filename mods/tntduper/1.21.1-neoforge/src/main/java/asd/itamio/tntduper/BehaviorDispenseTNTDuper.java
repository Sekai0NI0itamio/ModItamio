package asd.itamio.tntduper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class BehaviorDispenseTNTDuper extends DefaultDispenseItemBehavior {

    @Override
    protected ItemStack execute(BlockSource source, ItemStack stack) {
        try {
            Direction direction = source.state().getValue(DispenserBlock.FACING);
            BlockPos blockpos = source.pos().relative(direction);
            PrimedTnt primedTnt = new PrimedTnt(
                source.level(),
                (double) blockpos.getX() + 0.5D,
                (double) blockpos.getY(),
                (double) blockpos.getZ() + 0.5D,
                null
            );
            source.level().addFreshEntity(primedTnt);
            source.level().playSound(
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
