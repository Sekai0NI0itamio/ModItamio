package asd.itamio.createtnt.mixin;

import asd.itamio.createtnt.EnhancedExplosion;
import asd.itamio.createtnt.ModConfig;
import asd.itamio.createtnt.ModNetwork;
import asd.itamio.createtnt.RocketTracker;
import asd.itamio.createtnt.SpawnExplosionMessage;
import java.util.List;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Redirects enabled crossbow/dispenser fireworks into enhanced blasts. */
@Mixin(FireworkRocketEntity.class)
public class RocketExplosionMixin {
    @Shadow @Final
    private static EntityDataAccessor<ItemStack> DATA_ID_FIREWORKS_ITEM;

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void createtnt$redirectBlast(CallbackInfo ci) {
        FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
        if (self.level().isClientSide || !self.isShotAtAngle() || !ModConfig.rocketShellExplosions) return;
        ServerLevel level = (ServerLevel) self.level();
        double x = self.getX(), y = self.getY(), z = self.getZ();
        Vec3 motion = self.getDeltaMovement();
        double len = motion.length();
        float axisX = 0.0F, axisY = 1.0F, axisZ = 0.0F;
        if (len >= 1.0E-4D) {
            axisX = (float) (-motion.x / len); axisY = (float) (-motion.y / len); axisZ = (float) (-motion.z / len);
        }
        int stars = countStars(self);
        float particlePower = 3.0F * (ModConfig.tntStrength / 4.0F);
        if (stars <= 0) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(x, y, z) < 1.0E8D) {
                    ModNetwork.sendExplosion(player, new SpawnExplosionMessage(x, y, z,
                        particlePower * 0.4F, true, 0.0F, 0.0F, 0.0F, axisX, axisY, axisZ));
                }
            }
        } else {
            float powerMult = 1.0F + level.getRandom().nextFloat() * 2.0F;
            float blockPower = ModConfig.blockPower * powerMult;
            float entityPower = ModConfig.entityPower * powerMult * stars;
            EnhancedExplosion boom = new EnhancedExplosion(level, self, x, y, z,
                blockPower, entityPower, false, true, particlePower);
            boom.setPlumeAxis(axisX, axisY, axisZ);
            boom.explode();
            boom.finalizeExplosion(true);
            float chainRadius = Math.max(blockPower, entityPower) * 2.0F + 4.0F;
            List<PrimedTnt> chained = level.getEntitiesOfClass(PrimedTnt.class,
                new AABB(x - chainRadius, y - chainRadius, z - chainRadius,
                    x + chainRadius, y + chainRadius, z + chainRadius));
            for (PrimedTnt tnt : chained) tnt.setFuse(0);
        }
        RocketTracker.untrack(self.getUUID());
        self.discard();
        ci.cancel();
    }

    private static int countStars(FireworkRocketEntity self) {
        ItemStack stack = self.getEntityData().get(DATA_ID_FIREWORKS_ITEM);
        net.minecraft.nbt.CompoundTag tag = stack.isEmpty() ? null : stack.getTagElement("Fireworks");
        net.minecraft.nbt.ListTag list = tag != null ? tag.getList("Explosions", 10) : null;
        return list != null ? list.size() : 0;
    }
}
