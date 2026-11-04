package asd.itamio.createtnt.mixin;

import asd.itamio.createtnt.CameraShakeHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the camera shake to the camera's render state at the end of
 * extractRenderState. Fabric has no viewport event, so we hook the camera.
 */
@Mixin(Camera.class)
public class CameraShakeMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/renderer/state/level/CameraRenderState;F)V", at = @At("TAIL"))
    private void createtnt$shakeCamera(CameraRenderState cameraState, float cameraEntityPartialTicks, CallbackInfo ci) {
        Vec3 disp = CameraShakeHandler.getDisplacementFor(cameraEntityPartialTicks);
        if (disp.lengthSqr() < 1.0E-6D) {
            return;
        }
        Quaternionf shake = new Quaternionf().rotationYXZ(
            (float) (disp.x * Mth.DEG_TO_RAD),
            (float) (disp.y * Mth.DEG_TO_RAD),
            (float) (disp.z * Mth.DEG_TO_RAD));
        cameraState.orientation.mul(shake);
        cameraState.viewRotationMatrix.rotate(shake);
    }
}
