package asd.itamio.createtnt;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * Enhanced explosion with separate block destruction and entity damage radii.
 *
 * <p>Unlike vanilla {@code Explosion}, block destruction reach and entity
 * damage reach are separate radii ({@code blockRadius} vs {@code entityRadius}),
 * exactly like a high-explosive blast. On completion it sends a
 * packet to clients so they render the big plume + blast wave particles.</p>
 */
public class EnhancedExplosion extends Explosion {

    private final Level level;
    private final double x;
    private final double y;
    private final double z;
    private final Entity exploder;
    /** Block destruction radius. */
    private final float blockRadius;
    /** Entity damage radius. */
    private final float entityRadius;
    private final boolean damagesTerrain;
    private final List<BlockPos> affectedBlockPositions;
    private final Map<Player, Vec3> playerKnockbackMap;
    /** TNT block positions collected during the blast, for simultaneous
     *  chain detonation. */
    private final List<BlockPos> chainedTNTPositions = new java.util.ArrayList<>();

    /** Maps each affected block position to the maximum remaining ray energy
     *  at that position. Used to scale launch velocity: blocks behind resistant
     *  walls have very little energy left and barely move, while blocks in the
     *  open retain most of the energy and fly far. */
    private final Map<BlockPos, Float> blockEnergyMap = new java.util.HashMap<>();

    /** Particle sizing power — decoupled from destruction power so the
     *  random TNT strength multiplier doesn't make particles absurdly big.
     *  Scales gently: a 3x-strength blast is only ~1.5x bigger visually. */
    private final float particlePower;

    public EnhancedExplosion(Level level, @Nullable Entity entity, double x, double y, double z,
                             float blockRadius, float entityRadius, boolean causesFire, boolean damagesTerrain) {
        this(level, entity, x, y, z, blockRadius, entityRadius, causesFire, damagesTerrain, blockRadius);
    }

    public EnhancedExplosion(Level level, @Nullable Entity entity, double x, double y, double z,
                             float blockRadius, float entityRadius, boolean causesFire, boolean damagesTerrain,
                             float particlePower) {
        super(level, entity, null, null, x, y, z, blockRadius, causesFire,
            damagesTerrain ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.KEEP);
        this.level = level;
        this.exploder = entity;
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockRadius = blockRadius;
        this.entityRadius = entityRadius;
        this.damagesTerrain = damagesTerrain;
        this.particlePower = particlePower;
        this.affectedBlockPositions = Lists.newArrayList();
        this.playerKnockbackMap = Maps.newHashMap();
    }

    /**
     * Part 1: compute destroyed blocks and deal entity damage/knockback.
     * Block reach uses {@link #blockRadius}, entity reach uses {@link #entityRadius}.
     */
    @Override
    public void explode() {
        Set<BlockPos> set = Sets.newHashSet();

        // Block destruction sampling (same ray-cast approach as vanilla,
        // but the reach is blockRadius).
        for (int j = 0; j < 16; ++j) {
            for (int k = 0; k < 16; ++k) {
                for (int l = 0; l < 16; ++l) {
                    if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
                        double d0 = (double) ((float) j / 15.0F * 2.0F - 1.0F);
                        double d1 = (double) ((float) k / 15.0F * 2.0F - 1.0F);
                        double d2 = (double) ((float) l / 15.0F * 2.0F - 1.0F);
                        double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                        d0 = d0 / d3;
                        d1 = d1 / d3;
                        d2 = d2 / d3;
                        float f = this.blockRadius * (0.7F + this.level.getRandom().nextFloat() * ModConfig.rayReachVariation);
                        double d4 = this.x;
                        double d6 = this.y;
                        double d8 = this.z;
                        BlockPos lastBlock = null;

                        for (float f1 = 0.3F; f > 0.0F; f -= ModConfig.rayStepDecay) {
                            BlockPos blockpos = BlockPos.containing(d4, d6, d8);

                            // --- Energy transfer: per-block shock absorption ---
                            // The ray carries the TNT's shock energy. When the
                            // shock enters a NEW block (the 0.3-step march can
                            // sample the same block several times — absorb only
                            // on first entry so capacity is per-block, not
                            // per-step), the block absorbs as much energy as it
                            // can handle:
                            //   capacity = (blastResistance + base) * multiplier
                            // If the shock exceeds the capacity, the block
                            // explodes and the shock continues weakened by
                            // exactly the absorbed amount (E -= capacity).
                            // Otherwise the block absorbs the entire shock and
                            // SURVIVES — the ray ends here.
                            if (!blockpos.equals(lastBlock)) {
                                lastBlock = blockpos;
                                BlockState state = this.level.getBlockState(blockpos);

                                if (!state.isAir()) {
                                    float resistance = state.getBlock().getExplosionResistance();

                                    // Unbreakable blocks (bedrock, barriers)
                                    // absorb the whole shock and never explode.
                                    if (resistance < 0.0F) {
                                        break;
                                    }

                                    float capacity = (resistance + ModConfig.resistanceBase)
                                        * ModConfig.energyAbsorptionMultiplier;

                                    if (f > capacity) {
                                        // Shock overwhelms the block: it explodes.
                                        set.add(blockpos);
                                        // Track the residual shock energy
                                        // after absorption (drives debris
                                        // launch speed). Keep the max when
                                        // several rays hit the same block.
                                        float residual = f - capacity;
                                        Float existing = this.blockEnergyMap.get(blockpos);
                                        if (existing == null || residual > existing) {
                                            this.blockEnergyMap.put(blockpos, residual);
                                        }
                                        // The block cancels its capacity worth
                                        // of explosion energy; the weakened
                                        // shock travels on.
                                        f -= capacity;
                                    } else {
                                        // Block absorbs the entire remaining
                                        // shock and survives. Ray ends.
                                        break;
                                    }
                                }
                            }

                            d4 += d0 * 0.30000001192092896D;
                            d6 += d1 * 0.30000001192092896D;
                            d8 += d2 * 0.30000001192092896D;
                        }
                    }
                }
            }
        }

