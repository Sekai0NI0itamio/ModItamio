package asd.itamio.createtnt;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/** Particle options for the explosion cloud controller: scale + plume flag. */
public record ShellCloudOption(float scale, boolean isPlume) implements ParticleOptions {

    public static final ParticleOptions.Deserializer<ShellCloudOption> DESERIALIZER =
        new ParticleOptions.Deserializer<>() {
            @Override
            public ShellCloudOption fromCommand(ParticleType<ShellCloudOption> type, StringReader reader)
                throws CommandSyntaxException {
                reader.expect(' ');
                float scale = reader.readFloat();
                reader.expect(' ');
                boolean isPlume = reader.readBoolean();
                return new ShellCloudOption(scale, isPlume);
            }

            @Override
            public ShellCloudOption fromNetwork(ParticleType<ShellCloudOption> type, FriendlyByteBuf buf) {
                return new ShellCloudOption(buf.readFloat(), buf.readBoolean());
            }
        };

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeFloat(this.scale);
        buf.writeBoolean(this.isPlume);
    }

    @Override
    public String writeToString() {
        return this.scale + " " + this.isPlume;
    }

    @Override
    public ParticleType<?> getType() {
        return BetterTNTs.SHELL_EXPLOSION_CLOUD.get();
    }
}
