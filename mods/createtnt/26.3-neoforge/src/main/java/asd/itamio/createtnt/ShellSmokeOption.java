package asd.itamio.createtnt;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

/** Particle options for the big explosion smoke puff: lifetime + size scale.
 *  {@code trail} marks the rocket flight trail puffs, which hang almost in
 *  place instead of drifting apart like the explosion cloud puffs. */
public record ShellSmokeOption(int lifetime, float scale, boolean trail) implements ParticleOptions {
    @Override
    public ParticleType<?> getType() {
        return BetterTNTs.SHELL_EXPLOSION_SMOKE.get();
    }
}
