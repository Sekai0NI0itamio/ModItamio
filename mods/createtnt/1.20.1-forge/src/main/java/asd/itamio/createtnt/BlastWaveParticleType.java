package asd.itamio.createtnt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleType;

/** ParticleType carrying {@link BlastWaveOption} (power). */
public class BlastWaveParticleType extends ParticleType<BlastWaveOption> {

    public BlastWaveParticleType() {
        super(false, BlastWaveOption.DESERIALIZER);
    }

    @Override
    public Codec<BlastWaveOption> codec() {
        return RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("power").forGetter(BlastWaveOption::power))
            .apply(i, BlastWaveOption::new));
    }
}
