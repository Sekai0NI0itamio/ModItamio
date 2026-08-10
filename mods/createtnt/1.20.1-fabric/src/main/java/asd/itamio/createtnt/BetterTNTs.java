package asd.itamio.createtnt;

import asd.itamio.ModInfoPrinter;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create TNT — makes TNT explode like artillery shells: big smoke plumes,
 * blast rays, delayed boom sound, screen shake, energy-transfer block
 * destruction, and structural collapse.
 */
public class BetterTNTs implements ModInitializer {

    public static final String MOD_ID = "createtnt";
    public static final String MOD_NAME = "Create TNT";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final String CREDITS =
        "Particle system, smoke textures, blast-wave and screen-shake effects, and sound assets " +
        "are from the mod Create Big Cannons by rbasamoyai; its explosion physics were " +
        "referenced and adapted with our own tweaks. Structure collapse cascade scheduling " +
        "is adapted from the mod Simple Block Physics by FerrinEmber.";

    // ---- Registries (direct, Fabric style) ----
    public static SoundEvent EXPLOSION_BOOM;

    public static EntityType<EntityEnhancedTNTPrimed> ENHANCED_TNT;
    public static EntityType<EntityEnhancedFallingBlock> ENHANCED_FALLING_BLOCK;

    public static ParticleType<ShellSmokeOption> SHELL_EXPLOSION_SMOKE;
    public static ParticleType<ShellCloudOption> SHELL_EXPLOSION_CLOUD;
    public static ParticleType<BlastWaveOption> SHELL_BLAST_WAVE;

    @Override
    public void onInitialize() {
        EXPLOSION_BOOM = Registry.register(BuiltInRegistries.SOUND_EVENT,
            new ResourceLocation(MOD_ID, "explosion_boom"),
            SoundEvent.createVariableRangeEvent(new ResourceLocation(MOD_ID, "explosion_boom")));

        ENHANCED_TNT = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            new ResourceLocation(MOD_ID, "enhanced_tnt_primed"),
            EntityType.Builder.<EntityEnhancedTNTPrimed>of(EntityEnhancedTNTPrimed::new, MobCategory.MISC)
                .sized(0.98F, 0.98F).clientTrackingRange(10).updateInterval(10)
                .build(new ResourceLocation(MOD_ID, "enhanced_tnt_primed").toString()));
        ENHANCED_FALLING_BLOCK = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            new ResourceLocation(MOD_ID, "enhanced_falling_block"),
            EntityType.Builder.<EntityEnhancedFallingBlock>of(EntityEnhancedFallingBlock::new, MobCategory.MISC)
                .sized(0.98F, 0.98F).clientTrackingRange(10).updateInterval(20)
                .build(new ResourceLocation(MOD_ID, "enhanced_falling_block").toString()));

        SHELL_EXPLOSION_SMOKE = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            new ResourceLocation(MOD_ID, "shell_explosion_smoke"), new ShellSmokeParticleType());
        SHELL_EXPLOSION_CLOUD = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            new ResourceLocation(MOD_ID, "shell_explosion_cloud"), new ShellCloudParticleType());
        SHELL_BLAST_WAVE = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            new ResourceLocation(MOD_ID, "shell_blast_wave"), new BlastWaveParticleType());

        // Plain .txt config with live reload, in the config dir.
        java.io.File cfgFile = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getConfigDir().resolve("createtnt.txt").toFile();
        ModConfig.load(cfgFile);

        // Server-tick handler processing scheduled (one-by-one) chain
        // detonations.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(
            server -> EntityEnhancedTNTPrimed.tickScheduled());

        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info(CREDITS);
        LOGGER.info("{} initialized. Enhanced TNT explosion physics active.", MOD_NAME);
    }
}
