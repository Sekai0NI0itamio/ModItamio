package asd.itamio.createtnt;

import net.minecraft.client.renderer.entity.RenderTNTPrimed;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        // Use factories so Forge supplies the initialized RenderManager after
        // client rendering has been set up. Constructing these renderers with
        // Minecraft.getMinecraft().getRenderManager() during preInit leaves
        // their RenderManager null and crashes when TNT is rendered.
        RenderingRegistry.registerEntityRenderingHandler(EntityEnhancedTNTPrimed.class,
            manager -> new RenderTNTPrimed(manager));
        RenderingRegistry.registerEntityRenderingHandler(EntityEnhancedFallingBlock.class,
            manager -> new RenderEnhancedFallingBlock(manager));

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
