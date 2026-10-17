package asd.itamio.createtnt;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Tracks crossbow/dispenser firework rockets for acceleration, drilling and shell blasts. */
public final class RocketTracker {
    private static final double ACCEL = 0.025D;
    private static final double MAX_SPEED = 3.0D;
    private static final int MAX_TRACK_AGE = 1200;
    private static final int MAX_TRACKED = 200;
    private static final double RANGE_SQR = 1.0E8D;
    private static final int MAX_DRILL = 3;        // break up to 3 blocks, then detonate
    private static final double DRILL_STEP = 0.2D; // march step along the flight vector
    private static final Map<UUID, Tracked> TRACKED = new LinkedHashMap<>();

    private static final class Tracked {
        final ServerLevel level; final Entity rocket; final int stars;
        double x, y, z, mx, my, mz; int age; int drilled;
        Tracked(ServerLevel level, Entity rocket) {
            this.level = level; this.rocket = rocket;
            this.stars = RocketTracker.starsFrom(((FireworkRocketEntity) rocket).getItem());
            snapshot();
        }
        void snapshot() {
            x = rocket.getX(); y = rocket.getY(); z = rocket.getZ();
            Vec3 motion = rocket.getDeltaMovement(); mx = motion.x; my = motion.y; mz = motion.z;
        }
    }

    private RocketTracker() { }
    public static void track(ServerLevel level, FireworkRocketEntity rocket) {
        if (TRACKED.size() < MAX_TRACKED) TRACKED.putIfAbsent(rocket.getUUID(), new Tracked(level, rocket));
    }
    public static void untrack(UUID id) { TRACKED.remove(id); }

    /** Number of explosion stars in the firework; 0 = no blast. */
    public static int starsFrom(ItemStack stack) {
        Fireworks fw = stack.get(DataComponents.FIREWORKS);
        return fw != null ? fw.explosions().size() : 0;
    }

    /** Gunpowder flight level of the firework, clamped to 0..7. */
    public static int flightFrom(ItemStack stack) {
        Fireworks fw = stack.get(DataComponents.FIREWORKS);
        int v = fw != null ? fw.flightDuration() : 0;
        return Math.max(0, Math.min(7, v));
    }

    public static void tickAll() {
        if (TRACKED.isEmpty()) return;
        boolean enabled = ModConfig.rocketShellExplosions;
        Iterator<Map.Entry<UUID, Tracked>> it = TRACKED.entrySet().iterator();
        while (it.hasNext()) {
            Tracked tr = it.next().getValue();
            if (tr.rocket.isRemoved()) {
                if (enabled) detonateShell(tr.level, (FireworkRocketEntity) tr.rocket);
                it.remove(); continue;
            }
            if (tr.age > MAX_TRACK_AGE) { it.remove(); continue; }
            tr.snapshot(); tr.age++;
            if (!enabled) continue;
            if (tr.stars > 0 && tr.drilled < MAX_DRILL) {
                Vec3 motion = tr.rocket.getDeltaMovement();
                double speed = motion.length();
                if (speed > 1.0E-4D) {
                    double reach = Math.min(speed, MAX_SPEED) + 0.5D;
                    for (double d = DRILL_STEP; d <= reach && tr.drilled < MAX_DRILL; d += DRILL_STEP) {
                        BlockPos pos = BlockPos.containing(
                            tr.x + motion.x / speed * d,
                            tr.y + motion.y / speed * d,
                            tr.z + motion.z / speed * d);
                        BlockState state = tr.level.getBlockState(pos);
                        if (state.isAir() || state.getCollisionShape(tr.level, pos).isEmpty()) continue;
                        if (state.getDestroySpeed(tr.level, pos) < 0.0F) break;   // unbreakable wall
                        tr.level.destroyBlock(pos, false, tr.rocket, Block.UPDATE_LIMIT);             // no drops, with break effects
                        tr.drilled++;
                    }
                    if (tr.drilled >= MAX_DRILL) {
                        detonateShell(tr.level, (FireworkRocketEntity) tr.rocket);
                        it.remove();
                        continue;
                    }
                }
            }
            Vec3 motion = tr.rocket.getDeltaMovement(); double speed = motion.length();
            if (speed > 1.0E-4D && speed < MAX_SPEED) {
                Vec3 dir = motion.normalize();
                tr.rocket.setDeltaMovement(motion.add(dir.scale(Math.min(ACCEL, MAX_SPEED - speed))));
            }
            tr.level.sendParticles(new ShellSmokeOption(60, 1.5F), tr.x, tr.y, tr.z,
                2, 0.1D, 0.1D, 0.1D, 0.0D);
        }
    }

    /**
     * Detonates a shell rocket. Stars scale the blast (1 star = 1x ... 7 stars = 4x
     * block power); a rocket without stars only flashes its directional plume.
     */
    public static void detonateShell(ServerLevel level, FireworkRocketEntity rocket) {
        double x = rocket.getX(), y = rocket.getY(), z = rocket.getZ();
        Vec3 motion = rocket.getDeltaMovement();
        double len = motion.length();
        float axisX = 0.0F, axisY = 1.0F, axisZ = 0.0F;
        if (len >= 1.0E-4D) {
            axisX = (float) (-motion.x / len); axisY = (float) (-motion.y / len); axisZ = (float) (-motion.z / len);
        }
        int stars = starsFrom(rocket.getItem());
        float directionalPower = 3.0F * (ModConfig.tntStrength / 4.0F);
        if (stars <= 0) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(x, y, z) >= RANGE_SQR) continue;
                ModNetwork.sendExplosion(player, new SpawnExplosionMessage(x, y, z,
                    directionalPower * 0.4F, true, 0.0F, 0.0F, 0.0F, axisX, axisY, axisZ));
            }
            rocket.discard();
            return;
        }
        int clampedStars = Math.min(7, stars);
        float starScale = 1.0F + (clampedStars - 1) * 0.5F;   // 1 star = 1x ... 7 stars = 4x
        float powerMult = 1.0F + level.getRandom().nextFloat() * 2.0F;
        float blockPower = ModConfig.blockPower * powerMult * starScale;
        float entityPower = ModConfig.entityPower * powerMult * stars;
        float particlePower = ModConfig.tntStrength * (1.0F + (powerMult - 1.0F) * 0.25F);
        EnhancedExplosion boom = new EnhancedExplosion(level, rocket, x, y, z,
            blockPower, entityPower, false, true, particlePower);
        boom.setPlumeAxis(axisX, axisY, axisZ);
        boom.explode();
        boom.finalizeExplosion(true);
        float chainRadius = Math.max(blockPower, entityPower) * 2.0F + 4.0F;
        List<PrimedTnt> chained = level.getEntitiesOfClass(PrimedTnt.class,
            new AABB(x - chainRadius, y - chainRadius, z - chainRadius,
                x + chainRadius, y + chainRadius, z + chainRadius));
        for (PrimedTnt tnt : chained) tnt.setFuse(0);
        rocket.discard();
    }
}
