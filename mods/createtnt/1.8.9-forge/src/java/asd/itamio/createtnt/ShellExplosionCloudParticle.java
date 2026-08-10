package asd.itamio.createtnt;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * Hidden controller particle that drives the explosion smoke cloud.
 *
 * <p>For its first 5 ticks ({@code PLUME_AGE}) it bursts
 * {@link ShellExplosionSmokeParticle} puffs around the blast center — 20 per
 * tick on the "All" particle setting, 10 on "Decreased", 5 on "Minimal"
 * (scaled by {@code baseSmokeAmount}) — while its trail sub-particles paint
 * the blast-ray streaks over up to 5 ticks. It renders nothing itself and
 * expires after 10 ticks.</p>
 *
 * <p>Spawn geometry: puffs get random gaussian-ish offsets and velocities,
 * ground blasts bias upward (the plume). The blast rays are laid out on a
 * jittered sphere lattice with a random global rotation per explosion, plus
 * 1-4 guaranteed tall pillar rays off the top.</p>
 */
public class ShellExplosionCloudParticle extends EntityFX {

    /** Ticks over which the controller spawns the primary plume puffs. */
    private static final int PLUME_AGE = 5;

    private final float power;
    private final List<TrailSubparticle> trails = new LinkedList<TrailSubparticle>();
    private final boolean isPlume;

    ShellExplosionCloudParticle(World world, double x, double y, double z,
                                float power, boolean isPlume) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        // Zero velocity; the controller itself renders nothing.
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;
        this.particleGravity = 0.0F;
        this.noClip = true;
        this.power = power;
        this.isPlume = isPlume;
        // The controller expires after 10 ticks.
        this.particleMaxAge = 10;

        // Blast-ray trails can be disabled in the config.
        if (ModConfig.showExtraShellExplosionTrails) {
            double secondaryVelScale = this.power * 0.35D;
            // Blast-ray counts scale with the in-game particle setting:
            // ALL 12+rand(6), DECREASED 6+rand(3), MINIMAL 4.
            int secondaryCount;
            switch (getParticleStatus()) {
                case 0: secondaryCount = 12 + this.rand.nextInt(6); break; // ALL
                case 1: secondaryCount = 6 + this.rand.nextInt(3); break;  // DECREASED
                default: secondaryCount = 4; break;                         // MINIMAL
            }
            secondaryCount = Math.max(1, (int) (secondaryCount * ModConfig.explosionTrailMultiplier));
            double gravity = this.isPlume ? -0.5D : -0.1D;

            // Distribute the blast-ray directions evenly across a sphere
            // (Fibonacci lattice) with per-ray jitter and a random global
            // rotation per explosion — so every explosion looks different,
            // each line shoots out in its own direction, and no two lines
            // start out touching each other.
            double goldenAngle = Math.PI * (3.0D - Math.sqrt(5.0D));
            double rotYaw = this.rand.nextDouble() * Math.PI * 2.0D;
            double rotTilt = this.rand.nextDouble() * (this.isPlume ? 0.5D : Math.PI);
            double cosYaw = Math.cos(rotYaw);
            double sinYaw = Math.sin(rotYaw);
            double cosTilt = Math.cos(rotTilt);
            double sinTilt = Math.sin(rotTilt);

            for (int i = 0; i < secondaryCount; ++i) {
                double dy = secondaryCount <= 1 ? 0.0D
                    : 1.0D - 2.0D * (double) i / (double) (secondaryCount - 1);
                double r = Math.sqrt(Math.max(0.0D, 1.0D - dy * dy));
                double theta = goldenAngle * (double) i;
                double dx = Math.cos(theta) * r;
                double dz = Math.sin(theta) * r;
                // Per-ray jitter off the perfect lattice (keeps it organic).
                dx += (this.rand.nextDouble() - this.rand.nextDouble()) * 0.35D;
                dy += (this.rand.nextDouble() - this.rand.nextDouble()) * 0.35D;
                dz += (this.rand.nextDouble() - this.rand.nextDouble()) * 0.35D;
                // Ground blasts send their rays upward (the plume case).
                if (this.isPlume) {
                    dy = Math.abs(dy);
                }
                // Random global rotation: yaw, then tilt.
                double tx = dx * cosYaw + dz * sinYaw;
                double tz = dz * cosYaw - dx * sinYaw;
                double ty = dy * cosTilt - tz * sinTilt;
                tz = dy * sinTilt + tz * cosTilt;
                dx = tx;
                dy = ty;
                dz = tz;
                // Normalize to a unit direction.
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1.0E-4D) {
                    dx = 0.0D;
                    dy = 1.0D;
                    dz = 0.0D;
                    len = 1.0D;
                }
                dx /= len;
                dy /= len;
                dz /= len;

                double speed = 0.4D + this.rand.nextDouble() * 0.6D;
                Vec3 vel = scale(new Vec3(dx * speed, dy * speed, dz * speed), secondaryVelScale);
                // Start each ray slightly out along its own direction so the
                // lines read as clearly separated streaks from the core.
                Vec3 displacement = scale(new Vec3(dx, dy, dz),
                    1.0D + this.rand.nextDouble() * 1.5D);
                int lifetime = 5;
                this.trails.add(new TrailSubparticle(displacement, vel, 0.85D, gravity, lifetime));
            }

