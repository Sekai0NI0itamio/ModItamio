package asd.itamio.createtnt;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/** Particle options for the big explosion smoke puff: lifetime + size scale.
 *  {@code trail} marks the rocket flight trail puffs, which hang almost in
 *  place instead of drifting apart like the explosion cloud puffs. */
public record ShellSmokeOption(int lifetime, float scale, boolean trail) implements ParticleOptions {

    public static final ParticleOptions.Deserializer<ShellSmokeOption> DESERIALIZER =
        new ParticleOptions.Deserializer<>() {
            @Override
            public ShellSmokeOption fromCommand(ParticleType<ShellSmokeOption> type, StringReader reader)
                throws CommandSyntaxException {
                reader.expect(' ');
                int lifetime = reader.readInt();
                reader.expect(' ');
                float scale = reader.readFloat();
                // The trailing trail flag is optional so the old two-number
                // form still parses.
                boolean trail = false;
                if (reader.canRead() && reader.peek() == ' ') {
                    reader.expect(' ');
                    trail = reader.readBoolean();
                }
                return new ShellSmokeOption(lifetime, scale, trail);
            }

            @Override
            public ShellSmokeOption fromNetwork(ParticleType<ShellSmokeOption> type, FriendlyByteBuf buf) {
                return new ShellSmokeOption(buf.readVarInt(), buf.readFloat(), buf.readBoolean());
            }
        };

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeVarInt(this.lifetime);
        buf.writeFloat(this.scale);
        buf.writeBoolean(this.trail);
    }

    @Override
    public String writeToString() {
        return this.lifetime + " " + this.scale + " " + this.trail;
    }

    @Override
    public ParticleType<?> getType() {
        return BetterTNTs.SHELL_EXPLOSION_SMOKE.get();
    }
}
