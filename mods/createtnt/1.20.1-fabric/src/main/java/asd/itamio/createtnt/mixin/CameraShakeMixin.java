package asd.itamio.createtnt.mixin;

import asd.itamio.createtnt.CameraShakeHandler;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the camera shake to the camera's yaw/pitch angles at the end of
 * {@code Camera.setup}. 1.20.1's GameRenderer consumes the camera angles
 * (Axis.XP/YP rotations), NOT the rotation quaternion, so the quaternion can
 * not be used here. Roll is not rendered by the vanilla 1.20.1 view at all.
 */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {

    @Invoker("setRotation")
    protected abstract void createtnt$invokeSetRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"))
    private void createtnt$shakeCamera(BlockGetter level, Entity entity, boolean thirdPerson,
                                       boolean inverse, float partialTicks, CallbackInfo ci) {
        Vec3 disp = CameraShakeHandler.getDisplacementFor(partialTicks);
        if (disp.lengthSqr() < 1.0E-6D) {
            return;
        }
        Camera self = (Camera) (Object) this;
        float yaw = self.getYRot() + (float) disp.x;
        float pitch = Mth.clamp(self.getXRot() + (float) disp.y, -90.0F, 90.0F);
        this.createtnt$invokeSetRotation(yaw, pitch);
    }
}
