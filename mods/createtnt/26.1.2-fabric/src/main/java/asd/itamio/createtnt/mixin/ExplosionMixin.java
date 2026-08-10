package asd.itamio.createtnt.mixin;

import asd.itamio.createtnt.EnhancedExplosion;
import asd.itamio.createtnt.ModConfig;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla explosions that originate from a primed TNT with our
 * {@link EnhancedExplosion}. Fabric has no explosion event, so we hook the
 * server-side explode entry point directly.
 *
 * <p>1.21.11: the explode entry point returns void and takes an extra
 * {@code WeightedList<ExplosionParticleInfo>} (the vanilla explosion-particle
 * pool) — we cancel the call, so that pool is never used.</p>
 */
@Mixin(ServerLevel.class)
public class ExplosionMixin {

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void createtnt$explode(Entity entity, DamageSource damageSource,
                                   ExplosionDamageCalculator calculator,
                                   double x, double y, double z, float radius, boolean causesFire,
                                   Level.ExplosionInteraction interaction,
                                   ParticleOptions smallParticle, ParticleOptions bigParticle,
                                   WeightedList<ExplosionParticleInfo> explosionParticles,
                                   Holder<SoundEvent> sound,
                                   CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;

        // Find a primed TNT at the explosion point.
        List<PrimedTnt> tnts = level.getEntitiesOfClass(PrimedTnt.class,
            new AABB(x - 1.0D, y - 1.0D, z - 1.0D, x + 1.0D, y + 1.0D, z + 1.0D));
        if (tnts.isEmpty()) {
            return;
        }

        // Run the enhanced explosion instead of the vanilla one.
        float powerMult = 1.0F + level.getRandom().nextFloat() * 2.0F;
        float blockPower = ModConfig.blockPower * powerMult;
        float entityPower = ModConfig.entityPower * powerMult;
        float particlePower = ModConfig.tntStrength * (1.0F + (powerMult - 1.0F) * 0.25F);

        EnhancedExplosion enhancedExplosion = new EnhancedExplosion(level, tnts.get(0), x, y, z,
            blockPower, entityPower, false, true, particlePower);
        enhancedExplosion.detonate();
        enhancedExplosion.finalizeExplosion(true);

        // Chain reaction: instantly detonate every primed TNT caught in the blast.
        float chainRadius = Math.max(blockPower, entityPower) * 2.0F + 4.0F;
        List<PrimedTnt> chained = level.getEntitiesOfClass(PrimedTnt.class,
            new AABB(x - chainRadius, y - chainRadius, z - chainRadius,
                x + chainRadius, y + chainRadius, z + chainRadius));
        for (PrimedTnt tnt : chained) {
            tnt.setFuse(0);
        }

        // Vanilla packets/particles are fully replaced by our own packet.
        ci.cancel();
    }
}
