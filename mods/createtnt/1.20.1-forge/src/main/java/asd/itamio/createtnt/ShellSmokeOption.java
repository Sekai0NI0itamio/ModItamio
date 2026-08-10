package asd.itamio.createtnt;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/** Particle options for the big explosion smoke puff: lifetime + size scale. */
public record ShellSmokeOption(int lifetime, float scale) implements ParticleOptions {

    public static final ParticleOptions.Deserializer<ShellSmokeOption> DESERIALIZER =
        new ParticleOptions.Deserializer<>() {
            @Override
            public ShellSmokeOption fromCommand(ParticleType<ShellSmokeOption> type, StringReader reader)
                throws CommandSyntaxException {
                reader.expect(' ');
                int lifetime = reader.readInt();
                reader.expect(' ');
                float scale = reader.readFloat();
                return new ShellSmokeOption(lifetime, scale);
            }

            @Override
            public ShellSmokeOption fromNetwork(ParticleType<ShellSmokeOption> type, FriendlyByteBuf buf) {
                return new ShellSmokeOption(buf.readVarInt(), buf.readFloat());
            }
        };

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeVarInt(this.lifetime);
        buf.writeFloat(this.scale);
    }

    @Override
    public String writeToString() {
        return this.lifetime + " " + this.scale;
    }

    @Override
    public ParticleType<?> getType() {
        return BetterTNTs.SHELL_EXPLOSION_SMOKE.get();
    }
}
