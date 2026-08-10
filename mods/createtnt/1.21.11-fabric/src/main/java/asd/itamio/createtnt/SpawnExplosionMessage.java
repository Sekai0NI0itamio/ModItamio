package asd.itamio.createtnt;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Tells the client to render the explosion at a given position.
 *
 * <p>The payload carries the explosion {@code power} (particle sizing scale)
 * and the receiving player's knockback vector, which the client applies
 * directly to its own player entity (player motion is client-authoritative).</p>
 */
public record SpawnExplosionMessage(double x, double y, double z, float power, boolean isPlume,
                                    float knockbackX, float knockbackY, float knockbackZ)
    implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpawnExplosionMessage> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(BetterTNTs.MOD_ID, "spawn_explosion"));

    public static final StreamCodec<FriendlyByteBuf, SpawnExplosionMessage> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public SpawnExplosionMessage decode(FriendlyByteBuf buf) {
                return new SpawnExplosionMessage(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readBoolean(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat());
            }

            @Override
            public void encode(FriendlyByteBuf buf, SpawnExplosionMessage msg) {
                buf.writeDouble(msg.x);
                buf.writeDouble(msg.y);
                buf.writeDouble(msg.z);
                buf.writeFloat(msg.power);
                buf.writeBoolean(msg.isPlume);
                buf.writeFloat(msg.knockbackX);
                buf.writeFloat(msg.knockbackY);
                buf.writeFloat(msg.knockbackZ);
            }
        };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
