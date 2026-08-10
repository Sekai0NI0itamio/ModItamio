package asd.itamio.createtnt;

import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Renders the shell-explosion smoke AFTER the translucent block pass via
 * {@link RenderWorldLastEvent}.
 *
 * <p>In 1.12.2, all vanilla particle passes (including FX layer 3) run before
 * translucent blocks (water/lava), so flowing water paints over the smoke.
 * This handler renders the smoke after the translucent pass so it draws on
 * top of water instead of being painted over by it.</p>
 *
 * <p>Solid blocks still occlude the smoke because they write depth during the
 * solid/cutout passes; translucent blocks don't write depth, so the smoke
 * passes the depth test against terrain but ignores water surfaces.</p>
 *
 * <p><b>Matrix note:</b> in 1.12.2 the modelview at {@code RenderWorldLastEvent}
 * is rotation-only (plus tiny constant offsets) — the camera POSITION is never
 * on the matrix; vanilla particles emit camera-relative vertices. This handler
 * emits the exact same camera-relative quads, just later in the frame (after
 * the translucent pass), so no matrix work is needed at all.</p>
 */
@SideOnly(Side.CLIENT)
public class SmokeRenderHandler {

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (ShellExplosionSmokeParticle.getActiveParticles().isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null) {
            viewer = mc.player;
        }
        if (world == null || viewer == null) {
            return;
        }

        float partialTicks = event.getPartialTicks();

        // Rotation basis, exactly as ParticleManager.renderLitParticles.
        float rotX = MathHelper.cos(viewer.rotationYaw * 0.017453292F);
        float rotZ = MathHelper.cos(viewer.rotationPitch * 0.017453292F);
        float rotYZ = MathHelper.sin(viewer.rotationYaw * 0.017453292F);
        float rotXY = -rotYZ * MathHelper.sin(viewer.rotationPitch * 0.017453292F);
        float rotXZ = rotX * MathHelper.sin(viewer.rotationPitch * 0.017453292F);

        mc.entityRenderer.enableLightmap();

        // Translucent blend state, no depth writes.
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.alphaFunc(516, 0.003921569F);
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        RenderHelper.disableStandardItemLighting();

        long worldTime = world.getTotalWorldTime();
        Iterator<ShellExplosionSmokeParticle> iter =
            ShellExplosionSmokeParticle.getActiveParticles().iterator();
        while (iter.hasNext()) {
            ShellExplosionSmokeParticle particle = iter.next();
            // Prune particles that are no longer being ticked: dead, from an
            // old world, or silently evicted from the 16384-entry layer cap
            // (those freeze mid-animation — drop them instead of rendering
            // ghost smoke forever). When the game is paused, world time stops
            // too, so paused particles are not falsely pruned.
            if (particle.isDead() || particle.getParticleWorld() != world
                || worldTime - particle.lastTickWorldTime > 2L) {
                iter.remove();
                continue;
            }
            particle.renderSmoke(viewer, partialTicks, rotX, rotZ, rotYZ, rotXY, rotXZ);
        }

        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.alphaFunc(516, 0.1F);

        mc.entityRenderer.disableLightmap();
    }
}
