package asd.itamio.createtnt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

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
 * <p>Rendering: translucent sprites on the blocks texture atlas; the engine
 * renders these after translucent blocks, so the smoke draws on top of water.</p>
 */
public class ShellExplosionSmokeParticle extends EntityFX {

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
    private static final Set<ShellExplosionSmokeParticle> ACTIVE =
        Collections.newSetFromMap(new ConcurrentHashMap<ShellExplosionSmokeParticle, Boolean>());
    private static final List<ShellExplosionSmokeParticle> TICK_SNAPSHOT =
        new ArrayList<ShellExplosionSmokeParticle>();
    private static long snapshotGameTime = -1L;

    /** Icon array loaded via the texture stitch event (10 frames). */
    public static TextureAtlasSprite[] icons = new TextureAtlasSprite[FRAME_COUNT];

    /** Registers the 10 smoke frame icons on the blocks texture atlas. */
    public static void loadIcons(TextureMap map) {
        for (int i = 0; i < FRAME_COUNT; i++) {
            icons[i] = map.registerSprite(new ResourceLocation("createtnt", "explosion_smoke" + i));
        }
    }

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

    ShellExplosionSmokeParticle(World world, double x, double y, double z,
                                double dx, double dy, double dz, int lifetime, float scale) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        // Outward velocity from the blast, with a tiny upward jitter.
        this.motionX = dx;
        this.motionY = dy + this.rand.nextFloat() / 500.0F;
        this.motionZ = dz;
        this.particleGravity = GRAVITY;
        this.noClip = false;
        this.setSize(0.25F, 0.25F);
        // Size: default particleScale (0.1) × 0.75 × the spawn scale.
        this.particleScale *= 0.75F * scale;
        // Size-dependent lifetime: the smaller the puff, the faster it
        // disappears. Puffs at the pivot size (particleScale ~1.2) keep the
        // full lifetime; small wisps live as little as 30% of it.
        float sizeLifetimeFactor = MathHelper.clamp_float(this.particleScale / 1.2F, 0.3F, 1.0F);
        this.particleMaxAge = Math.max(1, Math.round(lifetime * sizeLifetimeFactor));
        this.frame = 0;
        // Random per-puff darkness: some puffs are noticeably darker. Kept
        // for life (retained shade, no graying).
        this.shade = ModConfig.smokeDarknessMin + this.rand.nextFloat()
            * (ModConfig.smokeDarknessMax - ModConfig.smokeDarknessMin);
        this.particleRed = this.shade;
        this.particleGreen = this.shade;
        this.particleBlue = this.shade;
        this.particleAlpha = 1.0F;
        // Per-puff heat stats: some puffs are hotter (rise harder) and some
        // hold their heat longer (rise taller) than others.
        this.heat = ModConfig.smokeHeatMin + this.rand.nextFloat()
            * (ModConfig.smokeHeatMax - ModConfig.smokeHeatMin);
        this.heatDecay = ModConfig.smokeHeatDecayMin + this.rand.nextFloat()
            * (ModConfig.smokeHeatDecayMax - ModConfig.smokeHeatDecayMin);
        // Jittered lift-off time so puffs don't all start rising in lockstep.
        this.riseDelay = Math.max(0,
            Math.round(ModConfig.smokeRiseDelay * (0.5F + this.rand.nextFloat())));
        // Per-puff random repulsion radius (1-2 blocks) — rolled once.
        this.repelRadius = 1.0F + this.rand.nextFloat();
        // Per-puff logarithmic fade steepness (ray puffs only).
        this.logSteepness = 2.0F + this.rand.nextFloat() * 4.0F;
        // Per-puff fade speed (base puffs): some turn transparent faster.
        this.fadePower = 1.0F + this.rand.nextFloat() * 1.5F;
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
        double dx = this.posX - centerX;
        double dy = this.posY - centerY;
        double dz = this.posZ - centerZ;
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
        this.rayStartSpeed = 0.6F + this.rand.nextFloat() * 0.4F; // 0.6-1.0 b/s
        this.particleMaxAge = Math.max(1, Math.round(140.0F * (0.9F + this.rand.nextFloat() * 0.2F)));
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
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        if (this.particleAge++ >= this.particleMaxAge) {
            this.setDead();
            return;
        }

