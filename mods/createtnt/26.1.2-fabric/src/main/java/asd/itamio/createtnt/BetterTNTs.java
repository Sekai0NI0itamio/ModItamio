package asd.itamio.createtnt;

import asd.itamio.ModInfoPrinter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        EXPLOSION_BOOM = Registry.register(BuiltInRegistries.SOUND_EVENT,
            id("explosion_boom"),
            SoundEvent.createVariableRangeEvent(id("explosion_boom")));

        // 1.21.11: EntityType.Builder.build takes the entity's ResourceKey.
        ResourceKey<EntityType<?>> tntKey = ResourceKey.create(Registries.ENTITY_TYPE, id("enhanced_tnt_primed"));
        ENHANCED_TNT = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            id("enhanced_tnt_primed"),
            EntityType.Builder.<EntityEnhancedTNTPrimed>of(EntityEnhancedTNTPrimed::new, MobCategory.MISC)
                .sized(0.98F, 0.98F).clientTrackingRange(10).updateInterval(10)
                .build(tntKey));
        ResourceKey<EntityType<?>> fallingKey = ResourceKey.create(Registries.ENTITY_TYPE, id("enhanced_falling_block"));
        ENHANCED_FALLING_BLOCK = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            id("enhanced_falling_block"),
            EntityType.Builder.<EntityEnhancedFallingBlock>of(EntityEnhancedFallingBlock::new, MobCategory.MISC)
                .sized(0.98F, 0.98F).clientTrackingRange(10).updateInterval(20)
                .build(fallingKey));

        SHELL_EXPLOSION_SMOKE = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            id("shell_explosion_smoke"), new ShellSmokeParticleType());
        SHELL_EXPLOSION_CLOUD = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            id("shell_explosion_cloud"), new ShellCloudParticleType());
        SHELL_BLAST_WAVE = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            id("shell_blast_wave"), new BlastWaveParticleType());

        // Explosion payload channel (server → client).
        // 26.1.2 fabric API: playS2C() was renamed to clientboundPlay().
        PayloadTypeRegistry.clientboundPlay().register(SpawnExplosionMessage.TYPE,
            SpawnExplosionMessage.STREAM_CODEC);

        // Plain .txt config with live reload, in the config dir.
        java.io.File cfgFile = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getConfigDir().resolve("createtnt.txt").toFile();
        ModConfig.load(cfgFile);

        // Server-tick handlers: collapse queue + scheduled chain detonations.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            BlockCollapseHandler tickHandler = BlockCollapseHandler.getInstance();
            tickHandler.onServerTick();
            EntityEnhancedTNTPrimed.tickScheduled();
        });

        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info(CREDITS);
        LOGGER.info("{} initialized. Enhanced TNT explosion physics active.", MOD_NAME);
    }
}
