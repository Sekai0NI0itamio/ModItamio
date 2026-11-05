package asd.itamio.createtnt.mixin;

import asd.itamio.createtnt.CameraShakeHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraShakeMixin {

    @Shadow
    private int matrixPropertiesDirty;

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void createtnt$shakeCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
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
        // Force viewRotationMatrix to be recomputed from the shaken rotation
        // during extractRenderState (DIRTY_VIEW_ROT = 1).
        this.matrixPropertiesDirty |= 1;
    }
}
