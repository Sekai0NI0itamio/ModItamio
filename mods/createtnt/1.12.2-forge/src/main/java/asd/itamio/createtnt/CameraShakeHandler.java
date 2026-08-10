package asd.itamio.createtnt;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Camera shake: a spring–damper oscillator per rotational axis
 * (yaw/pitch/roll). A blast kicks the velocity with a randomized impulse; the
 * spring oscillates and settles. The camera offset is sampled per frame in
 * {@link EntityViewRenderEvent.CameraSetup} with partial-tick interpolation.
 *
 * <ul>
 *   <li>restitution = {@code shakeSpringiness} (0.08)</li>
 *   <li>drag = {@code shakeDecay} (0.3)</li>
 *   <li>magnitudes scaled by {@code shakeIntensity} (1.3)</li>
 *   <li>per-axis clamp {@code 45 * intensity} degrees</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class CameraShakeHandler {

    private static final Random RANDOM = new Random();

    /** One queued shake impulse (magnitudes + blast position). */
    private static final class ScreenShakeEffect {
        int duration;
        final float yawMagnitude;
        final float pitchMagnitude;
        final float rollMagnitude;
        final double posX;
        final double posY;
        final double posZ;

        ScreenShakeEffect(int duration, float yawMagnitude, float pitchMagnitude, float rollMagnitude,
                          double posX, double posY, double posZ) {
            this.duration = duration;
            this.yawMagnitude = yawMagnitude;
            this.pitchMagnitude = pitchMagnitude;
            this.rollMagnitude = rollMagnitude;
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
        }

        /** Counts down; returns true when the shake should fire now. */
        boolean tick() {
            return --this.duration <= 0;
        }
    }

    private static final Set<ScreenShakeEffect> delayedShakes = new LinkedHashSet<>();
    private static Vec3d velocity = Vec3d.ZERO;
    private static Vec3d acceleration = Vec3d.ZERO;
    private static Vec3d displacement = Vec3d.ZERO;

    /**
     * Immediate shake from the blast wave (duration 0 = applied at once).
     */
    public static void addShakeEffect(float yawMagnitude, float pitchMagnitude, float rollMagnitude,
                                      double posX, double posY, double posZ) {
        addEffect(new ScreenShakeEffect(0, yawMagnitude, pitchMagnitude, rollMagnitude, posX, posY, posZ));
    }

    /**
     * Adds a shake; a duration of -1 delays the shake by the blast wave's
     * travel time to the player.
     */
    public static void addEffect(ScreenShakeEffect effect) {
        if (!ModConfig.screenShake) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (effect.duration == -1) {
            int ticks = 0;
            if (ModConfig.blastEffectDelaySpeed >= 1e-2F && mc.player != null) {
                double distSqr = mc.player.getDistanceSq(effect.posX, effect.posY, effect.posZ);
                double timeInSec = Math.sqrt(distSqr) / (double) ModConfig.blastEffectDelaySpeed;
                ticks = (int) Math.floor(timeInSec * 20.0D);
            }
            effect = new ScreenShakeEffect(ticks, effect.yawMagnitude, effect.pitchMagnitude,
                effect.rollMagnitude, effect.posX, effect.posY, effect.posZ);
        }
        if (effect.duration <= 0) {
            immediatelyAddEffect(effect);
        } else {
            delayedShakes.add(effect);
        }
    }

    /** Kicks the spring velocity with a randomized impulse. */
    private static void immediatelyAddEffect(ScreenShakeEffect effect) {
        // Scale magnitudes by the configured shake intensity.
        float shakeScale = ModConfig.shakeIntensity;
        float yaw = effect.yawMagnitude * shakeScale;
        float pitch = effect.pitchMagnitude * shakeScale;
        float roll = effect.rollMagnitude * shakeScale;
        double dy = yaw * (RANDOM.nextDouble() - RANDOM.nextDouble()) * 0.5D;
        double dp = pitch * (RANDOM.nextDouble() - RANDOM.nextDouble()) * 0.5D;
        double dr = roll * (RANDOM.nextDouble() - RANDOM.nextDouble()) * 0.5D;
        velocity = velocity.add(dy, dp, dr);
    }

    /** Releases delayed shakes, then integrates the spring. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (Iterator<ScreenShakeEffect> iter = delayedShakes.iterator(); iter.hasNext(); ) {
            ScreenShakeEffect effect = iter.next();
            if (effect.tick()) {
                immediatelyAddEffect(effect);
                iter.remove();
            }
        }
        int iterations = 10;
        double dt = 1.0D / iterations;
        double restitution = Math.max(0.005D, ModConfig.shakeSpringiness);
        double drag = Math.max(0.005D, ModConfig.shakeDecay);

        for (int i = 0; i < iterations; ++i) {
            Vec3d newRotationDisplacement = displacement
                .add(velocity.scale(dt))
                .add(acceleration.scale(0.5D * dt * dt));
            Vec3d newAccel = displacement.scale(-restitution)
                .add(velocity.scale(-drag));
            Vec3d newVel = velocity
                .add(acceleration.add(newAccel).scale(0.5D * dt));
            displacement = newRotationDisplacement;
            velocity = newVel;
            acceleration = newAccel;
            applyConstraints();
        }
        if (displacement.lengthSquared() < 1e-4D
            && velocity.lengthSquared() < 1e-4D
            && acceleration.lengthSquared() < 1e-4D) {
            clearEffects();
        }
    }

    /** Resets the spring to rest. */
    private static void clearEffects() {
        displacement = Vec3d.ZERO;
        velocity = Vec3d.ZERO;
        acceleration = Vec3d.ZERO;
    }

    /** Clamps each axis to 45 * intensity degrees. */
    private static void applyConstraints() {
        double maxRotation = 45.0D * ModConfig.shakeIntensity;
        if (Math.abs(displacement.x) > maxRotation) {
            displacement = new Vec3d(Math.copySign(maxRotation, displacement.x), displacement.y, displacement.z);
            velocity = new Vec3d(0.0D, velocity.y, velocity.z);
        }
        if (Math.abs(displacement.y) > maxRotation) {
            displacement = new Vec3d(displacement.x, Math.copySign(maxRotation, displacement.y), displacement.z);
            velocity = new Vec3d(velocity.x, 0.0D, velocity.z);
        }
        // Each axis is clamped independently and its velocity zeroed.
        if (Math.abs(displacement.z) > maxRotation) {
            displacement = new Vec3d(displacement.x, displacement.y, Math.copySign(maxRotation, displacement.z));
            velocity = new Vec3d(velocity.x, velocity.y, 0.0D);
        }
    }

    /** Samples the spring with partial-tick interpolation. */
    @SubscribeEvent
    public void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        float partialTicks = Minecraft.getMinecraft().getRenderPartialTicks();
        Vec3d currentDisp = displacement
            .add(velocity.scale(partialTicks))
            .add(acceleration.scale(partialTicks * partialTicks * 0.5D));
        event.setYaw(event.getYaw() + (float) currentDisp.x);
        event.setPitch(event.getPitch() + (float) currentDisp.y);
        event.setRoll(event.getRoll() + (float) currentDisp.z);
    }
}
