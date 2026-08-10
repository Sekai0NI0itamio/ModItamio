package asd.itamio.createtnt;

import net.minecraft.client.renderer.entity.RenderTNTPrimed;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void registerClientHooks() {
        MinecraftForge.EVENT_BUS.register(new CameraShakeHandler());
        MinecraftForge.EVENT_BUS.register(new SmokeRenderHandler());
    }

    @Override
    public void preRenderInit() {
        RenderingRegistry.registerEntityRenderingHandler(EntityEnhancedFallingBlock.class,
            manager -> new RenderEnhancedFallingBlock(manager));
        RenderingRegistry.registerEntityRenderingHandler(EntityEnhancedTNTPrimed.class,
            manager -> new RenderTNTPrimed(manager));
    }
}