            // --- Guaranteed pillar rays ---
            // Every explosion gets 1-4 long pillar rays climbing off the top
            // in random steep directions — EVERY one is 15-25 blocks tall.
            // Their puffs are heat-boosted so the pillars keep rising after
            // the streak.
            int pillarCount = 1 + this.rand.nextInt(4);
            for (int i = 0; i < pillarCount; ++i) {
                this.addPillarRay(secondaryVelScale, 15.0D, 25.0D);
            }
        }
    }

    /**
     * Adds one guaranteed long pillar ray climbing off the top of the blast in
     * a random steep direction. The speed is derived FROM the target length
     * (over the trail's 5-tick geometric travel at drag 0.85 ≈ 3.708x
     * velocity), so the requested length holds at any explosion power.
     * Pillar puffs get a heat boost in the trail spawn loop so they keep
     * rising after the streak.
     */
    private void addPillarRay(double velScale, double minLength, double maxLength) {
        double azimuth = this.rand.nextDouble() * Math.PI * 2.0D;
        double elevation = 1.047D + this.rand.nextDouble() * 0.489D; // 60-88 degrees
        double dx = Math.cos(elevation) * Math.cos(azimuth);
        double dy = Math.sin(elevation);
        double dz = Math.cos(elevation) * Math.sin(azimuth);
        double targetLength = minLength + this.rand.nextDouble() * (maxLength - minLength);
        double speed = targetLength / (3.708D * velScale);
        Vec3 vel = scale(new Vec3(dx * speed, dy * speed, dz * speed), velScale);
        Vec3 displacement = scale(new Vec3(dx, dy, dz),
            1.0D + this.rand.nextDouble() * 1.5D);
        // Near-zero trail gravity so the pillar climbs straight instead of sagging.
        this.trails.add(new TrailSubparticle(displacement, vel, 0.85D, -0.05D, 5, true));
    }

    @Override
    public void onUpdate() {
        if (this.particleAge < PLUME_AGE) {
            float primaryScale = this.power * 2.0F;
            int plumes;
            switch (getParticleStatus()) {
                case 0: plumes = 20; break; // ALL
                case 1: plumes = 10; break; // DECREASED
                default: plumes = 5; break;  // MINIMAL
            }
            // Base smoke amount multiplier (0.45 = about half the default density).
            plumes = Math.max(1, (int) (plumes * ModConfig.baseSmokeAmount));
            double velScale = this.power * 0.25D;
            velScale *= 1.0D - (float) this.particleAge / (float) PLUME_AGE;
            double displacementScale = this.power * 0.35D;
            for (int i = 0; i <= plumes; ++i) {
                double rx = this.posX + (this.rand.nextDouble() - this.rand.nextDouble()) * displacementScale;
                double ry = this.rand.nextDouble() - this.rand.nextDouble();
                if (this.isPlume) {
                    ry = Math.abs(ry);
                }
                ry *= displacementScale;
                ry += this.posY + 0.5D;
                double rz = this.posZ + (this.rand.nextDouble() - this.rand.nextDouble()) * displacementScale;
                double dx = (this.rand.nextDouble() - this.rand.nextDouble()) * velScale;
                double dy = this.rand.nextDouble() - this.rand.nextDouble();
                if (this.isPlume) {
                    dy += 1.2D;
                }
                dy *= velScale;
                double dz = (this.rand.nextDouble() - this.rand.nextDouble()) * velScale;
                // Base smoke lifetime: 7 seconds (140 ticks) — the large
                // puffs disappear around the same time as the ray puffs.
                int lifetime = 140;
                Minecraft.getMinecraft().effectRenderer.addEffect(
                    new ShellExplosionSmokeParticle(this.worldObj, rx, ry, rz, dx, dy, dz, lifetime, primaryScale));
            }
        }

        int trailSteps = getParticleStatus() == 0 ? 2 : 1; // 0 = ALL
        float secondaryScale = this.power * 0.75F;
        for (Iterator<TrailSubparticle> iter = this.trails.iterator(); iter.hasNext(); ) {
            TrailSubparticle trail = iter.next();
            if (this.particleAge > trail.lifetime) {
                iter.remove();
                continue;
            }
            Vec3 origin = trail.calculateDisplacement(this.particleAge);
            Vec3 next = trail.calculateDisplacement(this.particleAge + 1);
            double velScale = 0.125D;
            // Wide spawn spread so ray puffs form soft wisps instead of sharp
            // radiate lines with gaps between them.
            double displacementScale = 0.3D;
            // Pillar rays travel 10-16 blocks per tick — with only a few
            // puffs per tick they would be sparse dotted lines. Spawn enough
            // puffs per tick to keep the whole pillar densely filled.
            int steps = trailSteps;
            if (trail.pillar) {
                double stepDist = distance(origin, next);
                steps = Math.min(40, Math.max(2,
                    (int) Math.ceil(stepDist / ModConfig.pillarTrailSpacing)));
            }
            for (int i = 0; i <= steps; ++i) {
                Vec3 pos = lerp(origin, next, (double) i / (double) steps);
                double rx = pos.xCoord + this.posX + (this.rand.nextDouble() - this.rand.nextDouble()) * displacementScale;
                double ry = pos.yCoord + this.posY + (this.rand.nextDouble() - this.rand.nextDouble()) * displacementScale;
                double rz = pos.zCoord + this.posZ + (this.rand.nextDouble() - this.rand.nextDouble()) * displacementScale;
                double dx = (this.rand.nextDouble() - this.rand.nextDouble()) * velScale;
                double dy = (this.rand.nextDouble() - this.rand.nextDouble()) * velScale;
                double dz = (this.rand.nextDouble() - this.rand.nextDouble()) * velScale;
                // Ray puffs: static for 2s, then scatter outward at their own
                // constant small speed; they live ~70% of the base smoke's
                // stay (set inside markAsRay).
                ShellExplosionSmokeParticle puff = new ShellExplosionSmokeParticle(
                    this.worldObj, rx, ry, rz, dx, dy, dz, 140, secondaryScale);
                puff.markAsRay(this.posX, this.posY, this.posZ);
                // Pillar-ray puffs run hotter so the guaranteed tall rays
                // keep climbing after the initial streak.
                if (trail.pillar) {
                    puff.boostHeat(1.6F);
                }
                Minecraft.getMinecraft().effectRenderer.addEffect(puff);
            }
        }

        // Age the (invisible, motionless) controller so it expires after its
        // 10-tick lifetime. We handle this manually instead of calling
        // super.onUpdate() to avoid unwanted physics on the controller.
        this.particleAge++;
        if (this.particleAge >= this.particleMaxAge) {
            this.setDead();
        }
    }

    @Override
    public void renderParticle(WorldRenderer wr, Entity entity, float partialTicks,
                               float rotationX, float rotationZ, float rotationYZ,
                               float rotationXY, float rotationXZ) {
        // No rendering — this is an invisible controller particle.
    }

    @Override
    public int getFXLayer() {
        return -1;
    }

    /**
     * The in-game particle setting: 0 = ALL, 1 = DECREASED, 2 = MINIMAL.
     */
    private static int getParticleStatus() {
        return Minecraft.getMinecraft().gameSettings.particleSetting;
    }

    // ── Vec3 helper methods (1.8.9 Vec3 has limited operations) ──

    private static Vec3 scale(Vec3 v, double s) {
        return new Vec3(v.xCoord * s, v.yCoord * s, v.zCoord * s);
    }

    private static Vec3 add(Vec3 a, Vec3 b) {
        return new Vec3(a.xCoord + b.xCoord, a.yCoord + b.yCoord, a.zCoord + b.zCoord);
    }

    private static double distance(Vec3 a, Vec3 b) {
        double dx = a.xCoord - b.xCoord;
        double dy = a.yCoord - b.yCoord;
        double dz = a.zCoord - b.zCoord;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(
            a.xCoord + (b.xCoord - a.xCoord) * t,
            a.yCoord + (b.yCoord - a.yCoord) * t,
            a.zCoord + (b.zCoord - a.zCoord) * t);
    }

    /** A straight-line smoke-streak trajectory with drag and gravity. */
    private static final class TrailSubparticle {
        private final Vec3 displacement;
        private final Vec3 vel;
        private final double drag;
        private final double gravity;
        private final int lifetime;
        /** True for the guaranteed tall pillar rays (denser trails). */
        private final boolean pillar;

        private TrailSubparticle(Vec3 displacement, Vec3 vel, double drag, double gravity, int lifetime) {
            this(displacement, vel, drag, gravity, lifetime, false);
        }

        private TrailSubparticle(Vec3 displacement, Vec3 vel, double drag, double gravity,
                                 int lifetime, boolean pillar) {
            this.displacement = displacement;
            this.vel = vel;
            this.drag = drag;
            this.gravity = gravity;
            this.lifetime = lifetime;
            this.pillar = pillar;
        }

        private Vec3 calculateDisplacement(int ticks) {
            if (ticks <= 0) {
                return this.displacement;
            }
            if (this.drag == 1.0D) {
                return add(this.displacement, scale(this.vel, (double) ticks));
            }
            double geo = (1.0D - Math.pow(this.drag, ticks)) / (1.0D - this.drag);
            return add(add(this.displacement, scale(this.vel, geo)),
                new Vec3(0.0D, this.gravity * ticks, 0.0D));
        }
    }
}
