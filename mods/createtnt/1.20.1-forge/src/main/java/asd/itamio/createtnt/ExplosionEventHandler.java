package asd.itamio.createtnt;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Hooks entity join and rocket damage. Explosion replacement (TNT and
 * creepers) lives in {@link asd.itamio.createtnt.mixin.ExplosionMixin} instead
 * of {@code ExplosionEvent.Start}: cancelling that event stops the blast logic
 * but the server still broadcasts the vanilla explode packet, which made
 * creepers keep their vanilla particles. The mixin cancels the whole explode
 * method before the packet is sent.
 *
 * <p>On entity join: vanilla {@link PrimedTnt} is replaced with
 * {@link EntityEnhancedTNTPrimed} so the water-aware fuse logic applies to
 * all TNT ignited in the world (flint &amp; steel, redstone, arrows, etc.),
 * and shot fireworks are handed to {@link RocketTracker}.</p>
 */
public class ExplosionEventHandler {

    /** Suppresses vanilla damage while a tracked rocket is being redirected. */
    @SubscribeEvent
    public void onRocketDamage(LivingHurtEvent event) {
        if (!ModConfig.rocketShellExplosions) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof FireworkRocketEntity
                && RocketTracker.isTracked(direct.getUUID())) {
            event.setCanceled(true);
        }
    }

    /**
     * Replaces vanilla {@link PrimedTnt} with {@link EntityEnhancedTNTPrimed}
     * when it joins the world. This ensures all TNT — whether ignited by flint
     * &amp; steel, redstone, arrows, or explosions — gets the water-aware fuse
     * logic.
     *
     * <p>Only replaces on the server side. The enhanced entity is then synced
     * to clients via the spawn packet.</p>
     */
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof FireworkRocketEntity rocket && rocket.isShotAtAngle()) {
            RocketTracker.track((ServerLevel) event.getLevel(), rocket);
            return;
        }
        // Use exact class match so we don't catch our own enhanced TNT subclass.
        if (event.getEntity().getClass() != PrimedTnt.class) {
            return;
        }

        PrimedTnt vanilla = (PrimedTnt) event.getEntity();
        EntityEnhancedTNTPrimed enhanced = new EntityEnhancedTNTPrimed(
            event.getLevel(), vanilla.getX(), vanilla.getY(), vanilla.getZ(),
            vanilla.getOwner());
        enhanced.setFuse(vanilla.getFuse());
        enhanced.setDeltaMovement(vanilla.getDeltaMovement());

        event.setCanceled(true);
        event.getLevel().addFreshEntity(enhanced);
    }
}
