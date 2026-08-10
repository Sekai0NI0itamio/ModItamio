package asd.itamio.createtnt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * A single large smoke puff of the explosion cloud.
 *
 * <p>Behavior: the puff is born with an outward velocity from the blast
 * (strong quadratic air drag stalls the burst within a few ticks), then the
 * large base puffs spread apart by mutual repulsion, wander with turbulence,
 * ride the wind, and rise by their own heat level — cooling off and sinking
 * at the end. Their speed cap ramps exponentially from a slow drift to the
 * full cap at the end of life. Ray puffs hold their streak position for 2
 * seconds, then expand outward with an exponentially accelerating scatter
 * while growing and logarithmically fading.</p>
 *
 * <p>Rendering: 1.12.2 has no particle texture atlas for custom sprites, so
 * each frame texture is bound directly and the quad is drawn with a deferred
 * translucent pass (see {@link SmokeRenderHandler}) so the smoke draws on top
 * of water instead of being painted over by it.</p>
 */
@SideOnly(Side.CLIENT)
public class ShellExplosionSmokeParticle extends Particle {

    /** The smoke animation has 10 frame textures (explosion_smoke0..9). */
    private static final int FRAME_COUNT = 10;

    /**
     * All live smoke particles, rendered deferred by {@link SmokeRenderHandler}
     * after the translucent block pass. In 1.12.2 every particle pass runs
     * BEFORE translucent blocks (water/lava), so in-pass smoke gets painted
     * over by flowing water. The deferred pass renders after translucents,
     * so smoke draws on top of water.
     */
    private static final java.util.Set<ShellExplosionSmokeParticle> ACTIVE_PARTICLES =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /**
     * Per-tick snapshot of all live smoke puffs, used for the mutual
     * repulsion sampling. Rebuilt once per world tick (the first puff to
     * tick that tick refreshes it) so each puff only checks a few random
     * neighbors instead of an O(n²) all-pairs scan.
     */
    private static final java.util.List<ShellExplosionSmokeParticle> TICK_SNAPSHOT =
        new java.util.ArrayList<>();
    private static long snapshotWorldTime = -1L;

    /** Slight downward pull per tick (positive = slowly sinks). */
    private static final float GRAVITY = 0.05F;

    private final TextureManager textureManager;
    private final ResourceLocation[] frameTextures;
    private int frame;
    /** Per-puff brightness multiplier (smokeDarknessMin..smokeDarknessMax).
     *  Rolled at spawn and retained for the puff's whole life, so the cloud
     *  is a static mix of gray, dark, and near-white puffs. */
    private final float shade;
    /** Per-puff heat: hotter puffs rise harder and longer (tall columns);
     *  cooler ones stall early (short). Decays each tick (cooling). */
    private float heat;
    /** Per-puff cooling rate (heat multiplier per tick). */
    private float heatDecay;
    /** Per-puff randomized delay before it starts rising. */
    private int riseDelay;
    /** Fraction of lifetime spent at full opacity before fading (base smoke
     *  holds for 40% of its life). */
    private float fadeHoldFraction = 0.4F;
    /** Per-puff fade speed (base smoke): the alpha tail is t^fadePower, so
     *  puffs with a higher power turn transparent faster. Rolled 1.0-2.5 at
     *  spawn so the cloud doesn't dissolve uniformly. */
    private final float fadePower;
    /** True for puffs spawned by the blast rays: static for 2s, then they
     *  expand outward with an accelerating scatter. */
    private boolean isRayParticle = false;
    /** This puff's own repulsion radius in blocks (random 1-2 at spawn,
     *  fixed for life). Only base puffs repel. */
    private final float repelRadius;
    /** Ray puffs: unit direction pointing away from the blast center (set by
     *  markAsRay). The actual speed ramps exponentially over the expansion
     *  phase: slow at first, fast at the end. */
    private double rayDirX;
    private double rayDirY;
    private double rayDirZ;
    /** Ray puffs: outward speed at the start of the expansion (0.6-1.0 b/s). */
    private float rayStartSpeed;
    /** Ray puffs: per-puff logarithmic fade steepness (2-6) — higher fades
     *  faster at first. Different per puff so rays decay at different speeds
     *  but die at similar times. */
    private float logSteepness;
    /**
     * World time of the last onUpdate. Used by {@link SmokeRenderHandler} to
     * prune particles that are no longer being ticked: the particle manager
     * caps each layer at 16384 entries and silently evicts the oldest without
     * setExpired() — such particles freeze (no tick, no animation) and would
     * otherwise render forever as ghost smoke after massive chain explosions.
     */
    long lastTickWorldTime;

