package asd.itamio.createtnt;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

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
public class ShellExplosionCloudParticle extends NoRenderParticle {

    /** Ticks over which the controller spawns the primary plume puffs. */
    private static final int PLUME_AGE = 5;

    private final float power;
    private final List<TrailSubparticle> trails = new LinkedList<>();
    private final boolean isPlume;
    private final SpriteSet sprites;

    ShellExplosionCloudParticle(ClientLevel level, double x, double y, double z,
                                float power, boolean isPlume, SpriteSet sprites) {
        super(level, x, y, z);
        // Zero velocity; the controller itself renders nothing.
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.power = power;
        this.isPlume = isPlume;
        this.sprites = sprites;
        // The controller expires after 10 ticks.
        this.lifetime = 10;

        // Blast-ray trails can be disabled in the config.
        if (ModConfig.showExtraShellExplosionTrails) {
            double secondaryVelScale = this.power * 0.35D;
            // Blast-ray counts scale with the in-game particle setting:
            // ALL 12+rand(6), DECREASED 6+rand(3), MINIMAL 4.
            int secondaryCount;
            switch (getParticleStatus()) {
                case ALL: secondaryCount = 12 + this.random.nextInt(6); break;
                case DECREASED: secondaryCount = 6 + this.random.nextInt(3); break;
                default: secondaryCount = 4; break;
            }
            secondaryCount = Math.max(1, (int) (secondaryCount * ModConfig.explosionTrailMultiplier));
            double gravity = this.isPlume ? -0.5D : -0.1D;

            // Distribute the blast-ray directions evenly across a sphere
            // (Fibonacci lattice) with per-ray jitter and a random global
            // rotation per explosion — so every explosion looks different,
            // each line shoots out in its own direction, and no two lines
            // start out touching each other.
            double goldenAngle = Math.PI * (3.0D - Math.sqrt(5.0D));
            double rotYaw = this.random.nextDouble() * Math.PI * 2.0D;
            double rotTilt = this.random.nextDouble() * (this.isPlume ? 0.5D : Math.PI);
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
                dx += (this.random.nextDouble() - this.random.nextDouble()) * 0.35D;
                dy += (this.random.nextDouble() - this.random.nextDouble()) * 0.35D;
                dz += (this.random.nextDouble() - this.random.nextDouble()) * 0.35D;
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

                double speed = 0.4D + this.random.nextDouble() * 0.6D;
                Vec3 vel = new Vec3(dx * speed, dy * speed, dz * speed).scale(secondaryVelScale);
                // Start each ray slightly out along its own direction so the
                // lines read as clearly separated streaks from the core.
                Vec3 displacement = new Vec3(dx, dy, dz)
                    .scale(1.0D + this.random.nextDouble() * 1.5D);
                int lifetime = 5;
                this.trails.add(new TrailSubparticle(displacement, vel, 0.85D, gravity, lifetime));
            }

            // --- Guaranteed pillar rays ---
            // Every explosion gets 1-4 long pillar rays climbing off the top
            // in random steep directions — EVERY one is 15-25 blocks tall.
            // Their puffs are heat-boosted so the pillars keep rising after
            // the streak.
            int pillarCount = 1 + this.random.nextInt(4);
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
        double azimuth = this.random.nextDouble() * Math.PI * 2.0D;
        double elevation = 1.047D + this.random.nextDouble() * 0.489D; // 60-88 degrees
        double dx = Math.cos(elevation) * Math.cos(azimuth);
        double dy = Math.sin(elevation);
        double dz = Math.cos(elevation) * Math.sin(azimuth);
        double targetLength = minLength + this.random.nextDouble() * (maxLength - minLength);
        double speed = targetLength / (3.708D * velScale);
        Vec3 vel = new Vec3(dx * speed, dy * speed, dz * speed).scale(velScale);
        Vec3 displacement = new Vec3(dx, dy, dz)
            .scale(1.0D + this.random.nextDouble() * 1.5D);
        // Near-zero trail gravity so the pillar climbs straight instead of sagging.
        this.trails.add(new TrailSubparticle(displacement, vel, 0.85D, -0.05D, 5, true));
    }

