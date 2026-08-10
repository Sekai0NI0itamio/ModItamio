package asd.itamio.createtnt;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A single large smoke puff of the explosion cloud.
 *
 * <p>Behavior: the puff is born with an outward velocity from the blast
 * (strong quadratic air drag stalls the burst within a few ticks), then the
 * large base puffs spread apart by mutual repulsion, wander with turbulence,
 * and rise by their own heat level — cooling off and sinking at the end.
 * Their speed cap ramps exponentially from a slow drift to the full cap at
 * the end of life. Ray puffs hold their streak position for 2 seconds, then
 * expand outward at a constant small speed while logarithmically fading.</p>
 *
 * <p>Rendering: translucent particle-atlas sprites; the engine renders these
 * after translucent blocks, so the smoke draws on top of water.</p>
 *
 * <p>1.21.11: the old {@code TextureSheetParticle} was merged into
 * {@code SingleQuadParticle}, which requires the initial sprite at
 * construction and reports translucency through {@code getLayer()}.</p>
 */
public class ShellExplosionSmokeParticle extends SingleQuadParticle {

    /** The smoke animation has 10 frame sprites (explosion_smoke0..9). */
    private static final int FRAME_COUNT = 10;

    /** Slight downward pull per tick (positive = slowly sinks). */
    private static final float GRAVITY = 0.05F;

    /**
     * Per-tick snapshot of all live smoke puffs, used for the mutual
     * repulsion sampling. Rebuilt once per game tick (the first puff to tick
     * that tick refreshes it) so each puff only checks a few random neighbors
     * instead of an O(n²) all-pairs scan.
     */
    private static final java.util.Set<ShellExplosionSmokeParticle> ACTIVE =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static final java.util.List<ShellExplosionSmokeParticle> TICK_SNAPSHOT =
        new java.util.ArrayList<>();
    private static long snapshotGameTime = -1L;

    private final SpriteSet sprites;
    private int frame;

    /** Per-puff brightness multiplier (smokeDarknessMin..smokeDarknessMax),
     *  rolled at spawn and retained for the puff's whole life. */
    private final float shade;
    /** Per-puff heat: hotter puffs rise harder and longer; decays per tick. */
    private float heat;
    /** Per-puff cooling rate (heat multiplier per tick). */
    private float heatDecay;
    /** Per-puff randomized delay before it starts rising. */
    private int riseDelay;
    /** Fraction of lifetime spent at full opacity before fading (base smoke
     *  holds for 40% of its life). */
    private float fadeHoldFraction = 0.4F;
    /** Per-puff fade speed (base smoke): the alpha tail is t^fadePower, so
     *  puffs with a higher power turn transparent faster. */
    private final float fadePower;
    /** True for puffs spawned by the blast rays: static for 2s, then they
     *  expand outward at a constant small speed. */
    private boolean isRayParticle = false;
    /** This puff's own repulsion radius in blocks (random 1-2 at spawn,
     *  fixed for life). Only base puffs repel. */
    private final float repelRadius;
    /** Ray puffs: unit direction pointing away from the blast center. */
    private double rayDirX;
    private double rayDirY;
    private double rayDirZ;
    /** Ray puffs: constant outward speed (0.6-1.0 blocks/sec, rolled once). */
    private float rayStartSpeed;
    /** Ray puffs: per-puff logarithmic fade steepness (2-6) — higher fades
     *  faster at first. Different per puff so rays decay at different speeds
     *  but die at similar times. */
    private float logSteepness;