        // The effective speed cap in blocks/tick (base puffs only).
        double cappedMaxSpeed = -1.0D;

        if (this.isRayParticle) {
            if (this.particleAge < 40) {
                // First 2 seconds: ray puffs hold their streak position —
                // completely static, no movement at all.
                this.motionX = 0.0D;
                this.motionY = 0.0D;
                this.motionZ = 0.0D;
                this.moveEntity(0.0D, 0.0D, 0.0D);
            } else {
                // After 2 seconds: the ray puffs expand outward at their own
                // CONSTANT small speed (0.6-1.0 b/s, rolled at spawn) — no
                // acceleration curve, no other forces.
                double s = (double) this.rayStartSpeed / 20.0D; // b/s → b/tick
                this.motionX = this.rayDirX * s;
                this.motionY = this.rayDirY * s;
                this.motionZ = this.rayDirZ * s;
                this.moveEntity(this.motionX, this.motionY, this.motionZ);
            }
        } else {
            // Base (large) puffs move from birth.
            int riseAge = this.particleAge;

            // Gravity: a slight downward pull.
            this.motionY -= 0.04D * (double) this.particleGravity;
            // Move with collision.
            this.moveEntity(this.motionX, this.motionY, this.motionZ);
            // Puffs whose vertical motion is blocked speed up horizontally instead.
            if (this.posY == this.prevPosY) {
                this.motionX *= 1.1D;
                this.motionZ *= 1.1D;
            }

            // --- Momentum friction: gentle 0.97x per tick so the spawn
            // velocity carries the puff outward for a while — the cloud
            // expands in every direction purely from the blast's momentum. ---
            this.motionX *= 0.97D;
            this.motionZ *= 0.97D;

            // --- Buoyancy: base puffs rise by their heat ---
            float riseRamp = MathHelper.clamp_float(
                (float) riseAge / (float) ModConfig.smokeRiseRamp, 0.0F, 1.0F);
            this.motionY = this.motionY * 0.92D + ModConfig.smokeBuoyancy * riseRamp * this.heat;
            // Cooling: heat fades over the puff's life; cooled puffs sink.
            this.heat *= this.heatDecay;
            if (riseAge > 0 && this.heat < 0.12F) {
                this.motionY -= 0.0015D;
            }
            if (this.onGround) {
                this.motionX *= 0.7D;
                this.motionZ *= 0.7D;
            }

            // --- Mutual repulsion (base puffs only) ---
            // Puffs push away from each other within this puff's own fixed
            // radius (1-2 blocks, rolled once at spawn), so the cloud keeps
            // expanding evenly instead of clumping.
            long now = this.worldObj.getTotalWorldTime();
            if (now != snapshotGameTime) {
                TICK_SNAPSHOT.clear();
                TICK_SNAPSHOT.addAll(ACTIVE);
                snapshotGameTime = now;
            }
            int snapSize = TICK_SNAPSHOT.size();
            if (snapSize > 1) {
                for (int i = 0; i < 6; ++i) {
                    ShellExplosionSmokeParticle other = TICK_SNAPSHOT.get(this.rand.nextInt(snapSize));
                    if (other == this || other.isDead) {
                        continue;
                    }
                    double dx = this.posX - other.posX;
                    double dy = this.posY - other.posY;
                    double dz = this.posZ - other.posZ;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    double radius = (double) this.repelRadius;
                    if (distSq < radius * radius && distSq > 1.0E-4D) {
                        double dist = Math.sqrt(distSq);
                        // Bounded push along the normalized direction, fading
                        // linearly to zero at the edge of the radius.
                        double push = (double) ModConfig.smokeRepelStrength
                            * (1.0D - dist / radius);
                        this.motionX += dx / dist * push;
                        this.motionY += dy / dist * push * 0.5D;
                        this.motionZ += dz / dist * push;
                    }
                }
            }

            // --- Turbulence: small random per-tick acceleration so every puff
            // wanders on its own path and the cloud expands organically. ---
            double turb = (double) ModConfig.smokeTurbulence;
            if (turb > 0.0D) {
                this.motionX += (this.rand.nextDouble() - 0.5D) * 2.0D * turb;
                this.motionZ += (this.rand.nextDouble() - 0.5D) * 2.0D * turb;
                this.motionY += (this.rand.nextDouble() - 0.5D) * turb; // gentler vertically
            }

            // --- Speed cap: flat smokeBaseCap (1.2 b/s), soft-approached so
            // it never jerks to a stop. ---
            cappedMaxSpeed = (double) ModConfig.smokeBaseCap / 20.0D; // b/s → b/tick
            double totalSpeed = Math.sqrt(this.motionX * this.motionX
                + this.motionY * this.motionY + this.motionZ * this.motionZ);
            if (totalSpeed > cappedMaxSpeed) {
                // Soft cap: bleed off 40% of the excess per tick instead of
                // hard-clamping.
                double target = cappedMaxSpeed + (totalSpeed - cappedMaxSpeed) * 0.6D;
                double k = target / totalSpeed;
                this.motionX *= k;
                this.motionY *= k;
                this.motionZ *= k;
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
            float fadeProgress = MathHelper.clamp_float(
                (float) (this.particleAge - 40)
                    / (float) Math.max(1, this.particleMaxAge - 40), 0.0F, 1.0F);
            if (fadeProgress <= 0.0F) {
                this.particleAlpha = 1.0F;
            } else {
                double k = (double) this.logSteepness;
                this.particleAlpha = 1.0F
                    - (float) (Math.log(1.0D + fadeProgress * (Math.exp(k) - 1.0D)) / k);
            }
        } else {
            // Base smoke: full opacity for the first 40% of life, then an
            // ease-out tail whose speed varies per puff (t^fadePower) so some
            // turn transparent faster than others.
            float progress = MathHelper.clamp_float((float) this.particleAge / (float) this.particleMaxAge, 0.0F, 1.0F);
            if (progress <= this.fadeHoldFraction) {
                this.particleAlpha = 1.0F;
            } else {
                float t = (1.0F - progress) / (1.0F - this.fadeHoldFraction);
                this.particleAlpha = (float) Math.pow((double) t, (double) this.fadePower);
            }
        }
    }

