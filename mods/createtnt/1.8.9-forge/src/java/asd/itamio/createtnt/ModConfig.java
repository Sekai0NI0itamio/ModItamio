package asd.itamio.createtnt;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraftforge.common.config.Configuration;

/**
 * Configuration for Create TNT, backed by Forge's {@link Configuration}.
 *
 * <p>All values are read from a standard Forge config file (one block per
 * category). Defaults and ranges are declared inline; out-of-range values
 * written by the user are clamped automatically by Forge.</p>
 */
public class ModConfig {

    // ═══════════════════════════════════════════════════════════════════════
    //  SIMPLE — general gameplay options
    // ═══════════════════════════════════════════════════════════════════════
    /** Whether fluid SOURCE blocks are scattered by explosions (flowing water
     *  is never scattered — it drains naturally once sources are gone). */
    public static boolean scatterFluids = false;
    public static float tntStrength = 12.0F;
    /** Global explosive power scale applied to block destruction and entity
     *  damage power. 1.0 = full strength, 0.4 = reduced by 60%. */
    public static float explosionPowerScale = 0.4F;
    /** Knockback/scatter impulse scale for entities (mobs, players, primed
     *  TNT, debris). 1.0 = full shove, 0.35 = minimal nudge. */
    public static float entityKnockbackScale = 0.35F;
    public static boolean simultaneousChainDetonation = true;
    public static int chainDetonationDelay = 2;
    public static boolean screenShake = true;
    /** Whether the big smoke plume cloud spawns for explosions. */
    public static boolean showShellExplosionClouds = true;
    /** Whether the explosion cloud spawns trailing blast-ray smoke
     *  streaks. */
    public static boolean showExtraShellExplosionTrails = true;
    /** Multiplier for the main plume (base smoke) puff count. 1.0 = full
     *  density, 0.45 = half. Does not affect the blast rays. */
    public static float baseSmokeAmount = 0.45F;
    /** Scales the ENTIRE client-side explosion effect (smoke cloud size and
     *  spread, blast rays, blast-wave shake radius/strength, sound volume).
     *  1.0 = full size, 0.5 = half size. Does not affect block
     *  destruction — that is {@code tntStrength}. */
    public static float explosionVisualScale = 0.5F;
    /** Multiplier for the number of blast-ray trail streaks that shoot out
     *  of the explosion (12-17 on the "All" particle setting, spread evenly
     *  in random directions so no two lines touch). */
    public static float explosionTrailMultiplier = 1.0F;
    /** Smoke brightness range: each puff (and blast-ray puff) gets a random
     *  shade in this range, kept for life — 0.1 = near-black, 1.0 = white,
     *  so the cloud has clearly visible dark and light puffs. */
    public static float smokeDarknessMin = 0.1F;
    public static float smokeDarknessMax = 1.0F;
    /** Fraction of {@code blockRadius} — item drop chance = this / blockRadius. */
    public static float blockDropChance = 0.25F;
    /** Upward buoyancy per tick once the smoke starts rising (hot plume).
     *  0.01 = clearly visible drift; still bounded by the 1 b/s speed cap. */
    public static float smokeBuoyancy = 0.01F;
    /** Ticks after birth before smoke starts rising (the outward burst phase).
     *  25 = smoke hangs for ~1.2s after the blast before slowly lifting. */
    public static int smokeRiseDelay = 25;
    /** Ticks over which the rise ramps from 0 to full strength. */
    public static int smokeRiseRamp = 20;
    /** Per-puff heat range: buoyancy multiplier (hotter = rises harder).
     *  Each puff rolls a random heat in this range at birth. */
    public static float smokeHeatMin = 0.2F;
    public static float smokeHeatMax = 1.6F;
    /** Per-puff cooling rate range (heat kept per tick). Lower = cools fast
     *  (short rise), higher = holds heat (tall column). */
    public static float smokeHeatDecayMin = 0.99F;
    public static float smokeHeatDecayMax = 0.999F;
    /** Per-tick random turbulence acceleration on smoke puffs — breaks up
     *  pattern-like spreading so the cloud expands organically. */
    public static float smokeTurbulence = 0.02F;
    /** Strength of the mutual puff repulsion — overlapping puffs push away
     *  from each other so the cloud keeps expanding evenly. 0 = off. */
    public static float smokeRepelStrength = 0.008F;
    /** Speed cap for the large base smoke, in blocks per second
     *  (1.2 = a bit faster than the ray particles' 0.6-1.0). */
    public static float smokeBaseCap = 1.2F;
    /** Blocks between puffs along a pillar ray — smaller = denser pillar
     *  trails (0.4 keeps tall pillars visibly solid). */
    public static float pillarTrailSpacing = 0.4F;

