package asd.itamio.createtnt;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.logging.log4j.Logger;
import asd.itamio.ModInfoPrinter;

@Mod(modid = BetterTNTs.MOD_ID, name = BetterTNTs.MOD_NAME, version = BetterTNTs.VERSION,
     acceptedMinecraftVersions = "[1.12.2]")
public class BetterTNTs {

    public static final String MOD_ID = "createtnt";
    public static final String MOD_NAME = "Create TNT";
    public static final String VERSION = "1.0.0";

    public static Logger LOGGER;

    /** Explosion boom sound (3 variants). */
    public static SoundEvent EXPLOSION_SOUND;

    @Mod.Instance(MOD_ID)
    public static BetterTNTs instance;

    private static int nextEntityId = 0;

    @SidedProxy(clientSide = "asd.itamio.createtnt.ClientProxy",
                serverSide = "asd.itamio.createtnt.CommonProxy")
    public static CommonProxy proxy;

    private static final String CREDITS =
        "Particle system, smoke textures, blast-wave and screen-shake effects, and sound assets " +
        "are from the mod Create Big Cannons by rbasamoyai; its explosion physics were " +
        "referenced and adapted with our own tweaks. Structure collapse cascade scheduling " +
        "is adapted from the mod Simple Block Physics by FerrinEmber.";

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION, CREDITS);
        // Use a plain .txt config file (not Forge's .cfg format) so users can
        // edit it easily with any text editor.
        java.io.File cfgFile = new java.io.File(event.getModConfigurationDirectory(), "createtnt.txt");
        ModConfig.load(cfgFile);
        ModNetwork.register();
        registerSounds();
        registerEntities();
        proxy.preRenderInit();
        MinecraftForge.EVENT_BUS.register(new ExplosionEventHandler());
        MinecraftForge.EVENT_BUS.register(new BlockCollapseHandler());
        // Register the static @SubscribeEvent server-tick handler that
        // processes scheduled (one-by-one) chain detonations.
        MinecraftForge.EVENT_BUS.register(EntityEnhancedTNTPrimed.class);
        proxy.registerClientHooks();
        LOGGER.info("{} initialized. Enhanced TNT explosion physics active.", MOD_NAME);
    }

    private static void registerSounds() {
        ResourceLocation soundLocation = new ResourceLocation(MOD_ID, "explosion_boom");
        EXPLOSION_SOUND = new SoundEvent(soundLocation).setRegistryName(soundLocation);
        ForgeRegistries.SOUND_EVENTS.register(EXPLOSION_SOUND);
    }

    private static void registerEntities() {
        EntityRegistry.registerModEntity(
            new ResourceLocation(MOD_ID, "enhanced_falling_block"),
            EntityEnhancedFallingBlock.class,
            "enhanced_falling_block",
            nextEntityId++,
            instance,
            64, 20, true);
        EntityRegistry.registerModEntity(
            new ResourceLocation(MOD_ID, "enhanced_tnt_primed"),
            EntityEnhancedTNTPrimed.class,
            "enhanced_tnt_primed",
            nextEntityId++,
            instance,
            64, 20, true);
    }
}