        this.affectedBlockPositions.addAll(set);

        // Explicitly add all fluid SOURCE blocks within the explosion radius.
        // The ray-cast sampling above may miss fluid blocks because fluids
        // have huge blast resistance and stop rays instantly. Only source
        // blocks (LEVEL 0) are collected: flowing water (levels 1-7) is left
        // untouched — it drains/flows naturally once its sources are gone,
        // instead of being flung as weird partial-block scatter.
        // Skipped entirely when the user disables fluid scattering in the
        // config — fluids are then left untouched by the blast.
        if (ModConfig.scatterFluids) {
            int fluidR = Mth.ceil(this.blockRadius);
            for (int fx = -fluidR; fx <= fluidR; fx++) {
                for (int fy = -fluidR; fy <= fluidR; fy++) {
                    for (int fz = -fluidR; fz <= fluidR; fz++) {
                        if (fx * fx + fy * fy + fz * fz <= fluidR * fluidR) {
                            BlockPos fpos = BlockPos.containing(
                                (int) this.x + fx, (int) this.y + fy, (int) this.z + fz);
                            BlockState fstate = this.level.getBlockState(fpos);
                            if (fstate.getBlock() instanceof LiquidBlock
                                && (fstate.getFluidState().is(FluidTags.WATER)
                                    || fstate.getFluidState().is(FluidTags.LAVA))
                                && fstate.getValue(LiquidBlock.LEVEL) == 0) {
                                set.add(fpos);
                            }
                        }
                    }
                }
            }
        }
        // Re-add in case any new fluid positions were found.
        for (BlockPos fp : set) {
            if (!this.affectedBlockPositions.contains(fp)) {
                this.affectedBlockPositions.add(fp);
            }
        }

        float f3 = this.entityRadius * 2.0F;
        int k1 = Mth.floor(this.x - (double) f3 - 1.0D);
        int l1 = Mth.floor(this.x + (double) f3 + 1.0D);
        int i2 = Mth.floor(this.y - (double) f3 - 1.0D);
        int i1 = Mth.floor(this.y + (double) f3 + 1.0D);
        int j2 = Mth.floor(this.z - (double) f3 - 1.0D);
        int j1 = Mth.floor(this.z + (double) f3 + 1.0D);
        List<Entity> list = this.level.getEntities(this.exploder,
            new AABB(k1, i2, j2, l1, i1, j1));
        net.minecraftforge.event.ForgeEventFactory.onExplosionDetonate(this.level, this, list, (double) f3);
        Vec3 vec3 = new Vec3(this.x, this.y, this.z);

