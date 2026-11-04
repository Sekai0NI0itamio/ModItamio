package asd.itamio.createtnt;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
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
    private static final int MAX_DRILL = 3;        // break up to 3 blocks, then detonate
    private static final double DRILL_STEP = 0.2D; // march step along the flight vector
    // Trail puffs are small, so ShellExplosionSmokeParticle keeps only 30% of the
    // requested lifetime: 240 ticks resolve to ~72 ticks (3.6 s) on screen.
    private static final int TRAIL_LIFETIME = 240;
    private static final float TRAIL_SCALE = 1.5F;

    private static final class Tracked {
        final ServerLevel level;
        final Entity rocket;
        final int stars;
        double x, y, z;
        double mx, my, mz;
        int age;
        int drilled;

        Tracked(ServerLevel level, Entity rocket) {
            this.level = level;
            this.rocket = rocket;
            this.stars = starsFrom(((FireworkRocketEntity) rocket).getItem());
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
        if (!ModConfig.rocketShellExplosions || !isAllowedSource(level, rocket)) {
            return;
        }
        if (TRACKED.size() >= MAX_TRACKED) {
            return;
        }
        TRACKED.putIfAbsent(rocket.getUUID(), new Tracked(level, rocket));
    }

    /** Whether a rocket came from a crossbow, a dispenser, or (opt-in) another mod. */
    private static boolean isAllowedSource(ServerLevel level, FireworkRocketEntity rocket) {
        Entity owner = rocket.getOwner();
        if (owner == null) {
            if (!ModConfig.rocketShellExplosionsFromDispensers) {
                return false;
            }
            // Ownerless rockets are usually dispensers, but mod spells can
            // spawn ownerless rockets too. Only treat it as a dispenser shot
            // when a dispenser block is adjacent to the spawn position.
            BlockPos pos = rocket.blockPosition();
            for (Direction dir : Direction.values()) {
                if (level.getBlockState(pos.relative(dir)).getBlock() instanceof DispenserBlock) {
                    return true;
                }
            }
            return ModConfig.rocketShellExplosionsFromOtherMods;
        }
        if (owner instanceof LivingEntity living) {
            boolean crossbow = living.getMainHandItem().getItem() instanceof CrossbowItem
                || living.getOffhandItem().getItem() instanceof CrossbowItem;
            return crossbow || ModConfig.rocketShellExplosionsFromOtherMods;
        }
        return ModConfig.rocketShellExplosionsFromOtherMods;
    }



    public static boolean isTracked(UUID id) {
        return TRACKED.containsKey(id);
    }

    public static void untrack(UUID id) {
        TRACKED.remove(id);
    }

    /** Number of explosion stars in the firework's item stack. */
    public static int starsFrom(ItemStack stack) {
        Fireworks fw = stack.get(DataComponents.FIREWORKS);
        return fw != null ? fw.explosions().size() : 0;
    }

    /** Gunpowder count (flight duration) of the firework, clamped to 0..7. */
    public static int flightFrom(ItemStack stack) {
        Fireworks fw = stack.get(DataComponents.FIREWORKS);
        int v = fw != null ? fw.flightDuration() : 0;
        return Math.max(0, Math.min(7, v));
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
                it.remove();
                if (enabled) {
                    detonateShell(tr.level, (FireworkRocketEntity) rocket);
                }
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
            if (tr.stars > 0 && tr.drilled < MAX_DRILL) {
                Vec3 motion = rocket.getDeltaMovement();
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
                        detonateShell(tr.level, (FireworkRocketEntity) rocket);
                        it.remove();
                        continue;
                    }
                }
            }
            Vec3 motion = rocket.getDeltaMovement();
            double speed = motion.length();
            if (speed > 1.0E-4D && speed < MAX_SPEED) {
                Vec3 dir = motion.normalize();
                rocket.setDeltaMovement(motion.add(dir.scale(Math.min(ACCEL, MAX_SPEED - speed))));
            }
            sendTrail(tr.level, tr.x, tr.y, tr.z);
        }
    }

    /**
     * Sends this tick's trail puffs to every player who could see them. The
     * plain sendParticles call stops at 32 blocks, and clients drop unforced
     * particles past 32 blocks of the camera; the per-player overload with
     * force=true stretches that limit to the 512-block visible range and
     * marks the packet so clients render each puff at any distance, matching
     * the reach of the explosion visuals.
     */
    private static void sendTrail(ServerLevel level, double x, double y, double z) {
        ShellSmokeOption trail = new ShellSmokeOption(TRAIL_LIFETIME, TRAIL_SCALE, true);
        for (ServerPlayer player : level.players()) {
            level.sendParticles(player, trail, true, x, y, z, 2, 0.1D, 0.1D, 0.1D, 0.0D);
        }
    }

    /**
     * Detonates a shell rocket at its position: no stars → directional visual
     * only; stars → enhanced blast with star-scaled block destruction and
     * TNT-level entity damage ({@link ModConfig#rocketExplosionDamageScale}),
     * then chain-detonates nearby primed TNT. Does not touch the tracker map —
     * callers untrack.
     */
    public static void detonateShell(ServerLevel level, FireworkRocketEntity rocket) {
        untrack(rocket.getUUID());
        Vec3 origin = clearBlastOrigin(level, rocket.getX(), rocket.getY(), rocket.getZ(),
            rocket.getDeltaMovement());
        double x = origin.x;
        double y = origin.y;
        double z = origin.z;
        Vec3 motion = rocket.getDeltaMovement();
        double len = motion.length();
        float axisX = 0.0F;
        float axisY = 1.0F;
        float axisZ = 0.0F;
        if (len >= 1.0E-4D) {
            axisX = (float) (-motion.x / len);
            axisY = (float) (-motion.y / len);
            axisZ = (float) (-motion.z / len);
        }

        int stars = starsFrom(rocket.getItem());
        float directionalPower = 3.0F * (ModConfig.tntStrength / 4.0F);
        if (stars <= 0) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(x, y, z) >= RANGE_SQR) {
                    continue;
                }
                PacketDistributor.sendToPlayer(player, new SpawnExplosionMessage(x, y, z,
                    directionalPower * 0.4F, true, 0.0F, 0.0F, 0.0F,
                    axisX, axisY, axisZ));
            }
            rocket.discard();
            return;
        }

        int clampedStars = Math.min(7, stars);
        float starScale = 1.0F + (clampedStars - 1) * 0.5F;   // 1 star = 1x ... 7 stars = 4x
        float powerMult = 1.0F + level.getRandom().nextFloat() * 2.0F;
        float blockPower = ModConfig.rocketShellBlockDamage
            ? ModConfig.blockPower * powerMult * starScale
            : 0.0F;
        float entityPower = ModConfig.entityPower * powerMult * ModConfig.rocketExplosionDamageScale;
        float particlePower = ModConfig.tntStrength * (1.0F + (powerMult - 1.0F) * 0.25F);
        EnhancedExplosion boom = new EnhancedExplosion(level, rocket, x, y, z,
            blockPower, entityPower, false, true, particlePower);
        boom.setPlumeAxis(axisX, axisY, axisZ);
        boom.explode();
        boom.finalizeExplosion(true);

        float chainRadius = Math.max(blockPower, entityPower) * 2.0F + 4.0F;
        if (ModConfig.rocketShellBlockDamage) {
            ChainDetonation.detonate(level.getEntitiesOfClass(PrimedTnt.class,
                new AABB(x - chainRadius, y - chainRadius, z - chainRadius,
                    x + chainRadius, y + chainRadius, z + chainRadius)), x, y, z);
        }
        rocket.discard();
    }
    /**
     * Returns a blast origin that is not buried inside a solid block, so
     * vanilla {@code Explosion.getSeenPercent} does not reduce impact damage
     * to 1 when a rocket detonates against a wall.
     */
    private static Vec3 clearBlastOrigin(ServerLevel level, double x, double y, double z, Vec3 motion) {
        if (isClear(level, x, y, z)) {
            return new Vec3(x, y, z);
        }
        double search = ModConfig.rocketBlastOriginSearch;
        if (search <= 0.0D) {
            return new Vec3(x, y, z);
        }
        Vec3 back = motion.lengthSqr() > 1.0E-6D
            ? motion.normalize().scale(-1.0D)
            : new Vec3(0.0D, 1.0D, 0.0D);
        for (double d = 0.25D; d <= search; d += 0.25D) {
            double px = x + back.x * d;
            double py = y + back.y * d;
            double pz = z + back.z * d;
            if (isClear(level, px, py, pz)) {
                return new Vec3(px, py, pz);
            }
        }
        for (double d = 0.5D; d <= search + 0.5D; d += 0.5D) {
            if (isClear(level, x, y + d, z)) {
                return new Vec3(x, y + d, z);
            }
        }
        return new Vec3(x, y, z);
    }

    private static boolean isClear(ServerLevel level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.getCollisionShape(level, pos).isEmpty();
    }

}
