package asd.itamio.createtnt;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;

/** Tracks crossbow/dispenser firework rockets for acceleration and visuals. */
public final class RocketTracker {
    private static final double ACCEL = 0.025D;
    private static final double MAX_SPEED = 3.0D;
    private static final int MAX_TRACK_AGE = 1200;
    private static final int MAX_TRACKED = 200;
    private static final double RANGE_SQR = 1.0E8D;

    private static final class Tracked {
        final ServerLevel level;
        final Entity rocket;
        double x, y, z, mx, my, mz;
        int age;

        Tracked(ServerLevel level, Entity rocket) {
            this.level = level;
            this.rocket = rocket;
            snapshot();
        }

        void snapshot() {
            x = rocket.getX(); y = rocket.getY(); z = rocket.getZ();
            Vec3 motion = rocket.getDeltaMovement();
            mx = motion.x; my = motion.y; mz = motion.z;
        }
    }

    private static final Map<UUID, Tracked> TRACKED = new LinkedHashMap<>();

    private RocketTracker() {
    }

    public static void track(ServerLevel level, FireworkRocketEntity rocket) {
        if (TRACKED.size() < MAX_TRACKED) {
            TRACKED.putIfAbsent(rocket.getUUID(), new Tracked(level, rocket));
        }
    }

    public static void untrack(UUID id) {
        TRACKED.remove(id);
    }

    public static void tickAll() {
        if (TRACKED.isEmpty()) return;
        boolean enabled = ModConfig.rocketShellExplosions;
        Iterator<Map.Entry<UUID, Tracked>> it = TRACKED.entrySet().iterator();
        while (it.hasNext()) {
            Tracked tr = it.next().getValue();
            if (tr.rocket.isRemoved()) {
                if (enabled) sendDirectionalVisual(tr);
                it.remove();
                continue;
            }
            if (tr.age > MAX_TRACK_AGE) {
                it.remove();
                continue;
            }
            tr.snapshot();
            tr.age++;
            if (!enabled) continue;
            Vec3 motion = tr.rocket.getDeltaMovement();
            double speed = motion.length();
            if (speed > 1.0E-4D && speed < MAX_SPEED) {
                Vec3 dir = motion.normalize();
                tr.rocket.setDeltaMovement(motion.add(dir.scale(Math.min(ACCEL, MAX_SPEED - speed))));
            }
            tr.level.sendParticles(new ShellSmokeOption(60, 1.5F), tr.x, tr.y, tr.z,
                2, 0.1D, 0.1D, 0.1D, 0.0D);
        }
    }

    private static void sendDirectionalVisual(Tracked tr) {
        double len = Math.sqrt(tr.mx * tr.mx + tr.my * tr.my + tr.mz * tr.mz);
        float axisX = 0.0F, axisY = 1.0F, axisZ = 0.0F;
        if (len >= 1.0E-4D) {
            axisX = (float) (-tr.mx / len);
            axisY = (float) (-tr.my / len);
            axisZ = (float) (-tr.mz / len);
        }
        float power = 3.0F * (ModConfig.tntStrength / 4.0F);
        for (ServerPlayer player : tr.level.players()) {
            if (player.distanceToSqr(tr.x, tr.y, tr.z) >= RANGE_SQR) continue;
            ModNetwork.sendExplosion(player, new SpawnExplosionMessage(tr.x, tr.y, tr.z,
                power, true, 0.0F, 0.0F, 0.0F, axisX, axisY, axisZ));
        }
    }
}
