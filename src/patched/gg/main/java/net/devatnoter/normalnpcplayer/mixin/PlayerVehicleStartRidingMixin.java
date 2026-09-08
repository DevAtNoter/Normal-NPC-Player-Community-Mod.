package net.devatnoter.normalnpcplayer.mixin;

import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Minecraft 1.20.1 hard-rejects Player as a vehicle inside Entity.startRiding
 * because EntityType.PLAYER.canSerialize() is false. This blocks both the
 * server-side mount call and, critically, the client-side handling of
 * ClientboundSetPassengersPacket.
 *
 * We only change the canSerialize check inside startRiding; the normal
 * passenger list, vehicle link, rideTick(), and positionRider() pipeline stay
 * vanilla. PlayerHeadPassengerMixin separately limits which passengers a
 * Player may accept.
 */
@Mixin(net.minecraft.world.entity.Entity.class)
public abstract class PlayerVehicleStartRidingMixin {

    @Redirect(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z"
            )
    )
    private boolean normalnpcplayer$allowPlayerVehicle(EntityType<?> type) {
        if (type == EntityType.PLAYER) {
            return true;
        }
        return type.canSerialize();
    }
}