    @Override
    public void tick() {
        if (this.age < PLUME_AGE) {
            float primaryScale = this.power * 2.0F;
            int plumes;
            switch (getParticleStatus()) {
                case ALL: plumes = 20; break;
                case DECREASED: plumes = 10; break;
                default: plumes = 5; break;
            }
            // Base smoke amount multiplier (0.45 = about half the default density).
            plumes = Math.max(1, (int) (plumes * ModConfig.baseSmokeAmount));
            double velScale = this.power * 0.25D;
            velScale *= 1.0D - (float) this.age / (float) PLUME_AGE;
            double displacementScale = this.power * 0.35D;
            for (int i = 0; i <= plumes; ++i) {
                double rx = this.x + (this.random.nextDouble() - this.random.nextDouble()) * displacementScale;
                double ry = this.random.nextDouble() - this.random.nextDouble();
                if (this.isPlume) {
                    ry = Math.abs(ry);
                }
                ry *= displacementScale;
                ry += this.y + 0.5D;
                double rz = this.z + (this.random.nextDouble() - this.random.nextDouble()) * displacementScale;
                double dx = (this.random.nextDouble() - this.random.nextDouble()) * velScale;
                double dy = this.random.nextDouble() - this.random.nextDouble();
                if (this.isPlume) {
                    dy += 1.2D;
                }
                dy *= velScale;
                double dz = (this.random.nextDouble() - this.random.nextDouble()) * velScale;
                // Base smoke lifetime: 7 seconds (140 ticks) — the large
                // puffs disappear around the same time as the ray puffs.
                int lifetime = 140;
                // 1.21.11: addParticle gained an overrideLimiter flag before
                // the alwaysShow flag; we want always-show without bypassing
                // the particle limiter.
                this.level.addParticle(new ShellSmokeOption(lifetime, primaryScale),
                    false, true, rx, ry, rz, dx, dy, dz);
            }
        }

        int trailSteps = getParticleStatus() == ParticleStatus.ALL ? 2 : 1;
        float secondaryScale = this.power * 0.75F;
        for (Iterator<TrailSubparticle> iter = this.trails.iterator(); iter.hasNext(); ) {
            TrailSubparticle trail = iter.next();
            if (this.age > trail.lifetime) {
                iter.remove();
                continue;
            }
            Vec3 origin = trail.calculateDisplacement(this.age);
            Vec3 next = trail.calculateDisplacement(this.age + 1);
            double velScale = 0.125D;
            // Wide spawn spread so ray puffs form soft wisps instead of sharp
            // radiate lines with gaps between them.
            double displacementScale = 0.3D;
            // Pillar rays travel 10-16 blocks per tick — with only a few
            // puffs per tick they would be sparse dotted lines. Spawn enough
            // puffs per tick to keep the whole pillar densely filled.
            int steps = trailSteps;
            if (trail.pillar) {
                double stepDist = origin.distanceTo(next);
                steps = Math.min(40, Math.max(2,
                    (int) Math.ceil(stepDist / ModConfig.pillarTrailSpacing)));
            }
            for (int i = 0; i <= steps; ++i) {
                Vec3 pos = origin.lerp(next, (double) i / (double) steps)
                    .add(this.x, this.y, this.z);
                double rx = pos.x + (this.random.nextDouble() - this.random.nextDouble()) * displacementScale;
                double ry = pos.y + (this.random.nextDouble() - this.random.nextDouble()) * displacementScale;
                double rz = pos.z + (this.random.nextDouble() - this.random.nextDouble()) * displacementScale;
                double dx = (this.random.nextDouble() - this.random.nextDouble()) * velScale;
                double dy = (this.random.nextDouble() - this.random.nextDouble()) * velScale;
                double dz = (this.random.nextDouble() - this.random.nextDouble()) * velScale;
                // Ray puffs: static for 2s, then scatter outward at their own
                // constant small speed; they live ~70% of the base smoke's
                // stay (set inside markAsRay).
                ShellExplosionSmokeParticle puff = new ShellExplosionSmokeParticle(
                    this.level, rx, ry, rz, dx, dy, dz, this.sprites, 140, secondaryScale);
                puff.markAsRay(this.x, this.y, this.z);
                // Pillar-ray puffs run hotter so the guaranteed tall rays
                // keep climbing after the initial streak.
                if (trail.pillar) {
                    puff.boostHeat(1.6F);
                }
                Minecraft.getInstance().particleEngine.add(puff);
            }
        }

        // Age the (invisible, motionless) controller so it expires after its
        // 10-tick lifetime.
        super.tick();
    }

    /** The in-game particle setting: ALL, DECREASED, or MINIMAL. */
    private static ParticleStatus getParticleStatus() {
        return Minecraft.getInstance().options.particles().get();
    }

    /** Provider creating the controller from its option data. */
    public static class Provider implements ParticleProvider<ShellCloudOption> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(ShellCloudOption data, ClientLevel level, double x, double y,
                                       double z, double xSpeed, double ySpeed, double zSpeed,
                                       RandomSource random) {
            return new ShellExplosionCloudParticle(level, x, y, z, data.scale(), data.isPlume(),
                this.sprites);
        }
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
                return this.displacement.add(this.vel.scale((double) ticks));
            }
            double geo = (1.0D - Math.pow(this.drag, ticks)) / (1.0D - this.drag);
            return this.displacement.add(this.vel.scale(geo)).add(0.0D, this.gravity * ticks, 0.0D);
        }
    }
}
