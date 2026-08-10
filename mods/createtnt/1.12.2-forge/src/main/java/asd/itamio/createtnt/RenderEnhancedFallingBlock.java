package asd.itamio.createtnt;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderFallingBlock;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Custom renderer for {@link EntityEnhancedFallingBlock}.
 *
 * <p>Vanilla {@link RenderFallingBlock} only renders blocks whose render type
 * is {@link EnumBlockRenderType#MODEL}. Fluids (water/lava) use
 * {@link EnumBlockRenderType#LIQUID}, so they are never drawn as falling
 * entities. This renderer delegates to the parent for MODEL blocks and
 * draws a semi-transparent colored cube for LIQUID blocks so the player
 * can see flying water/lava blobs.</p>
 */
@SideOnly(Side.CLIENT)
public class RenderEnhancedFallingBlock extends RenderFallingBlock {

    public RenderEnhancedFallingBlock(RenderManager renderManagerIn) {
        super(renderManagerIn);
    }

    @Override
    public void doRender(EntityFallingBlock entity, double x, double y, double z,
                         float entityYaw, float partialTicks) {
        IBlockState state = entity.getBlock();
        if (state != null && state.getRenderType() == EnumBlockRenderType.LIQUID) {
            renderFluidCube(entity, state, x, y, z);
            return;
        }
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    /**
     * Renders a semi-transparent colored cube for fluid falling blocks.
     * Water = light blue, Lava = bright orange. This gives the player a
     * clear visual of flying water/lava blobs from explosions in/near fluids.
     */
    private void renderFluidCube(EntityFallingBlock entity, IBlockState state,
                                  double x, double y, double z) {
        Material mat = state.getMaterial();
        float r, g, b, alpha;
        if (mat == Material.LAVA) {
            r = 0.95F; g = 0.40F; b = 0.05F; alpha = 0.85F;
        } else {
            // Water (and any other fluid defaults to water-blue).
            r = 0.20F; g = 0.40F; b = 0.85F; alpha = 0.70F;
        }

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                                  GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();

        GlStateManager.translate((float) x, (float) y, (float) z);

        // Subtle wobble so the blob looks alive, not like a static cube.
        float wobble = (float) Math.sin(entity.ticksExisted * 0.3D) * 0.05F;
        GlStateManager.translate(0.5F, 0.5F, 0.5F);
        GlStateManager.scale(1.0F + wobble, 1.0F - wobble, 1.0F + wobble);
        GlStateManager.translate(-0.5F, -0.5F, -0.5F);

        GlStateManager.color(r, g, b, alpha);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION);

        // Min/max corners of the 0.98^3 cube (matches falling block size).
        double x0 = 0.01D, x1 = 0.97D;
        double y0 = 0.01D, y1 = 0.97D;
        double z0 = 0.01D, z1 = 0.97D;

        // -Z face
        buffer.pos(x0, y0, z0).endVertex();
        buffer.pos(x1, y0, z0).endVertex();
        buffer.pos(x1, y1, z0).endVertex();
        buffer.pos(x0, y1, z0).endVertex();
        // +Z face
        buffer.pos(x0, y0, z1).endVertex();
        buffer.pos(x0, y1, z1).endVertex();
        buffer.pos(x1, y1, z1).endVertex();
        buffer.pos(x1, y0, z1).endVertex();
        // -X face
        buffer.pos(x0, y0, z0).endVertex();
        buffer.pos(x0, y1, z0).endVertex();
        buffer.pos(x0, y1, z1).endVertex();
        buffer.pos(x0, y0, z1).endVertex();
        // +X face
        buffer.pos(x1, y0, z0).endVertex();
        buffer.pos(x1, y0, z1).endVertex();
        buffer.pos(x1, y1, z1).endVertex();
        buffer.pos(x1, y1, z0).endVertex();
        // -Y face
        buffer.pos(x0, y0, z0).endVertex();
        buffer.pos(x0, y0, z1).endVertex();
        buffer.pos(x1, y0, z1).endVertex();
        buffer.pos(x1, y0, z0).endVertex();
        // +Y face
        buffer.pos(x0, y1, z0).endVertex();
        buffer.pos(x1, y1, z0).endVertex();
        buffer.pos(x1, y1, z1).endVertex();
        buffer.pos(x0, y1, z1).endVertex();

        tessellator.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityFallingBlock entity) {
        return TextureMap.LOCATION_BLOCKS_TEXTURE;
    }
}
