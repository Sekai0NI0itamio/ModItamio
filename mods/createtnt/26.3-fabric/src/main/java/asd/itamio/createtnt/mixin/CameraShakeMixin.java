package asd.itamio.createtnt.mixin;

import asd.itamio.createtnt.CameraShakeHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the camera shake to the camera's rotation quaternion at the end of
 * the per-frame camera update. Fabric has no viewport event, so we hook the
 * camera directly.
 */
@Mixin(Camera.class)
public class CameraShakeMixin {

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void createtnt$shakeCameraUpdate(DeltaTracker deltaTracker, CallbackInfo ci) {
        Camera self = (Camera) (Object) this;
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        Vec3 disp = CameraShakeHandler.getDisplacementFor(partialTicks);
        if (disp.lengthSqr() < 1.0E-6D) {
            return;
        }
        Quaternionf shake = new Quaternionf().rotationYXZ(
            (float) (disp.x * Mth.DEG_TO_RAD),
            (float) (disp.y * Mth.DEG_TO_RAD),
            (float) (disp.z * Mth.DEG_TO_RAD));
        self.rotation().mul(shake);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void createtnt$shakeCameraExtract(CameraRenderState cameraState, DeltaTracker deltaTracker, CallbackInfo ci) {
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        Vec3 disp = CameraShakeHandler.getDisplacementFor(partialTicks);
        if (disp.lengthSqr() < 1.0E-6D) {
            return;
        }
        Quaternionf shake = new Quaternionf().rotationYXZ(
            (float) (disp.x * Mth.DEG_TO_RAD),
            (float) (disp.y * Mth.DEG_TO_RAD),
            (float) (disp.z * Mth.DEG_TO_RAD));
        cameraState.orientation.mul(shake);
    }
}