    /**
     * @param scale    base size multiplier of the puff
     * @param lifetime initial lifetime in ticks (size-adjusted in here)
     */
    public ShellExplosionSmokeParticle(World world, double x, double y, double z,
                                       double dx, double dy, double dz, int lifetime, float scale) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        // Outward velocity from the blast, with a tiny upward jitter.
        this.motionX = dx;
        this.motionY = dy + this.rand.nextFloat() / 500.0F;
        this.motionZ = dz;
        // Full white base color; the per-puff shade is applied at render time.
        this.particleRed = 1.0F;
        this.particleGreen = 1.0F;
        this.particleBlue = 1.0F;
        this.particleAlpha = 1.0F;
        this.particleGravity = GRAVITY;
        // Small collision box for the physics collision checks.
        this.setSize(0.25F, 0.25F);
        // Puffs collide with blocks.
        this.canCollide = true;
        // Size: each puff gets a random 0.75x-1.5x multiplier on the base
        // scale so the cloud is uneven and organic rather than uniform.
        this.particleScale = (this.rand.nextFloat() * 0.75F + 0.75F) * scale;
        // Size-dependent lifetime: the smaller the puff, the faster it
        // disappears. Puffs at the pivot size (particleScale ~12) keep the
        // full lifetime; small wisps live as little as 30% of it.
        float sizeLifetimeFactor = MathHelper.clamp(this.particleScale / 12.0F, 0.3F, 1.0F);
        this.particleMaxAge = Math.max(1, Math.round(lifetime * sizeLifetimeFactor));
        this.frame = 0;
        // Random per-puff darkness: some puffs start noticeably darker.
        this.shade = ModConfig.smokeDarknessMin + this.rand.nextFloat()
            * (ModConfig.smokeDarknessMax - ModConfig.smokeDarknessMin);
        // Per-puff heat stats: some puffs are hotter (rise harder) and some
        // hold their heat longer (rise taller) than others.
        this.heat = ModConfig.smokeHeatMin + this.rand.nextFloat()
            * (ModConfig.smokeHeatMax - ModConfig.smokeHeatMin);
        this.heatDecay = ModConfig.smokeHeatDecayMin + this.rand.nextFloat()
            * (ModConfig.smokeHeatDecayMax - ModConfig.smokeHeatDecayMin);
        // Jittered lift-off time so puffs don't all start rising in lockstep.
        this.riseDelay = Math.max(0,
            Math.round(ModConfig.smokeRiseDelay * (0.5F + this.rand.nextFloat())));
        // Per-puff random repulsion radius (1-2 blocks) — rolled once at
        // spawn and constant for life.
        this.repelRadius = 1.0F + this.rand.nextFloat();
        // Per-puff logarithmic fade steepness (ray puffs only).
        this.logSteepness = 2.0F + this.rand.nextFloat() * 4.0F;
        // Per-puff fade speed (base puffs): some turn transparent faster.
        this.fadePower = 1.0F + this.rand.nextFloat() * 1.5F;

