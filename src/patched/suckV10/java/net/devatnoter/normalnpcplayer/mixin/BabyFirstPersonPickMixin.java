package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents the Baby riding the local player from becoming the local player's
 * entity-raycast target in first person.
 *
 * This is client-only. The Baby remains pickable for other players and the
 * server still treats the Baby as a normal entity. This fixes the classic
 * first-person problem where a passenger sitting in front of the camera
 * steals the crosshair hit result.
 */
@Mixin(BabyNPCPlayerEntity.class)
public abstract class BabyFirstPersonPickMixin {

    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void normalnpcplayer$ignoreLocalHeadPassenger(
            CallbackInfoReturnable<Boolean> cir
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        BabyNPCPlayerEntity baby = (BabyNPCPlayerEntity) (Object) this;
        if (baby.getVehicle() == minecraft.player
                && minecraft.player.hasPassenger(baby)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
