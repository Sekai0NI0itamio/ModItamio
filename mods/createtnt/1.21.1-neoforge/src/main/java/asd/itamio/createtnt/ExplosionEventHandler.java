package asd.itamio.createtnt;

import java.util.List;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Hooks {@link ExplosionEvent.Start} and {@link EntityJoinWorldEvent}.
 *
 * <p>On explosion: the vanilla explosion is cancelled and replaced with a
 * {@link EnhancedExplosion} that behaves like a high-explosive blast.</p>
 *
 * <p>On entity join: vanilla {@link PrimedTnt} is replaced with
 * {@link EntityEnhancedTNTPrimed} so the water-aware fuse logic applies to
 * all TNT ignited in the world (flint &amp; steel, redstone, arrows, etc.).</p>
 */
public class ExplosionEventHandler {

    @SubscribeEvent
    public void onExplosionStart(ExplosionEvent.Start event) {
        if (!(event.getLevel() instanceof Level)) {
            return;
        }
        Level level = (Level) event.getLevel();
        if (level.isClientSide) {
            return;
        }

        Explosion explosion = event.getExplosion();
        double x = explosion.center().x;
        double y = explosion.center().y;
        double z = explosion.center().z;

        // Find a primed TNT at the explosion point.
        List<PrimedTnt> tnts = level.getEntitiesOfClass(PrimedTnt.class,
            new AABB(x - 1.0D, y - 1.0D, z - 1.0D, x + 1.0D, y + 1.0D, z + 1.0D));
        if (tnts.isEmpty()) {
            return;
        }

        // Cancel the small vanilla explosion and run the enhanced explosion instead.
        event.setCanceled(true);

        // Randomize power per explosion: 1.0x to 3.0x so each TNT feels
        // different — some pop like a normal blast, others hit up to 3x
        // harder. Radius and damage scale with the multiplier; particle
        // size scales gently (0.25x the multiplier effect) off the full
        // tntStrength so the smoke cloud stays big even on weak rolls.
        float powerMult = 1.0F + level.getRandom().nextFloat() * 2.0F;
        float blockPower = ModConfig.blockPower * powerMult;
        float entityPower = ModConfig.entityPower * powerMult;
        float particlePower = ModConfig.tntStrength * (1.0F + (powerMult - 1.0F) * 0.25F);

        EnhancedExplosion enhancedExplosion = new EnhancedExplosion(level, tnts.get(0), x, y, z,
            blockPower, entityPower, false, true, particlePower);
        enhancedExplosion.explode();
        enhancedExplosion.finalizeExplosion(true);

        // Chain reaction: instantly detonate every primed TNT caught in the blast
        // (both already-lit TNT and TNT blocks ignited by this explosion). Setting
        // the fuse to 0 makes them detonate on the very next tick, so a cluster of
        // TNT goes off in rapid succession instead of being pushed around and
        // waiting out their fuses.
        float chainRadius = Math.max(blockPower, entityPower) * 2.0F + 4.0F;
        List<PrimedTnt> chained = level.getEntitiesOfClass(PrimedTnt.class,
            new AABB(x - chainRadius, y - chainRadius, z - chainRadius,
                x + chainRadius, y + chainRadius, z + chainRadius));
        for (PrimedTnt tnt : chained) {
            tnt.setFuse(0);
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
