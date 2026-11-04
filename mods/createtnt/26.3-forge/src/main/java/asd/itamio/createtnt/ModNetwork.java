package asd.itamio.createtnt;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

import java.util.function.Supplier;

public class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        Identifier.fromNamespaceAndPath(BetterTNTs.MOD_ID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    static {
        int id = 0;
        CHANNEL.registerMessage(id++, SpawnExplosionMessage.class,
            SpawnExplosionMessage::encode,
            SpawnExplosionMessage::decode,
            ModNetwork::handleClient);
    }

    /** Sends the explosion packet to a client. */
    public static void sendExplosion(ServerPlayer player, SpawnExplosionMessage msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    /** Client-side payload handler. */
    private static void handleClient(SpawnExplosionMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ShellExplosionEffects.handlePacket(msg));
        ctx.setPacketHandled(true);
    }

    /** Forge registration entry point (no-op; channel is static). */
    public static void register(Object event) {
    }
}
