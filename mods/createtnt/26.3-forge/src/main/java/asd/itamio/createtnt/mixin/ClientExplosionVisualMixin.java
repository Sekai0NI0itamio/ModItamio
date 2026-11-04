package asd.itamio.createtnt.mixin;

import asd.itamio.createtnt.ModConfig;
import asd.itamio.createtnt.ShellExplosionEffects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side visual replacement for vanilla explosions. Vanilla explosion
 * packets spawn {@code EXPLOSION_EMITTER}/{@code EXPLOSION} particles; when
 * enabled we swap them for the mod's plume. Works on servers that do not
 * have the mod installed, because it only touches client rendering.
 */
@Mixin(ClientLevel.class)
public class ClientExplosionVisualMixin {

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            at = @At("HEAD"), cancellable = true)
    private void createtnt$replaceVanillaExplosion(ParticleOptions options,
                                                   double x, double y, double z,
                                                   double vx, double vy, double vz,
                                                   CallbackInfo ci) {
        if (!ModConfig.replaceVanillaExplosionVisuals || !ModConfig.showShellExplosionClouds) {
            return;
        }
        if (options != ParticleTypes.EXPLOSION_EMITTER && options != ParticleTypes.EXPLOSION) {
            return;
        }
        if (options == ParticleTypes.EXPLOSION_EMITTER) {
            ShellExplosionEffects.spawnShellExplosion((ClientLevel) (Object) this, x, y, z,
                ModConfig.vanillaExplosionVisualPower, false, 0.0F, 1.0F, 0.0F);
        }
        ci.cancel();
    }
}