    // ═══════════════════════════════════════════════════════════════════════
    //  ADVANCED — explosion algorithm
    // ═══════════════════════════════════════════════════════════════════════
    /** Per-block shock absorption: capacity = (blastResistance + base) * this.
     *  A block absorbs up to `capacity` shock energy; it explodes only when
     *  the incoming shock energy exceeds what it can handle, and the weakened
     *  shock continues with (energy - capacity). Default 1.0 ≈ vanilla-like
     *  penetration (stone capacity 6.3, dirt 0.8): a TNT shock of ~8-16 energy
     *  breaks 1-2 stone blocks before a stone block absorbs it. Raise for
     *  tougher blocks (e.g. 2.0 makes most stone survive a single TNT). */
    public static float energyAbsorptionMultiplier = 1.0F;
    /** Base resistance added to every block before scaling. */
    public static float resistanceBase = 0.3F;
    /** Energy subtracted per ray-march step. */
    public static float rayStepDecay = 0.225F;
    /** Per-ray reach variation range (random 0–this added to 0.7 baseline). */
    public static float rayReachVariation = 0.6F;
    /** Chain-explosion power scaling in water (fraction). */
    public static float waterResistanceScale = 0.6F;
    /** Chain-explosion power scaling in lava (fraction). */
    public static float lavaResistanceScale = 0.3F;
    /** Probability that a high-energy water block evaporates (drops nothing). */
    public static float waterEvaporationChance = 0.4F;
    /** Fraction of blockRadius defining the high-energy evaporation zone. */
    public static float waterEvaporationThreshold = 0.4F;

    // ── Fluid launch velocity (solid blocks no longer scatter) ──
    public static float fluidLaunchSpeed = 0.30F;
    public static float fluidUpwardBoost = 0.20F;
    public static float fluidVelocityCapY = 0.30F;
    public static float fluidVelocityCapXZ = 0.35F;

    // ═══════════════════════════════════════════════════════════════════════
    //  ADVANCED — TNT fuse & water behavior
    // ═══════════════════════════════════════════════════════════════════════
    /** Min ticks lit before water can unlight TNT (0.5 s = 10). */
    public static int waterUnlightMinTicks = 10;
    /** Max ticks lit after which water no longer unlights TNT (1 s = 20). */
    public static int waterUnlightMaxTicks = 20;

    // ═══════════════════════════════════════════════════════════════════════
    //  ADVANCED — falling block physics
    // ═══════════════════════════════════════════════════════════════════════
    public static float fallingBlockGravity = 0.04F;
    public static float fallingBlockHorizontalDecay = 0.04F;
    public static float fallingBlockImpactDamageMultiplier = 12.0F;
    public static float fallingBlockImpactDamageCap = 50.0F;
    public static int fallingBlockMaxLifetime = 600;

    // ═══════════════════════════════════════════════════════════════════════
    //  ADVANCED — camera shake
    // ═══════════════════════════════════════════════════════════════════════
    public static float shakeSpringiness = 0.08F;
    public static float shakeDecay = 0.3F;
    public static float shakeIntensity = 1.3F;
    public static float shakePowerMultiplier = 6.0F;
    public static float shakePowerLimit = 45.0F;
    public static float blastEffectDelaySpeed = 320.0F;