        this.frameTextures = new ResourceLocation[FRAME_COUNT];
        for (int i = 0; i < FRAME_COUNT; ++i) {
            this.frameTextures[i] =
                new ResourceLocation(BetterTNTs.MOD_ID, "textures/particle/explosion_smoke" + i + ".png");
        }
        this.textureManager = Minecraft.getMinecraft().getTextureManager();
        // Initialize to now so the stale-render pruning doesn't discard the
        // particle before its first tick (the render pass can run before
        // the first onUpdate).
        this.lastTickWorldTime = world.getTotalWorldTime();
        ACTIVE_PARTICLES.add(this);
    }

    /**
     * Marks this puff as a blast-ray particle: it stays static and opaque for
     * 2 seconds, then drifts AWAY from the blast center at its own constant
     * small speed (0.6-1.0 b/s, rolled here) while logarithmically fading.
     * Lifetime is 70% of the base smoke's full stay (140 ticks), slightly
     * jittered so puffs die at similar but not identical times.
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
     * turbulence, an exponentially ramping speed cap, then wind drift, sprite
     * animation, and the alpha fade. Ray puffs: static for 2 seconds, then an
     * exponentially accelerating outward scatter with a logarithmic fade.
     */
    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.lastTickWorldTime = this.world.getTotalWorldTime();

        if (this.particleAge++ >= this.particleMaxAge) {
            this.setExpired();
            return;
        }

        // The effective speed cap in blocks/tick for the wind clamp below;
        // only base puffs get one (rays follow their own scatter curve).
        double cappedMaxSpeed = -1.0D;

        if (this.isRayParticle) {
            if (this.particleAge < 40) {
                // First 2 seconds: ray puffs hold their streak position —
                // completely static, no movement at all.
                this.motionX = 0.0D;
                this.motionY = 0.0D;
                this.motionZ = 0.0D;
                this.move(0.0D, 0.0D, 0.0D);
            } else {
                // After 2 seconds: the ray puffs expand outward at their own
                // CONSTANT small speed (0.6-1.0 b/s, rolled at spawn) — no
                // acceleration curve, no other forces.
                double s = (double) this.rayStartSpeed / 20.0D; // b/s → b/tick
                this.motionX = this.rayDirX * s;
                this.motionY = this.rayDirY * s;
                this.motionZ = this.rayDirZ * s;
                this.move(this.motionX, this.motionY, this.motionZ);
            }
        } else {
            // Base (large) puffs are the first to spread and move — all their
            // physics start at birth.
            int riseAge = this.particleAge;

            // Gravity: a slight downward pull.
            this.motionY -= 0.04D * (double) this.particleGravity;
            // Move with collision.
            this.move(this.motionX, this.motionY, this.motionZ);
            // Puffs whose vertical motion is blocked speed up horizontally instead.
            if (this.posY == this.prevPosY) {
                this.motionX *= 1.1D;
                this.motionZ *= 1.1D;
            }

            // --- Momentum friction ---
            // Gentle 0.97x per tick so the puff's spawn velocity carries it
            // outward for a while: the cloud expands in every direction
            // purely from the blast's momentum (no wind at all).
            this.motionX *= 0.97D;
            this.motionZ *= 0.97D;

            // --- Buoyancy: base puffs rise by their heat ---
            float riseRamp = MathHelper.clamp(
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
            // expanding evenly instead of clumping. Samples a few random
            // neighbors per tick — statistically sufficient over many ticks.
            long now = this.world.getTotalWorldTime();
            if (now != snapshotWorldTime) {
                TICK_SNAPSHOT.clear();
                TICK_SNAPSHOT.addAll(ACTIVE_PARTICLES);
                snapshotWorldTime = now;
            }
            int snapSize = TICK_SNAPSHOT.size();
            if (snapSize > 1) {
                for (int i = 0; i < 6; ++i) {
                    ShellExplosionSmokeParticle other = TICK_SNAPSHOT.get(this.rand.nextInt(snapSize));
                    if (other == this || !other.isAlive()) {
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

            // --- Speed cap: the large base smoke is capped at a flat
            // smokeBaseCap (1.5 b/s), soft-approached so it never jerks to
            // a stop. ---
            cappedMaxSpeed = (double) ModConfig.smokeBaseCap / 20.0D; // b/s → b/tick
            double totalSpeed = Math.sqrt(this.motionX * this.motionX
                + this.motionY * this.motionY + this.motionZ * this.motionZ);
            if (totalSpeed > cappedMaxSpeed) {
                // Soft cap: bleed off 40% of the excess per tick instead of
                // hard-clamping, so the speed decays smoothly toward the
                // limit and the smoke never snaps to a stop.
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
            float fadeProgress = MathHelper.clamp(
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
            float progress = MathHelper.clamp((float) this.particleAge / (float) this.particleMaxAge, 0.0F, 1.0F);
            if (progress <= this.fadeHoldFraction) {
                this.particleAlpha = 1.0F;
            } else {
                float t = (1.0F - progress) / (1.0F - this.fadeHoldFraction);
                this.particleAlpha = (float) Math.pow((double) t, (double) this.fadePower);
            }
        }
    }

    /**
     * Quartic sprite progression over the frame set: the smoke visually
     * "cools" quickly at first, then holds the late frames.
     */
    private void setSpriteFromAge() {
        float progress = MathHelper.clamp((float) this.particleAge / (float) this.particleMaxAge * 4.0F, 0.0F, 1.0F);
        float inv = 1.0F - progress;
        float spriteProgress = 1.0F - inv * inv * inv * inv;
        int f = (int) Math.floor(spriteProgress * (double) FRAME_COUNT);
        if (f >= FRAME_COUNT) {
            f = FRAME_COUNT - 1;
        }
        this.frame = f;
    }

    /**
     * FX layer 3 = "custom texture" layer (no shared buffer is begun for it,
     * so nothing is drawn in-pass). The actual draw happens deferred in
     * {@link SmokeRenderHandler} after translucent blocks — see
     * {@link #renderSmoke}.
     */
    @Override
    public int getFXLayer() {
        return 3;
    }

    /** In-pass render is a no-op; smoke is drawn deferred, after water. */
    @Override
    public void renderParticle(BufferBuilder buffer, Entity entityIn, float partialTicks,
                               float rotationX, float rotationZ, float rotationYZ,
                               float rotationXY, float rotationXZ) {
    }

    /**
     * Deferred draw called by {@link SmokeRenderHandler} during
     * RenderWorldLastEvent (after the translucent block pass). The handler
     * owns the GL state (blend, depth mask, lightmap), so this method only
     * binds its frame texture and emits one camera-relative billboard quad
     * with the size growth, spawn-locked shade, and brightness flash.
     */
    public void renderSmoke(Entity entityIn, float partialTicks,
                            float rotationX, float rotationZ, float rotationYZ,
                            float rotationXY, float rotationXZ) {
        // Constant size for the puff's whole life (no growth).
        float f4 = 0.1F * this.particleScale;

        // Camera position from the viewer (exact, not the stale statics).
        double camX = entityIn.lastTickPosX + (entityIn.posX - entityIn.lastTickPosX) * (double) partialTicks;
        double camY = entityIn.lastTickPosY + (entityIn.posY - entityIn.lastTickPosY) * (double) partialTicks;
        double camZ = entityIn.lastTickPosZ + (entityIn.posZ - entityIn.lastTickPosZ) * (double) partialTicks;

        float f5 = (float) (this.prevPosX + (this.posX - this.prevPosX) * (double) partialTicks - camX);
        float f6 = (float) (this.prevPosY + (this.posY - this.prevPosY) * (double) partialTicks - camY);
        float f7 = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * (double) partialTicks - camZ);

        int light = this.getBrightnessForRender(partialTicks);
        int sky = light >> 16 & 65535;
        int block = light & 65535;

        // The puff's shade is determined at spawn and retained for its whole
        // life — no graying over time.
        float shadeNow = this.shade;

        this.textureManager.bindTexture(this.frameTextures[this.frame]);

        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(7, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
        buf.pos((double) (f5 - rotationX * f4 - rotationXY * f4), (double) (f6 - rotationZ * f4), (double) (f7 - rotationYZ * f4 - rotationXZ * f4)).tex(1.0D, 1.0D).color(this.particleRed * shadeNow, this.particleGreen * shadeNow, this.particleBlue * shadeNow, this.particleAlpha).lightmap(sky, block).endVertex();
        buf.pos((double) (f5 - rotationX * f4 + rotationXY * f4), (double) (f6 + rotationZ * f4), (double) (f7 - rotationYZ * f4 + rotationXZ * f4)).tex(1.0D, 0.0D).color(this.particleRed * shadeNow, this.particleGreen * shadeNow, this.particleBlue * shadeNow, this.particleAlpha).lightmap(sky, block).endVertex();
        buf.pos((double) (f5 + rotationX * f4 + rotationXY * f4), (double) (f6 + rotationZ * f4), (double) (f7 + rotationYZ * f4 + rotationXZ * f4)).tex(0.0D, 0.0D).color(this.particleRed * shadeNow, this.particleGreen * shadeNow, this.particleBlue * shadeNow, this.particleAlpha).lightmap(sky, block).endVertex();
        buf.pos((double) (f5 + rotationX * f4 - rotationXY * f4), (double) (f6 - rotationZ * f4), (double) (f7 + rotationYZ * f4 - rotationXZ * f4)).tex(0.0D, 1.0D).color(this.particleRed * shadeNow, this.particleGreen * shadeNow, this.particleBlue * shadeNow, this.particleAlpha).lightmap(sky, block).endVertex();
        Tessellator.getInstance().draw();
    }

    /**
     * Quartic full-bright flash near the blast core, decaying over the first
     * quarter-lifetime window.
     */
    @Override
    public int getBrightnessForRender(float partialTick) {
        float progress = 1.0F - MathHelper.clamp(((float) this.particleAge + partialTick) / (float) this.particleMaxAge * 4.0F, 0.0F, 1.0F);
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

    @Override
    public void setExpired() {
        super.setExpired();
        ACTIVE_PARTICLES.remove(this);
    }

    /** Active smoke particles for the deferred render pass. */
    public static java.util.Set<ShellExplosionSmokeParticle> getActiveParticles() {
        return ACTIVE_PARTICLES;
    }

    /** Whether the particle manager has discarded this particle. */
    public boolean isDead() {
        return !this.isAlive();
    }

    /** The world this particle lives in (for stale-entry pruning). */
    public World getParticleWorld() {
        return this.world;
    }
}
