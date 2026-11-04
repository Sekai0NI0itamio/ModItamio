package asd.itamio.createtnt;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Networking for the mod (Fabric). The payload type + codec are registered in
 * {@link BetterTNTs#onInitialize}; the client receiver in
 * {@link CreateTNTClient#onInitializeClient}.
 */
public class ModNetwork {

    /** Sends the explosion packet to a client. */
    public static void sendExplosion(ServerPlayer player, SpawnExplosionMessage msg) {
        ServerPlayNetworking.send(player, msg);
    }
}
