package asd.itamio.createtnt;

import asd.itamio.ModInfoPrinter;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;

/**
 * Create TNT — makes TNT explode like artillery shells: big smoke plumes,
 * blast rays, delayed boom sound, screen shake, energy-transfer block
 * destruction, and structural collapse.
 */
@Mod(modid = BetterTNTs.MOD_ID, name = BetterTNTs.MOD_NAME, version = BetterTNTs.VERSION)
public class BetterTNTs {

    public static final String MOD_ID = "createtnt";
    public static final String MOD_NAME = "Create TNT";
    public static final String VERSION = "1.0.0";

    @Mod.Instance(MOD_ID)
    public static BetterTNTs INSTANCE;

    @SidedProxy(clientSide = "asd.itamio.createtnt.ClientProxy",
                serverSide = "asd.itamio.createtnt.CommonProxy")
    public static CommonProxy proxy;

    public static final ResourceLocation EXPLOSION_BOOM_SOUND =
        new ResourceLocation(MOD_ID, "explosion_boom");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.load(event.getSuggestedConfigurationFile());
        ModNetwork.register();

        EntityRegistry.registerModEntity(
            EntityEnhancedTNTPrimed.class, "enhanced_tnt_primed",
            0, INSTANCE, 80, 10, true);
        EntityRegistry.registerModEntity(
            EntityEnhancedFallingBlock.class, "enhanced_falling_block",
            1, INSTANCE, 80, 20, true);

        MinecraftForge.EVENT_BUS.register(new ExplosionEventHandler());
        MinecraftForge.EVENT_BUS.register(new BlockCollapseHandler());
        // Static @SubscribeEvent server-tick handler that processes scheduled
        // (one-by-one) chain detonations.
        MinecraftForge.EVENT_BUS.register(EntityEnhancedTNTPrimed.class);

        ModInfoPrinter.print(System.out::println, MOD_NAME, VERSION);
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit();
    }
}
