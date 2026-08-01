package asd.itamio.modernshop;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Client-side entry point for the Modern Shop mod.
 *
 * <p>Packet routing to {@link ClientPacketHandler} is already wired through
 * the SimpleChannel registered in {@link ModernShop#commonSetup}, so unlike
 * the Fabric reference (which must call {@code ClientPacketHandler.register()}
 * to subscribe to {@code ClientPlayNetworking}) no explicit registration is
 * needed here. This class mirrors the Fabric reference's client entry point
 * and provides a place for client-specific setup (screen factories,
 * keybindings, renderers).
 */
@Mod(ModernShop.MOD_ID)
public class ModernShopClient {
    public ModernShopClient() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        // Packet handling is routed via ModernShop's SimpleChannel, so no
        // explicit register() call is needed (unlike Fabric's
        // ClientPlayNetworking.registerGlobalReceiver).
        // Client-specific setup (screen factories, keybindings) goes here.
    }
}
