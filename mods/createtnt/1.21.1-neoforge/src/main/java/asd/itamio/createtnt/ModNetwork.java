package asd.itamio.createtnt;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Payload registration for the mod. Carries the one packet that tells clients
 * where to render the explosion particles (and their knockback).
 */
public class ModNetwork {

    /** Registered from the mod event bus in {@link BetterTNTs}. */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
            SpawnExplosionMessage.TYPE,
            SpawnExplosionMessage.STREAM_CODEC,
            ModNetwork::handleClient);
    }

    /** Client-side payload handler: apply knockback, spawn the effects. */
    private static void handleClient(SpawnExplosionMessage payload, IPayloadContext context) {
        context.enqueueWork(() -> ShellExplosionEffects.handlePacket(payload));
    }
}
