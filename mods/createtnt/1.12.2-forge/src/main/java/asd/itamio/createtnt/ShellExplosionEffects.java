package asd.itamio.createtnt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

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
@SideOnly(Side.CLIENT)
public final class ShellExplosionEffects {

    private ShellExplosionEffects() {
    }

    public static void spawnShellExplosion(double x, double y, double z, float power, boolean isPlume) {
        World world = Minecraft.getMinecraft().world;
        ParticleManager manager = Minecraft.getMinecraft().effectRenderer;
        if (world == null || manager == null) {
            return;
        }

        // Scale the ENTIRE effect bundle (smoke cloud size/spread, blast-ray
        // size, blast-wave shake radius/strength, sound volume) with the
        // configured visual scale.
        power *= ModConfig.explosionVisualScale;

        manager.addEffect(new BlastWaveEffectParticle(world, x, y, z, power));

        if (ModConfig.showShellExplosionClouds) {
            manager.addEffect(new ShellExplosionCloudParticle(world, x, y, z, power, isPlume));
        }
    }
}