    ShellExplosionSmokeParticle(ClientLevel level, double x, double y, double z,
                                double dx, double dy, double dz, SpriteSet sprites,
                                int lifetime, float scale) {
        // SingleQuadParticle needs an initial sprite at construction; the
        // age-driven frame picker below replaces it on the first tick anyway.
        super(level, x, y, z, 0.0D, 0.0D, 0.0D, sprites.get(level.getRandom()));
        this.sprites = sprites;
        // Outward velocity from the blast, with a tiny upward jitter.
        this.xd = dx;
        this.yd = dy + this.random.nextFloat() / 500.0F;
        this.zd = dz;
        this.gravity = GRAVITY;
        this.friction = 0.98F;
        this.hasPhysics = true;
        this.setSize(0.25F, 0.25F);
        // Size: default quadSize (0.1-0.2) × 0.75 × the spawn scale.
        this.quadSize *= 0.75F * scale;
        // Size-dependent lifetime: the smaller the puff, the faster it
        // disappears. Puffs at the pivot size (quadSize ~1.2) keep the full
        // lifetime; small wisps live as little as 30% of it.
        float sizeLifetimeFactor = Mth.clamp(this.quadSize / 1.2F, 0.3F, 1.0F);
        this.lifetime = Math.max(1, Math.round(lifetime * sizeLifetimeFactor));
        this.frame = 0;
        // Random per-puff darkness: some puffs are noticeably darker. Kept
        // for life (retained shade, no graying).
        this.shade = ModConfig.smokeDarknessMin + this.random.nextFloat()
            * (ModConfig.smokeDarknessMax - ModConfig.smokeDarknessMin);
        this.rCol = this.shade;
        this.gCol = this.shade;
        this.bCol = this.shade;
        this.alpha = 1.0F;
        // Per-puff heat stats: some puffs are hotter (rise harder) and some
        // hold their heat longer (rise taller) than others.
        this.heat = ModConfig.smokeHeatMin + this.random.nextFloat()
            * (ModConfig.smokeHeatMax - ModConfig.smokeHeatMin);
        this.heatDecay = ModConfig.smokeHeatDecayMin + this.random.nextFloat()
            * (ModConfig.smokeHeatDecayMax - ModConfig.smokeHeatDecayMin);
        // Jittered lift-off time so puffs don't all start rising in lockstep.
        this.riseDelay = Math.max(0,
            Math.round(ModConfig.smokeRiseDelay * (0.5F + this.random.nextFloat())));
        // Per-puff random repulsion radius (1-2 blocks) — rolled once.
        this.repelRadius = 1.0F + this.random.nextFloat();
        // Per-puff logarithmic fade steepness (ray puffs only).
        this.logSteepness = 2.0F + this.random.nextFloat() * 4.0F;
        // Per-puff fade speed (base puffs): some turn transparent faster.
        this.fadePower = 1.0F + this.random.nextFloat() * 1.5F;
        this.setSpriteFromAge();
        ACTIVE.add(this);
    }

