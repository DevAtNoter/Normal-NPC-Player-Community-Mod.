package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Player-specific seat positioning for Baby passengers.
 *
 * <p>The one-argument Entity#positionRider(Entity) method is final in 1.20.1.
 * Vanilla delegates the actual movement to the protected two-argument overload
 * using Entity.MoveFunction. We hook that overload so the normal passenger
 * lifecycle remains vanilla while NNP controls only the Baby's seat position.</p>
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

        cir.setReturnValue(
                player.getPassengers().isEmpty()
                        || player.hasPassenger(baby)
        );
    }

    /**
     * This is the actual passenger movement hook used by Entity#positionRider(Entity)
     * in Minecraft 1.20.1. Do not replace it with a tick-based setPos loop.
     */
    @Inject(
            method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void normalnpcplayer$positionBabyOnTopFace(
            Entity passenger,
            Entity.MoveFunction moveFunction,
            CallbackInfo ci
    ) {
        if (!((Object) this instanceof Player player)) {
            return;
        }

        if (!(passenger instanceof BabyNPCPlayerEntity baby)
                || !player.hasPassenger(baby)) {
            return;
        }

        /*
         * The reference point is the actual TOP FACE of the Player's
         * bounding box, not a semantic "head offset".
         *
         * Entity#getY() is the bottom of the bounding box in 1.20.1, so:
         *
         *     topFaceY = player.getY() + player.getBbHeight()
         *
         * Baby#getY() is likewise its feet/bottom of its bounding box.
         * Therefore placing Baby at topFaceY makes its feet sit directly
         * on the Player's top face. HEAD_RIDE_OFFSET is retained only as
         * an explicit vertical clearance control.
         */
        final double topFaceY = player.getY() + player.getBbHeight();
        final double y = topFaceY + BabyNPCPlayerEntity.HEAD_RIDE_OFFSET;

        // Optional offsets are local to the Player's facing direction.
        final double forward = BabyNPCPlayerEntity.HEAD_RIDE_FORWARD_OFFSET;
        final double side = BabyNPCPlayerEntity.HEAD_RIDE_SIDE_OFFSET;
        final double yawRadians = Math.toRadians(player.getYRot());

        // Minecraft forward = (-sin(yaw), cos(yaw)).
        final double x = player.getX()
                - Math.sin(yawRadians) * forward
                + Math.cos(yawRadians) * side;
        final double z = player.getZ()
                + Math.cos(yawRadians) * forward
                + Math.sin(yawRadians) * side;

        // Keep the real passenger MoveFunction; no tick-based follow loop.
        moveFunction.accept(passenger, x, y, z);

        passenger.setYRot(player.getYRot());
        passenger.setYHeadRot(player.getYRot());
        if (passenger instanceof net.minecraft.world.entity.LivingEntity living) {
            living.setYBodyRot(player.getYRot());
        }

        ci.cancel();
    }
}
