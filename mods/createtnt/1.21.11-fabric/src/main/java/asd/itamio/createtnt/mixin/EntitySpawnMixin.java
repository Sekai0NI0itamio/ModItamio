package asd.itamio.createtnt.mixin;

import asd.itamio.createtnt.EntityEnhancedTNTPrimed;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces vanilla {@link PrimedTnt} with {@link EntityEnhancedTNTPrimed}
 * when it spawns, so all TNT (flint &amp; steel, redstone, arrows, chains)
 * gets the water-aware fuse logic. Fabric has no entity-join event, so we
 * hook the spawn entry point.
 */
@Mixin(ServerLevel.class)
public class EntitySpawnMixin {

    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void createtnt$replaceTnt(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        // Exact class match so we don't catch our own enhanced subclass.
        if (entity.getClass() != PrimedTnt.class) {
            return;
        }
        ServerLevel level = (ServerLevel) (Object) this;
        PrimedTnt vanilla = (PrimedTnt) entity;

        EntityEnhancedTNTPrimed enhanced = new EntityEnhancedTNTPrimed(
            level, vanilla.getX(), vanilla.getY(), vanilla.getZ(), vanilla.getOwner());
        enhanced.setFuse(vanilla.getFuse());
        enhanced.setDeltaMovement(vanilla.getDeltaMovement());

        cir.setReturnValue(level.addFreshEntity(enhanced));
        cir.cancel();
    }
}
