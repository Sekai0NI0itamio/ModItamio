package asd.itamio.createtnt;

import java.util.List;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

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
public class EntityEnhancedTNTPrimed extends EntityTNTPrimed {

    /** Ticks since the TNT was ignited. Used to determine water-unlit behavior. */
    private int ticksSinceLit;

    public EntityEnhancedTNTPrimed(World world) {
        super(world);
        this.ticksSinceLit = 0;
    }

    public EntityEnhancedTNTPrimed(World world, double x, double y, double z, EntityLivingBase igniter) {
        super(world, x, y, z, igniter);
        this.setPosition(x, y, z);
        double d0 = world.rand.nextDouble() * (Math.PI * 2.0D);
        this.motionX = -Math.sin(d0) * 0.02D;
        this.motionY = 0.2D;
        this.motionZ = -Math.cos(d0) * 0.02D;
        this.fuse = 80;
        this.prevPosX = x;
        this.prevPosY = y;
        this.prevPosZ = z;
        this.ticksSinceLit = 0;
    }

    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        // Physics — same as vanilla EntityTNTPrimed.
        this.motionY -= 0.04D;
        this.moveEntity(this.motionX, this.motionY, this.motionZ);
        this.motionX *= 0.98D;
        this.motionY *= 0.98D;
        this.motionZ *= 0.98D;
        if (this.onGround) {
            this.motionX *= 0.7D;
            this.motionY *= -0.5D;
            this.motionZ *= 0.7D;
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
        int currentFuse = this.fuse;
        if (!this.worldObj.isRemote && inWater && this.ticksSinceLit >= ModConfig.waterUnlightMinTicks
            && this.ticksSinceLit < ModConfig.waterUnlightMaxTicks && currentFuse > 1) {
            this.unlit();
            return;
        }

        // Normal fuse countdown.
        int newFuse = currentFuse - 1;
        this.fuse = newFuse;

        if (newFuse <= 0) {
            // Explode BEFORE setDead() so chain-reaction logic can still
            // find this TNT entity via getEntitiesWithinAABB.
            if (!this.worldObj.isRemote) {
                // Directly create an EnhancedExplosion — reliable in water.
                // 1.0x to 3.0x random power multiplier — each TNT varies in strength.
                float powerMult = 1.0F + this.worldObj.rand.nextFloat() * 2.0F;
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
                BlockPos tntPos = new BlockPos(this);
                boolean inWaterBlock = this.worldObj.getBlockState(tntPos).getBlock().getMaterial() == Material.water;
                boolean inLavaBlock = this.worldObj.getBlockState(tntPos).getBlock().getMaterial() == Material.lava;
                if (inWaterBlock) {
                    blockPower *= ModConfig.waterResistanceScale;
                    entityPower *= ModConfig.waterResistanceScale;
                }
                if (inLavaBlock) {
                    blockPower *= ModConfig.lavaResistanceScale;
                    entityPower *= ModConfig.lavaResistanceScale;
                }

                EnhancedExplosion explosion = new EnhancedExplosion(
                    this.worldObj, this,
                    this.posX, this.posY + (double) (this.height / 16.0F), this.posZ,
                    blockPower, entityPower, false, true, particlePower);
                explosion.doExplosionA();
                explosion.doExplosionB(true);

                // --- Simultaneous chain detonation ---
                // All TNT blocks and primed TNT entities caught in the blast
                // are collected and detonated IMMEDIATELY (same tick), so the
                // entire field goes up at once instead of one-by-one.
                detonateChain(explosion, blockPower, entityPower);
                chainVisited.clear();
            }
            this.setDead();
        } else if (this.worldObj.isRemote) {
            this.worldObj.spawnParticle(EnumParticleTypes.SMOKE_NORMAL,
                this.posX, this.posY + 0.5D, this.posZ, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * Unlights the TNT: kills the entity and places a TNT block (or drops an
     * item if the position is occupied). Plays a fire-extinguish sound to
     * signal the dousing.
     */
    private void unlit() {
        this.setDead();
        if (!this.worldObj.isRemote) {
            BlockPos pos = new BlockPos(this);
            if (this.worldObj.getBlockState(pos).getBlock().isReplaceable(this.worldObj, pos)) {
                this.worldObj.setBlockState(pos, Blocks.tnt.getDefaultState(), 3);
            } else {
                this.entityDropItem(new ItemStack(Blocks.tnt), 0.0F);
            }
            this.worldObj.playSoundEffect(this.posX, this.posY, this.posZ,
                "game.fire.extinguish", 0.8F, 1.2F);
        }
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger("TicksSinceLit", this.ticksSinceLit);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        this.ticksSinceLit = tag.getInteger("TicksSinceLit");
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
        java.util.Collections.synchronizedList(new java.util.ArrayList<ScheduledDetonation>());

    /**
     * Processes chain TNTs collected by an explosion. The center (cx/cy/cz) is
     * the position the blast originated from, used to find and scatter primed
     * TNT entities in range.
     */
    private void detonateChain(EnhancedExplosion explosion, float blockPower, float entityPower) {
        // The chain search is centered on THIS TNT's position.
        detonateChainAt(this.worldObj, this.posX, this.posY, this.posZ, this,
            explosion, blockPower, entityPower);
    }

    /**
     * Position-based chain detonation. Finds primed TNT entities around the
     * given center and scatters them, then either detonates all collected TNT
     * block positions immediately (simultaneous mode) or schedules them with a
     * staggered delay (one-by-one mode).
     */
    private static void detonateChainAt(World world, double cx, double cy, double cz,
                                        EntityEnhancedTNTPrimed cause,
                                        EnhancedExplosion explosion,
                                        float blockPower, float entityPower) {
        java.util.List<BlockPos> tntPositions = new java.util.ArrayList<>(
            explosion.getChainedTNTPositions());

        // Find primed TNT entities around the blast center and scatter them.
        float chainRadius = Math.max(blockPower, entityPower) * 2.0F + 4.0F;
        List<EntityTNTPrimed> chainedTNTs = world.getEntitiesWithinAABB(
            EntityTNTPrimed.class,
            new AxisAlignedBB(
                cx - chainRadius, cy - chainRadius, cz - chainRadius,
                cx + chainRadius, cy + chainRadius, cz + chainRadius));

        for (EntityTNTPrimed tnt : chainedTNTs) {
            if (tnt == cause) continue;
            double dx = tnt.posX - cx;
            double dy = tnt.posY - cy;
            double dz = tnt.posZ - cz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 0.0D) {
                double scatter = 0.4D * (1.0D - dist / (double) chainRadius);
                scatter = Math.max(0.1D, scatter);
                tnt.motionX += (dx / dist) * scatter;
                tnt.motionY += (dy / dist) * scatter + 0.2D;
                tnt.motionZ += (dz / dist) * scatter;
            }
        }

        if (ModConfig.simultaneousChainDetonation) {
            // Detonate all collected TNT positions immediately (same tick).
            for (BlockPos pos : tntPositions) {
                if (chainVisited.contains(pos)) continue;
                chainVisited.add(pos);
                detonatePosition(world, pos, cause);
            }
        } else {
            // One-by-one: schedule each position to explode with a staggered
            // delay so they go off sequentially rather than all at once.
            long now = world.getTotalWorldTime();
            int index = 0;
            for (BlockPos pos : tntPositions) {
                if (chainVisited.contains(pos)) continue;
                chainVisited.add(pos);
                long fireAt = now + (long) ModConfig.chainDetonationDelay * (index + 1);
                scheduledDetonations.add(new ScheduledDetonation(
                    world, pos, cause, fireAt));
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
    private static void detonatePosition(World world, BlockPos pos, EntityEnhancedTNTPrimed cause) {
        // Fluid resistance scaling for chain explosions.
        float chainBlockPower = ModConfig.blockPower;
        float chainEntityPower = ModConfig.entityPower;
        boolean inWater = world.getBlockState(pos).getBlock().getMaterial() == Material.water;
        boolean inLava = world.getBlockState(pos).getBlock().getMaterial() == Material.lava;
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
            world, cause, cx, cy, cz,
            chainBlockPower, chainEntityPower, false, true, ModConfig.tntStrength);
        chainExplosion.doExplosionA();
        chainExplosion.doExplosionB(true);

        // Recursively process any TNTs caught in this chain explosion,
        // centered on this position.
        detonateChainAt(world, cx, cy, cz, cause, chainExplosion, chainBlockPower, chainEntityPower);
    }

    /**
     * Processes scheduled (one-by-one) chain detonations on the server tick.
     * Any entry whose fire time has passed is detonated now and removed.
     * Registered on the Forge event bus in {@link BetterTNTs}.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (scheduledDetonations.isEmpty()) return;

        // Snapshot the due entries while holding the lock briefly, then
        // process them outside the synchronized block to avoid reentrancy
        // (each detonation may add new scheduled entries).
        java.util.List<ScheduledDetonation> due = new java.util.ArrayList<>();
        synchronized (scheduledDetonations) {
            java.util.Iterator<ScheduledDetonation> iter = scheduledDetonations.iterator();
            while (iter.hasNext()) {
                ScheduledDetonation sd = iter.next();
                if (sd.world.getTotalWorldTime() >= sd.fireAt) {
                    due.add(sd);
                    iter.remove();
                }
            }
        }
        for (ScheduledDetonation sd : due) {
            detonatePosition(sd.world, sd.pos, sd.cause);
        }
    }

    /** Holder for a deferred chain-detonation. */
    private static final class ScheduledDetonation {
        final World world;
        final BlockPos pos;
        final EntityEnhancedTNTPrimed cause;
        final long fireAt;

        ScheduledDetonation(World world, BlockPos pos, EntityEnhancedTNTPrimed cause, long fireAt) {
            this.world = world;
            this.pos = pos;
            this.cause = cause;
            this.fireAt = fireAt;
        }
    }
}
