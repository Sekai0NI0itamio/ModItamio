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
 * Crossbow/dispenser rockets fly longer for each gunpowder in the firework:
 * every gunpowder adds {@code rocketFlightBonusPerGunpowder} extra ticks.
 */
@Mixin(FireworkRocketEntity.class)
public class RocketLifetimeMixin {
    @Shadow private int lifetime;

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;DDDZ)V",
            at = @At("TAIL"))
    private void createtnt$bonusFlightTime(Level level, ItemStack stack, double x, double y, double z,
                                           boolean shotAtAngle, CallbackInfo ci) {
        if (!shotAtAngle || !ModConfig.rocketShellExplosions) return;
        int gunpowder = RocketTracker.flightFrom(stack);
        if (gunpowder > 0 && ModConfig.rocketFlightBonusPerGunpowder > 0) {
            this.lifetime += gunpowder * ModConfig.rocketFlightBonusPerGunpowder;
        }
    }
}
