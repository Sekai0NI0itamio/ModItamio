package asd.itamio.createtnt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.entity.state.FallingBlockRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Renderer for {@link EntityEnhancedFallingBlock}.
 *
 * <p>The vanilla {@link FallingBlockRenderer} only draws blocks with a baked
 * model — fluids (water/lava) have no model, so scattered fluid blobs would
 * be invisible. This renderer delegates solid blocks to the parent and draws
 * a semi-transparent colored cube for fluids so the player can see flying
 * water/lava blobs.</p>
 *
 * <p>1.21.11: entities render through the render-state pipeline — the entity
 * is snapshotted into a render state ({@link #extractRenderState}) and drawn
 * later ({@link #submit}), so the fluid flag and the wobble phase are
 * captured in our own render-state subclass. The cube itself is submitted as
 * custom geometry through the {@link SubmitNodeCollector}.</p>
 */
public class RenderEnhancedFallingBlock extends FallingBlockRenderer {

    /** Render state carrying the fluid block state + the wobble phase. */
    private static final class EnhancedFallingBlockRenderState extends FallingBlockRenderState {
        /** Non-null when the falling block is a fluid (draw the cube). */
        BlockState fluidState;
        /** Precomputed scale wobble so the blob looks alive. */
        float wobble;
    }

    public RenderEnhancedFallingBlock(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public FallingBlockRenderState createRenderState() {
        return new EnhancedFallingBlockRenderState();
    }

    @Override
    public void extractRenderState(FallingBlockEntity entity, FallingBlockRenderState state,
                                   float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (state instanceof EnhancedFallingBlockRenderState enhanced) {
            BlockState bs = entity.getBlockState();
            enhanced.fluidState = bs != null && bs.getBlock() instanceof LiquidBlock ? bs : null;
            enhanced.wobble = (float) Math.sin(entity.tickCount * 0.3D) * 0.05F;
        }
    }

    @Override
    public void submit(FallingBlockRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (state instanceof EnhancedFallingBlockRenderState enhanced && enhanced.fluidState != null) {
            renderFluidCube(enhanced, poseStack, collector);
            return;
        }
        super.submit(state, poseStack, collector, cameraState);
    }

    /**
     * Draws a semi-transparent colored cube for fluid falling blocks.
     * Water = light blue, lava = bright orange, with a subtle wobble so the
     * blob reads as liquid rather than a static cube.
     */
    private void renderFluidCube(EnhancedFallingBlockRenderState state,
                                 PoseStack poseStack, SubmitNodeCollector collector) {
        final float r, g, b, alpha;
        if (state.fluidState.getFluidState().is(FluidTags.LAVA)) {
            r = 0.95F; g = 0.40F; b = 0.05F; alpha = 0.85F;
        } else {
            // Water (and any other fluid defaults to water-blue).
            r = 0.20F; g = 0.40F; b = 0.85F; alpha = 0.70F;
        }

        poseStack.pushPose();
        // Center the cube on the entity.
        poseStack.translate(-0.49F, 0.0F, -0.49F);
        // Subtle wobble so the blob looks alive.
        float wobble = state.wobble;
        poseStack.translate(0.49F, 0.49F, 0.49F);
        poseStack.scale(1.0F + wobble, 1.0F - wobble, 1.0F + wobble);
        poseStack.translate(-0.49F, -0.49F, -0.49F);

        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) -> {
            Matrix4f mat = pose.pose();

            float x0 = 0.01F, y0 = 0.01F, z0 = 0.01F;
            float x1 = 0.97F, y1 = 0.97F, z1 = 0.97F;

            // -Z face
            quad(vc, mat, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, alpha);
            // +Z face
            quad(vc, mat, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, r, g, b, alpha);
            // -X face
            quad(vc, mat, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, r, g, b, alpha);
            // +X face
            quad(vc, mat, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, alpha);
            // -Y face
            quad(vc, mat, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, r, g, b, alpha);
            // +Y face
            quad(vc, mat, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, alpha);
        });

        poseStack.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f mat,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             float r, float g, float b, float a) {
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(mat, x4, y4, z4).setColor(r, g, b, a);
    }
}
