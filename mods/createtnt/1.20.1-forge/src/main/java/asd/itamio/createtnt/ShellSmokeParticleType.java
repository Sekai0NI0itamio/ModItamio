package asd.itamio.createtnt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleType;

/** ParticleType carrying {@link ShellSmokeOption} (lifetime + scale). */
public class ShellSmokeParticleType extends ParticleType<ShellSmokeOption> {

    public ShellSmokeParticleType() {
        super(false, ShellSmokeOption.DESERIALIZER);
    }

    @Override
    public Codec<ShellSmokeOption> codec() {
        return RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("lifetime").forGetter(ShellSmokeOption::lifetime),
            Codec.FLOAT.fieldOf("scale").forGetter(ShellSmokeOption::scale))
            .apply(i, ShellSmokeOption::new));
    }
}
