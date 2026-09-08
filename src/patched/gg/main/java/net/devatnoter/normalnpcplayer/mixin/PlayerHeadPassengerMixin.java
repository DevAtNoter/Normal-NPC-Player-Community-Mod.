package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes Player a valid vehicle for the mod's Baby head passenger while leaving
 * every other vanilla passenger relationship untouched. The actual passenger
 * list and rider lifecycle are still handled by Entity.startRiding()/rideTick().
 */
@Mixin(Entity.class)
public abstract class PlayerHeadPassengerMixin {

    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
    private void normalnpcplayer$allowBabyHeadPassenger(
            Entity passenger,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!((Object) this instanceof Player player)) {
            return;
        }

        if (!(passenger instanceof BabyNPCPlayerEntity baby)) {
            return;
        }

        // One Baby head passenger at a time. Re-checking the same passenger is
        // allowed so the normal Entity mounting pipeline remains idempotent.
        cir.setReturnValue(
                player.getPassengers().isEmpty()
                        || player.hasPassenger(baby)
        );
    }

    @Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void normalnpcplayer$positionBabyOnHead(
            Entity passenger,
            CallbackInfo ci
    ) {
        if (!((Object) this instanceof Player player)) {
            return;
        }

        if (!(passenger instanceof BabyNPCPlayerEntity baby)
                || !player.hasPassenger(baby)) {
            return;
        }

        float yawRadians = player.getYRot() * ((float) Math.PI / 180.0F);
        double x = player.getX()
                + Math.sin(yawRadians) * BabyNPCPlayerEntity.HEAD_RIDE_BACK_OFFSET;
        double y = player.getY()
                + player.getBbHeight()
                + BabyNPCPlayerEntity.HEAD_RIDE_OFFSET;
        double z = player.getZ()
                - Math.cos(yawRadians) * BabyNPCPlayerEntity.HEAD_RIDE_BACK_OFFSET;

        passenger.setPos(x, y, z);
        passenger.setYRot(player.getYRot());
        passenger.setYHeadRot(player.getYRot());
        if (passenger instanceof LivingEntity living) {
            living.setYBodyRot(player.getYRot());
        }

        ci.cancel();
    }
}
