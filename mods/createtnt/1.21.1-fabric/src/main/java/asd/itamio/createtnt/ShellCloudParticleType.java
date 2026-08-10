package asd.itamio.createtnt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** ParticleType carrying {@link ShellCloudOption} (scale + plume flag). */
public class ShellCloudParticleType extends ParticleType<ShellCloudOption> {

    private static final MapCodec<ShellCloudOption> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.FLOAT.fieldOf("scale").forGetter(ShellCloudOption::scale),
        Codec.BOOL.fieldOf("isPlume").forGetter(ShellCloudOption::isPlume))
        .apply(i, ShellCloudOption::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, ShellCloudOption> STREAM_CODEC =
        StreamCodec.of(
            (buf, o) -> {
                buf.writeFloat(o.scale());
                buf.writeBoolean(o.isPlume());
            },
            buf -> new ShellCloudOption(buf.readFloat(), buf.readBoolean()));

    public ShellCloudParticleType() {
        super(false);
    }

    @Override
    public MapCodec<ShellCloudOption> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, ShellCloudOption> streamCodec() {
        return STREAM_CODEC;
    }
}
