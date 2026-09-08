package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class BabyItemSlotTakeMixin {

    /**
     * Final extraction guard.
     *
     * Any vanilla/container route that attempts to take Baby from a Slot
     * is denied. This is the last line of defense against Baby disappearing
     * into a container's transfer logic.
     */
    @Inject(
            method = "safeTake",
            at = @At("HEAD"),
            cancellable = true
    )
    private void normalnpcplayer$blockBabyTake(
            int amount,
            int maxAmount,
            Player player,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        Slot slot =
                (Slot) (Object) this;

        ItemStack current =
                slot.getItem();

        if (!BabyNPCPlayerItem.isBabyStack(current)) {
            return;
        }

        cir.setReturnValue(
                ItemStack.EMPTY
        );
    }
}
