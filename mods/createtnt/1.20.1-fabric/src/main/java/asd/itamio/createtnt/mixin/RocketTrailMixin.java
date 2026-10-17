package asd.itamio.createtnt.mixin;

import asd.itamio.createtnt.ModConfig;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hides the vanilla white firework trail for shell rockets (crossbow/dispenser);
 * the mod spawns its own smoke. Elytra/hand rockets keep the vanilla trail.
 */
@Mixin(FireworkRocketEntity.class)
public class RocketTrailMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void createtnt$suppressVanillaTrail(Level level, ParticleOptions options,
                                                double x, double y, double z,
                                                double vx, double vy, double vz) {
        FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
        if (ModConfig.rocketShellExplosions && self.isShotAtAngle()) {
            return;
        }
        level.addParticle(options, x, y, z, vx, vy, vz);
    }
}
