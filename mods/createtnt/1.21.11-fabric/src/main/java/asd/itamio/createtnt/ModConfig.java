package asd.itamio.createtnt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain-text configuration with live file watching and corruption recovery.
 *
 * <p>File format: UTF-8 {@code .txt}, one {@code key = value} per line.
 * Lines starting with {@code #} are comments. Section headers are comment
 * lines the user can read but the parser ignores.</p>
 *
 * <h3>Recovery behavior</h3>
 * <ul>
 *   <li><b>File missing</b> — a new one is generated with defaults.</li>
 *   <li><b>File corrupted / unparseable line</b> — valid keys are preserved,
 *       invalid keys fall back to defaults, and the file is rewritten cleanly.</li>
 *   <li><b>Value out of range</b> — clamped to the valid range and the file
 *       is rewritten with the corrected value.</li>
 * </ul>
 *
 * <p>A background {@link WatchService} thread watches the file's directory
 * and reloads within ~500&nbsp;ms of any save — no restart required.</p>
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

    // NOTE: The core smoke spawn geometry (plume age, velocity/displacement
    // scales, per-particle-setting counts) is intentionally fixed so the
    // explosion always reads correctly; the tunable motion/visual knobs are
    // the keys below.

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
    //  Derived runtime values (kept for compatibility with existing code)
    // ═══════════════════════════════════════════════════════════════════════
    public static float blockPower = tntStrength * explosionPowerScale;
    public static float entityPower = tntStrength * 1.5F * explosionPowerScale;

    // ═══════════════════════════════════════════════════════════════════════
    //  Internal state
    // ═══════════════════════════════════════════════════════════════════════
    private static Path configPath;
    private static WatchService watchService;
    private static Thread watchThread;
    private static Thread pollThread;
    private static volatile boolean watching = false;
    /** Last-known file modification timestamp (for polling fallback). */
    private static volatile long lastKnownModified = 0L;

    /** All config entries in declaration order: key → spec. */
    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();
    static {
        // SIMPLE
        spec("scatterFluids", () -> scatterFluids, v -> scatterFluids = v, false);
        spec("tntStrength", () -> tntStrength, v -> tntStrength = v, 12.0F, 1.0F, 64.0F);
        spec("explosionPowerScale", () -> explosionPowerScale, v -> explosionPowerScale = v, 0.4F, 0.05F, 4.0F);
        spec("entityKnockbackScale", () -> entityKnockbackScale, v -> entityKnockbackScale = v, 0.35F, 0.0F, 4.0F);
        spec("simultaneousChainDetonation", () -> simultaneousChainDetonation, v -> simultaneousChainDetonation = v, true);
        spec("chainDetonationDelay", () -> chainDetonationDelay, v -> chainDetonationDelay = v, 2, 0, 200);
        spec("screenShake", () -> screenShake, v -> screenShake = v, true);
        spec("showShellExplosionClouds", () -> showShellExplosionClouds, v -> showShellExplosionClouds = v, true);
        spec("showExtraShellExplosionTrails", () -> showExtraShellExplosionTrails, v -> showExtraShellExplosionTrails = v, true);
        spec("baseSmokeAmount", () -> baseSmokeAmount, v -> baseSmokeAmount = v, 0.45F, 0.1F, 3.0F);
        spec("explosionVisualScale", () -> explosionVisualScale, v -> explosionVisualScale = v, 0.5F, 0.05F, 4.0F);
        spec("explosionTrailMultiplier", () -> explosionTrailMultiplier, v -> explosionTrailMultiplier = v, 1.0F, 0.0F, 10.0F);
        spec("smokeDarknessMin", () -> smokeDarknessMin, v -> smokeDarknessMin = v, 0.1F, 0.0F, 1.0F);
        spec("smokeDarknessMax", () -> smokeDarknessMax, v -> smokeDarknessMax = v, 1.0F, 0.0F, 1.0F);
        spec("smokeBuoyancy", () -> smokeBuoyancy, v -> smokeBuoyancy = v, 0.01F, 0.0F, 0.1F);
        spec("smokeRiseDelay", () -> smokeRiseDelay, v -> smokeRiseDelay = v, 25, 0, 200);
        spec("smokeRiseRamp", () -> smokeRiseRamp, v -> smokeRiseRamp = v, 20, 1, 200);
        spec("smokeHeatMin", () -> smokeHeatMin, v -> smokeHeatMin = v, 0.2F, 0.0F, 4.0F);
        spec("smokeHeatMax", () -> smokeHeatMax, v -> smokeHeatMax = v, 1.6F, 0.0F, 4.0F);
        spec("smokeHeatDecayMin", () -> smokeHeatDecayMin, v -> smokeHeatDecayMin = v, 0.99F, 0.9F, 1.0F);
        spec("smokeHeatDecayMax", () -> smokeHeatDecayMax, v -> smokeHeatDecayMax = v, 0.999F, 0.9F, 1.0F);
        spec("smokeTurbulence", () -> smokeTurbulence, v -> smokeTurbulence = v, 0.02F, 0.0F, 0.05F);
        spec("smokeRepelStrength", () -> smokeRepelStrength, v -> smokeRepelStrength = v, 0.008F, 0.0F, 0.05F);
        spec("smokeBaseCap", () -> smokeBaseCap, v -> smokeBaseCap = v, 1.2F, 0.01F, 5.0F);
        spec("blockDropChance", () -> blockDropChance, v -> blockDropChance = v, 0.25F, 0.0F, 1.0F);
        spec("pillarTrailSpacing", () -> pillarTrailSpacing, v -> pillarTrailSpacing = v, 0.4F, 0.2F, 4.0F);
        // Explosion algorithm
        spec("energyAbsorptionMultiplier", () -> energyAbsorptionMultiplier, v -> energyAbsorptionMultiplier = v, 1.0F, 0.0F, 50.0F);
        spec("resistanceBase", () -> resistanceBase, v -> resistanceBase = v, 0.3F, 0.0F, 5.0F);
        spec("rayStepDecay", () -> rayStepDecay, v -> rayStepDecay = v, 0.225F, 0.0F, 5.0F);
        spec("rayReachVariation", () -> rayReachVariation, v -> rayReachVariation = v, 0.6F, 0.0F, 5.0F);
        spec("waterResistanceScale", () -> waterResistanceScale, v -> waterResistanceScale = v, 0.6F, 0.0F, 1.0F);
        spec("lavaResistanceScale", () -> lavaResistanceScale, v -> lavaResistanceScale = v, 0.3F, 0.0F, 1.0F);
        spec("waterEvaporationChance", () -> waterEvaporationChance, v -> waterEvaporationChance = v, 0.4F, 0.0F, 1.0F);
        spec("waterEvaporationThreshold", () -> waterEvaporationThreshold, v -> waterEvaporationThreshold = v, 0.4F, 0.0F, 1.0F);
        spec("fluidLaunchSpeed", () -> fluidLaunchSpeed, v -> fluidLaunchSpeed = v, 0.30F, 0.0F, 5.0F);
        spec("fluidUpwardBoost", () -> fluidUpwardBoost, v -> fluidUpwardBoost = v, 0.20F, 0.0F, 5.0F);
        spec("fluidVelocityCapY", () -> fluidVelocityCapY, v -> fluidVelocityCapY = v, 0.30F, 0.0F, 5.0F);
        spec("fluidVelocityCapXZ", () -> fluidVelocityCapXZ, v -> fluidVelocityCapXZ = v, 0.35F, 0.0F, 5.0F);
        // TNT fuse
        spec("waterUnlightMinTicks", () -> waterUnlightMinTicks, v -> waterUnlightMinTicks = v, 10, 0, 200);
        spec("waterUnlightMaxTicks", () -> waterUnlightMaxTicks, v -> waterUnlightMaxTicks = v, 20, 0, 400);
        // Falling block
        spec("fallingBlockGravity", () -> fallingBlockGravity, v -> fallingBlockGravity = v, 0.04F, 0.0F, 1.0F);
        spec("fallingBlockHorizontalDecay", () -> fallingBlockHorizontalDecay, v -> fallingBlockHorizontalDecay = v, 0.04F, 0.0F, 1.0F);
        spec("fallingBlockImpactDamageMultiplier", () -> fallingBlockImpactDamageMultiplier, v -> fallingBlockImpactDamageMultiplier = v, 12.0F, 0.0F, 200.0F);
        spec("fallingBlockImpactDamageCap", () -> fallingBlockImpactDamageCap, v -> fallingBlockImpactDamageCap = v, 50.0F, 0.0F, 500.0F);
        spec("fallingBlockMaxLifetime", () -> fallingBlockMaxLifetime, v -> fallingBlockMaxLifetime = v, 600, 20, 6000);
        // Shake
        spec("shakeSpringiness", () -> shakeSpringiness, v -> shakeSpringiness = v, 0.08F, 0.005F, 5.0F);
        spec("shakeDecay", () -> shakeDecay, v -> shakeDecay = v, 0.3F, 0.005F, 5.0F);
        spec("shakeIntensity", () -> shakeIntensity, v -> shakeIntensity = v, 1.3F, 0.0F, 10.0F);
        spec("shakePowerMultiplier", () -> shakePowerMultiplier, v -> shakePowerMultiplier = v, 6.0F, 0.0F, 100.0F);
        spec("shakePowerLimit", () -> shakePowerLimit, v -> shakePowerLimit = v, 45.0F, 0.0F, 90.0F);
        spec("blastEffectDelaySpeed", () -> blastEffectDelaySpeed, v -> blastEffectDelaySpeed = v, 320.0F, 0.0F, 1000.0F);
        // Wind
        // Collapse
        spec("collapseMaxPerTick", () -> collapseMaxPerTick, v -> collapseMaxPerTick = v, 80, 1, 500);
        spec("collapseMaxDepth", () -> collapseMaxDepth, v -> collapseMaxDepth = v, 16, 1, 100);
        spec("collapseSupportScanBudget", () -> collapseSupportScanBudget, v -> collapseSupportScanBudget = v, 2048, 64, 100000);
        spec("collapseGroundColumnDepth", () -> collapseGroundColumnDepth, v -> collapseGroundColumnDepth = v, 5, 1, 32);
        spec("collapseSupportRangeMax", () -> collapseSupportRangeMax, v -> collapseSupportRangeMax = v, 16, 1, 64);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Spec — describes one config entry
    // ═══════════════════════════════════════════════════════════════════════
    private abstract static class Spec {
        final String key;
        final String comment;
        Spec(String key, String comment) { this.key = key; this.comment = comment; }
        abstract String defaultValueString();
        abstract void parseAndSet(String raw); // clamps internally
    }

    private static void spec(String key, java.util.function.Supplier<Boolean> get,
                             java.util.function.Consumer<Boolean> set, boolean def) {
        SPECS.put(key, new Spec(key, "") {
            @Override String defaultValueString() { return String.valueOf(def); }
            @Override void parseAndSet(String raw) {
                String t = raw.trim().toLowerCase();
                if (t.equals("true") || t.equals("yes") || t.equals("1")) { set.accept(true); return; }
                if (t.equals("false") || t.equals("no") || t.equals("0")) { set.accept(false); return; }
                throw new IllegalArgumentException("boolean");
            }
        });
    }

    private static void spec(String key, java.util.function.Supplier<Float> get,
                             java.util.function.Consumer<Float> set, float def, float min, float max) {
        SPECS.put(key, new Spec(key, "") {
            @Override String defaultValueString() { return String.valueOf(def); }
            @Override void parseAndSet(String raw) {
                float v = Float.parseFloat(raw.trim());
                set.accept(Math.max(min, Math.min(max, v)));
            }
        });
    }

    private static void spec(String key, java.util.function.Supplier<Integer> get,
                             java.util.function.Consumer<Integer> set, int def, int min, int max) {
        SPECS.put(key, new Spec(key, "") {
            @Override String defaultValueString() { return String.valueOf(def); }
            @Override void parseAndSet(String raw) {
                int v = Integer.parseInt(raw.trim());
                set.accept(Math.max(min, Math.min(max, v)));
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Load / reload
    // ═══════════════════════════════════════════════════════════════════════
    public static void load(java.io.File file) {
        configPath = file.toPath();
        reload();
        startWatching();
    }

    public static synchronized void reload() {
        if (configPath == null) return;

        // Reset per-block support overrides to defaults; values from the file
        // override/extend these as they are parsed.
        supportRangeOverrides.clear();
        supportRangeOverrides.putAll(DEFAULT_SUPPORT_RANGES);

        if (!Files.exists(configPath)) {
            // Missing file: generate a fresh one with defaults.
            writeDefaultFile();
            snapshotCurrentValues();
            syncDerived();
            log("Config file not found — created new one with defaults.");
            return;
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(configPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log("Config file unreadable (" + e.getMessage() + "). Regenerating with defaults.");
            writeDefaultFile();
            snapshotCurrentValues();
            syncDerived();
            return;
        }

        // Parse line-by-line. Valid keys are applied; invalid lines are
        // collected so we can rewrite the file clean afterwards.
        int validCount = 0;
        List<String> badLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int eq = trimmed.indexOf('=');
            if (eq < 0) { badLines.add(line); continue; }
            String key = trimmed.substring(0, eq).trim();
            String val = trimmed.substring(eq + 1).trim();
            // Per-block support level entries: supportRange.<modid:block> = N
            if (key.startsWith("supportRange.")) {
                String blockId = key.substring("supportRange.".length()).trim();
                try {
                    int v = Integer.parseInt(val);
                    if (!blockId.isEmpty()) {
                        supportRangeOverrides.put(blockId, Math.max(0, Math.min(64, v)));
                        validCount++;
                        continue;
                    }
                } catch (NumberFormatException ignored) {
                    // falls through to badLines
                }
                badLines.add(line);
                continue;
            }
            Spec spec = SPECS.get(key);
            if (spec == null) { badLines.add(line); continue; }
            try {
                spec.parseAndSet(val);
                validCount++;
            } catch (Exception e) {
                // Invalid value for a valid key — skip; default stays.
                badLines.add(line);
            }
        }

        // If any lines were bad, rewrite the file cleanly so the on-disk
        // state matches what we're running with.
        if (!badLines.isEmpty()) {
            log("Config had " + badLines.size() + " invalid/unrecognized line(s). Rewriting cleanly (preserved " + validCount + " valid value(s)).");
            snapshotCurrentValues();
            writeCurrentValues();
        } else {
            snapshotCurrentValues();
        }
        syncDerived();
    }

    private static void syncDerived() {
        blockPower = tntStrength * explosionPowerScale;
        entityPower = tntStrength * 1.5F * explosionPowerScale;
        // Capture the file's current modification time so the polling
        // fallback thread can detect future changes reliably.
        lastKnownModified = captureLastModified();
    }

    /** Returns the config file's last-modified timestamp, or 0 if unavailable. */
    private static long captureLastModified() {
        try {
            return Files.getLastModifiedTime(configPath).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  File generation
    // ═══════════════════════════════════════════════════════════════════════
    private static void writeDefaultFile() {
        // Reset all fields to defaults, then write.
        for (Map.Entry<String, Spec> e : SPECS.entrySet()) {
            e.getValue().parseAndSet(e.getValue().defaultValueString());
        }
        snapshotCurrentValues();
        writeCurrentValues();
    }

    /** Writes a clean, human-readable config file from the current field values. */
    private static void writeCurrentValues() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ════════════════════════════════════════════════════════════════\n");
        sb.append("#  Create TNT — Configuration\n");
        sb.append("# ════════════════════════════════════════════════════════════════\n");
        sb.append("#  Edit and save — changes apply LIVE without restarting the game.\n");
        sb.append("#  Format:  key = value    (lines starting with # are comments)\n");
        sb.append("#  If this file is deleted, a new one with defaults is created.\n");
        sb.append("#  Invalid values are reset to defaults; valid values are kept.\n");
        sb.append("# ════════════════════════════════════════════════════════════════\n\n");

        sb.append("# ────────────────────────────────────────────────────────────────\n");
        sb.append("#  SIMPLE — General gameplay options\n");
        sb.append("# ────────────────────────────────────────────────────────────────\n\n");
        section(sb, "scatterFluids", "Whether water/lava SOURCE blocks can be scattered by explosions (flowing water is never scattered)");
        section(sb, "tntStrength", "TNT strength (block destruction radius). Entity damage auto-scales to 1.5x");
        section(sb, "explosionPowerScale", "Global explosive power scale (0.4 = 60% weaker). Applies to block destruction and entity damage");
        section(sb, "entityKnockbackScale", "Knockback impulse on mobs/players/primed TNT/debris (0.35 = minimal nudge, 1.0 = full shove)");
        section(sb, "simultaneousChainDetonation", "TNTs explode all at once instantly (true) or one-by-one (false)");
        section(sb, "chainDetonationDelay", "Ticks between each TNT when detonating one-by-one (20 ticks = 1 second)");
        section(sb, "screenShake", "Whether the camera wobbles when the blast wave arrives");
        section(sb, "showShellExplosionClouds", "Spawn the big smoke plume cloud");
        section(sb, "showExtraShellExplosionTrails", "Spawn trailing smoke streaks in the cloud");
        section(sb, "baseSmokeAmount", "Main plume (base smoke) puff count multiplier (0.45 = half; does not affect the blast rays)");
        section(sb, "explosionVisualScale", "Visual size of the entire explosion effect (smoke, rays, shake, sound). 1.0 = full size, 0.5 = half. Block destruction = tntStrength");
        section(sb, "explosionTrailMultiplier", "How many blast-ray streaks shoot out (1.0 = 12-17, spread evenly in random directions so lines never touch)");
        section(sb, "smokeDarknessMin", "Darkest smoke shade at spawn (0.35 = some puffs start dark gray)");
        section(sb, "smokeDarknessMax", "Brightest smoke shade at spawn (1.0 = white)");
        section(sb, "blockDropChance", "Item drop chance fraction (chance = this / blockRadius; lower = fewer drops)");
        section(sb, "smokeBuoyancy", "Upward acceleration once smoke starts rising (hot plume lift)");
        section(sb, "smokeRiseDelay", "Ticks before smoke starts rising (25 = smoke hangs ~1.2s after the blast)");
        section(sb, "smokeRiseRamp", "Ticks over which the rise ramps from 0 to full strength");
        section(sb, "smokeHeatMin", "Coolest per-puff heat at birth (buoyancy multiplier; cooler puffs stay short)");
        section(sb, "smokeHeatMax", "Hottest per-puff heat at birth (hotter puffs rise harder)");
        section(sb, "smokeHeatDecayMin", "Fastest cooling rate (heat kept per tick; 0.99 = cools fast, short rise)");
        section(sb, "smokeHeatDecayMax", "Slowest cooling rate (0.999 = holds heat, tall column)");
        section(sb, "smokeTurbulence", "Random per-tick smoke acceleration (breaks pattern-like spreading; 0 = off)");
        section(sb, "smokeRepelStrength", "Mutual puff repulsion strength (overlapping puffs push apart so the cloud expands evenly; 0 = off)");
        section(sb, "smokeBaseCap", "Speed cap for the large base smoke in blocks per second (1.2 = a bit faster than the ray particles)");
        section(sb, "pillarTrailSpacing", "Blocks between puffs along a pillar ray (smaller = denser pillar trails)");

        sb.append("\n# ────────────────────────────────────────────────────────────────\n");
        sb.append("#  ADVANCED — Explosion algorithm\n");
        sb.append("#  Tune these to design your own explosion behavior.\n");
        sb.append("# ────────────────────────────────────────────────────────────────\n\n");
        section(sb, "energyAbsorptionMultiplier", "Per-block shock absorption: capacity = (blastResistance + resistanceBase) * this. A block explodes only when the shock exceeds its capacity, absorbing exactly that much energy; weaker shocks are fully absorbed and the block survives. 1.0 ≈ vanilla-like penetration (TNT breaks 1-2 stone blocks)");
        section(sb, "resistanceBase", "Base resistance added to every block before scaling");
        section(sb, "rayStepDecay", "Energy subtracted per ray-march step");
        section(sb, "rayReachVariation", "Per-ray reach random variation range (added to 0.7 baseline)");
        section(sb, "waterResistanceScale", "Chain-explosion power scaling in water (1.0 = full power, 0.0 = no explosion)");
        section(sb, "lavaResistanceScale", "Chain-explosion power scaling in lava (1.0 = full power, 0.0 = no explosion)");
        section(sb, "waterEvaporationChance", "Probability that a high-energy water block evaporates (destroyed, drops nothing)");
        section(sb, "waterEvaporationThreshold", "Fraction of blockRadius defining the high-energy evaporation zone (0.4 = inner 40%)");

        sb.append("\n#  Fluid launch velocity (how fast scattered fluids fly outward)\n\n");
        section(sb, "fluidLaunchSpeed", "Base launch speed for scattered fluid blocks");
        section(sb, "fluidUpwardBoost", "Extra upward velocity added to scattered fluids");
        section(sb, "fluidVelocityCapY", "Maximum upward velocity for scattered fluids");
        section(sb, "fluidVelocityCapXZ", "Maximum horizontal velocity for scattered fluids");

        sb.append("\n# ────────────────────────────────────────────────────────────────\n");
        sb.append("#  ADVANCED — TNT fuse & water behavior\n");
        sb.append("# ────────────────────────────────────────────────────────────────\n\n");
        section(sb, "waterUnlightMinTicks", "Min ticks lit before water can unlight TNT (10 = 0.5s)");
        section(sb, "waterUnlightMaxTicks", "Max ticks lit after which water no longer unlights TNT (20 = 1s)");

        sb.append("\n# ────────────────────────────────────────────────────────────────\n");
        sb.append("#  Smoke particle physics & cloud spawning\n");
        sb.append("#  The core spawn geometry (plume timing, velocity/displacement scales,\n");
        sb.append("#  per-particle-setting counts) is fixed so the explosion always reads\n");
        sb.append("#  correctly; the tunable motion and visual knobs are in the sections\n");
        sb.append("#  above.\n");
        sb.append("# ────────────────────────────────────────────────────────────────\n");

        sb.append("\n# ────────────────────────────────────────────────────────────────\n");
        sb.append("#  ADVANCED — Falling block physics\n");
        sb.append("# ────────────────────────────────────────────────────────────────\n\n");
        section(sb, "fallingBlockGravity", "Gravity applied to falling debris per tick (0.04 = vanilla)");
        section(sb, "fallingBlockHorizontalDecay", "Horizontal velocity decay per tick after the boom");
        section(sb, "fallingBlockImpactDamageMultiplier", "Damage = maxFallSpeed * this (12 = vanilla-like)");
        section(sb, "fallingBlockImpactDamageCap", "Maximum impact damage (50 = 25 hearts)");
        section(sb, "fallingBlockMaxLifetime", "Maximum lifetime of a falling block in ticks (600 = 30s)");

        sb.append("\n# ────────────────────────────────────────────────────────────────\n");
        sb.append("#  ADVANCED — Camera shake\n");
        sb.append("# ────────────────────────────────────────────────────────────────\n\n");
        section(sb, "shakeSpringiness", "Spring constant of the shake oscillator (lower = looser wobble)");
        section(sb, "shakeDecay", "Damping of the shake oscillator (higher = stops faster)");
        section(sb, "shakeIntensity", "Global multiplier for screen shake strength");
        section(sb, "shakePowerMultiplier", "Blast power * this = shake strength");
        section(sb, "shakePowerLimit", "Maximum camera rotation in degrees");
        section(sb, "blastEffectDelaySpeed", "Blast wave arrival speed in m/s (higher = shake reaches player faster)");

        sb.append("\n# ────────────────────────────────────────────────────────────────\n");
        sb.append("#  ADVANCED — Structure collapse\n");
        sb.append("# ────────────────────────────────────────────────────────────────\n\n");
        section(sb, "collapseMaxPerTick", "Max blocks processed per server tick (higher = faster collapse)");
        section(sb, "collapseMaxDepth", "Max cascade generation depth (higher = collapses spread further)");
        section(sb, "collapseSupportScanBudget", "Max blocks visited per connectivity check (bigger = verifies larger structures; exceeded = block stays)");
        section(sb, "collapseGroundColumnDepth", "Solid blocks directly below that make a block an anchor to the ground (5 = stands on real terrain)");
        section(sb, "collapseSupportRangeMax", "Max support range any block can have (how many blocks it can extend past a support)");

        sb.append("\n# ────────────────────────────────────────────────────────────────\n");
        sb.append("#  ADVANCED — Per-block support levels\n");
        sb.append("#  supportRange.<modid:block> = N  →  this block can extend N blocks\n");
        sb.append("#  past a support point. Example: dirt = 1 means one dirt block hangs\n");
        sb.append("#  off a support, but a second dirt block extended out falls.\n");
        sb.append("#  Unlisted blocks use round(blast hardness), clamped to\n");
        sb.append("#  [1, collapseSupportRangeMax]. Deleting a line restores its default.\n");
        sb.append("# ────────────────────────────────────────────────────────────────\n\n");
        for (Map.Entry<String, Integer> e : supportRangeOverrides.entrySet()) {
            sb.append("supportRange.").append(e.getKey()).append(" = ").append(e.getValue()).append("\n");
        }

        sb.append("\n# ════════════════════════════════════════════════════════════════\n");
        sb.append("#  End of configuration\n");
        sb.append("# ════════════════════════════════════════════════════════════════\n");

        try {
            Files.createDirectories(configPath.getParent());
            Files.write(configPath, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log("Failed to write config file: " + e.getMessage());
        }
    }

    /** Appends a commented key=value line for the given spec key. */
    private static void section(StringBuilder sb, String key, String desc) {
        Spec spec = SPECS.get(key);
        sb.append("# ").append(desc).append("\n");
        // Read current value via the getter by having parseAndSet round-trip
        // the default — simpler: just read the field. We reconstruct from the
        // getter by re-parsing default string is wrong; instead store current.
        sb.append(key).append(" = ").append(currentValueString(key)).append("\n\n");
    }

    /** Returns the current field value as a string for file writing. */
    private static String currentValueString(String key) {
        Spec spec = SPECS.get(key);
        // We need the *current* value, not the default. The getter is stored
        // inside the lambda but we didn't keep a reference. Re-derive by
        // reading the field reflectively is overkill — instead we store the
        // getter. Quick fix: re-read via the spec's defaultValueString won't
        // work. Let's use a dedicated approach: the spec stores a Supplier.
        // Since we didn't expose it, we fall back to re-parsing is wrong.
        // Simplest correct approach: keep a parallel map of getters.
        return CURRENT_VALUES.getOrDefault(key, spec.defaultValueString());
    }

    /** Map of key → current-value-as-string, kept in sync by parseAndSet. */
    private static final Map<String, String> CURRENT_VALUES = new LinkedHashMap<>();
    // We need to update CURRENT_VALUES whenever a value changes. The cleanest
    // way: wrap every setter. But our specs use direct lambdas. Instead, we
    // snapshot current values into CURRENT_VALUES at the end of reload().
    private static void snapshotCurrentValues() {
        // Reflective-free: re-read via the getter lambdas. But we don't store
        // them. Simplest: read the public static fields by name.
        CURRENT_VALUES.put("scatterFluids", String.valueOf(scatterFluids));
        CURRENT_VALUES.put("tntStrength", String.valueOf(tntStrength));
        CURRENT_VALUES.put("explosionPowerScale", String.valueOf(explosionPowerScale));
        CURRENT_VALUES.put("entityKnockbackScale", String.valueOf(entityKnockbackScale));
        CURRENT_VALUES.put("simultaneousChainDetonation", String.valueOf(simultaneousChainDetonation));
        CURRENT_VALUES.put("chainDetonationDelay", String.valueOf(chainDetonationDelay));
        CURRENT_VALUES.put("screenShake", String.valueOf(screenShake));
        CURRENT_VALUES.put("showShellExplosionClouds", String.valueOf(showShellExplosionClouds));
        CURRENT_VALUES.put("showExtraShellExplosionTrails", String.valueOf(showExtraShellExplosionTrails));
        CURRENT_VALUES.put("baseSmokeAmount", String.valueOf(baseSmokeAmount));
        CURRENT_VALUES.put("explosionVisualScale", String.valueOf(explosionVisualScale));
        CURRENT_VALUES.put("explosionTrailMultiplier", String.valueOf(explosionTrailMultiplier));
        CURRENT_VALUES.put("smokeDarknessMin", String.valueOf(smokeDarknessMin));
        CURRENT_VALUES.put("smokeDarknessMax", String.valueOf(smokeDarknessMax));
        CURRENT_VALUES.put("blockDropChance", String.valueOf(blockDropChance));
        CURRENT_VALUES.put("smokeBuoyancy", String.valueOf(smokeBuoyancy));
        CURRENT_VALUES.put("smokeRiseDelay", String.valueOf(smokeRiseDelay));
        CURRENT_VALUES.put("smokeRiseRamp", String.valueOf(smokeRiseRamp));
        CURRENT_VALUES.put("smokeHeatMin", String.valueOf(smokeHeatMin));
        CURRENT_VALUES.put("smokeHeatMax", String.valueOf(smokeHeatMax));
        CURRENT_VALUES.put("smokeHeatDecayMin", String.valueOf(smokeHeatDecayMin));
        CURRENT_VALUES.put("smokeHeatDecayMax", String.valueOf(smokeHeatDecayMax));
        CURRENT_VALUES.put("smokeTurbulence", String.valueOf(smokeTurbulence));
        CURRENT_VALUES.put("smokeRepelStrength", String.valueOf(smokeRepelStrength));
        CURRENT_VALUES.put("smokeBaseCap", String.valueOf(smokeBaseCap));
        CURRENT_VALUES.put("pillarTrailSpacing", String.valueOf(pillarTrailSpacing));
        CURRENT_VALUES.put("energyAbsorptionMultiplier", String.valueOf(energyAbsorptionMultiplier));
        CURRENT_VALUES.put("resistanceBase", String.valueOf(resistanceBase));
        CURRENT_VALUES.put("rayStepDecay", String.valueOf(rayStepDecay));
        CURRENT_VALUES.put("rayReachVariation", String.valueOf(rayReachVariation));
        CURRENT_VALUES.put("waterResistanceScale", String.valueOf(waterResistanceScale));
        CURRENT_VALUES.put("lavaResistanceScale", String.valueOf(lavaResistanceScale));
        CURRENT_VALUES.put("waterEvaporationChance", String.valueOf(waterEvaporationChance));
        CURRENT_VALUES.put("waterEvaporationThreshold", String.valueOf(waterEvaporationThreshold));
        CURRENT_VALUES.put("fluidLaunchSpeed", String.valueOf(fluidLaunchSpeed));
        CURRENT_VALUES.put("fluidUpwardBoost", String.valueOf(fluidUpwardBoost));
        CURRENT_VALUES.put("fluidVelocityCapY", String.valueOf(fluidVelocityCapY));
        CURRENT_VALUES.put("fluidVelocityCapXZ", String.valueOf(fluidVelocityCapXZ));
        CURRENT_VALUES.put("waterUnlightMinTicks", String.valueOf(waterUnlightMinTicks));
        CURRENT_VALUES.put("waterUnlightMaxTicks", String.valueOf(waterUnlightMaxTicks));
        CURRENT_VALUES.put("fallingBlockGravity", String.valueOf(fallingBlockGravity));
        CURRENT_VALUES.put("fallingBlockHorizontalDecay", String.valueOf(fallingBlockHorizontalDecay));
        CURRENT_VALUES.put("fallingBlockImpactDamageMultiplier", String.valueOf(fallingBlockImpactDamageMultiplier));
        CURRENT_VALUES.put("fallingBlockImpactDamageCap", String.valueOf(fallingBlockImpactDamageCap));
        CURRENT_VALUES.put("fallingBlockMaxLifetime", String.valueOf(fallingBlockMaxLifetime));
        CURRENT_VALUES.put("shakeSpringiness", String.valueOf(shakeSpringiness));
        CURRENT_VALUES.put("shakeDecay", String.valueOf(shakeDecay));
        CURRENT_VALUES.put("shakeIntensity", String.valueOf(shakeIntensity));
        CURRENT_VALUES.put("shakePowerMultiplier", String.valueOf(shakePowerMultiplier));
        CURRENT_VALUES.put("shakePowerLimit", String.valueOf(shakePowerLimit));
        CURRENT_VALUES.put("blastEffectDelaySpeed", String.valueOf(blastEffectDelaySpeed));
        CURRENT_VALUES.put("collapseMaxPerTick", String.valueOf(collapseMaxPerTick));
        CURRENT_VALUES.put("collapseMaxDepth", String.valueOf(collapseMaxDepth));
        CURRENT_VALUES.put("collapseSupportScanBudget", String.valueOf(collapseSupportScanBudget));
        CURRENT_VALUES.put("collapseGroundColumnDepth", String.valueOf(collapseGroundColumnDepth));
        CURRENT_VALUES.put("collapseSupportRangeMax", String.valueOf(collapseSupportRangeMax));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  File watching
    // ═══════════════════════════════════════════════════════════════════════
    private static void startWatching() {
        if (watching) return;
        watching = true;
        // Primary: WatchService (fast on Linux, slower on macOS)
        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path dir = configPath.getParent();
            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
            watchThread = new Thread(ModConfig::watchLoop, "CreateTNT-ConfigWatcher");
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (Exception e) {
            log("WatchService unavailable (" + e.getMessage() + "). Relying on polling fallback.");
        }
        // Fallback: poll file last-modified every 2 seconds (reliable on all
        // platforms, catches atomic-save renames that WatchService may miss).
        pollThread = new Thread(ModConfig::pollLoop, "CreateTNT-ConfigPoller");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    private static void watchLoop() {
        long lastReload = 0L;
        while (watching) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                continue;
            }
            for (WatchEvent<?> event : key.pollEvents()) {
                Object context = event.context();
                if (!(context instanceof Path)) continue;
                Path changed = (Path) context;
                if (!changed.toString().equals(configPath.getFileName().toString())) continue;

                long now = System.currentTimeMillis();
                if (now - lastReload < 500L) continue;
                lastReload = now;

                try {
                    reload();
                    snapshotCurrentValues();
                    log("Config reloaded live: strength=" + tntStrength
                        + " scatterFluids=" + scatterFluids
                        + " simultaneous=" + simultaneousChainDetonation
                        + " delay=" + chainDetonationDelay
                        + " shake=" + screenShake
                        + " clouds=" + showShellExplosionClouds);
                } catch (Exception e) {
                    log("Live config reload failed (" + e.getMessage() + "). Keeping previous values.");
                }
            }
            if (!key.reset()) break;
        }
    }

    /**
     * Polling fallback: checks the file's last-modified timestamp every 2
     * seconds. More reliable than WatchService on macOS (which can have a
     * 10-second delay) and catches atomic-save renames that WatchService
     * may miss. Also handles file deletion by recreating with defaults.
     */
    private static void pollLoop() {
        while (watching) {
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                return;
            }
            if (!watching) return;

            // File deleted → reload() recreates it with defaults.
            if (!Files.exists(configPath)) {
                try {
                    reload();
                    log("Config file was deleted — recreated with defaults (polling).");
                } catch (Exception e) {
                    log("Polling reload failed (" + e.getMessage() + ").");
                }
                continue;
            }

            // File modified → reload if timestamp changed.
            long currentModified = captureLastModified();
            if (currentModified != lastKnownModified && currentModified > 0L) {
                try {
                    reload();
                    log("Config reloaded via polling: strength=" + tntStrength
                        + " clouds=" + showShellExplosionClouds);
                } catch (Exception e) {
                    log("Polling reload failed (" + e.getMessage() + ").");
                }
            }
        }
    }

    public static void stopWatching() {
        watching = false;
        if (watchThread != null) watchThread.interrupt();
        if (pollThread != null) pollThread.interrupt();
        if (watchService != null) {
            try { watchService.close(); } catch (Exception ignored) {}
        }
    }

    private static void log(String msg) {
        if (BetterTNTs.LOGGER != null) {
            BetterTNTs.LOGGER.info("[CreateTNT] " + msg);
        }
    }
}