    /**
     * Marks this puff as a blast-ray particle: it stays static and opaque for
     * 2 seconds, then drifts AWAY from the blast center at its own constant
     * small speed (0.6-1.0 blocks/sec) while logarithmically fading. Lifetime
     * is 70% of the base smoke's full stay (140 ticks), slightly jittered.
     */
    public void markAsRay(double centerX, double centerY, double centerZ) {
        this.isRayParticle = true;
        double dx = this.x - centerX;
        double dy = this.y - centerY;
        double dz = this.z - centerZ;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-3D) {
            dx = 0.0D;
            dy = 1.0D;
            dz = 0.0D;
            len = 1.0D;
        }
        this.rayDirX = dx / len;
        this.rayDirY = dy / len;
        this.rayDirZ = dz / len;
        this.rayStartSpeed = 0.6F + this.random.nextFloat() * 0.4F; // 0.6-1.0 b/s
        this.lifetime = Math.max(1, Math.round(140.0F * (0.9F + this.random.nextFloat() * 0.2F)));
    }

    /** Multiplies this puff's heat (used for the guaranteed tall pillar rays). */
    public void boostHeat(float factor) {
        this.heat *= factor;
    }

    /**
     * Per-tick behavior. Base (large) puffs: gravity, collision movement,
     * quadratic air drag, heat-driven buoyancy and cooling, mutual repulsion,
     * turbulence, and a flat soft speed cap. Ray puffs: static for 2 seconds,
     * then a constant-speed outward scatter with a logarithmic fade.
     */
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        // The effective speed cap in blocks/tick (base puffs only).
        double cappedMaxSpeed = -1.0D;

        if (this.isRayParticle) {
            if (this.age < 40) {
                // First 2 seconds: ray puffs hold their streak position —
                // completely static, no movement at all.
                this.xd = 0.0D;
                this.yd = 0.0D;
                this.zd = 0.0D;
                this.move(0.0D, 0.0D, 0.0D);
            } else {
                // After 2 seconds: the ray puffs expand outward at their own
                // CONSTANT small speed (0.6-1.0 b/s, rolled at spawn) — no
                // acceleration curve, no other forces.
                double s = (double) this.rayStartSpeed / 20.0D; // b/s → b/tick
                this.xd = this.rayDirX * s;
                this.yd = this.rayDirY * s;
                this.zd = this.rayDirZ * s;
                this.move(this.xd, this.yd, this.zd);
            }
        } else {
            // Base (large) puffs move from birth.
            int riseAge = this.age;

            // Gravity: a slight downward pull.
            this.yd -= 0.04D * (double) this.gravity;
            // Move with collision.
            this.move(this.xd, this.yd, this.zd);
            // Puffs whose vertical motion is blocked speed up horizontally instead.
            if (this.y == this.yo) {
                this.xd *= 1.1D;
                this.zd *= 1.1D;
            }

            // --- Momentum friction: gentle 0.97x per tick so the spawn
            // velocity carries the puff outward for a while — the cloud
            // expands in every direction purely from the blast's momentum. ---
            this.xd *= 0.97D;
            this.zd *= 0.97D;

            // --- Buoyancy: base puffs rise by their heat ---
            float riseRamp = Mth.clamp(
                (float) riseAge / (float) ModConfig.smokeRiseRamp, 0.0F, 1.0F);
            this.yd = this.yd * 0.92D + ModConfig.smokeBuoyancy * riseRamp * this.heat;
            // Cooling: heat fades over the puff's life; cooled puffs sink.
            this.heat *= this.heatDecay;
            if (riseAge > 0 && this.heat < 0.12F) {
                this.yd -= 0.0015D;
            }
            if (this.onGround) {
                this.xd *= 0.7D;
                this.zd *= 0.7D;
            }

            // --- Mutual repulsion (base puffs only) ---
            // Puffs push away from each other within this puff's own fixed
            // radius (1-2 blocks, rolled once at spawn), so the cloud keeps
            // expanding evenly instead of clumping.
            long now = this.level.getGameTime();
            if (now != snapshotGameTime) {
                TICK_SNAPSHOT.clear();
                TICK_SNAPSHOT.addAll(ACTIVE);
                snapshotGameTime = now;
            }
            int snapSize = TICK_SNAPSHOT.size();
            if (snapSize > 1) {
                for (int i = 0; i < 6; ++i) {
                    ShellExplosionSmokeParticle other = TICK_SNAPSHOT.get(this.random.nextInt(snapSize));
                    if (other == this || !other.isAlive()) {
                        continue;
                    }
                    double dx = this.x - other.x;
                    double dy = this.y - other.y;
                    double dz = this.z - other.z;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    double radius = (double) this.repelRadius;
                    if (distSq < radius * radius && distSq > 1.0E-4D) {
                        double dist = Math.sqrt(distSq);
                        // Bounded push along the normalized direction, fading
                        // linearly to zero at the edge of the radius.
                        double push = (double) ModConfig.smokeRepelStrength
                            * (1.0D - dist / radius);
                        this.xd += dx / dist * push;
                        this.yd += dy / dist * push * 0.5D;
                        this.zd += dz / dist * push;
                    }
                }
            }

            // --- Turbulence: small random per-tick acceleration so every puff
            // wanders on its own path and the cloud expands organically. ---
            double turb = (double) ModConfig.smokeTurbulence;
            if (turb > 0.0D) {
                this.xd += (this.random.nextDouble() - 0.5D) * 2.0D * turb;
                this.zd += (this.random.nextDouble() - 0.5D) * 2.0D * turb;
                this.yd += (this.random.nextDouble() - 0.5D) * turb; // gentler vertically
            }

            // --- Speed cap: flat smokeBaseCap (1.2 b/s), soft-approached so
            // it never jerks to a stop. ---
            cappedMaxSpeed = (double) ModConfig.smokeBaseCap / 20.0D; // b/s → b/tick
            double totalSpeed = Math.sqrt(this.xd * this.xd
                + this.yd * this.yd + this.zd * this.zd);
            if (totalSpeed > cappedMaxSpeed) {
                // Soft cap: bleed off 40% of the excess per tick instead of
                // hard-clamping.
                double target = cappedMaxSpeed + (totalSpeed - cappedMaxSpeed) * 0.6D;
                double k = target / totalSpeed;
                this.xd *= k;
                this.yd *= k;
                this.zd *= k;
            }
        }

        // Advance the sprite frame from the puff's age.
        this.setSpriteFromAge();

        // --- Alpha fade ---
        if (this.isRayParticle) {
            // Ray puffs: full opacity for the first 2 seconds, then a
            // LOGARITHMIC fade — the transparency rate is high at first and
            // slows down later. Each puff rolls its own log steepness, so
            // they decay at different speeds but all disappear at similar
            // times (~70% of the base smoke's stay).
            float fadeProgress = Mth.clamp(
                (float) (this.age - 40)
                    / (float) Math.max(1, this.lifetime - 40), 0.0F, 1.0F);
            if (fadeProgress <= 0.0F) {
                this.alpha = 1.0F;
            } else {
                double k = (double) this.logSteepness;
                this.alpha = 1.0F
                    - (float) (Math.log(1.0D + fadeProgress * (Math.exp(k) - 1.0D)) / k);
            }
        } else {
            // Base smoke: full opacity for the first 40% of life, then an
            // ease-out tail whose speed varies per puff (t^fadePower) so some
            // turn transparent faster than others.
            float progress = Mth.clamp((float) this.age / (float) this.lifetime, 0.0F, 1.0F);
            if (progress <= this.fadeHoldFraction) {
                this.alpha = 1.0F;
            } else {
                float t = (1.0F - progress) / (1.0F - this.fadeHoldFraction);
                this.alpha = (float) Math.pow((double) t, (double) this.fadePower);
            }
        }
    }

    /** Quartic sprite progression over the frame set: the smoke visually
     *  "cools" quickly at first, then holds the late frames. */
    private void setSpriteFromAge() {
        float progress = Mth.clamp((float) this.age / (float) this.lifetime * 4.0F, 0.0F, 1.0F);
        float inv = 1.0F - progress;
        float spriteProgress = 1.0F - inv * inv * inv * inv;
        this.setSprite(this.sprites.get((int) Math.floor(spriteProgress * this.lifetime), this.lifetime));
    }

    /** Constant size for the puff's whole life (no growth). */
    @Override
    public float getQuadSize(float partialTicks) {
        return this.quadSize;
    }

    /**
     * Quartic full-bright flash near the blast core, decaying over the first
     * quarter-lifetime window. (26.1.2: the packed-light hook on particles was
     * renamed from getLightColor to getLightCoords.)
     */
    @Override
    protected int getLightCoords(float partialTick) {
        float progress = 1.0F - Mth.clamp((this.age + partialTick) / (float) this.lifetime * 4.0F, 0.0F, 1.0F);
        float brightness = progress * progress * progress * progress;

        int i = super.getLightCoords(partialTick);
        int j = i & 0xFF;
        int k = i >> 16 & 0xFF;
        j += (int) (brightness * 240.0F);
        if (j > 240) {
            j = 240;
        }
        return j | k << 16;
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public void remove() {
        super.remove();
        ACTIVE.remove(this);
    }

    /** Provider creating the particle from its option data. */
    public static class Provider implements ParticleProvider<ShellSmokeOption> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(ShellSmokeOption data, ClientLevel level, double x, double y,
                                       double z, double dx, double dy, double dz, RandomSource random) {
            return new ShellExplosionSmokeParticle(level, x, y, z, dx, dy, dz, this.sprites,
                data.lifetime(), data.scale());
        }
    }
}
