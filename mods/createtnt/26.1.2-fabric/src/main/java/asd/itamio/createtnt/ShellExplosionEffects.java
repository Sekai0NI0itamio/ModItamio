package asd.itamio.createtnt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side entry point for the explosion visuals.
 *
 * <p>Every explosion spawns exactly two controller particles:</p>
 * <ol>
 *   <li>a {@link BlastWaveEffectParticle} — an invisible expanding wavefront
 *       that triggers the screen shake and plays the delayed boom when it
 *       reaches the player, and</li>
 *   <li>a {@link ShellExplosionCloudParticle} — the big smoke plume with its
 *       blast-ray streaks, gated by the {@code showShellExplosionClouds}
 *       config.</li>
 * </ol>
 */
public final class ShellExplosionEffects {

    private ShellExplosionEffects() {
    }

    /** Packet handler: apply knockback to the local player, then spawn. */
    public static void handlePacket(SpawnExplosionMessage msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        Vec3 knock = new Vec3(msg.knockbackX(), msg.knockbackY(), msg.knockbackZ());
        mc.player.setDeltaMovement(mc.player.getDeltaMovement().add(knock));
        spawnShellExplosion(mc.level, msg.x(), msg.y(), msg.z(), msg.power(), msg.isPlume());
    }

    public static void spawnShellExplosion(ClientLevel level, double x, double y, double z,
                                           float power, boolean isPlume) {
        // Scale the ENTIRE effect bundle (smoke cloud size/spread, blast-ray
        // size, blast-wave shake radius/strength, sound volume) with the
        // configured visual scale.
        power *= ModConfig.explosionVisualScale;

        // 1.21.11: addParticle(options, overrideLimiter, alwaysShow, ...).
        level.addParticle(new BlastWaveOption(power), false, true, x, y, z, 0.0D, 0.0D, 0.0D);

        if (ModConfig.showShellExplosionClouds) {
            level.addParticle(new ShellCloudOption(power, isPlume), false, true, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }
}
