package asd.itamio.createtnt;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockTNT;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

/**
 * Enhanced explosion with separate block destruction and entity damage radii.
 *
 * <p>Unlike vanilla {@code Explosion}, block destruction reach and entity
 * damage reach are separate radii ({@code blockRadius} vs {@code entityRadius}),
 * exactly like a high-explosive blast. On completion it sends a
 * packet to clients so they render the big plume + blast wave particles.</p>
 */
public class EnhancedExplosion extends Explosion {

    private final World world;
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
    private final Map<EntityPlayer, Vec3d> playerKnockbackMap;
    /** TNT block positions collected during the blast, for simultaneous
     * chain detonation. */
    private final List<BlockPos> chainedTNTPositions = new java.util.ArrayList<>();

    /** Maps each affected block position to the maximum remaining ray energy
     * at that position. Used to scale launch velocity: blocks behind resistant
     * walls have very little energy left and barely move, while blocks in the
     * open retain most of the energy and fly far. */
    private final Map<BlockPos, Float> blockEnergyMap = new java.util.HashMap<>();

    /** Particle sizing power — decoupled from destruction power so the
     *  random TNT strength multiplier doesn't make particles absurdly big.
     *  Scales gently: a 3x-strength blast is only ~1.5x bigger visually. */
    private final float particlePower;

    public EnhancedExplosion(World worldIn, @Nullable Entity entityIn, double x, double y, double z,
                          float blockRadius, float entityRadius, boolean causesFire, boolean damagesTerrain) {
        this(worldIn, entityIn, x, y, z, blockRadius, entityRadius, causesFire, damagesTerrain, blockRadius);
    }

