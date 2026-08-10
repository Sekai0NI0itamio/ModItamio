package asd.itamio.createtnt;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

/** Particle options for the explosion cloud controller: scale + plume flag. */
public record ShellCloudOption(float scale, boolean isPlume) implements ParticleOptions {
    @Override
    public ParticleType<?> getType() {
        return BetterTNTs.SHELL_EXPLOSION_CLOUD.get();
    }
}
