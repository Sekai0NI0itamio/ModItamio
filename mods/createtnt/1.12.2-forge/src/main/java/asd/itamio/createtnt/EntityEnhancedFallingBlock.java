package asd.itamio.createtnt;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

/**
 * Enhanced falling block entity with realistic impact physics.
 *
 * <p>Features beyond vanilla {@link EntityFallingBlock}:</p>
 * <ul>
 *   <li>Breaks glass blocks on impact and continues falling through</li>
 *   <li>Breaks leaves on impact and continues falling through</li>
 *   <li>Damage to entities scales with accumulated fall speed</li>
 *   <li>Fluid source blocks (water/lava) are placed on landing so they flow again</li>
 *   <li>Implements {@link IEntityAdditionalSpawnData} to sync the block state to
 *       the client (vanilla {@code EntityFallingBlock} only sends the block state
 *       via the type-70 spawn packet, which mod entities don't use).</li>
 * </ul>
 */
public class EntityEnhancedFallingBlock extends EntityFallingBlock
    implements IEntityAdditionalSpawnData {

    /** Tracks the maximum downward speed reached during the fall. */
    private float maxFallSpeed = 0.0F;

    /** Stuck detection: ticks spent effectively motionless without onGround
     *  (wedged inside a wall or balanced on another entity). After a few
     *  such ticks the block force-settles instead of glitching forever. */
    private int stuckTicks;
    private double lastSettleX;
    private double lastSettleY;
    private double lastSettleZ;

    /**
     * Client-side copy of the block state. On the server, {@code fallTile} is
     * set by the constructor. On the client, the no-arg constructor is used
     * (via {@code EntityRegistration.newInstance}), so {@code fallTile} is null.
     * This field is populated from {@link #readSpawnData} and returned by
     * {@link #getBlock()} when the parent's {@code fallTile} is null.
     */
    private IBlockState syncedState;

    public EntityEnhancedFallingBlock(World world) {
        super(world);
    }

    public EntityEnhancedFallingBlock(World world, double x, double y, double z, IBlockState state) {
        super(world, x, y, z, state);
    }

    @Override
    public void onUpdate() {
        IBlockState fallTile = this.getBlock();
        if (fallTile == null || fallTile.getMaterial() == Material.AIR) {
            this.setDead();
            return;
        }

        Block block = fallTile.getBlock();

        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        if (this.fallTime++ == 0) {
            BlockPos blockpos = new BlockPos(this);
            if (this.world.getBlockState(blockpos).getBlock() == block) {
                this.world.setBlockToAir(blockpos);
            }
            // Note: do NOT kill the entity if the block is already gone.
            // The explosion may have already set the block to air before
            // spawning this entity, which is a valid state.
        }

        if (!this.hasNoGravity()) {
            this.motionY -= ModConfig.fallingBlockGravity;
        }

        // Track maximum fall speed for damage scaling.
        double currentSpeed = Math.abs(this.motionY);
        if (currentSpeed > this.maxFallSpeed) {
            this.maxFallSpeed = (float) currentSpeed;
        }

        // --- Horizontal energy decay (boom then slow down) ---
        // No hard cap on initial launch — let the explosion's boom fling
        // blocks outward at full force. Then apply a linear decay to the
        // horizontal speed vector so blocks rapidly lose energy and settle.
        // Decay: 0.04 blocks/tick = 0.8 blocks/sec (fast slow-down after boom).
        double hSpeed = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
        if (hSpeed > 0.0D) {
            double decay = ModConfig.fallingBlockHorizontalDecay;
            double newSpeed = Math.max(0.0D, hSpeed - decay);
            double decayScale = newSpeed / hSpeed;
            this.motionX *= decayScale;
            this.motionZ *= decayScale;
        }

        this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);

        // --- Debris smoke trail ---
        // Spawn a small smoke puff every few ticks while the block is flying.
        if (!this.world.isRemote && this.fallTime > 1 && this.fallTime % 3 == 0) {
            double speed = Math.sqrt(this.motionX * this.motionX
                + this.motionY * this.motionY + this.motionZ * this.motionZ);
            if (speed > 0.1D) {
                this.world.spawnParticle(EnumParticleTypes.SMOKE_LARGE,
                    this.posX, this.posY, this.posZ,
                    0.0D, 0.02D, 0.0D);
            }
        }

        if (!this.world.isRemote) {
            BlockPos blockpos1 = new BlockPos(this);

            if (!this.onGround) {
                if (this.fallTime > 100 && (blockpos1.getY() < 1 || blockpos1.getY() > 256)) {
                    // Fell out of the world — lost to the void.
                    this.setDead();
                    return;
                }
                if (this.fallTime > ModConfig.fallingBlockMaxLifetime) {
                    // Lifetime expired: settle where it is instead of vanishing.
                    this.setDead();
                    this.settleBlock(new BlockPos(this));
                    return;
                }

                // --- Stuck detection ---
                // A falling block wedged inside a wall or resting on another
                // falling block entity never gets onGround (entities don't
                // collide with blocks when fully embedded). If it has been
                // effectively motionless for several ticks, force-settle it.
                double movedSq = (this.posX - this.lastSettleX) * (this.posX - this.lastSettleX)
                    + (this.posY - this.lastSettleY) * (this.posY - this.lastSettleY)
                    + (this.posZ - this.lastSettleZ) * (this.posZ - this.lastSettleZ);
                double speedSq = this.motionX * this.motionX
                    + this.motionY * this.motionY + this.motionZ * this.motionZ;
                if (movedSq < 1.0E-6D && speedSq < 1.0E-4D) {
                    this.stuckTicks++;
                } else {
                    this.stuckTicks = 0;
                }
                this.lastSettleX = this.posX;
                this.lastSettleY = this.posY;
                this.lastSettleZ = this.posZ;
                if (this.stuckTicks >= 10) {
                    this.setDead();
                    this.settleBlock(new BlockPos(this));
                    return;
                }
            } else {
                // Check the block the entity is landing on (just below).
                BlockPos belowPos = new BlockPos(
                    this.posX, this.posY - 0.01D, this.posZ);
                IBlockState belowState = this.world.getBlockState(belowPos);
                Block belowBlock = belowState.getBlock();

                // --- Break-through logic ---
                // Glass: shatter on impact, continue falling.
                // Leaves: disintegrate, continue falling (no item drop).
                boolean breakThrough = false;
                if (belowBlock == Blocks.GLASS || belowBlock == Blocks.STAINED_GLASS
                    || belowBlock == Blocks.GLASS_PANE
                    || belowBlock == Blocks.STAINED_GLASS_PANE) {
                    // No item drop — glass shatters into nothing.
                    this.world.setBlockToAir(belowPos);
                    this.world.playEvent(2001, belowPos,
                        Block.getStateId(belowState));
                    breakThrough = true;
                } else if (belowState.getMaterial() == Material.LEAVES
                    || belowBlock == Blocks.LEAVES || belowBlock == Blocks.LEAVES2) {
                    this.world.setBlockToAir(belowPos);
                    breakThrough = true;
                }

                if (breakThrough) {
                    // Keep falling through the broken block.
                    this.onGround = false;
                    // Reduce speed from the impact but preserve momentum.
                    this.motionX *= 0.85D;
                    this.motionY *= 0.6D;
                    this.motionZ *= 0.85D;
                } else {
                    // --- Normal landing ---
                    this.motionX *= 0.7D;
                    this.motionZ *= 0.7D;
                    this.motionY *= -0.5D;

                    // Deal speed-scaled damage to entities in the landing zone.
                    dealImpactDamage();

                    if (belowBlock != Blocks.PISTON_EXTENSION) {
                        this.setDead();
                        this.settleBlock(blockpos1);
                    }
                }
            }
        }

        this.motionX *= 0.98D;
        this.motionY *= 0.98D;
        this.motionZ *= 0.98D;
    }

    /**
     * Converts this falling entity back into a placed block. Searches upward
     * from the entity's position for the first free spot (air, liquid, or a
     * replaceable block like grass/snow) with solid footing below it, and
     * places the block there. This handles the cases where the entity's own
     * position is not placeable: embedded in terrain by float error, stacked
     * on another just-placed debris block, or wedged inside a wall.
     *
     * <p>If no valid spot exists (void, ceiling of bedrock...), solid blocks
     * drop as an item instead of vanishing; fluids simply evaporate.</p>
     */
    private void settleBlock(BlockPos startPos) {
        IBlockState fallTile = this.getBlock();
        if (fallTile == null || fallTile.getMaterial() == Material.AIR) {
            return;
        }
        Block block = fallTile.getBlock();
        boolean isFluid = fallTile.getMaterial() == Material.WATER
            || fallTile.getMaterial() == Material.LAVA;

        for (int i = 0; i < 5; i++) {
            BlockPos tryPos = startPos.up(i);
            if (tryPos.getY() > 255) {
                break;
            }
            IBlockState at = this.world.getBlockState(tryPos);
            Material atMat = at.getMaterial();
            boolean free = atMat == Material.AIR
                || atMat.isLiquid()
                || at.getBlock().isReplaceable(this.world, tryPos);
            if (!free) {
                continue;
            }
            // Solid blocks placed in air need solid footing below (not
            // air/liquid/torch...). When displacing a liquid, lodged debris
            // is fine anywhere.
            boolean displacingLiquid = atMat == Material.WATER || atMat == Material.LAVA;
            if (!isFluid && !displacingLiquid
                && BlockFalling.canFallThrough(this.world.getBlockState(tryPos.down()))) {
                continue;
            }

            IBlockState placeState = fallTile;
            if (isFluid) {
                // Convert static liquid (source) to flowing liquid with
                // LEVEL=0 so it spreads naturally, like a water bucket.
                if (block == Blocks.WATER) {
                    placeState = Blocks.FLOWING_WATER.getDefaultState()
                        .withProperty(BlockLiquid.LEVEL, 0);
                } else if (block == Blocks.LAVA) {
                    placeState = Blocks.FLOWING_LAVA.getDefaultState()
                        .withProperty(BlockLiquid.LEVEL, 0);
                }
            }
            this.world.setBlockState(tryPos, placeState, 3);
            // Grant the settled block a grace period so the collapse cascade
            // doesn't immediately re-fall it (which would loop forever when
            // an unsupported block lands on a static block right below it).
            BlockCollapseHandler.markSettled(this.world, tryPos);
            return;
        }

        // Nowhere to settle: drop the block as an item rather than deleting
        // it (fluids have no item form and simply evaporate).
        if (!isFluid) {
            Item item = Item.getItemFromBlock(block);
            if (item != Items.AIR) {
                this.entityDropItem(new ItemStack(item, 1, block.damageDropped(fallTile)), 0.0F);
            }
        }
    }

    /**
     * Deals damage to entities in the landing zone, scaled by accumulated fall
     * speed. Higher speed = more damage. Damage cap is configurable.
     */
    private void dealImpactDamage() {
        if (this.maxFallSpeed < 0.3D) return;

        float damage = this.maxFallSpeed * ModConfig.fallingBlockImpactDamageMultiplier;
        damage = Math.min(damage, ModConfig.fallingBlockImpactDamageCap);

        List<Entity> list = this.world.getEntitiesWithinAABBExcludingEntity(this,
            this.getEntityBoundingBox());
        DamageSource source = DamageSource.FALLING_BLOCK;

        for (Entity entity : list) {
            entity.attackEntityFrom(source, damage);
        }
    }

    /**
     * Returns the block state this falling entity represents. On the server,
     * the parent's {@code fallTile} field is set by the constructor. On the
     * client, {@code fallTile} is null because the no-arg constructor is used,
     * so we fall back to {@link #syncedState} (populated from spawn data).
     */
    @Override
    public IBlockState getBlock() {
        IBlockState state = super.getBlock();
        if (state != null) {
            return state;
        }
        return this.syncedState;
    }

    /**
     * Override of {@link EntityFallingBlock#fall} that prevents a
     * NullPointerException on the client where the parent's private
     * {@code fallTile} field is null. Impact damage is already handled by
     * {@link #dealImpactDamage()} in {@link #onUpdate()}.
     */
    @Override
    public void fall(float distance, float damageMultiplier) {
        // No-op: parent's fall() accesses private fallTile which is null on client.
    }

    // ---- IEntityAdditionalSpawnData ----

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        IBlockState state = super.getBlock();
        buffer.writeInt(state != null ? Block.getStateId(state) : -1);
        // Sync motion so the client has the explosion energy immediately
        // when the entity appears, rather than waiting for the first
        // server position update. This makes the "boom" expansion visible
        // from the very first render frame.
        buffer.writeDouble(this.motionX);
        buffer.writeDouble(this.motionY);
        buffer.writeDouble(this.motionZ);
    }

    @Override
    public void readSpawnData(ByteBuf buffer) {
        int stateId = buffer.readInt();
        if (stateId >= 0) {
            this.syncedState = Block.getStateById(stateId);
            // Set the parent's private fallTile field via reflection so that
            // RenderFallingBlock (which calls getBlock() internally) returns
            // the correct state on the client. Without this, fallTile is null
            // on the client because the no-arg constructor is used.
            ObfuscationReflectionHelper.setPrivateValue(
                EntityFallingBlock.class, this, this.syncedState, "field_175132_d");
            // On the client, immediately clear the block at the entity's
            // position. RenderFallingBlock skips rendering when the block at
            // the entity's position matches the entity's block state. By
            // clearing it here (before the entity is added to the world for
            // rendering), we ensure the first render call draws the entity.
            if (this.world != null && this.world.isRemote) {
                BlockPos pos = new BlockPos(this);
                if (this.world.getBlockState(pos).getBlock() == this.syncedState.getBlock()) {
                    this.world.setBlockToAir(pos);
                }
            }
        }
        // Apply the synced motion so the client sees the explosion energy
        // from the very first tick.
        this.motionX = buffer.readDouble();
        this.motionY = buffer.readDouble();
        this.motionZ = buffer.readDouble();
    }
}
