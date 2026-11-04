package asd.itamio.createtnt;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only registrations: particle providers, entity renderers, and the
 * camera-shake handler on the game event bus.
 */
@EventBusSubscriber(modid = BetterTNTs.MOD_ID, bus = EventBusSubscriber.Bus.MOD,
    value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(BetterTNTs.SHELL_EXPLOSION_SMOKE.get(),
            ShellExplosionSmokeParticle.Provider::new);
        event.registerSpriteSet(BetterTNTs.SHELL_EXPLOSION_CLOUD.get(),
            ShellExplosionCloudParticle.Provider::new);
        event.registerSpecial(BetterTNTs.SHELL_BLAST_WAVE.get(),
            new BlastWaveEffectParticle.Provider());
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BetterTNTs.ENHANCED_TNT.get(),
            net.minecraft.client.renderer.entity.TntRenderer::new);
        event.registerEntityRenderer(BetterTNTs.ENHANCED_FALLING_BLOCK.get(),
            RenderEnhancedFallingBlock::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
            NeoForge.EVENT_BUS.register(new CameraShakeHandler()));
    }
}
