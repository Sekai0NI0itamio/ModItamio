package asd.itamio.createtnt;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Enhanced falling block entity with realistic impact physics.
 *
 * <p>Features beyond vanilla {@link FallingBlockEntity}:</p>
 * <ul>
 *   <li>Breaks glass blocks on impact and continues falling through</li>
 *   <li>Breaks leaves on impact and continues falling through</li>
 *   <li>Damage to entities scales with accumulated fall speed</li>
 *   <li>Fluid source blocks (water/lava) are placed on landing so they flow again</li>
 *   <li>The block state rides along the vanilla spawn packet (the extra data
 *       int), so the client renders the correct block from the first frame.</li>
 * </ul>
 */
public class EntityEnhancedFallingBlock extends FallingBlockEntity {

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
     * Server-side copy of the block state. The parent's private
     * {@code blockState} field is only set through its private constructor or
     * the client spawn packet, so we keep our own copy and return it from
     * {@link #getBlockState()} whenever it's set.
     */
    private BlockState syncedState;

    public EntityEnhancedFallingBlock(EntityType<? extends FallingBlockEntity> type, Level level) {
        super(type, level);
    }

    public EntityEnhancedFallingBlock(Level level, double x, double y, double z, BlockState state) {
        this(BetterTNTs.ENHANCED_FALLING_BLOCK.get(), level);
        this.syncedState = state;
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.setStartPos(this.blockPosition());
        this.time = 0;
    }

    @Override
    public void tick() {
        BlockState fallTile = this.getBlockState();
        if (fallTile == null || fallTile.isAir()) {
            this.discard();
            return;
        }

        Block block = fallTile.getBlock();

        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();

        if (this.time++ == 0) {
            BlockPos blockpos = this.blockPosition();
            if (this.level().getBlockState(blockpos).getBlock() == block) {
                this.level().setBlock(blockpos, Blocks.AIR.defaultBlockState(), 3);
            }
            // Note: do NOT kill the entity if the block is already gone.
            // The explosion may have already set the block to air before
            // spawning this entity, which is a valid state.
        }

        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -ModConfig.fallingBlockGravity, 0.0D));
        }

        // Track maximum fall speed for damage scaling.
        double currentSpeed = Math.abs(this.getDeltaMovement().y);
        if (currentSpeed > this.maxFallSpeed) {
            this.maxFallSpeed = (float) currentSpeed;
        }

        // --- Horizontal energy decay (boom then slow down) ---
        // No hard cap on initial launch — let the explosion's boom fling
        // blocks outward at full force. Then apply a linear decay to the
        // horizontal speed vector so blocks rapidly lose energy and settle.
        double hSpeed = Math.sqrt(this.getDeltaMovement().x * this.getDeltaMovement().x
            + this.getDeltaMovement().z * this.getDeltaMovement().z);
        if (hSpeed > 0.0D) {
            double decay = ModConfig.fallingBlockHorizontalDecay;
            double newSpeed = Math.max(0.0D, hSpeed - decay);
            double decayScale = newSpeed / hSpeed;
            this.setDeltaMovement(new Vec3dHelper(this.getDeltaMovement()).scaleXZ(decayScale));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());

        // --- Debris smoke trail ---
        // Spawn a small smoke puff every few ticks while the block is flying.
        if (!this.level().isClientSide && this.time > 1 && this.time % 3 == 0) {
            double speed = this.getDeltaMovement().length();
            if (speed > 0.1D && this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    this.getX(), this.getY(), this.getZ(), 1, 0.0D, 0.02D, 0.0D, 0.0D);
            }
        }

        if (!this.level().isClientSide) {
            BlockPos blockpos1 = this.blockPosition();

            if (!this.onGround()) {
                if (this.time > 100 && (blockpos1.getY() < this.level().getMinBuildHeight()
                    || blockpos1.getY() > this.level().getMaxBuildHeight())) {
                    // Fell out of the world — lost to the void.
                    this.discard();
                    return;
                }
                if (this.time > ModConfig.fallingBlockMaxLifetime) {
                    // Lifetime expired: settle where it is instead of vanishing.
                    this.discard();
                    this.settleBlock(this.blockPosition());
                    return;
                }

                // --- Stuck detection ---
                // A falling block wedged inside a wall or resting on another
                // falling block entity never gets onGround (entities don't
                // collide with blocks when fully embedded). If it has been
                // effectively motionless for several ticks, force-settle it.
                double movedSq = (this.getX() - this.lastSettleX) * (this.getX() - this.lastSettleX)
                    + (this.getY() - this.lastSettleY) * (this.getY() - this.lastSettleY)
                    + (this.getZ() - this.lastSettleZ) * (this.getZ() - this.lastSettleZ);
                double speedSq = this.getDeltaMovement().lengthSqr();
                if (movedSq < 1.0E-6D && speedSq < 1.0E-4D) {
                    this.stuckTicks++;
                } else {
                    this.stuckTicks = 0;
                }
                this.lastSettleX = this.getX();
                this.lastSettleY = this.getY();
                this.lastSettleZ = this.getZ();
                if (this.stuckTicks >= 10) {
                    this.discard();
                    this.settleBlock(this.blockPosition());
                    return;
                }
            } else {
                // Check the block the entity is landing on (just below).
                BlockPos belowPos = BlockPos.containing(
                    this.getX(), this.getY() - 0.01D, this.getZ());
                BlockState belowState = this.level().getBlockState(belowPos);
                Block belowBlock = belowState.getBlock();

                // --- Break-through logic ---
                // Glass: shatter on impact, continue falling.
                // Leaves: disintegrate, continue falling (no item drop).
                boolean breakThrough = false;
                if (belowBlock instanceof TransparentBlock
                    || belowBlock instanceof net.minecraft.world.level.block.StainedGlassPaneBlock
                    || belowBlock == Blocks.GLASS_PANE) {
                    // No item drop — glass shatters into nothing.
                    this.level().setBlock(belowPos, Blocks.AIR.defaultBlockState(), 3);
                    this.level().levelEvent(2001, belowPos, Block.getId(belowState));
                    breakThrough = true;
                } else if (belowBlock instanceof net.minecraft.world.level.block.LeavesBlock) {
                    this.level().setBlock(belowPos, Blocks.AIR.defaultBlockState(), 3);
                    breakThrough = true;
                }

                if (breakThrough) {
                    // Keep falling through the broken block.
                    this.setOnGround(false);
                    // Reduce speed from the impact but preserve momentum.
                    this.setDeltaMovement(new Vec3dHelper(this.getDeltaMovement()).scaleImpact(0.85D, 0.6D, 0.85D));
                } else {
                    // --- Normal landing ---
                    this.setDeltaMovement(new Vec3dHelper(this.getDeltaMovement()).scaleImpact(0.7D, -0.5D, 0.7D));

                    // Deal speed-scaled damage to entities in the landing zone.
                    dealImpactDamage();

                    if (belowBlock != Blocks.MOVING_PISTON) {
                        this.discard();
                        this.settleBlock(blockpos1);
                    }
                }
            }
        }

        this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
    }

    /** Tiny helper for scaling delta movement components. */
    private static final class Vec3dHelper {
        private final double x, y, z;
        Vec3dHelper(net.minecraft.world.phys.Vec3 v) { this.x = v.x; this.y = v.y; this.z = v.z; }
        net.minecraft.world.phys.Vec3 scaleXZ(double scale) {
            return new net.minecraft.world.phys.Vec3(this.x * scale, this.y, this.z * scale);
        }
        net.minecraft.world.phys.Vec3 scaleImpact(double sx, double sy, double sz) {
            return new net.minecraft.world.phys.Vec3(this.x * sx, this.y * sy, this.z * sz);
        }
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
        BlockState fallTile = this.getBlockState();
        if (fallTile == null || fallTile.isAir()) {
            return;
        }
        Block block = fallTile.getBlock();
        boolean isFluid = fallTile.getFluidState().is(FluidTags.WATER)
            || fallTile.getFluidState().is(FluidTags.LAVA);

        for (int i = 0; i < 5; i++) {
            BlockPos tryPos = startPos.above(i);
            if (tryPos.getY() > this.level().getMaxBuildHeight() - 1) {
                break;
            }
            BlockState at = this.level().getBlockState(tryPos);
            boolean free = at.isAir()
                || !at.getFluidState().isEmpty()
                || at.canBeReplaced();
            if (!free) {
                continue;
            }
            // Solid blocks placed in air need solid footing below (not
            // air/liquid/torch...). When displacing a liquid, lodged debris
            // is fine anywhere.
            boolean displacingLiquid = !at.getFluidState().isEmpty();
            if (!isFluid && !displacingLiquid
                && FallingBlock.isFree(this.level().getBlockState(tryPos.below()))) {
                continue;
            }

            // Fluid source blocks (Blocks.WATER / Blocks.LAVA) flow naturally
            // when placed — their LEVEL property defaults to 0.
            BlockState placeState = fallTile;
            this.level().setBlock(tryPos, placeState, 3);
            // Grant the settled block a grace period so the collapse cascade
            // doesn't immediately re-fall it (which would loop forever when
            // an unsupported block lands on a static block right below it).
            BlockCollapseHandler.markSettled(this.level(), tryPos);
            return;
        }

        // Nowhere to settle: drop the block as an item rather than deleting
        // it (fluids have no item form and simply evaporate).
        if (!isFluid) {
            Item item = Item.byBlock(block);
            if (item != Items.AIR) {
                this.spawnAtLocation(new ItemStack(item));
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

        List<Entity> list = this.level().getEntities(this, this.getBoundingBox());
        DamageSource source = this.level().damageSources().fallingBlock(this);

        for (Entity entity : list) {
            entity.hurt(source, damage);
        }
    }

    /**
     * Returns the block state this falling entity represents. On the server
     * our own field is set by the constructor; on the client the parent's
     * field is populated from the spawn packet — fall back to it.
     */
    @Override
    public BlockState getBlockState() {
        if (this.syncedState != null) {
            return this.syncedState;
        }
        return super.getBlockState();
    }

    /** Sends the block state id in the vanilla spawn packet's extra data. */
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity, Block.getId(this.getBlockState()));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("EnhancedState", Block.getId(this.getBlockState()));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("EnhancedState")) {
            this.syncedState = Block.stateById(tag.getInt("EnhancedState"));
        }
    }
}
