package asd.itamio.createtnt;

import asd.itamio.ModInfoPrinter;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(BetterTNTs.MOD_ID)
public class BetterTNTs {

    public static final String MOD_ID = "createtnt";
    public static final String MOD_NAME = "Create TNT";
    public static final String VERSION = "2.1.0";

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

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static final RegistryObject<SoundEvent, SoundEvent> EXPLOSION_BOOM = SOUNDS.register("explosion_boom",
        () -> SoundEvent.createVariableRangeEvent(id("explosion_boom")));

    public static final RegistryObject<EntityType<?>, EntityType<EntityEnhancedTNTPrimed>> ENHANCED_TNT =
        ENTITIES.register("enhanced_tnt_primed",
            () -> {
                ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id("enhanced_tnt_primed"));
                return EntityType.Builder.<EntityEnhancedTNTPrimed>of(EntityEnhancedTNTPrimed::new, MobCategory.MISC)
                    .sized(0.98F, 0.98F).clientTrackingRange(10).updateInterval(10)
                    .build(key);
            });

    public static final RegistryObject<EntityType<?>, EntityType<EntityEnhancedFallingBlock>> ENHANCED_FALLING_BLOCK =
        ENTITIES.register("enhanced_falling_block",
            () -> {
                ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id("enhanced_falling_block"));
                return EntityType.Builder.<EntityEnhancedFallingBlock>of(EntityEnhancedFallingBlock::new, MobCategory.MISC)
                    .sized(0.98F, 0.98F).clientTrackingRange(10).updateInterval(20)
                    .build(key);
            });

    public static final RegistryObject<ParticleType<?>, ParticleType<ShellSmokeOption>> SHELL_EXPLOSION_SMOKE =
        PARTICLES.register("shell_explosion_smoke", ShellSmokeParticleType::new);
    public static final RegistryObject<ParticleType<?>, ParticleType<ShellCloudOption>> SHELL_EXPLOSION_CLOUD =
        PARTICLES.register("shell_explosion_cloud", ShellCloudParticleType::new);
    public static final RegistryObject<ParticleType<?>, ParticleType<BlastWaveOption>> SHELL_BLAST_WAVE =
        PARTICLES.register("shell_blast_wave", BlastWaveParticleType::new);

    public BetterTNTs(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        SOUNDS.register(modBusGroup);
        ENTITIES.register(modBusGroup);
        PARTICLES.register(modBusGroup);

        java.io.File cfgFile = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
            .resolve("createtnt.txt").toFile();
        ModConfig.load(cfgFile);

        MinecraftForge.EVENT_BUS.register(new ExplosionEventHandler());
        MinecraftForge.EVENT_BUS.register(new BlockCollapseHandler());
        MinecraftForge.EVENT_BUS.register(EntityEnhancedTNTPrimed.class);

        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info(CREDITS);
        LOGGER.info("{} initialized. Enhanced TNT explosion physics active.", MOD_NAME);
    }
}
