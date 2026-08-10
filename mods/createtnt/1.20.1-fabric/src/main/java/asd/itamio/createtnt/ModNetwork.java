package asd.itamio.createtnt;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Networking for the mod (Fabric). Carries the one packet that tells clients
 * where to render the explosion particles (and their knockback).
 */
public class ModNetwork {

    /** The explosion-effects channel id. */
    public static final ResourceLocation EXPLOSION_CHANNEL =
        new ResourceLocation(BetterTNTs.MOD_ID, "spawn_explosion");

    /** Sends the explosion packet to a client. */
    public static void sendExplosion(ServerPlayer player, SpawnExplosionMessage msg) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        msg.toBytes(buf);
        ServerPlayNetworking.send(player, EXPLOSION_CHANNEL, buf);
    }
}
