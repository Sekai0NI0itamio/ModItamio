package asd.itamio.createtnt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** ParticleType carrying {@link BlastWaveOption} (power). */
public class BlastWaveParticleType extends ParticleType<BlastWaveOption> {

    private static final MapCodec<BlastWaveOption> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.FLOAT.fieldOf("power").forGetter(BlastWaveOption::power))
        .apply(i, BlastWaveOption::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, BlastWaveOption> STREAM_CODEC =
        StreamCodec.of(
            (buf, o) -> buf.writeFloat(o.power()),
            buf -> new BlastWaveOption(buf.readFloat()));

    public BlastWaveParticleType() {
        super(false);
    }

    @Override
    public MapCodec<BlastWaveOption> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, BlastWaveOption> streamCodec() {
        return STREAM_CODEC;
    }
}
