package asd.itamio.createtnt;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Camera shake: a spring–damper oscillator per rotational axis
 * (yaw/pitch/roll). A blast kicks the velocity with a randomized impulse; the
 * spring oscillates and settles. The camera offset is applied to the local
 * player's rotation in {@link TickEvent.RenderTickEvent} using a delta
 * approach (only the change since the last frame is applied, so mouse input
 * is never lost).
 *
 * <ul>
 *   <li>restitution = {@code shakeSpringiness} (0.08)</li>
 *   <li>drag = {@code shakeDecay} (0.3)</li>
 *   <li>magnitudes scaled by {@code shakeIntensity} (1.3)</li>
 *   <li>per-axis clamp {@code 45 * intensity} degrees</li>
 * </ul>
 *
 * <p>Note: 1.8.9 has no {@code ViewportEvent.ComputeCameraAngles} event, so
 * the shake is applied by directly modifying {@code rotationYaw} and
 * {@code rotationPitch} on the player entity before each render. Roll (the z
 * axis of the spring) is computed but not applied — the first-person camera
 * in 1.8.9 has no roll channel.</p>
 */
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

    private static final Set<ScreenShakeEffect> delayedShakes = new LinkedHashSet<ScreenShakeEffect>();
    private static Vec3 velocity = new Vec3(0.0D, 0.0D, 0.0D);
    private static Vec3 acceleration = new Vec3(0.0D, 0.0D, 0.0D);
    private static Vec3 displacement = new Vec3(0.0D, 0.0D, 0.0D);

    /** Last shake offset applied to the player's rotation (for delta calc). */
    private static float lastYawOffset = 0.0F;
    private static float lastPitchOffset = 0.0F;

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
            if (ModConfig.blastEffectDelaySpeed >= 1e-2F && mc.thePlayer != null) {
                double distSqr = mc.thePlayer.getDistanceSq(effect.posX, effect.posY, effect.posZ);
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
        velocity = new Vec3(velocity.xCoord + dy, velocity.yCoord + dp, velocity.zCoord + dr);
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
            Vec3 newRotationDisplacement = add(add(displacement, scale(velocity, dt)),
                scale(acceleration, 0.5D * dt * dt));
            Vec3 newAccel = add(scale(displacement, -restitution), scale(velocity, -drag));
            Vec3 newVel = add(velocity, scale(add(acceleration, newAccel), 0.5D * dt));
            displacement = newRotationDisplacement;
            velocity = newVel;
            acceleration = newAccel;
            applyConstraints();
        }
        if (lengthSqr(displacement) < 1e-4D
            && lengthSqr(velocity) < 1e-4D
            && lengthSqr(acceleration) < 1e-4D) {
            clearEffects();
        }
    }

    /**
     * Applies the shake offset to the player's rotation using a delta
     * approach: only the difference from the last applied offset is added,
     * so mouse input between frames is preserved.
     */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        if (event.phase == TickEvent.Phase.START) {
            float partialTicks = event.renderTickTime;
            // Interpolate the spring state for smooth sub-tick rendering.
            Vec3 currentDisp = add(add(displacement, scale(velocity, partialTicks)),
                scale(acceleration, partialTicks * partialTicks * 0.5D));
            float yawOffset = (float) currentDisp.xCoord;
            float pitchOffset = (float) currentDisp.yCoord;
            // Apply only the delta so mouse rotation is never overwritten.
            mc.thePlayer.rotationYaw += yawOffset - lastYawOffset;
            mc.thePlayer.rotationPitch += pitchOffset - lastPitchOffset;
            lastYawOffset = yawOffset;
            lastPitchOffset = pitchOffset;
        }
    }

    /** Resets the spring to rest. */
    private static void clearEffects() {
        displacement = new Vec3(0.0D, 0.0D, 0.0D);
        velocity = new Vec3(0.0D, 0.0D, 0.0D);
        acceleration = new Vec3(0.0D, 0.0D, 0.0D);
        lastYawOffset = 0.0F;
        lastPitchOffset = 0.0F;
    }

    /** Clamps each axis to 45 * intensity degrees. */
    private static void applyConstraints() {
        double maxRotation = 45.0D * ModConfig.shakeIntensity;
        if (Math.abs(displacement.xCoord) > maxRotation) {
            displacement = new Vec3(Math.copySign(maxRotation, displacement.xCoord), displacement.yCoord, displacement.zCoord);
            velocity = new Vec3(0.0D, velocity.yCoord, velocity.zCoord);
        }
        if (Math.abs(displacement.yCoord) > maxRotation) {
            displacement = new Vec3(displacement.xCoord, Math.copySign(maxRotation, displacement.yCoord), displacement.zCoord);
            velocity = new Vec3(velocity.xCoord, 0.0D, velocity.zCoord);
        }
        if (Math.abs(displacement.zCoord) > maxRotation) {
            displacement = new Vec3(displacement.xCoord, displacement.yCoord, Math.copySign(maxRotation, displacement.zCoord));
            velocity = new Vec3(velocity.xCoord, velocity.yCoord, 0.0D);
        }
    }

    // ── Vec3 helper methods (1.8.9 Vec3 has limited operations) ──

    private static Vec3 scale(Vec3 v, double s) {
        return new Vec3(v.xCoord * s, v.yCoord * s, v.zCoord * s);
    }

    private static Vec3 add(Vec3 a, Vec3 b) {
        return new Vec3(a.xCoord + b.xCoord, a.yCoord + b.yCoord, a.zCoord + b.zCoord);
    }

    private static double lengthSqr(Vec3 v) {
        return v.xCoord * v.xCoord + v.yCoord * v.yCoord + v.zCoord * v.zCoord;
    }
}
