package asd.itamio.createtnt.mixin;

import asd.itamio.createtnt.ModConfig;
import asd.itamio.createtnt.RocketTracker;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crossbow/dispenser rocket flight duration by gunpowder charge: 1-2 gunpowder
 * rockets keep the vanilla duration for their charge; a 3-gunpowder rocket
 * (the most a player can craft in survival) flies twice as long. Rockets with
 * more gunpowder than vanilla allows follow the 3-gunpowder rule.
 */
@Mixin(FireworkRocketEntity.class)
public class RocketLifetimeMixin {
    @Shadow private int lifetime;

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;DDDZ)V",
            at = @At("TAIL"))
    private void createtnt$gunpowderFlightTime(Level level, ItemStack stack, double x, double y, double z,
                                               boolean shotAtAngle, CallbackInfo ci) {
        if (!shotAtAngle || !ModConfig.rocketShellExplosions) return;
        if (RocketTracker.flightFrom(stack) >= 3) {
            this.lifetime *= 2;
        }
    }
}