        for (int k2 = 0; k2 < list.size(); ++k2) {
            Entity entity = list.get(k2);

            if (!entity.ignoreExplosion()) {
                double d12 = Math.sqrt(entity.distanceToSqr(this.x, this.y, this.z)) / (double) f3;

                if (d12 <= 1.0D) {
                    // Already-ignited TNT entities are SCATTERED (moved like
                    // regular blocks) instead of detonated. Apply knockback
                    // velocity away from the blast center so they fly outward.
                    // Their fuse continues counting down naturally — they may
                    // explode mid-flight if their fuse runs out.
                    if (entity instanceof PrimedTnt) {
                        double tdx = entity.getX() - this.x;
                        double tdy = entity.getY() + (double) entity.getEyeHeight() - this.y;
                        double tdz = entity.getZ() - this.z;
                        double tdist = (double) Mth.sqrt((float) (tdx * tdx + tdy * tdy + tdz * tdz));
                        if (tdist != 0.0D) {
                            tdx = tdx / tdist;
                            tdy = tdy / tdist;
                            tdz = tdz / tdist;
                            double tdensity = (double) Explosion.getSeenPercent(vec3, entity);
                            double tpush = (1.0D - d12) * tdensity * ModConfig.entityKnockbackScale;
                            entity.setDeltaMovement(entity.getDeltaMovement().add(
                                tdx * tpush * 2.0D,
                                tdy * tpush * 2.0D + 0.3D * ModConfig.entityKnockbackScale,
                                tdz * tpush * 2.0D));
                        }
                        continue;
                    }

                    // Falling blocks from previous explosions are immune to damage
                    // but accumulate additional velocity from this explosion. This
                    // means chain TNT detonations boost existing flying debris
                    // instead of destroying it, and reset the fall timer so the
                    // blocks don't vanish mid-air.
                    if (entity instanceof FallingBlockEntity) {
                        double d5 = entity.getX() - this.x;
                        double d7 = entity.getY() + (double) entity.getEyeHeight() - this.y;
                        double d9 = entity.getZ() - this.z;
                        double d13 = (double) Mth.sqrt((float) (d5 * d5 + d7 * d7 + d9 * d9));
                        if (d13 != 0.0D) {
                            d5 = d5 / d13;
                            d7 = d7 / d13;
                            d9 = d9 / d13;
                            double d14 = (double) Explosion.getSeenPercent(vec3, entity);
                            double d10 = (1.0D - d12) * d14 * ModConfig.entityKnockbackScale;
                            // Accumulate velocity — additive so energy builds up
                            // across multiple explosions. Reduced multiplier so
                            // chain explosions don't fling debris too far.
                            entity.setDeltaMovement(entity.getDeltaMovement().add(
                                d5 * d10 * 1.0D,
                                d7 * d10 * 1.0D + 0.15D * ModConfig.entityKnockbackScale,
                                d9 * d10 * 1.0D));
                        }
                        // Reset the fall timer so the block doesn't expire mid-air.
                        ((FallingBlockEntity) entity).time = 0;
                        continue;
                    }

                    double d5 = entity.getX() - this.x;
                    double d7 = entity.getY() + (double) entity.getEyeHeight() - this.y;
                    double d9 = entity.getZ() - this.z;
                    double d13 = (double) Mth.sqrt((float) (d5 * d5 + d7 * d7 + d9 * d9));

                    if (d13 != 0.0D) {
                        double d14 = (double) Explosion.getSeenPercent(vec3, entity);
                        double d10 = (1.0D - d12) * d14;
                        // Entity damage formula: (d^2 + d)/2 * 7 * radius + 1
                        entity.hurt(this.level.damageSources().explosion(this),
                            (float) ((int) ((d10 * d10 + d10) / 2.0D * 7.0D * (double) f3 + 1.0D)));
                        double d11 = d10;

                        if (entity instanceof LivingEntity) {
                            d11 = ProtectionEnchantment.getExplosionKnockbackAfterDampener(
                                (LivingEntity) entity, d10);
                        }

                        // 360° omnidirectional push: normalize the horizontal (X/Z)
                        // direction independently from Y so entities are always pushed
                        // away from the blast horizontally, regardless of height
                        // difference. This prevents the push from being mostly
                        // vertical when the entity is at the same Y as the TNT.
                        double horizDist = (double) Mth.sqrt((float) (d5 * d5 + d9 * d9));
                        double pushX, pushY, pushZ;

                        if (horizDist > 0.01D) {
                            // Normalized horizontal direction (360° outward).
                            pushX = d5 / horizDist;
                            pushZ = d9 / horizDist;
                        } else {
                            // Entity is directly above/below the blast — pick a
                            // random horizontal direction so they still get pushed
                            // outward instead of only straight up/down.
                            float angle = this.level.getRandom().nextFloat() * 6.2831855F;
                            pushX = (double) Mth.cos(angle);
                            pushZ = (double) Mth.sin(angle);
                        }

                        // Y push: normalized vertical component, but capped so the
                        // horizontal push isn't overshadowed.
                        pushY = d7 / d13;
                        if (pushY > 0.6D) pushY = 0.6D;
                        if (pushY < -0.3D) pushY = -0.3D;

                        // Apply the push, scaled by entityKnockbackScale so
                        // entities are nudged rather than launched (default
                        // 0.35 = minimal scattering).
                        double pushStrength = d11 * 1.5D * ModConfig.entityKnockbackScale;
                        entity.setDeltaMovement(entity.getDeltaMovement().add(
                            pushX * pushStrength,
                            pushY * pushStrength + 0.2D * ModConfig.entityKnockbackScale,
                            pushZ * pushStrength));

                        if (entity instanceof Player) {
                            Player player = (Player) entity;

                            if (!player.isSpectator()
                                && (!player.isCreative() || !player.getAbilities().flying)) {
                                this.playerKnockbackMap.put(player,
                                    new Vec3(pushX * d10 * ModConfig.entityKnockbackScale,
                                        pushY * d10 * ModConfig.entityKnockbackScale,
                                        pushZ * d10 * ModConfig.entityKnockbackScale));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Part 2: destroy the affected blocks and tell clients to render the blast
     * particles. All affected solid blocks are destroyed in place (no block
     * scattering) to form the crater; TNT blocks are collected for chain
     * detonation; fluids are scattered as fluid entities / evaporated when
     * {@code scatterFluids} is enabled.
     */
    @Override
    public void finalizeExplosion(boolean spawnParticles) {
        // The explosion sound is NOT played here. The
        // delayed blast wave particle plays the boom when the wave reaches each
        // player, giving the "shake + delayed sound" on impact.

        // Send the particle + knockback packet to every client in range.
        // Per-player knockback rides along with the packet and the client
        // applies it directly — server-side player motion is
        // client-authoritative and would be discarded anyway.
        sendExplosionToClients();

        if (this.damagesTerrain) {
            for (BlockPos blockpos : this.affectedBlockPositions) {
                BlockState state = this.level.getBlockState(blockpos);
                Block block = state.getBlock();

                if (!state.isAir()) {
                    boolean isFluid = state.getFluidState().is(FluidTags.WATER)
                        || state.getFluidState().is(FluidTags.LAVA);
                    boolean isTNT = block instanceof TntBlock;

                    if (isFluid && ModConfig.scatterFluids) {
                        // --- Energy at this fluid block (ray residual or
                        // distance fallback) — drives evaporation and the
                        // fluid launch speed.
                        double dx = (double) blockpos.getX() + 0.5D - this.x;
                        double dy = (double) blockpos.getY() + 0.5D - this.y;
                        double dz = (double) blockpos.getZ() + 0.5D - this.z;
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        Float storedEnergy = this.blockEnergyMap.get(blockpos);
                        double energyFactor;
                        if (storedEnergy != null) {
                            energyFactor = Math.max(0.1D, storedEnergy / this.blockRadius);
                        } else {
                            energyFactor = Math.max(0.3D, 1.0D - dist / (double) this.blockRadius);
                        }
                        double variation = 0.7D + this.level.getRandom().nextDouble() * 0.6D;
                        energyFactor *= variation;

                        // --- Water evaporation ---
                        // Water blocks that receive enough blast energy —
                        // comparable to the energy needed to break cobblestone
                        // (a high-energy zone near the blast center) — have a
                        // 40% chance to "evaporate": the block is destroyed
                        // and drops nothing, simulating the blast flash-
                        // vaporizing the water. The remaining 60% scatter as
                        // falling water entities (existing behavior). Lava
                        // never evaporates; it always scatters.
                        boolean isWater = state.getFluidState().is(FluidTags.WATER);
                        float evapThreshold = this.blockRadius * ModConfig.waterEvaporationThreshold;
                        boolean highEnergy = (storedEnergy != null && storedEnergy >= evapThreshold)
                            || (storedEnergy == null
                                && (1.0D - dist / (double) this.blockRadius) >= 0.6D);
                        if (isWater && highEnergy && this.level.getRandom().nextFloat() < ModConfig.waterEvaporationChance) {
                            // Evaporate: remove the water block, drop nothing.
                            this.level.setBlock(blockpos, Blocks.AIR.defaultBlockState(), 18);
                            continue;
                        }

                        // Remove the fluid block BEFORE spawning the entity.
                        // Flag 18 = 2 (client update) | 16 (skip neighbor
                        // notification) so neighboring fluids don't flow into
                        // the gap before the entity is spawned.
                        this.level.setBlock(blockpos, Blocks.AIR.defaultBlockState(), 18);

                        double baseSpeed = ModConfig.fluidLaunchSpeed * energyFactor;
                        double motionX, motionY, motionZ;
                        if (dist > 0.001D) {
                            motionX = (dx / dist) * baseSpeed;
                            motionY = (dy / dist) * baseSpeed + ModConfig.fluidUpwardBoost * energyFactor;
                            motionZ = (dz / dist) * baseSpeed;
                        } else {
                            motionX = 0.0D;
                            motionY = baseSpeed;
                            motionZ = 0.0D;
                        }
                        motionY = Math.min(motionY, ModConfig.fluidVelocityCapY);
                        motionX = Math.max(-ModConfig.fluidVelocityCapXZ, Math.min(ModConfig.fluidVelocityCapXZ, motionX));
                        motionZ = Math.max(-ModConfig.fluidVelocityCapXZ, Math.min(ModConfig.fluidVelocityCapXZ, motionZ));

                        EntityEnhancedFallingBlock entity = new EntityEnhancedFallingBlock(
                            this.level,
                            (double) ((float) blockpos.getX() + 0.5F),
                            (double) blockpos.getY(),
                            (double) ((float) blockpos.getZ() + 0.5F),
                            state);
                        entity.setDeltaMovement(new Vec3(motionX, motionY, motionZ));
                        this.level.addFreshEntity(entity);
                    } else if (isTNT) {
                        // TNT blocks are collected for simultaneous chain
                        // detonation instead of calling onBlockExploded
                        // (which spawns primed TNTs with random fuses).
                        this.level.setBlock(blockpos, Blocks.AIR.defaultBlockState(), 18);
                        this.chainedTNTPositions.add(blockpos);
                    } else {
                        // Solid blocks are destroyed in place — no scattering.
                        // Greatly reduced item drop chance (1/4 of vanilla's
                        // already-low rate) so explosions vaporize most blocks
                        // instead of dropping them.
                        if (block.dropFromExplosion(this)
                            && this.level.getRandom().nextFloat() < ModConfig.blockDropChance / this.blockRadius) {
                            Block.dropResources(state, this.level, blockpos, null);
                        }
                        this.level.setBlock(blockpos, Blocks.AIR.defaultBlockState(), 3);
                        block.wasExploded(this.level, blockpos, this);
                    }
                }
            }
        }

        // Queue blocks adjacent to the crater for collapse checking. These are
        // the blocks that may have lost their structural support due to the
        // explosion. The collapse handler processes them over the next few ticks
        // (anchored support check + cascade), then stops when settled.
        Set<BlockPos> affected = new HashSet<>(this.affectedBlockPositions);
        for (BlockPos pos : this.affectedBlockPositions) {
            for (int[] dir : ADJACENT) {
                BlockPos adj = pos.offset(dir[0], dir[1], dir[2]);
                if (!affected.contains(adj)) {
                    BlockCollapseHandler.queueCollapseCheck(this.level, adj);
                }
            }
        }
    }

    /** Returns TNT block positions collected during the blast for chain detonation. */
    public List<BlockPos> getChainedTNTPositions() {
        return this.chainedTNTPositions;
    }

    private static final int[][] ADJACENT = {
        { 1, 0, 0}, {-1, 0, 0},
        { 0, 1, 0}, { 0,-1, 0},
        { 0, 0, 1}, { 0, 0,-1}
    };

    private void sendExplosionToClients() {
        // Plume = the block above the center is air and the block below is not.
        boolean isPlume = this.level.getBlockState(BlockPos.containing(this.x, this.y + 1, this.z)).isAir()
            && !this.level.getBlockState(BlockPos.containing(this.x, this.y - 1, this.z)).isAir();

        // Only players within distSqr < 263000 (~512 blocks) receive the
        // explosion packet, and each packet carries that player's own
        // knockback vector.
        for (Player player : this.level.players()) {
            if (!(player instanceof ServerPlayer)) {
                continue;
            }
            double distSqr = player.distanceToSqr(this.x, this.y, this.z);
            if (distSqr >= 263000.0D) {
                continue;
            }
            Vec3 knockback = this.playerKnockbackMap.getOrDefault(player, Vec3.ZERO);
            SpawnExplosionMessage msg = new SpawnExplosionMessage(this.x, this.y, this.z,
                this.particlePower, isPlume,
                (float) knockback.x, (float) knockback.y, (float) knockback.z);
            ModNetwork.NETWORK.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player), msg);
        }
    }

    @Override
    public Map<Player, Vec3> getHitPlayers() {
        return this.playerKnockbackMap;
    }

    @Override
    public List<BlockPos> getToBlow() {
        return this.affectedBlockPositions;
    }
}