    public EnhancedExplosion(World worldIn, @Nullable Entity entityIn, double x, double y, double z,
                          float blockRadius, float entityRadius, boolean causesFire, boolean damagesTerrain,
                          float particlePower) {
        super(worldIn, entityIn, x, y, z, blockRadius, causesFire, damagesTerrain);
        this.world = worldIn;
        this.exploder = entityIn;
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
    public void doExplosionA() {
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
                        float f = this.blockRadius * (0.7F + this.world.rand.nextFloat() * ModConfig.rayReachVariation);
                        double d4 = this.x;
                        double d6 = this.y;
                        double d8 = this.z;
                        BlockPos lastBlock = null;

                        for (float f1 = 0.3F; f > 0.0F; f -= ModConfig.rayStepDecay) {
                            BlockPos blockpos = new BlockPos(d4, d6, d8);

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
                                IBlockState iblockstate = this.world.getBlockState(blockpos);

                                if (iblockstate.getMaterial() != Material.AIR) {
                                    float resistance = this.exploder != null
                                        ? this.exploder.getExplosionResistance(this, this.world, blockpos, iblockstate)
                                        : iblockstate.getBlock().getExplosionResistance(this.world, blockpos, null, this);

                                    // Unbreakable blocks (bedrock, barriers)
                                    // absorb the whole shock and never explode.
                                    if (resistance < 0.0F) {
                                        break;
                                    }

                                    float capacity = (resistance + ModConfig.resistanceBase)
                                        * ModConfig.energyAbsorptionMultiplier;

                                    if (f > capacity) {
                                        // Shock overwhelms the block: it explodes.
                                        if (this.exploder == null
                                            || this.exploder.canExplosionDestroyBlock(this, this.world, blockpos, iblockstate, f)) {
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
            int fluidR = MathHelper.ceil(this.blockRadius);
            for (int fx = -fluidR; fx <= fluidR; fx++) {
                for (int fy = -fluidR; fy <= fluidR; fy++) {
                    for (int fz = -fluidR; fz <= fluidR; fz++) {
                        if (fx * fx + fy * fy + fz * fz <= fluidR * fluidR) {
                            BlockPos fpos = new BlockPos(
                                (int) this.x + fx, (int) this.y + fy, (int) this.z + fz);
                            IBlockState fstate = this.world.getBlockState(fpos);
                            Material fmat = fstate.getMaterial();
                            if ((fmat == Material.WATER || fmat == Material.LAVA)
                                && fstate.getBlock() instanceof BlockLiquid
                                && fstate.getValue(BlockLiquid.LEVEL).intValue() == 0) {
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
        int k1 = MathHelper.floor(this.x - (double) f3 - 1.0D);
        int l1 = MathHelper.floor(this.x + (double) f3 + 1.0D);
        int i2 = MathHelper.floor(this.y - (double) f3 - 1.0D);
        int i1 = MathHelper.floor(this.y + (double) f3 + 1.0D);
        int j2 = MathHelper.floor(this.z - (double) f3 - 1.0D);
        int j1 = MathHelper.floor(this.z + (double) f3 + 1.0D);
        List<Entity> list = this.world.getEntitiesWithinAABBExcludingEntity(this.exploder,
            new AxisAlignedBB(k1, i2, j2, l1, i1, j1));
        net.minecraftforge.event.ForgeEventFactory.onExplosionDetonate(this.world, this, list, (double) f3);
        Vec3d vec3d = new Vec3d(this.x, this.y, this.z);

        for (int k2 = 0; k2 < list.size(); ++k2) {
            Entity entity = list.get(k2);

            if (!entity.isImmuneToExplosions()) {
                double d12 = entity.getDistance(this.x, this.y, this.z) / (double) f3;

                if (d12 <= 1.0D) {
                    // Already-ignited TNT entities are SCATTERED (moved like
                    // regular blocks) instead of detonated. Apply knockback
                    // velocity away from the blast center so they fly outward.
                    // Their fuse continues counting down naturally — they may
                    // explode mid-flight if their fuse runs out.
                    if (entity instanceof EntityTNTPrimed) {
                        double tdx = entity.posX - this.x;
                        double tdy = entity.posY + (double) entity.getEyeHeight() - this.y;
                        double tdz = entity.posZ - this.z;
                        double tdist = (double) MathHelper.sqrt(tdx * tdx + tdy * tdy + tdz * tdz);
                        if (tdist != 0.0D) {
                            tdx = tdx / tdist;
                            tdy = tdy / tdist;
                            tdz = tdz / tdist;
                            double tdensity = (double) this.world.getBlockDensity(vec3d, entity.getEntityBoundingBox());
                            double tpush = (1.0D - d12) * tdensity * ModConfig.entityKnockbackScale;
                            entity.motionX += tdx * tpush * 2.0D;
                            entity.motionY += tdy * tpush * 2.0D + 0.3D * ModConfig.entityKnockbackScale;
                            entity.motionZ += tdz * tpush * 2.0D;
                        }
                        continue;
                    }

                    // Falling blocks from previous explosions are immune to damage
                    // but accumulate additional velocity from this explosion. This
                    // means chain TNT detonations boost existing flying debris
                    // instead of destroying it, and reset the fall timer so the
                    // blocks don't vanish mid-air.
                    if (entity instanceof EntityFallingBlock) {
                        double d5 = entity.posX - this.x;
                        double d7 = entity.posY + (double) entity.getEyeHeight() - this.y;
                        double d9 = entity.posZ - this.z;
                        double d13 = (double) MathHelper.sqrt(d5 * d5 + d7 * d7 + d9 * d9);
                        if (d13 != 0.0D) {
                            d5 = d5 / d13;
                            d7 = d7 / d13;
                            d9 = d9 / d13;
                            double d14 = (double) this.world.getBlockDensity(vec3d, entity.getEntityBoundingBox());
                            double d10 = (1.0D - d12) * d14 * ModConfig.entityKnockbackScale;
                            // Accumulate velocity — additive so energy builds up
                            // across multiple explosions. Reduced multiplier so
                            // chain explosions don't fling debris too far.
                            entity.motionX += d5 * d10 * 1.0D;
                            entity.motionY += d7 * d10 * 1.0D + 0.15D * ModConfig.entityKnockbackScale;
                            entity.motionZ += d9 * d10 * 1.0D;
                        }
                        // Reset the fall timer so the block doesn't expire mid-air.
                        ((EntityFallingBlock) entity).fallTime = 0;
                        continue;
                    }

                    double d5 = entity.posX - this.x;
                    double d7 = entity.posY + (double) entity.getEyeHeight() - this.y;
                    double d9 = entity.posZ - this.z;
                    double d13 = (double) MathHelper.sqrt(d5 * d5 + d7 * d7 + d9 * d9);

                    if (d13 != 0.0D) {
                        double d14 = (double) this.world.getBlockDensity(vec3d, entity.getEntityBoundingBox());
                        double d10 = (1.0D - d12) * d14;
                        // Reference blast physics entity damage formula: (d^2 + d)/2 * 7 * radius + 1
                        entity.attackEntityFrom(DamageSource.causeExplosionDamage(this),
                            (float) ((int) ((d10 * d10 + d10) / 2.0D * 7.0D * (double) f3 + 1.0D)));
                        double d11 = d10;

                        if (entity instanceof EntityLivingBase) {
                            d11 = EnchantmentProtection.getBlastDamageReduction((EntityLivingBase) entity, d10);
                        }

                        // 360° omnidirectional push: normalize the horizontal (X/Z)
                        // direction independently from Y so entities are always pushed
                        // away from the blast horizontally, regardless of height
                        // difference. This prevents the push from being mostly
                        // vertical when the entity is at the same Y as the TNT.
                        double horizDist = (double) MathHelper.sqrt(d5 * d5 + d9 * d9);
                        double pushX, pushY, pushZ;

                        if (horizDist > 0.01D) {
                            // Normalized horizontal direction (360° outward).
                            pushX = d5 / horizDist;
                            pushZ = d9 / horizDist;
                        } else {
                            // Entity is directly above/below the blast — pick a
                            // random horizontal direction so they still get pushed
                            // outward instead of only straight up/down.
                            float angle = this.world.rand.nextFloat() * 6.2831855F;
                            pushX = (double) MathHelper.cos(angle);
                            pushZ = (double) MathHelper.sin(angle);
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
                        entity.motionX += pushX * pushStrength;
                        entity.motionY += pushY * pushStrength + 0.2D * ModConfig.entityKnockbackScale;
                        entity.motionZ += pushZ * pushStrength;

                        if (entity instanceof EntityPlayer) {
                            EntityPlayer entityplayer = (EntityPlayer) entity;

                            if (!entityplayer.isSpectator()
                                && (!entityplayer.isCreative() || !entityplayer.capabilities.isFlying)) {
                                this.playerKnockbackMap.put(entityplayer,
                                    new Vec3d(pushX * d10 * ModConfig.entityKnockbackScale,
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
    public void doExplosionB(boolean spawnParticles) {
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
                IBlockState iblockstate = this.world.getBlockState(blockpos);
                Block block = iblockstate.getBlock();

                if (iblockstate.getMaterial() != Material.AIR) {
                    Material mat = iblockstate.getMaterial();
                    boolean isFluid = mat == Material.WATER || mat == Material.LAVA;
                    boolean isTNT = block instanceof BlockTNT;

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
                        double variation = 0.7D + this.world.rand.nextDouble() * 0.6D;
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
                        boolean isWater = mat == Material.WATER;
                        float evapThreshold = this.blockRadius * ModConfig.waterEvaporationThreshold;
                        boolean highEnergy = (storedEnergy != null && storedEnergy >= evapThreshold)
                            || (storedEnergy == null
                                && (1.0D - dist / (double) this.blockRadius) >= 0.6D);
                        if (isWater && highEnergy && this.world.rand.nextFloat() < ModConfig.waterEvaporationChance) {
                            // Evaporate: remove the water block, drop nothing.
                            this.world.setBlockState(blockpos,
                                Blocks.AIR.getDefaultState(), 18);
                            continue;
                        }

                        // Remove the fluid block BEFORE spawning the entity.
                        // Flag 18 = 2 (client update) | 16 (skip neighbor
                        // notification) so neighboring fluids don't flow into
                        // the gap before the entity is spawned.
                        this.world.setBlockState(blockpos,
                            Blocks.AIR.getDefaultState(), 18);

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
                            this.world,
                            (double) ((float) blockpos.getX() + 0.5F),
                            (double) blockpos.getY(),
                            (double) ((float) blockpos.getZ() + 0.5F),
                            iblockstate);
                        entity.motionX = motionX;
                        entity.motionY = motionY;
                        entity.motionZ = motionZ;
                        this.world.spawnEntity(entity);
                    } else if (isTNT) {
                        // TNT blocks are collected for simultaneous chain
                        // detonation instead of calling onBlockExploded
                        // (which spawns primed TNTs with random fuses).
                        this.world.setBlockState(blockpos,
                            Blocks.AIR.getDefaultState(), 18);
                        this.chainedTNTPositions.add(blockpos);
                    } else {
                        // Solid blocks are destroyed in place — no scattering.
                        // Greatly reduced item drop chance (1/4 of vanilla's
                        // already-low rate) so explosions vaporize most blocks
                        // instead of dropping them.
                        if (block.canDropFromExplosion(this)) {
                            block.dropBlockAsItemWithChance(this.world, blockpos,
                                this.world.getBlockState(blockpos),
                                ModConfig.blockDropChance / this.blockRadius, 0);
                        }
                        block.onBlockExploded(this.world, blockpos, this);
                    }
                }
            }
        }

        // Queue blocks adjacent to the crater for collapse checking. These are
        // the blocks that may have lost their structural support due to the
        // explosion. The collapse handler processes them over the next few ticks
        // (SBP-style BFS support check + cascade), then stops when settled.
        Set<BlockPos> affected = new HashSet<>(this.affectedBlockPositions);
        for (BlockPos pos : this.affectedBlockPositions) {
            for (int[] dir : ADJACENT) {
                BlockPos adj = pos.add(dir[0], dir[1], dir[2]);
                if (!affected.contains(adj)) {
                    BlockCollapseHandler.queueCollapseCheck(this.world, adj);
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
        boolean isPlume = this.world.getBlockState(new BlockPos(this.x, this.y + 1, this.z)).getMaterial() == Material.AIR
            && this.world.getBlockState(new BlockPos(this.x, this.y - 1, this.z)).getMaterial() != Material.AIR;

        // Only players within distSqr < 263000 (~512 blocks) receive the
        // explosion packet, and each packet carries that player's own
        // knockback vector.
        for (EntityPlayer player : this.world.playerEntities) {
            if (!(player instanceof EntityPlayerMP)) {
                continue;
            }
            double distSqr = player.getDistanceSq(this.x, this.y, this.z);
            if (distSqr >= 263000.0D) {
                continue;
            }
            Vec3d knockback = this.playerKnockbackMap.getOrDefault(player, Vec3d.ZERO);
            SpawnExplosionMessage msg = new SpawnExplosionMessage(this.x, this.y, this.z,
                this.particlePower, isPlume,
                (float) knockback.x, (float) knockback.y, (float) knockback.z);
            ModNetwork.NETWORK.sendTo(msg, (EntityPlayerMP) player);
        }
    }

    @Override
    public Map<EntityPlayer, Vec3d> getPlayerKnockbackMap() {
        return this.playerKnockbackMap;
    }

    @Override
    public List<BlockPos> getAffectedBlockPositions() {
        return this.affectedBlockPositions;
    }
}