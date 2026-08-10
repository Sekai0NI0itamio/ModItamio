package asd.itamio.createtnt;

import asd.itamio.ModInfoPrinter;
import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.slf4j.Logger;

/**
 * Create TNT — makes TNT explode like artillery shells: big smoke plumes,
 * blast rays, delayed boom sound, screen shake, energy-transfer block
 * destruction, and structural collapse.
 */
@Mod(BetterTNTs.MOD_ID)
public class BetterTNTs {

    public static final String MOD_ID = "createtnt";
    public static final String MOD_NAME = "Create TNT";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogUtils.getLogger();

    private static final String CREDITS =
        "Particle system, smoke textures, blast-wave and screen-shake effects, and sound assets " +
        "are from the mod Create Big Cannons by rbasamoyai; its explosion physics were " +
        "referenced and adapted with our own tweaks. Structure collapse cascade scheduling " +
        "is adapted from the mod Simple Block Physics by FerrinEmber.";

    // ---- Registries ----
    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
        DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_BOOM = SOUNDS.register("explosion_boom",
        () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "explosion_boom")));

    public static final DeferredHolder<EntityType<?>, EntityType<EntityEnhancedTNTPrimed>> ENHANCED_TNT =
        ENTITIES.register("enhanced_tnt_primed",
            () -> EntityType.Builder.<EntityEnhancedTNTPrimed>of(EntityEnhancedTNTPrimed::new, MobCategory.MISC)
                .sized(0.98F, 0.98F).clientTrackingRange(10).updateInterval(10)
                .build(ResourceLocation.fromNamespaceAndPath(MOD_ID, "enhanced_tnt_primed").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<EntityEnhancedFallingBlock>> ENHANCED_FALLING_BLOCK =
        ENTITIES.register("enhanced_falling_block",
            () -> EntityType.Builder.<EntityEnhancedFallingBlock>of(EntityEnhancedFallingBlock::new, MobCategory.MISC)
                .sized(0.98F, 0.98F).clientTrackingRange(10).updateInterval(20)
                .build(ResourceLocation.fromNamespaceAndPath(MOD_ID, "enhanced_falling_block").toString()));

    public static final DeferredHolder<ParticleType<?>, ParticleType<ShellSmokeOption>> SHELL_EXPLOSION_SMOKE =
        PARTICLES.register("shell_explosion_smoke", ShellSmokeParticleType::new);
    public static final DeferredHolder<ParticleType<?>, ParticleType<ShellCloudOption>> SHELL_EXPLOSION_CLOUD =
        PARTICLES.register("shell_explosion_cloud", ShellCloudParticleType::new);
    public static final DeferredHolder<ParticleType<?>, ParticleType<BlastWaveOption>> SHELL_BLAST_WAVE =
        PARTICLES.register("shell_blast_wave", BlastWaveParticleType::new);

    public BetterTNTs(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
        ENTITIES.register(modEventBus);
        PARTICLES.register(modEventBus);
        modEventBus.addListener(ModNetwork::register);

        // Plain .txt config with live reload, in the config dir.
        java.io.File cfgFile = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
            .resolve("createtnt.txt").toFile();
        ModConfig.load(cfgFile);

        NeoForge.EVENT_BUS.register(new ExplosionEventHandler());
        NeoForge.EVENT_BUS.register(new BlockCollapseHandler());
        // Static @SubscribeEvent server-tick handler that processes scheduled
        // (one-by-one) chain detonations.
        NeoForge.EVENT_BUS.register(EntityEnhancedTNTPrimed.class);

        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info(CREDITS);
        LOGGER.info("{} initialized. Enhanced TNT explosion physics active.", MOD_NAME);
    }
}
