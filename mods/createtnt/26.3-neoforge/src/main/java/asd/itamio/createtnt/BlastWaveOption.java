package asd.itamio.createtnt;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

/** Particle options for the blast wave (shake + delayed boom): power only. */
public record BlastWaveOption(float power) implements ParticleOptions {
    @Override
    public ParticleType<?> getType() {
        return BetterTNTs.SHELL_BLAST_WAVE;
    }
}
