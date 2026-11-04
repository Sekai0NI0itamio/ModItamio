package asd.itamio.createtnt;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

/** Particle options for the explosion cloud controller: scale, plume flag, plume axis. */
public record ShellCloudOption(float scale, boolean isPlume, float axisX, float axisY, float axisZ)
    implements ParticleOptions {
    @Override
    public ParticleType<?> getType() {
        return BetterTNTs.SHELL_EXPLOSION_CLOUD;
    }
}
