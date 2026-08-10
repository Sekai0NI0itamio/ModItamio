package asd.itamio.createtnt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * An invisible particle representing a blast wavefront expanding outward from
 * the explosion at {@code blastEffectDelaySpeed} (default 320 m/s).
 *
 * <p>When the front reaches the local player it applies the screen shake (with
 * quadratic falloff inside {@code blastRadius}) and plays the explosion sound
 * if the player is within {@code volume * 16} blocks — producing the
 * "see the flash, then the boom arrives" delay of a distant blast.</p>
 *
 * <p>Parameters: blast radius is {@code power * 12}, sound volume is
 * {@code max(power * 2, 16)}, pitch is {@code 0.8 + rand * 0.4}, and the shake
 * strength is {@code power * shakePowerMultiplier} clamped to
 * {@code shakePowerLimit} degrees.</p>
 */
@SideOnly(Side.CLIENT)
public class BlastWaveEffectParticle extends Particle {

    private final double blastRadius;
    private final float volume;
    private final float pitch;
    private final float shakePower;
    private final float shakeLimit;

    private final double functionalRadius;
    private double currentRadius;

    public BlastWaveEffectParticle(World world, double x, double y, double z, float power) {
        super(world, x, y, z);
        this.blastRadius = (double) power * 12.0D;
        this.volume = Math.max(power * 2.0F, 16.0F);
        this.pitch = 0.8F + this.rand.nextFloat() * 0.4F;
        this.shakePower = power * ModConfig.shakePowerMultiplier;
        this.shakeLimit = ModConfig.shakePowerLimit;

        this.functionalRadius = Math.max(this.blastRadius, (double) this.volume * 16.0D);
        this.currentRadius = 0.0D;

        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;
        this.particleGravity = 0.0F;
        this.canCollide = false;
        // 100-tick timeout so the wave can't linger forever.
        this.particleMaxAge = 100;
    }

    @Override
    public void onUpdate() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            this.setExpired();
            return;
        }

        // Near-zero delay speed means the effect is instantaneous.
        boolean instant = ModConfig.blastEffectDelaySpeed < 1e-2F;
        if (instant) {
            this.currentRadius = 0.0D;
        }
        double speed = ModConfig.blastEffectDelaySpeed * 0.05D;
        double newRadius = instant ? this.functionalRadius
            : Math.min(this.currentRadius + speed, this.functionalRadius);

        double dist = Math.sqrt(player.getDistanceSq(this.posX, this.posY, this.posZ));
        if (this.currentRadius <= dist && dist <= newRadius) {
            // The wavefront has arrived at the player this tick.
            if (dist < this.blastRadius && this.blastRadius > 0.1D) {
                float f = 1.0F - (float) (dist / this.blastRadius);
                float f2 = f * f;
                float shake = Math.min(this.shakeLimit, this.shakePower * f2);
                // Duration 0 = the shake is applied immediately on arrival.
                CameraShakeHandler.addShakeEffect(shake, shake * 0.5F, shake * 0.5F, this.posX, this.posY, this.posZ);
            }
            double volumeDist = (double) this.volume * 16.0D;
            if (dist < volumeDist && BetterTNTs.EXPLOSION_SOUND != null) {
                Minecraft.getMinecraft().getSoundHandler().playSound(
                    new PositionedSoundRecord(BetterTNTs.EXPLOSION_SOUND, SoundCategory.BLOCKS,
                        this.volume, this.pitch, (float) this.posX, (float) this.posY, (float) this.posZ));
            }
            this.setExpired();
            return;
        }

        if (instant || newRadius >= this.functionalRadius) {
            this.setExpired();
        } else {
            this.currentRadius = newRadius;
            // Only reached while the wave is still expanding, so the 100-tick
            // timeout only counts expansion time.
            this.prevPosX = this.posX;
            this.prevPosY = this.posY;
            this.prevPosZ = this.posZ;
            if (this.particleAge++ >= this.particleMaxAge) {
                this.setExpired();
            }
        }
    }

    /** Invisible controller — renders nothing. */
    @Override
    public void renderParticle(BufferBuilder buffer, Entity entityIn, float partialTicks,
                               float rotationX, float rotationZ, float rotationYZ,
                               float rotationXY, float rotationXZ) {
    }
}
