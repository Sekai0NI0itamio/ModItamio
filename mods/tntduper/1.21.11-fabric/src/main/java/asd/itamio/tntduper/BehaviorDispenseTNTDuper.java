package asd.itamio.tntduper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;

public class BehaviorDispenseTNTDuper implements DispenseItemBehavior {

    private final DispenseItemBehavior defaultBehavior = new DefaultDispenseItemBehavior();

    @Override
    public ItemStack dispense(BlockSource source, ItemStack stack) {
        try {
            ServerLevel level = source.level();
            Direction facing = source.state().getValue(DispenserBlock.FACING);
            BlockPos spawnPos = source.pos().relative(facing);

            PrimedTnt primedTnt = new PrimedTnt(
                level,
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                null // No living entity thrower
            );
            level.addFreshEntity(primedTnt);
            level.playSound(
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
        // The TNT item is NOT consumed, allowing infinite dispensing.
        return stack;
    }
}