    /** Quartic sprite progression over the frame set: the smoke visually
     *  "cools" quickly at first, then holds the late frames. */
    private void setSpriteFromAge() {
        float progress = MathHelper.clamp_float(
            (float) this.particleAge / (float) this.particleMaxAge * 4.0F, 0.0F, 1.0F);
        float inv = 1.0F - progress;
        float spriteProgress = 1.0F - inv * inv * inv * inv;
        this.frame = MathHelper.clamp_int(
            (int) Math.floor(spriteProgress * (float) FRAME_COUNT), 0, FRAME_COUNT - 1);
        this.particleIcon = icons[this.frame];
    }

    /**
     * Renders the puff using the current frame icon. The parent EntityFX
     * handles billboard quad generation and uses particleAlpha for
     * translucency.
     */
    @Override
    public void renderParticle(WorldRenderer wr, Entity entity, float partialTicks,
                               float rotationX, float rotationZ, float rotationYZ,
                               float rotationXY, float rotationXZ) {
        this.particleIcon = icons[this.frame];
        super.renderParticle(wr, entity, partialTicks, rotationX, rotationZ,
            rotationYZ, rotationXY, rotationXZ);
    }

    /**
     * Quartic full-bright flash near the blast core, decaying over the first
     * quarter-lifetime window.
     */
    @Override
    public int getBrightnessForRender(float partialTick) {
        float progress = 1.0F - MathHelper.clamp_float(
            (this.particleAge + partialTick) / (float) this.particleMaxAge * 4.0F, 0.0F, 1.0F);
        float brightness = progress * progress * progress * progress;

        int i = super.getBrightnessForRender(partialTick);
        int j = i & 0xFF;
        int k = i >> 16 & 0xFF;
        j += (int) (brightness * 240.0F);
        if (j > 240) {
            j = 240;
        }
        return j | k << 16;
    }

    /** Uses the blocks texture atlas (layer 0). */
    @Override
    public int getFXLayer() {
        return 0;
    }

    @Override
    public void setDead() {
        super.setDead();
        ACTIVE.remove(this);
    }
}
