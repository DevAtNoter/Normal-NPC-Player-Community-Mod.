package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class)
public abstract class BabyItemContainerMixin {

    /**
     * First line of defense:
     * stop Shift + Click before the menu-specific quickMoveStack() runs.
     */
    @Inject(
            method = "clicked",
            at = @At("HEAD"),
            cancellable = true
    )
    private void normalnpcplayer$blockBabyQuickMove(
            int slotId,
            int button,
            ClickType clickType,
            Player player,
            CallbackInfo ci
    ) {
        if (clickType != ClickType.QUICK_MOVE) {
            return;
        }

        AbstractContainerMenu menu =
                (AbstractContainerMenu) (Object) this;

        if (slotId < 0
                || slotId >= menu.slots.size()) {
            return;
        }

        Slot source =
                menu.slots.get(slotId);

        if (BabyNPCPlayerItem.isBabyStack(
                source.getItem()
        )) {
            ci.cancel();
        }
    }

    /**
     * Final/common transfer guard:
     *
     * Any menu that reaches moveItemStackTo() with a Baby stack is denied.
     *
     * This is the important part for menus whose quickMoveStack()
     * implementation performs its own routing.
     */
    @Inject(
            method = "moveItemStackTo",
            at = @At("HEAD"),
            cancellable = true
    )
    private void normalnpcplayer$blockBabyBulkMove(
            ItemStack stack,
            int startIndex,
            int endIndex,
            boolean reverseDirection,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (BabyNPCPlayerItem.isBabyStack(stack)) {
            cir.setReturnValue(false);
        }
    }
}