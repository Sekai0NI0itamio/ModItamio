package asd.itamio.createtnt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

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
public class BlastWaveEffectParticle extends NoRenderParticle {

    private final double blastRadius;
    private final float volume;
    private final float pitch;
    private final float shakePower;
    private final float shakeLimit;

    private final double functionalRadius;
    private double currentRadius;

    BlastWaveEffectParticle(ClientLevel level, double x, double y, double z, float power) {
        super(level, x, y, z);
        this.blastRadius = (double) power * 12.0D;
        this.volume = Math.max(power * 2.0F, 16.0F);
        this.pitch = 0.8F + this.random.nextFloat() * 0.4F;
        this.shakePower = power * ModConfig.shakePowerMultiplier;
        this.shakeLimit = ModConfig.shakePowerLimit;

        this.functionalRadius = Math.max(this.blastRadius, (double) this.volume * 16.0D);
        this.currentRadius = 0.0D;

        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        // 100-tick timeout so the wave can't linger forever.
        this.lifetime = 100;
    }

    @Override
    public void tick() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            this.remove();
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

        double dist = Math.sqrt(player.distanceToSqr(this.x, this.y, this.z));
        if (this.currentRadius <= dist && dist <= newRadius) {
            // The wavefront has arrived at the player this tick.
            if (dist < this.blastRadius && this.blastRadius > 0.1D) {
                float f = 1.0F - (float) (dist / this.blastRadius);
                float f2 = f * f;
                float shake = Math.min(this.shakeLimit, this.shakePower * f2);
                // Duration 0 = the shake is applied immediately on arrival.
                CameraShakeHandler.addShakeEffect(shake, shake * 0.5F, shake * 0.5F,
                    this.x, this.y, this.z);
            }
            double volumeDist = (double) this.volume * 16.0D;
            if (dist < volumeDist) {
                // 1.21.11: the positional SimpleSoundInstance takes a BlockPos
                // instead of raw coordinates.
                Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
                    BetterTNTs.EXPLOSION_BOOM, SoundSource.BLOCKS,
                    this.volume, this.pitch,
                    RandomSource.create(this.level.random.nextLong()),
                    net.minecraft.core.BlockPos.containing(this.x, this.y, this.z)));
            }
            this.remove();
            return;
        }

        if (instant || newRadius >= this.functionalRadius) {
            this.remove();
            return;
        }
        this.currentRadius = newRadius;
        // Only reached while the wave is still expanding, so the 100-tick
        // timeout only counts expansion time.
        super.tick();
    }

    /** Provider creating the wave from its option data. */
    public static class Provider implements ParticleProvider<BlastWaveOption> {
        @Override
        public Particle createParticle(BlastWaveOption data, ClientLevel level, double x, double y,
                                       double z, double xSpeed, double ySpeed, double zSpeed,
                                       RandomSource random) {
            return new BlastWaveEffectParticle(level, x, y, z, data.power());
        }
    }
}
