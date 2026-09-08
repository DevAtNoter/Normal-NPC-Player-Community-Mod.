package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Allows the NNP Baby to use Player as a real vanilla passenger vehicle.
 *
 * The passenger relationship remains vanilla. After vanilla has calculated
 * the complete passenger transform, the final Y is replaced through the
 * same MoveFunction used by Entity.positionRider(). This deliberately moves
 * the real Baby Entity/AABB, not just its renderer.
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
     * Vanilla positionRider() first computes its normal passenger position.
     * At TAIL we override that final position for the NNP head-riding Baby.
     *
     * This is intentionally not a renderer transform and does not depend on
     * Player Ladder's camera-only ModifyVariable hook.
     */
    @Inject(
            method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
            at = @At("TAIL")
    )
    private void normalnpcplayer$positionBabyOnHead(
            Entity passenger,
            Entity.MoveFunction moveFunction,
            CallbackInfo ci
    ) {
        Entity vehicle = (Entity) (Object) this;

        if (!(vehicle instanceof Player player)) {
            return;
        }

        if (!(passenger instanceof BabyNPCPlayerEntity baby)) {
            return;
        }

        // Both INITIAL and PERSISTENT modes use the same real passenger
        // positioning path. Their gameplay contracts are separate; the mode
        // itself controls lifecycle behavior, while this check only asks
        // whether the physical head-ride relationship is active.
        if (!baby.isHeadRideActive() || baby.getVehicle() != player) {
            return;
        }

        // Vanilla has already positioned the passenger at its normal rider
        // anchor. Keep that position as the BASE of the head ride, then apply
        // our configurable offset relative to that anchor. This is important:
        // the Baby remains attached to the vanilla passenger/head pipeline
        // instead of replacing it with raw Player X/Y/Z coordinates.
        final double baseX = baby.getX();
        final double baseY = baby.getY();
        final double baseZ = baby.getZ();

        // HEAD_RIDE_OFFSET_X/Y/Z are true world-space offsets relative to
        // the vanilla passenger anchor. Do not rotate them by Player yaw:
        // changing X/Y/Z must move the real Baby Entity in the corresponding
        // world axis, regardless of which direction the Player is facing.
        moveFunction.accept(
                baby,
                baseX + BabyNPCPlayerEntity.HEAD_RIDE_OFFSET_X,
                baseY + BabyNPCPlayerEntity.HEAD_RIDE_OFFSET_Y,
                baseZ + BabyNPCPlayerEntity.HEAD_RIDE_OFFSET_Z
        );

        // A head passenger is stationary relative to the Player.
        baby.setDeltaMovement(0.0D, 0.0D, 0.0D);
        baby.setNoGravity(true);
        baby.setYRot(player.getYRot());
        baby.setXRot(0.0F);
        baby.yHeadRot = player.getYRot();
        baby.yBodyRot = player.getYRot();
    }
}
