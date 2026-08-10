package asd.itamio.createtnt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleType;

/** ParticleType carrying {@link ShellCloudOption} (scale + plume flag). */
public class ShellCloudParticleType extends ParticleType<ShellCloudOption> {

    public ShellCloudParticleType() {
        super(false, ShellCloudOption.DESERIALIZER);
    }

    @Override
    public Codec<ShellCloudOption> codec() {
        return RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("scale").forGetter(ShellCloudOption::scale),
            Codec.BOOL.fieldOf("isPlume").forGetter(ShellCloudOption::isPlume))
            .apply(i, ShellCloudOption::new));
    }
}
