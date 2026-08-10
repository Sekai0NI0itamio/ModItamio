package asd.itamio.createtnt.mixin;

import asd.itamio.createtnt.CameraShakeHandler;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the camera shake to the camera's rotation quaternion at the end of
 * {@code Camera.setup}. Fabric has no viewport event, so we hook the camera
 * setup directly.
 */
@Mixin(Camera.class)
public class CameraShakeMixin {

    @Inject(method = "setup", at = @At("TAIL"))
    private void createtnt$shakeCamera(BlockGetter level, Entity entity, boolean thirdPerson,
                                       boolean inverse, float partialTicks, CallbackInfo ci) {
        Camera self = (Camera) (Object) this;
        Vec3 disp = CameraShakeHandler.getDisplacementFor(partialTicks);
        if (disp.lengthSqr() < 1.0E-6D) {
            return;
        }
        // Add the shake as a camera-space delta rotation on the live
        // quaternion (yaw = disp.x, pitch = disp.y, roll = disp.z).
        Quaternionf shake = new Quaternionf().rotationYXZ(
            (float) (disp.x * Mth.DEG_TO_RAD),
            (float) (disp.y * Mth.DEG_TO_RAD),
            (float) (disp.z * Mth.DEG_TO_RAD));
        self.rotation().mul(shake);
    }
}
