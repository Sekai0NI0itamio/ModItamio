package asd.itamio.createtnt;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderFallingBlock;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.item.EntityFallingBlock;
import org.lwjgl.opengl.GL11;

/**
 * Renderer for {@link EntityEnhancedFallingBlock}.
 *
 * <p>The vanilla {@link RenderFallingBlock} only draws blocks with a baked
 * model — fluids (water/lava) have no model, so scattered fluid blobs would
 * be invisible. This renderer delegates solid blocks to the parent and draws
 * a semi-transparent colored cube for fluids so the player can see flying
 * water/lava blobs.</p>
 */
public class RenderEnhancedFallingBlock extends RenderFallingBlock {

    public RenderEnhancedFallingBlock(RenderManager renderManager) {
        super(renderManager);
    }

    @Override
    public void doRender(EntityFallingBlock entity, double x, double y, double z,
                         float entityYaw, float partialTicks) {
        if (entity instanceof EntityEnhancedFallingBlock) {
            IBlockState state = ((EntityEnhancedFallingBlock) entity).getFallTileState();
            if (state != null && state.getBlock() instanceof BlockLiquid) {
                renderFluidCube(entity, state, x, y, z);
                return;
            }
        }
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    /**
     * Draws a semi-transparent colored cube for fluid falling blocks.
     * Water = light blue, lava = bright orange, with a subtle wobble so the
     * blob reads as liquid rather than a static cube.
     */
    private void renderFluidCube(EntityFallingBlock entity, IBlockState state,
                                 double x, double y, double z) {
        float r, g, b, alpha;
        if (state.getBlock().getMaterial() == Material.lava) {
            r = 0.95F; g = 0.40F; b = 0.05F; alpha = 0.85F;
        } else {
            // Water (and any other fluid defaults to water-blue).
            r = 0.20F; g = 0.40F; b = 0.85F; alpha = 0.70F;
        }

        GlStateManager.pushMatrix();
        // Center the cube on the entity.
        GlStateManager.translate((float) x - 0.5F, (float) y, (float) z - 0.5F);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        // Subtle wobble so the blob looks alive.
        float wobble = (float) Math.sin(entity.ticksExisted * 0.3D) * 0.05F;
        float s1 = 0.01F;
        float s2 = 0.97F + wobble;

        // -Z face
        wr.pos(s1, s1, s1).color(r, g, b, alpha).endVertex();
        wr.pos(s2, s1, s1).color(r, g, b, alpha).endVertex();
        wr.pos(s2, s2, s1).color(r, g, b, alpha).endVertex();
        wr.pos(s1, s2, s1).color(r, g, b, alpha).endVertex();
        // +Z face
        wr.pos(s1, s1, s2).color(r, g, b, alpha).endVertex();
        wr.pos(s1, s2, s2).color(r, g, b, alpha).endVertex();
        wr.pos(s2, s2, s2).color(r, g, b, alpha).endVertex();
        wr.pos(s2, s1, s2).color(r, g, b, alpha).endVertex();
        // -X face
        wr.pos(s1, s1, s1).color(r, g, b, alpha).endVertex();
        wr.pos(s1, s2, s1).color(r, g, b, alpha).endVertex();
        wr.pos(s1, s2, s2).color(r, g, b, alpha).endVertex();
        wr.pos(s1, s1, s2).color(r, g, b, alpha).endVertex();
        // +X face
        wr.pos(s2, s1, s1).color(r, g, b, alpha).endVertex();
        wr.pos(s2, s1, s2).color(r, g, b, alpha).endVertex();
        wr.pos(s2, s2, s2).color(r, g, b, alpha).endVertex();
        wr.pos(s2, s2, s1).color(r, g, b, alpha).endVertex();
        // -Y face
        wr.pos(s1, s1, s1).color(r, g, b, alpha).endVertex();
        wr.pos(s1, s1, s2).color(r, g, b, alpha).endVertex();
        wr.pos(s2, s1, s2).color(r, g, b, alpha).endVertex();
        wr.pos(s2, s1, s1).color(r, g, b, alpha).endVertex();
        // +Y face
        wr.pos(s1, s2, s1).color(r, g, b, alpha).endVertex();
        wr.pos(s2, s2, s1).color(r, g, b, alpha).endVertex();
        wr.pos(s2, s2, s2).color(r, g, b, alpha).endVertex();
        wr.pos(s1, s2, s2).color(r, g, b, alpha).endVertex();

        tessellator.draw();

        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
}
