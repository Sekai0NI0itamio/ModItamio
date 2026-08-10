package asd.itamio.createtnt;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/** Particle options for the blast wave (shake + delayed boom): power only. */
public record BlastWaveOption(float power) implements ParticleOptions {

    public static final ParticleOptions.Deserializer<BlastWaveOption> DESERIALIZER =
        new ParticleOptions.Deserializer<>() {
            @Override
            public BlastWaveOption fromCommand(ParticleType<BlastWaveOption> type, StringReader reader)
                throws CommandSyntaxException {
                reader.expect(' ');
                return new BlastWaveOption(reader.readFloat());
            }

            @Override
            public BlastWaveOption fromNetwork(ParticleType<BlastWaveOption> type, FriendlyByteBuf buf) {
                return new BlastWaveOption(buf.readFloat());
            }
        };

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeFloat(this.power);
    }

    @Override
    public String writeToString() {
        return String.valueOf(this.power);
    }

    @Override
    public ParticleType<?> getType() {
        return BetterTNTs.SHELL_BLAST_WAVE.get();
    }
}
