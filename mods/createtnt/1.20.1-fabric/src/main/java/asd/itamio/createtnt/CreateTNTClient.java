package asd.itamio.createtnt;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.renderer.entity.TntRenderer;

/**
 * Client-only registrations: particle providers, entity renderers, the
 * camera-shake tick, and the explosion packet receiver.
 */
public class CreateTNTClient implements ClientModInitializer {

    private static final CameraShakeHandler SHAKE_HANDLER = new CameraShakeHandler();

    @Override
    public void onInitializeClient() {
        // Particle providers.
        ParticleFactoryRegistry.getInstance().register(BetterTNTs.SHELL_EXPLOSION_SMOKE,
            sprites -> new ShellExplosionSmokeParticle.Provider(sprites));
        ParticleFactoryRegistry.getInstance().register(BetterTNTs.SHELL_EXPLOSION_CLOUD,
            sprites -> new ShellExplosionCloudParticle.Provider(sprites));
        ParticleFactoryRegistry.getInstance().register(BetterTNTs.SHELL_BLAST_WAVE,
            new BlastWaveEffectParticle.Provider());

        // Entity renderers.
        EntityRendererRegistry.register(BetterTNTs.ENHANCED_TNT, TntRenderer::new);
        EntityRendererRegistry.register(BetterTNTs.ENHANCED_FALLING_BLOCK, RenderEnhancedFallingBlock::new);

        // Camera shake spring tick.
        ClientTickEvents.END_CLIENT_TICK.register(client -> SHAKE_HANDLER.onClientTick());

        // Explosion packet receiver.
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.EXPLOSION_CHANNEL,
            (client, handler, buf, responseSender) -> {
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float power = buf.readFloat();
                boolean isPlume = buf.readBoolean();
                float kx = buf.readFloat();
                float ky = buf.readFloat();
                float kz = buf.readFloat();
                client.execute(() -> ShellExplosionEffects.handlePacket(x, y, z, power, isPlume, kx, ky, kz));
            });
    }
}
