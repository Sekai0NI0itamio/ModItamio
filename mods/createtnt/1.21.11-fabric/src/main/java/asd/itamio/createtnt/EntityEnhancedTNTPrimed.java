package asd.itamio.createtnt;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Enhanced primed TNT with water-aware fuse behavior.
 *
 * <p>Water interaction rules:</p>
 * <ul>
 *   <li>If the TNT is in water and has been lit for less than 1 second
 *       (20 ticks), it unlights after 0.5 seconds (10 ticks) and reverts
 *       to a placed TNT block.</li>
 *   <li>If the TNT has been lit for 1+ second (20+ ticks) before entering
 *       water, it stays lit and explodes normally — the explosion still
 *       destroys blocks and scatters fluids as if it exploded on land.</li>
 *   <li>If the fuse is about to expire (fuse ≤ 1), the water-unlit check
 *       is skipped so chain-reaction TNT always detonates.</li>
 * </ul>
 *
 * <p>The explosion itself is handled by {@link EnhancedExplosion}, created
 * directly at fuse end (reliable in water).</p>
 */
public class EntityEnhancedTNTPrimed extends PrimedTnt {

    /** Ticks since the TNT was ignited. Used to determine water-unlit behavior. */
    private int ticksSinceLit;

    public EntityEnhancedTNTPrimed(net.minecraft.world.entity.EntityType<? extends PrimedTnt> type,
                                   Level level) {
        super(type, level);
        this.ticksSinceLit = 0;
    }

    public EntityEnhancedTNTPrimed(Level level, double x, double y, double z, LivingEntity igniter) {
        this(BetterTNTs.ENHANCED_TNT, level);
        this.setPos(x, y, z);
        double d0 = level.random.nextDouble() * (Math.PI * 2.0D);
        this.setDeltaMovement(-Math.sin(d0) * 0.02D, 0.2D, -Math.cos(d0) * 0.02D);
        this.setFuse(80);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.ticksSinceLit = 0;
    }