    // ═══════════════════════════════════════════════════════════════════════
    //  ADVANCED — structure collapse
    // ═══════════════════════════════════════════════════════════════════════
    public static int collapseMaxPerTick = 80;
    public static int collapseMaxDepth = 16;
    /** Max blocks visited by the connectivity flood-fill per support check.
     *  If the connected component is larger, the block is treated as
     *  supported (conservative — never demolish what we can't fully verify). */
    public static int collapseSupportScanBudget = 2048;
    /** How many solid blocks directly below a block make it an anchor
     *  (i.e. standing on real ground). Default 5. */
    public static int collapseGroundColumnDepth = 5;
    /** Maximum support range any block can have (also used for unbreakable
     *  blocks). A block's support range is how many blocks it can extend
     *  past a support point. */
    public static int collapseSupportRangeMax = 16;
    /**
     * Per-block support range overrides, keyed by registry name
     * ({@code "minecraft:dirt"} → 1). Blocks not listed derive their range
     * from their blast hardness (rounded, clamped to
     * {@code [1, collapseSupportRangeMax]}).
     */
    public static final Map<String, Integer> supportRangeOverrides = new LinkedHashMap<>();
    /** Default overrides written into fresh config files (documentation). */
    private static final Map<String, Integer> DEFAULT_SUPPORT_RANGES = new LinkedHashMap<>();
    static {
        DEFAULT_SUPPORT_RANGES.put("minecraft:dirt", 1);
        DEFAULT_SUPPORT_RANGES.put("minecraft:grass", 1);
        DEFAULT_SUPPORT_RANGES.put("minecraft:sand", 1);
        DEFAULT_SUPPORT_RANGES.put("minecraft:gravel", 1);
        DEFAULT_SUPPORT_RANGES.put("minecraft:planks", 2);
        DEFAULT_SUPPORT_RANGES.put("minecraft:log", 2);
        DEFAULT_SUPPORT_RANGES.put("minecraft:log2", 2);
        DEFAULT_SUPPORT_RANGES.put("minecraft:stone", 2);
        DEFAULT_SUPPORT_RANGES.put("minecraft:cobblestone", 2);
        DEFAULT_SUPPORT_RANGES.put("minecraft:stonebrick", 2);
        DEFAULT_SUPPORT_RANGES.put("minecraft:brick_block", 2);
        DEFAULT_SUPPORT_RANGES.put("minecraft:iron_block", 5);
        DEFAULT_SUPPORT_RANGES.put("minecraft:obsidian", 16);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Derived runtime values (recalculated after every load)
    // ═══════════════════════════════════════════════════════════════════════
    public static float blockPower = tntStrength * explosionPowerScale;
    public static float entityPower = tntStrength * 1.5F * explosionPowerScale;

    // ═══════════════════════════════════════════════════════════════════════
    //  Load
    // ═══════════════════════════════════════════════════════════════════════
    public static void load(File file) {
        Configuration config = new Configuration(file);

        // ── SIMPLE — general gameplay options ──
        scatterFluids = config.getBoolean("scatterFluids", "general", false,
            "Whether water/lava SOURCE blocks can be scattered by explosions (flowing water is never scattered)");
        tntStrength = config.getFloat("tntStrength", "general", 12.0F, 1.0F, 64.0F,
            "TNT strength (block destruction radius). Entity damage auto-scales to 1.5x");
        explosionPowerScale = config.getFloat("explosionPowerScale", "general", 0.4F, 0.05F, 4.0F,
            "Global explosive power scale (0.4 = 60% weaker). Applies to block destruction and entity damage");
        entityKnockbackScale = config.getFloat("entityKnockbackScale", "general", 0.35F, 0.0F, 4.0F,
            "Knockback impulse on mobs/players/primed TNT/debris (0.35 = minimal nudge, 1.0 = full shove)");
        simultaneousChainDetonation = config.getBoolean("simultaneousChainDetonation", "general", true,
            "TNTs explode all at once instantly (true) or one-by-one (false)");
        chainDetonationDelay = config.getInt("chainDetonationDelay", "general", 2, 0, 200,
            "Ticks between each TNT when detonating one-by-one (20 ticks = 1 second)");
        screenShake = config.getBoolean("screenShake", "general", true,
            "Whether the camera wobbles when the blast wave arrives");
        showShellExplosionClouds = config.getBoolean("showShellExplosionClouds", "general", true,
            "Spawn the big smoke plume cloud");
        showExtraShellExplosionTrails = config.getBoolean("showExtraShellExplosionTrails", "general", true,
            "Spawn trailing smoke streaks in the cloud");
        baseSmokeAmount = config.getFloat("baseSmokeAmount", "general", 0.45F, 0.1F, 3.0F,
            "Main plume (base smoke) puff count multiplier (0.45 = half; does not affect the blast rays)");
        explosionVisualScale = config.getFloat("explosionVisualScale", "general", 0.5F, 0.05F, 4.0F,
            "Visual size of the entire explosion effect (smoke, rays, shake, sound). 1.0 = full size, 0.5 = half. Block destruction = tntStrength");
        explosionTrailMultiplier = config.getFloat("explosionTrailMultiplier", "general", 1.0F, 0.0F, 10.0F,
            "How many blast-ray streaks shoot out (1.0 = 12-17, spread evenly in random directions so lines never touch)");
        smokeDarknessMin = config.getFloat("smokeDarknessMin", "general", 0.1F, 0.0F, 1.0F,
            "Darkest smoke shade at spawn (0.35 = some puffs start dark gray)");
        smokeDarknessMax = config.getFloat("smokeDarknessMax", "general", 1.0F, 0.0F, 1.0F,
            "Brightest smoke shade at spawn (1.0 = white)");
        blockDropChance = config.getFloat("blockDropChance", "general", 0.25F, 0.0F, 1.0F,
            "Item drop chance fraction (chance = this / blockRadius; lower = fewer drops)");
        smokeBuoyancy = config.getFloat("smokeBuoyancy", "general", 0.01F, 0.0F, 0.1F,
            "Upward acceleration once smoke starts rising (hot plume lift)");
        smokeRiseDelay = config.getInt("smokeRiseDelay", "general", 25, 0, 200,
            "Ticks before smoke starts rising (25 = smoke hangs ~1.2s after the blast)");
        smokeRiseRamp = config.getInt("smokeRiseRamp", "general", 20, 1, 200,
            "Ticks over which the rise ramps from 0 to full strength");
        smokeHeatMin = config.getFloat("smokeHeatMin", "general", 0.2F, 0.0F, 4.0F,
            "Coolest per-puff heat at birth (buoyancy multiplier; cooler puffs stay short)");
        smokeHeatMax = config.getFloat("smokeHeatMax", "general", 1.6F, 0.0F, 4.0F,
            "Hottest per-puff heat at birth (hotter puffs rise harder)");
        smokeHeatDecayMin = config.getFloat("smokeHeatDecayMin", "general", 0.99F, 0.9F, 1.0F,
            "Fastest cooling rate (heat kept per tick; 0.99 = cools fast, short rise)");
        smokeHeatDecayMax = config.getFloat("smokeHeatDecayMax", "general", 0.999F, 0.9F, 1.0F,
            "Slowest cooling rate (0.999 = holds heat, tall column)");
        smokeTurbulence = config.getFloat("smokeTurbulence", "general", 0.02F, 0.0F, 0.05F,
            "Random per-tick smoke acceleration (breaks pattern-like spreading; 0 = off)");
        smokeRepelStrength = config.getFloat("smokeRepelStrength", "general", 0.008F, 0.0F, 0.05F,
            "Mutual puff repulsion strength (overlapping puffs push apart so the cloud expands evenly; 0 = off)");
        smokeBaseCap = config.getFloat("smokeBaseCap", "general", 1.2F, 0.01F, 5.0F,
            "Speed cap for the large base smoke in blocks per second (1.2 = a bit faster than the ray particles)");
        pillarTrailSpacing = config.getFloat("pillarTrailSpacing", "general", 0.4F, 0.2F, 4.0F,
            "Blocks between puffs along a pillar ray (smaller = denser pillar trails)");

        // ── ADVANCED — explosion algorithm ──
        energyAbsorptionMultiplier = config.getFloat("energyAbsorptionMultiplier", "explosion", 1.0F, 0.0F, 50.0F,
            "Per-block shock absorption: capacity = (blastResistance + resistanceBase) * this. 1.0 = vanilla-like penetration (TNT breaks 1-2 stone blocks)");
        resistanceBase = config.getFloat("resistanceBase", "explosion", 0.3F, 0.0F, 5.0F,
            "Base resistance added to every block before scaling");
        rayStepDecay = config.getFloat("rayStepDecay", "explosion", 0.225F, 0.0F, 5.0F,
            "Energy subtracted per ray-march step");
        rayReachVariation = config.getFloat("rayReachVariation", "explosion", 0.6F, 0.0F, 5.0F,
            "Per-ray reach random variation range (added to 0.7 baseline)");
        waterResistanceScale = config.getFloat("waterResistanceScale", "explosion", 0.6F, 0.0F, 1.0F,
            "Chain-explosion power scaling in water (1.0 = full power, 0.0 = no explosion)");
        lavaResistanceScale = config.getFloat("lavaResistanceScale", "explosion", 0.3F, 0.0F, 1.0F,
            "Chain-explosion power scaling in lava (1.0 = full power, 0.0 = no explosion)");
        waterEvaporationChance = config.getFloat("waterEvaporationChance", "explosion", 0.4F, 0.0F, 1.0F,
            "Probability that a high-energy water block evaporates (destroyed, drops nothing)");
        waterEvaporationThreshold = config.getFloat("waterEvaporationThreshold", "explosion", 0.4F, 0.0F, 1.0F,
            "Fraction of blockRadius defining the high-energy evaporation zone (0.4 = inner 40%)");
        fluidLaunchSpeed = config.getFloat("fluidLaunchSpeed", "explosion", 0.30F, 0.0F, 5.0F,
            "Base launch speed for scattered fluid blocks");
        fluidUpwardBoost = config.getFloat("fluidUpwardBoost", "explosion", 0.20F, 0.0F, 5.0F,
            "Extra upward velocity added to scattered fluids");
        fluidVelocityCapY = config.getFloat("fluidVelocityCapY", "explosion", 0.30F, 0.0F, 5.0F,
            "Maximum upward velocity for scattered fluids");
        fluidVelocityCapXZ = config.getFloat("fluidVelocityCapXZ", "explosion", 0.35F, 0.0F, 5.0F,
            "Maximum horizontal velocity for scattered fluids");

        // ── ADVANCED — TNT fuse & water behavior ──
        waterUnlightMinTicks = config.getInt("waterUnlightMinTicks", "tnt_fuse", 10, 0, 200,
            "Min ticks lit before water can unlight TNT (10 = 0.5s)");
        waterUnlightMaxTicks = config.getInt("waterUnlightMaxTicks", "tnt_fuse", 20, 0, 400,
            "Max ticks lit after which water no longer unlights TNT (20 = 1s)");

        // ── ADVANCED — falling block physics ──
        fallingBlockGravity = config.getFloat("fallingBlockGravity", "falling_block", 0.04F, 0.0F, 1.0F,
            "Gravity applied to falling debris per tick (0.04 = vanilla)");
        fallingBlockHorizontalDecay = config.getFloat("fallingBlockHorizontalDecay", "falling_block", 0.04F, 0.0F, 1.0F,
            "Horizontal velocity decay per tick after the boom");
        fallingBlockImpactDamageMultiplier = config.getFloat("fallingBlockImpactDamageMultiplier", "falling_block", 12.0F, 0.0F, 200.0F,
            "Damage = maxFallSpeed * this (12 = vanilla-like)");
        fallingBlockImpactDamageCap = config.getFloat("fallingBlockImpactDamageCap", "falling_block", 50.0F, 0.0F, 500.0F,
            "Maximum impact damage (50 = 25 hearts)");
        fallingBlockMaxLifetime = config.getInt("fallingBlockMaxLifetime", "falling_block", 600, 20, 6000,
            "Maximum lifetime of a falling block in ticks (600 = 30s)");

        // ── ADVANCED — camera shake ──
        shakeSpringiness = config.getFloat("shakeSpringiness", "camera_shake", 0.08F, 0.005F, 5.0F,
            "Spring constant of the shake oscillator (lower = looser wobble)");
        shakeDecay = config.getFloat("shakeDecay", "camera_shake", 0.3F, 0.005F, 5.0F,
            "Damping of the shake oscillator (higher = stops faster)");
        shakeIntensity = config.getFloat("shakeIntensity", "camera_shake", 1.3F, 0.0F, 10.0F,
            "Global multiplier for screen shake strength");
        shakePowerMultiplier = config.getFloat("shakePowerMultiplier", "camera_shake", 6.0F, 0.0F, 100.0F,
            "Blast power * this = shake strength");
        shakePowerLimit = config.getFloat("shakePowerLimit", "camera_shake", 45.0F, 0.0F, 90.0F,
            "Maximum camera rotation in degrees");
        blastEffectDelaySpeed = config.getFloat("blastEffectDelaySpeed", "camera_shake", 320.0F, 0.0F, 1000.0F,
            "Blast wave arrival speed in m/s (higher = shake reaches player faster)");

        // ── ADVANCED — structure collapse ──
        collapseMaxPerTick = config.getInt("collapseMaxPerTick", "collapse", 80, 1, 500,
            "Max blocks processed per server tick (higher = faster collapse)");
        collapseMaxDepth = config.getInt("collapseMaxDepth", "collapse", 16, 1, 100,
            "Max cascade generation depth (higher = collapses spread further)");
        collapseSupportScanBudget = config.getInt("collapseSupportScanBudget", "collapse", 2048, 64, 100000,
            "Max blocks visited per connectivity check (bigger = verifies larger structures; exceeded = block stays)");
        collapseGroundColumnDepth = config.getInt("collapseGroundColumnDepth", "collapse", 5, 1, 32,
            "Solid blocks directly below that make a block an anchor to the ground (5 = stands on real terrain)");
        collapseSupportRangeMax = config.getInt("collapseSupportRangeMax", "collapse", 16, 1, 64,
            "Max support range any block can have (how many blocks it can extend past a support)");

        // ── ADVANCED — per-block support levels ──
        // Reset to defaults, then override with whatever is in the file.
        supportRangeOverrides.clear();
        supportRangeOverrides.putAll(DEFAULT_SUPPORT_RANGES);
        for (Map.Entry<String, Integer> entry : DEFAULT_SUPPORT_RANGES.entrySet()) {
            String blockId = entry.getKey();
            int defaultValue = entry.getValue();
            int value = config.getInt(blockId, "support", defaultValue, 0, 64,
                "How many blocks this block can extend past a support point (unlisted blocks use round(blast hardness))");
            supportRangeOverrides.put(blockId, value);
        }

        if (config.hasChanged()) {
            config.save();
        }

        recalcDerived();
    }

    /** Recalculates the derived block/entity power fields from raw inputs. */
    private static void recalcDerived() {
        blockPower = tntStrength * explosionPowerScale;
        entityPower = tntStrength * 1.5F * explosionPowerScale;
    }
}
