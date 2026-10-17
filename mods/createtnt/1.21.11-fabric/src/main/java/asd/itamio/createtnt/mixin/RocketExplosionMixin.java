package asd.itamio.createtnt.mixin;

import asd.itamio.createtnt.ModConfig;
import asd.itamio.createtnt.RocketTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Redirects enabled crossbow/dispenser fireworks into enhanced blasts. */
@Mixin(FireworkRocketEntity.class)
public class RocketExplosionMixin {

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void createtnt$redirectBlast(ServerLevel level, CallbackInfo ci) {
        FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
        if (level.isClientSide() || !self.isShotAtAngle() || !ModConfig.rocketShellExplosions) return;
        RocketTracker.detonateShell(level, self);
        RocketTracker.untrack(self.getUUID());
        ci.cancel();
    }
}
