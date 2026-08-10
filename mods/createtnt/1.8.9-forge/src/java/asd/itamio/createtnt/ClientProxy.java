package asd.itamio.createtnt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderTNTPrimed;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        // Register entity renderers using RenderingRegistry.
        RenderingRegistry.registerEntityRenderingHandler(EntityEnhancedTNTPrimed.class,
            new RenderTNTPrimed(Minecraft.getMinecraft().getRenderManager()));
        RenderingRegistry.registerEntityRenderingHandler(EntityEnhancedFallingBlock.class,
            new RenderEnhancedFallingBlock(Minecraft.getMinecraft().getRenderManager()));

        // Register camera shake handler on the event bus.
        MinecraftForge.EVENT_BUS.register(new CameraShakeHandler());
    }

    @Override
    public void init() {
        // Register particle texture icons via TextureStitchEvent and store them
        // in the static fields of ShellExplosionSmokeParticle.
        MinecraftForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent
            public void onTextureStitch(TextureStitchEvent.Pre event) {
                ShellExplosionSmokeParticle.loadIcons(event.map);
            }
        });
    }
}
