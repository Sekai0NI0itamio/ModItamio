package asd.itamio.createtnt;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;

public class ModNetwork {

    public static final net.minecraftforge.network.SimpleChannel CHANNEL =
        net.minecraftforge.network.ChannelBuilder.named(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(BetterTNTs.MOD_ID, "main"))
            .simpleChannel();

    static {
        CHANNEL.messageBuilder(SpawnExplosionMessage.class, 0)
            .encoder(SpawnExplosionMessage::encode)
            .decoder(SpawnExplosionMessage::decode)
            .consumerMainThread((msg, ctx) -> {
                ShellExplosionEffects.handlePacket(msg);
                ctx.setPacketHandled(true);
            })
            .add();
    }

    /** Sends the explosion packet to a client. */
    public static void sendExplosion(ServerPlayer player, SpawnExplosionMessage msg) {
        CHANNEL.send(msg, PacketDistributor.PLAYER.with(player));
    }

    /** Forge registration entry point (no-op; channel is static). */
    public static void register(Object event) {
    }
}
