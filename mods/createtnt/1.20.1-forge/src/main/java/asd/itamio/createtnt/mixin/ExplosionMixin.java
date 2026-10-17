package asd.itamio.createtnt.mixin;

import asd.itamio.createtnt.EnhancedExplosion;
import asd.itamio.createtnt.ModConfig;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces vanilla explosions that originate from a primed TNT or a creeper
 * with our {@link EnhancedExplosion}, hooking the server-side explode entry
 * point directly.
 *
 * <p>Forge's {@code ExplosionEvent.Start} is not used for this: cancelling it
 * stops the blast logic, but {@code ServerLevel.explode} still broadcasts the
 * vanilla explode packet to nearby players afterwards, so clients keep
 * rendering the vanilla explosion particles and boom. Cancelling this method
 * at HEAD skips the packet too, leaving only our own explosion effect.</p>
 */
@Mixin(ServerLevel.class)
public class ExplosionMixin {

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void createtnt$explode(Entity entity, DamageSource damageSource,
                                   ExplosionDamageCalculator calculator,
                                   double x, double y, double z, float radius, boolean causesFire,
                                   Level.ExplosionInteraction interaction,
                                   CallbackInfoReturnable<Explosion> cir) {
        ServerLevel level = (ServerLevel) (Object) this;

        if (ModConfig.enhancedCreeperExplosions && entity instanceof Creeper) {
            float powerMult = 1.0F + level.getRandom().nextFloat() * 2.0F;
            float blockPower = ModConfig.blockPower * powerMult;
            float entityPower = ModConfig.entityPower * powerMult;
            float particlePower = ModConfig.tntStrength * (1.0F + (powerMult - 1.0F) * 0.25F);
            EnhancedExplosion enhancedExplosion = new EnhancedExplosion(level, entity, x, y, z,
                blockPower, entityPower, false, true, particlePower);
            enhancedExplosion.explode();
            enhancedExplosion.finalizeExplosion(true);
            float chainRadius = Math.max(blockPower, entityPower) * 2.0F + 4.0F;
            List<PrimedTnt> chained = level.getEntitiesOfClass(PrimedTnt.class,
                new AABB(x - chainRadius, y - chainRadius, z - chainRadius,
                    x + chainRadius, y + chainRadius, z + chainRadius));
            for (PrimedTnt tnt : chained) tnt.setFuse(0);
            cir.setReturnValue(enhancedExplosion);
            cir.cancel();
            return;
        }

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
        enhancedExplosion.explode();
        enhancedExplosion.finalizeExplosion(true);

        // Chain reaction: instantly detonate every primed TNT caught in the blast.
        float chainRadius = Math.max(blockPower, entityPower) * 2.0F + 4.0F;
        List<PrimedTnt> chained = level.getEntitiesOfClass(PrimedTnt.class,
            new AABB(x - chainRadius, y - chainRadius, z - chainRadius,
                x + chainRadius, y + chainRadius, z + chainRadius));
        for (PrimedTnt tnt : chained) {
            tnt.setFuse(0);
        }

        // Return the already-run explosion so callers get a valid object;
        // vanilla packets/particles are fully replaced by our own packet.
        cir.setReturnValue(enhancedExplosion);
        cir.cancel();
    }
}
