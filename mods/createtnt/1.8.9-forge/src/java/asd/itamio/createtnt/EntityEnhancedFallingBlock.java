package asd.itamio.createtnt;

import java.util.List;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
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
 *   <li>The block state rides along the vanilla spawn packet (the extra data
 *       int), so the client renders the correct block from the first frame.</li>
 * </ul>
 */
public class EntityEnhancedFallingBlock extends EntityFallingBlock implements IEntityAdditionalSpawnData {

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
     * Server-side copy of the block state. The parent's
     * {@code fallTile} field is set through its constructor or the client
     * spawn packet, so we keep our own copy and return it from
     * {@link #getFallTileState()} whenever it's set.
     */
    private IBlockState syncedState;

    public EntityEnhancedFallingBlock(World world) {
        super(world);
    }

    public EntityEnhancedFallingBlock(World world, double x, double y, double z, IBlockState state) {
        super(world, x, y, z, state);
        this.syncedState = state;
        this.prevPosX = x;
        this.prevPosY = y;
        this.prevPosZ = z;
        this.fallTime = 0;
    }

    /**
     * Returns the block state this falling entity represents. On the server
     * our own field is set by the constructor; on the client the parent's
     * field is populated from the spawn packet — fall back to it.
     */
    public IBlockState getFallTileState() {
        if (this.syncedState != null) {
            return this.syncedState;
        }
        return super.getBlock();
    }

    @Override
    public IBlockState getBlock() {
        if (this.syncedState != null) {
            return this.syncedState;
        }
        return super.getBlock();
    }

    @Override
    public void onUpdate() {
        IBlockState fallTile = getFallTileState();
        if (fallTile == null || fallTile.getBlock() == Blocks.air) {
            this.setDead();
            return;
        }

        Block block = fallTile.getBlock();

        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        if (this.fallTime++ == 0) {
            BlockPos blockpos = new BlockPos(this);
            if (this.worldObj.getBlockState(blockpos).getBlock() == block) {
                this.worldObj.setBlockState(blockpos, Blocks.air.getDefaultState(), 3);
            }
            // Note: do NOT kill the entity if the block is already gone.
            // The explosion may have already set the block to air before
            // spawning this entity, which is a valid state.
        }

        // 1.8.9 has no isNoGravity toggle; always apply gravity.
        this.motionY -= ModConfig.fallingBlockGravity;

        // Track maximum fall speed for damage scaling.
        double currentSpeed = Math.abs(this.motionY);
        if (currentSpeed > this.maxFallSpeed) {
            this.maxFallSpeed = (float) currentSpeed;
        }

        // --- Horizontal energy decay (boom then slow down) ---
        // No hard cap on initial launch — let the explosion's boom fling
        // blocks outward at full force. Then apply a linear decay to the
        // horizontal speed vector so blocks rapidly lose energy and settle.
        double hSpeed = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
        if (hSpeed > 0.0D) {
            double decay = ModConfig.fallingBlockHorizontalDecay;
            double newSpeed = Math.max(0.0D, hSpeed - decay);
            double decayScale = newSpeed / hSpeed;
            this.motionX *= decayScale;
            this.motionZ *= decayScale;
        }

        this.moveEntity(this.motionX, this.motionY, this.motionZ);

        // --- Debris smoke trail removed: server-side particle API differs
        // in 1.8.9 and the effect is purely cosmetic. ---

        if (!this.worldObj.isRemote) {
            BlockPos blockpos1 = new BlockPos(this);

            if (!this.onGround) {
                if (this.fallTime > 100 && (blockpos1.getY() < 0 || blockpos1.getY() > 256)) {
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
                double speedSq = this.motionX * this.motionX + this.motionY * this.motionY + this.motionZ * this.motionZ;
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
                    MathHelper.floor_double(this.posX),
                    MathHelper.floor_double(this.posY - 0.01D),
                    MathHelper.floor_double(this.posZ));
                IBlockState belowState = this.worldObj.getBlockState(belowPos);
                Block belowBlock = belowState.getBlock();

                // --- Break-through logic ---
                // Glass: shatter on impact, continue falling.
                // Leaves: disintegrate, continue falling (no item drop).
                boolean breakThrough = false;
                if (belowBlock instanceof BlockGlass
                    || belowBlock == Blocks.stained_glass
                    || belowBlock == Blocks.stained_glass_pane
                    || belowBlock == Blocks.glass_pane) {
                    // No item drop — glass shatters into nothing.
                    this.worldObj.setBlockState(belowPos, Blocks.air.getDefaultState(), 3);
                    this.worldObj.playAuxSFX(2001, belowPos, Block.getStateId(belowState));
                    breakThrough = true;
                } else if (belowBlock instanceof BlockLeaves) {
                    this.worldObj.setBlockState(belowPos, Blocks.air.getDefaultState(), 3);
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
                    this.motionY *= -0.5D;
                    this.motionZ *= 0.7D;

                    // Deal speed-scaled damage to entities in the landing zone.
                    dealImpactDamage();

                    if (belowBlock != Blocks.piston_extension) {
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
        IBlockState fallTile = getFallTileState();
        if (fallTile == null || fallTile.getBlock() == Blocks.air) {
            return;
        }
        Block block = fallTile.getBlock();
        boolean isFluid = fallTile.getBlock().getMaterial() == Material.water
            || fallTile.getBlock().getMaterial() == Material.lava;

        for (int i = 0; i < 5; i++) {
            BlockPos tryPos = startPos.up(i);
            if (tryPos.getY() > 255) {
                break;
            }
            IBlockState at = this.worldObj.getBlockState(tryPos);
            boolean free = at.getBlock() == Blocks.air
                || at.getBlock().getMaterial().isLiquid()
                || at.getBlock().isReplaceable(this.worldObj, tryPos);
            if (!free) {
                continue;
            }
            // Solid blocks placed in air need solid footing below (not
            // air/liquid/torch...). When displacing a liquid, lodged debris
            // is fine anywhere.
            boolean displacingLiquid = at.getBlock().getMaterial().isLiquid();
            if (!isFluid && !displacingLiquid && isFree(this.worldObj.getBlockState(tryPos.down()))) {
                continue;
            }

            // Fluid source blocks (Blocks.WATER / Blocks.LAVA) flow naturally
            // when placed — their LEVEL property defaults to 0.
            IBlockState placeState = fallTile;
            this.worldObj.setBlockState(tryPos, placeState, 3);
            // Grant the settled block a grace period so the collapse cascade
            // doesn't immediately re-fall it (which would loop forever when
            // an unsupported block lands on a static block right below it).
            BlockCollapseHandler.markSettled(this.worldObj, tryPos);
            return;
        }

        // Nowhere to settle: drop the block as an item rather than deleting
        // it (fluids have no item form and simply evaporate).
        if (!isFluid) {
            Item item = Item.getItemFromBlock(block);
            if (item != null) {
                this.entityDropItem(new ItemStack(item), 0.0F);
            }
        }
    }

    /**
     * 1.8.9 replacement for FallingBlock.isFree(state): a block is "free"
     * (i.e. not solid footing) if it is air, a liquid, or otherwise
     * non-movement-blocking.
     */
    private static boolean isFree(IBlockState state) {
        return state.getBlock() == Blocks.air
            || state.getBlock().getMaterial().isLiquid()
            || !state.getBlock().getMaterial().blocksMovement();
    }

    /**
     * Deals damage to entities in the landing zone, scaled by accumulated fall
     * speed. Higher speed = more damage. Damage cap is configurable.
     */
    private void dealImpactDamage() {
        if (this.maxFallSpeed < 0.3D) return;

        float damage = this.maxFallSpeed * ModConfig.fallingBlockImpactDamageMultiplier;
        damage = Math.min(damage, ModConfig.fallingBlockImpactDamageCap);

        List<Entity> list = this.worldObj.getEntitiesWithinAABBExcludingEntity(this, this.getEntityBoundingBox());
        DamageSource source = new DamageSource("fallingBlock");

        for (Entity entity : list) {
            entity.attackEntityFrom(source, damage);
        }
    }

    /** Sends the block state id in the vanilla spawn packet's extra data. */
    @Override
    public void writeSpawnData(ByteBuf buffer) {
        buffer.writeInt(Block.getStateId(getFallTileState()));
    }

    @Override
    public void readSpawnData(ByteBuf additionalData) {
        this.syncedState = Block.getStateById(additionalData.readInt());
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger("EnhancedState", Block.getStateId(getFallTileState()));
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        if (tag.hasKey("EnhancedState")) {
            this.syncedState = Block.getStateById(tag.getInteger("EnhancedState"));
        }
    }
}
