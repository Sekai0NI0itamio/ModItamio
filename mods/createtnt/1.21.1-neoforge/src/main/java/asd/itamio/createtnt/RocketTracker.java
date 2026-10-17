package asd.itamio.createtnt;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Tracks crossbow/dispenser firework rockets until their vanilla discard. */
public final class RocketTracker {

    private static final double ACCEL = 0.025D;
    private static final double MAX_SPEED = 3.0D;
    private static final int MAX_TRACK_AGE = 1200;
    private static final int MAX_TRACKED = 200;
    private static final double RANGE_SQR = 1.0E8D;

    private static final class Tracked {
        final ServerLevel level;
        final Entity rocket;
        double x, y, z;
        double mx, my, mz;
        int age;

        Tracked(ServerLevel level, Entity rocket) {
            this.level = level;
            this.rocket = rocket;
            snapshot();
        }

        void snapshot() {
            x = rocket.getX();
            y = rocket.getY();
            z = rocket.getZ();
            Vec3 motion = rocket.getDeltaMovement();
            mx = motion.x;
            my = motion.y;
            mz = motion.z;
        }
    }

    private static final Map<UUID, Tracked> TRACKED = new LinkedHashMap<>();

    private RocketTracker() {
    }

    public static void track(ServerLevel level, FireworkRocketEntity rocket) {
        if (TRACKED.size() >= MAX_TRACKED) {
            return;
        }
        TRACKED.putIfAbsent(rocket.getUUID(), new Tracked(level, rocket));
    }

    public static boolean isTracked(UUID id) {
        return TRACKED.containsKey(id);
    }

    public static void tickAll() {
        if (TRACKED.isEmpty()) {
            return;
        }
        boolean enabled = ModConfig.rocketShellExplosions;
        Iterator<Map.Entry<UUID, Tracked>> it = TRACKED.entrySet().iterator();
        while (it.hasNext()) {
            Tracked tr = it.next().getValue();
            Entity rocket = tr.rocket;
            if (rocket.isRemoved()) {
                if (enabled) {
                    explodeRocket(tr);
                }
                it.remove();
                continue;
            }
            if (tr.age > MAX_TRACK_AGE) {
                it.remove();
                continue;
            }
            tr.snapshot();
            tr.age++;
            if (!enabled) {
                continue;
            }
            Vec3 motion = rocket.getDeltaMovement();
            double speed = motion.length();
            if (speed > 1.0E-4D && speed < MAX_SPEED) {
                Vec3 dir = motion.normalize();
                rocket.setDeltaMovement(motion.add(dir.scale(Math.min(ACCEL, MAX_SPEED - speed))));
            }
            tr.level.sendParticles(new ShellSmokeOption(60, 1.5F), tr.x, tr.y, tr.z,
                2, 0.1D, 0.1D, 0.1D, 0.0D);
        }
    }

    private static void explodeRocket(Tracked tr) {
        double len = Math.sqrt(tr.mx * tr.mx + tr.my * tr.my + tr.mz * tr.mz);
        float axisX = 0.0F;
        float axisY = 1.0F;
        float axisZ = 0.0F;
        if (len >= 1.0E-4D) {
            axisX = (float) (-tr.mx / len);
            axisY = (float) (-tr.my / len);
            axisZ = (float) (-tr.mz / len);
        }

        int stars = countStars(tr.rocket);
        float directionalPower = 3.0F * (ModConfig.tntStrength / 4.0F);
        ServerLevel level = tr.level;
        if (stars <= 0) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(tr.x, tr.y, tr.z) >= RANGE_SQR) {
                    continue;
                }
                PacketDistributor.sendToPlayer(player, new SpawnExplosionMessage(tr.x, tr.y, tr.z,
                    directionalPower * 0.4F, true, 0.0F, 0.0F, 0.0F,
                    axisX, axisY, axisZ));
            }
            return;
        }

        float powerMult = 1.0F + level.getRandom().nextFloat() * 2.0F;
        float blockPower = ModConfig.blockPower * powerMult;
        float entityPower = ModConfig.entityPower * powerMult * stars;
        float particlePower = ModConfig.tntStrength * (1.0F + (powerMult - 1.0F) * 0.25F);
        EnhancedExplosion boom = new EnhancedExplosion(level, tr.rocket, tr.x, tr.y, tr.z,
            blockPower, entityPower, false, true, particlePower);
        boom.setPlumeAxis(axisX, axisY, axisZ);
        boom.explode();
        boom.finalizeExplosion(true);

        float chainRadius = Math.max(blockPower, entityPower) * 2.0F + 4.0F;
        for (PrimedTnt tnt : level.getEntitiesOfClass(PrimedTnt.class,
                new AABB(tr.x - chainRadius, tr.y - chainRadius, tr.z - chainRadius,
                    tr.x + chainRadius, tr.y + chainRadius, tr.z + chainRadius))) {
            tnt.setFuse(0);
        }
    }

    static int countStars(Entity rocket) {
        try {
            java.lang.reflect.Field f = fireworksItemField;
            if (f == null) {
                f = FireworkRocketEntity.class.getDeclaredField("DATA_ID_FIREWORKS_ITEM");
                f.setAccessible(true);
                fireworksItemField = f;
            }
            @SuppressWarnings("unchecked")
            net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.world.item.ItemStack> acc =
                (net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.world.item.ItemStack>) f.get(null);
            net.minecraft.world.item.ItemStack stack = rocket.getEntityData().get(acc);
            net.minecraft.world.item.component.Fireworks fw =
                stack.get(net.minecraft.core.component.DataComponents.FIREWORKS);
            return fw != null ? fw.explosions().size() : 0;
        } catch (Exception e) {
            return 1;
        }
    }

    private static volatile java.lang.reflect.Field fireworksItemField;
}
