package asd.itamio.seedprotect.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Cancels farmland trampling by intercepting the {@code turnToDirt} call inside
 * {@link FarmlandBlock#fallOn}. The redirect is a no-op so the farmland block
 * stays as farmland and any planted crop remains intact. Fall damage is
 * preserved because {@code super.fallOn(...)} still runs after the (now-empty)
 * trample branch — only the dirt conversion is suppressed.
 *
 * <p>Note: in MC 26.1.2 the vanilla class was renamed from {@code FarmBlock} to
 * {@code FarmlandBlock}, and the {@code fallOn} fall-distance parameter was
 * widened from {@code float} to {@code double}.</p>
 */
@Mixin(FarmlandBlock.class)
public abstract class FarmBlockMixin {

    @Redirect(
        method = "fallOn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/FarmlandBlock;turnToDirt(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"
        )
    )
    private void seedprotect$cancelTrample(Entity entity, BlockState blockState, Level level, BlockPos blockPos) {
        // No-op — intentionally do NOT call turnToDirt so farmland stays intact.
    }
}
