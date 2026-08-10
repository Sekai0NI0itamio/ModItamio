package asd.itamio.createtnt;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

/** Particle options for the big explosion smoke puff: lifetime + size scale. */
public record ShellSmokeOption(int lifetime, float scale) implements ParticleOptions {
    @Override
    public ParticleType<?> getType() {
        return BetterTNTs.SHELL_EXPLOSION_SMOKE.get();
    }
}
