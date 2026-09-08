package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class BabyItemSlotMixin {

    @Shadow
    @Final
    protected Container container;

    /**
     * External inventory slots can never accept Baby.
     */
    @Inject(
            method = "mayPlace",
            at = @At("HEAD"),
            cancellable = true
    )
    private void normalnpcplayer$rejectBabyPlace(
            ItemStack stack,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!BabyNPCPlayerItem.isBabyStack(stack)) {
            return;
        }

        if (!(container instanceof Inventory)) {
            cir.setReturnValue(false);
        }
    }
}