    @Override
    public void tick() {
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();

        // Physics — same as vanilla PrimedTnt.
        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
        }
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.7D, -0.5D, 0.7D));
        }

        boolean inWater = this.isInWater();
        this.ticksSinceLit++;

        // --- Water unlit logic ---
        // If in water, lit for 0.5-1 seconds (10-19 ticks), and the fuse
        // isn't about to expire, unlit and revert to a TNT block.
        // If lit for 1+ second (20+ ticks), water doesn't affect it —
        // the TNT explodes normally and the EnhancedExplosion scatters
        // water/lava as falling fluid entities.
        // Server-only: the server decides when to unlit; the client just
        // renders until the server kills the entity.
        int currentFuse = this.getFuse();
        if (!this.level().isClientSide() && inWater && this.ticksSinceLit >= ModConfig.waterUnlightMinTicks
            && this.ticksSinceLit < ModConfig.waterUnlightMaxTicks && currentFuse > 1) {
            this.unlit();
            return;
        }

        // Normal fuse countdown.
        int newFuse = currentFuse - 1;
        this.setFuse(newFuse);

        if (newFuse <= 0) {
            // Explode BEFORE discard() so chain-reaction logic can still
            // find this TNT entity via getEntitiesOfClass.
            if (!this.level().isClientSide()) {
                // Directly create an EnhancedExplosion — reliable in water.
                // 1.0x to 3.0x random power multiplier — each TNT varies in strength.
                float powerMult = 1.0F + this.level().getRandom().nextFloat() * 2.0F;
                float blockPower = ModConfig.blockPower * powerMult;
                float entityPower = ModConfig.entityPower * powerMult;
                // Particle power is decoupled from destruction power (the
                // smoke stays BIG even though destruction is scaled down):
                // based on full tntStrength with a gentle multiplier curve.
                float particlePower = ModConfig.tntStrength * (1.0F + (powerMult - 1.0F) * 0.25F);

                // --- Fluid resistance scaling ---
                // Explosions in water have more resistance (reduced power).
                // Explosions in lava have even more resistance and decreased
                // strength. The TNT still destroys blocks and scatters fluids,
                // but with reduced reach.
                BlockPos tntPos = this.blockPosition();
                boolean inWaterBlock = this.level().getFluidState(tntPos).is(FluidTags.WATER);
                boolean inLavaBlock = this.level().getFluidState(tntPos).is(FluidTags.LAVA);
                if (inWaterBlock) {
                    blockPower *= ModConfig.waterResistanceScale;
                    entityPower *= ModConfig.waterResistanceScale;
                }
                if (inLavaBlock) {
                    blockPower *= ModConfig.lavaResistanceScale;
                    entityPower *= ModConfig.lavaResistanceScale;
                }

                EnhancedExplosion explosion = new EnhancedExplosion(
                    this.level(), this,
                    this.getX(), this.getY() + (double) (this.getBbHeight() / 16.0F), this.getZ(),
                    blockPower, entityPower, false, true, particlePower);
                explosion.detonate();
                explosion.finalizeExplosion(true);

                // --- Simultaneous chain detonation ---
                // All TNT blocks and primed TNT entities caught in the blast
                // are collected and detonated IMMEDIATELY (same tick), so the
                // entire field goes up at once instead of one-by-one.
                detonateChain(explosion, blockPower, entityPower);
                chainVisited.clear();
            }
            this.discard();
        } else if (this.level().isClientSide()) {
            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                this.getX(), this.getY() + 0.5D, this.getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * Unlights the TNT: kills the entity and places a TNT block (or drops an
     * item if the position is occupied). Plays a fire-extinguish sound to
     * signal the dousing.
     */
    private void unlit() {
        this.discard();
        if (!this.level().isClientSide()) {
            BlockPos pos = this.blockPosition();
            if (this.level().getBlockState(pos).canBeReplaced()) {
                this.level().setBlock(pos, Blocks.TNT.defaultBlockState(), 3);
            } else {
                // 1.21.11: spawnAtLocation takes the ServerLevel explicitly.
                this.spawnAtLocation((net.minecraft.server.level.ServerLevel) this.level(),
                    new ItemStack(Blocks.TNT));
            }
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 1.2F);
        }
    }

    // 1.21.11: entity NBT goes through the ValueOutput/ValueInput abstraction.
    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("TicksSinceLit", this.ticksSinceLit);
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput tag) {
        super.readAdditionalSaveData(tag);
        this.ticksSinceLit = tag.getIntOr("TicksSinceLit", 0);
    }

    /**
     * Recursively detonates all TNT blocks and primed TNT entities caught in
     * the blast, all in the same tick. Uses a visited set to prevent infinite
     * recursion when TNTs are packed tightly.
     */
    private static final java.util.Set<BlockPos> chainVisited = new java.util.HashSet<>();

    /** A TNT position scheduled to explode at a later world tick. Used when the
     *  user disables simultaneous chain detonation in the config. */
    private static final java.util.List<ScheduledDetonation> scheduledDetonations =
        java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    /**
     * Processes chain TNTs collected by an explosion. The center (cx/cy/cz) is
     * the position the blast originated from, used to find and scatter primed
     * TNT entities in range.
     */
    private void detonateChain(EnhancedExplosion explosion, float blockPower, float entityPower) {
        // The chain search is centered on THIS TNT's position.
        detonateChainAt(this.level(), this.getX(), this.getY(), this.getZ(), this,
            explosion, blockPower, entityPower);
    }

    /**
     * Position-based chain detonation. Finds primed TNT entities around the
     * given center and scatters them, then either detonates all collected TNT
     * block positions immediately (simultaneous mode) or schedules them with a
     * staggered delay (one-by-one mode).
     */
    private static void detonateChainAt(Level level, double cx, double cy, double cz,
                                        EntityEnhancedTNTPrimed cause,
                                        EnhancedExplosion explosion,
                                        float blockPower, float entityPower) {
        java.util.List<BlockPos> tntPositions = new java.util.ArrayList<>(
            explosion.getChainedTNTPositions());

        // Find primed TNT entities around the blast center and scatter them.
        float chainRadius = Math.max(blockPower, entityPower) * 2.0F + 4.0F;
        List<PrimedTnt> chainedTNTs = level.getEntitiesOfClass(
            PrimedTnt.class,
            new AABB(
                cx - chainRadius, cy - chainRadius, cz - chainRadius,
                cx + chainRadius, cy + chainRadius, cz + chainRadius));

        for (PrimedTnt tnt : chainedTNTs) {
            if (tnt == cause) continue;
            double dx = tnt.getX() - cx;
            double dy = tnt.getY() - cy;
            double dz = tnt.getZ() - cz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 0.0D) {
                double scatter = 0.4D * (1.0D - dist / (double) chainRadius);
                scatter = Math.max(0.1D, scatter);
                tnt.setDeltaMovement(tnt.getDeltaMovement().add(
                    (dx / dist) * scatter,
                    (dy / dist) * scatter + 0.2D,
                    (dz / dist) * scatter));
            }
        }

        if (ModConfig.simultaneousChainDetonation) {
            // Detonate all collected TNT positions immediately (same tick).
            for (BlockPos pos : tntPositions) {
                if (chainVisited.contains(pos)) continue;
                chainVisited.add(pos);
                detonatePosition(level, pos, cause);
            }
        } else {
            // One-by-one: schedule each position to explode with a staggered
            // delay so they go off sequentially rather than all at once.
            long now = level.getGameTime();
            int index = 0;
            for (BlockPos pos : tntPositions) {
                if (chainVisited.contains(pos)) continue;
                chainVisited.add(pos);
                long fireAt = now + (long) ModConfig.chainDetonationDelay * (index + 1);
                scheduledDetonations.add(new ScheduledDetonation(
                    level, pos, cause, fireAt));
                index++;
            }
        }
    }

    /**
     * Detonates a single TNT block position immediately: applies fluid-
     * resistance scaling, creates the enhanced explosion, and recursively
     * processes any TNTs caught in this chain blast (respecting the
     * simultaneous/one-by-one config).
     */
    private static void detonatePosition(Level level, BlockPos pos, EntityEnhancedTNTPrimed cause) {
        // Fluid resistance scaling for chain explosions.
        float chainBlockPower = ModConfig.blockPower;
        float chainEntityPower = ModConfig.entityPower;
        boolean inWater = level.getFluidState(pos).is(FluidTags.WATER);
        boolean inLava = level.getFluidState(pos).is(FluidTags.LAVA);
        if (inWater) {
            chainBlockPower *= ModConfig.waterResistanceScale;
            chainEntityPower *= ModConfig.waterResistanceScale;
        } else if (inLava) {
            chainBlockPower *= ModConfig.lavaResistanceScale;
            chainEntityPower *= ModConfig.lavaResistanceScale;
        }

        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 0.5D;
        double cz = pos.getZ() + 0.5D;
        // Particle power uses the same tntStrength-based sizing as primary
        // explosions so chain detonations produce equally big smoke clouds.
        EnhancedExplosion chainExplosion = new EnhancedExplosion(
            level, cause, cx, cy, cz,
            chainBlockPower, chainEntityPower, false, true, ModConfig.tntStrength);
        chainExplosion.detonate();
        chainExplosion.finalizeExplosion(true);

        // Recursively process any TNTs caught in this chain explosion,
        // centered on this position.
        detonateChainAt(level, cx, cy, cz, cause, chainExplosion, chainBlockPower, chainEntityPower);
    }

    /**
     * Processes scheduled (one-by-one) chain detonations on the server tick.
     * Any entry whose fire time has passed is detonated now and removed.
     * Registered on the Forge event bus in {@link BetterTNTs}.
     */
    /** Processes scheduled (one-by-one) chain detonations on the server
     *  tick. Called from the Fabric server-tick callback. */
    public static void tickScheduled() {
        if (scheduledDetonations.isEmpty()) return;

        // Snapshot the due entries while holding the lock briefly, then
        // process them outside the synchronized block to avoid reentrancy
        // (each detonation may add new scheduled entries).
        java.util.List<ScheduledDetonation> due = new java.util.ArrayList<>();
        synchronized (scheduledDetonations) {
            java.util.Iterator<ScheduledDetonation> iter = scheduledDetonations.iterator();
            while (iter.hasNext()) {
                ScheduledDetonation sd = iter.next();
                if (sd.level.getGameTime() >= sd.fireAt) {
                    due.add(sd);
                    iter.remove();
                }
            }
        }
        for (ScheduledDetonation sd : due) {
            detonatePosition(sd.level, sd.pos, sd.cause);
        }
    }

    /** Holder for a deferred chain-detonation. */
    private static final class ScheduledDetonation {
        final Level level;
        final BlockPos pos;
        final EntityEnhancedTNTPrimed cause;
        final long fireAt;

        ScheduledDetonation(Level level, BlockPos pos, EntityEnhancedTNTPrimed cause, long fireAt) {
            this.level = level;
            this.pos = pos;
            this.cause = cause;
            this.fireAt = fireAt;
        }
    }
}
