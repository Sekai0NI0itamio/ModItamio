package asd.itamio.createtnt;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/** Particle options for the explosion cloud controller: scale, plume flag, plume axis. */
public record ShellCloudOption(float scale, boolean isPlume, float axisX, float axisY, float axisZ)
    implements ParticleOptions {

    public static final ParticleOptions.Deserializer<ShellCloudOption> DESERIALIZER =
        new ParticleOptions.Deserializer<>() {
            @Override
            public ShellCloudOption fromCommand(ParticleType<ShellCloudOption> type, StringReader reader)
                throws CommandSyntaxException {
                reader.expect(' ');
                float scale = reader.readFloat();
                reader.expect(' ');
                boolean isPlume = reader.readBoolean();
                reader.expect(' ');
                float axisX = reader.readFloat();
                reader.expect(' ');
                float axisY = reader.readFloat();
                reader.expect(' ');
                float axisZ = reader.readFloat();
                return new ShellCloudOption(scale, isPlume, axisX, axisY, axisZ);
            }

            @Override
            public ShellCloudOption fromNetwork(ParticleType<ShellCloudOption> type, FriendlyByteBuf buf) {
                return new ShellCloudOption(buf.readFloat(), buf.readBoolean(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat());
            }
        };

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeFloat(this.scale);
        buf.writeBoolean(this.isPlume);
        buf.writeFloat(this.axisX);
        buf.writeFloat(this.axisY);
        buf.writeFloat(this.axisZ);
    }

    @Override
    public String writeToString() {
        return this.scale + " " + this.isPlume + " " + this.axisX + " " + this.axisY + " " + this.axisZ;
    }

    @Override
    public ParticleType<?> getType() {
        return BetterTNTs.SHELL_EXPLOSION_CLOUD;
    }
}
