package asd.itamio.createtnt;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

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
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        mc.thePlayer.motionX += msg.knockbackX();
        mc.thePlayer.motionY += msg.knockbackY();
        mc.thePlayer.motionZ += msg.knockbackZ();
        spawnShellExplosion(mc.theWorld, msg.x(), msg.y(), msg.z(), msg.power(), msg.isPlume());
    }

    public static void spawnShellExplosion(World world, double x, double y, double z,
                                           float power, boolean isPlume) {
        // Scale the ENTIRE effect bundle (smoke cloud size/spread, blast-ray
        // size, blast-wave shake radius/strength, sound volume) with the
        // configured visual scale.
        power *= ModConfig.explosionVisualScale;

        Minecraft.getMinecraft().effectRenderer.addEffect(
            new BlastWaveEffectParticle(world, x, y, z, power));

        if (ModConfig.showShellExplosionClouds) {
            Minecraft.getMinecraft().effectRenderer.addEffect(
                new ShellExplosionCloudParticle(world, x, y, z, power, isPlume));
        }
    }
}
