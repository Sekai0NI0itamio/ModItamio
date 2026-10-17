package asd.itamio.createtnt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** ParticleType carrying {@link ShellSmokeOption} (lifetime + scale + trail flag). */
public class ShellSmokeParticleType extends ParticleType<ShellSmokeOption> {

    private static final MapCodec<ShellSmokeOption> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.INT.fieldOf("lifetime").forGetter(ShellSmokeOption::lifetime),
        Codec.FLOAT.fieldOf("scale").forGetter(ShellSmokeOption::scale),
        Codec.BOOL.optionalFieldOf("trail", false).forGetter(ShellSmokeOption::trail))
        .apply(i, ShellSmokeOption::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, ShellSmokeOption> STREAM_CODEC =
        StreamCodec.of(
            (buf, o) -> {
                buf.writeVarInt(o.lifetime());
                buf.writeFloat(o.scale());
                buf.writeBoolean(o.trail());
            },
            buf -> new ShellSmokeOption(buf.readVarInt(), buf.readFloat(), buf.readBoolean()));

    public ShellSmokeParticleType() {
        super(false);
    }

    @Override
    public MapCodec<ShellSmokeOption> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, ShellSmokeOption> streamCodec() {
        return STREAM_CODEC;
    }
}
