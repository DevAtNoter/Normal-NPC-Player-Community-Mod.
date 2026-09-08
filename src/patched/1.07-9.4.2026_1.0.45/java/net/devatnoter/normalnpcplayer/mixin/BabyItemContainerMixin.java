package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
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
     * Inventory/container hard lock for Baby Items.
     *
     * The Baby may not be picked up, placed, quick-moved, dragged, collected,
     * or swapped with another slot. The only vanilla SWAP route that remains
     * valid is F (button 40) when the hovered source is the Baby in MAIN_HAND.
     * Offhand-Baby F is handled by the dedicated client/server hand-swap packet.
     */
    @Inject(
            method = "clicked",
            at = @At("HEAD"),
            cancellable = true
    )
    private void normalnpcplayer$blockBabyInventoryMoves(
            int slotId,
            int button,
            ClickType clickType,
            Player player,
            CallbackInfo ci
    ) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;

        Slot source = null;
        if (slotId >= 0 && slotId < menu.slots.size()) {
            source = menu.slots.get(slotId);
        }

        // Any operation directly targeting a Baby is denied, except F on the
        // currently selected MAIN_HAND slot. That is the one intended escape.
        if (source != null && BabyNPCPlayerItem.isBabyStack(source.getItem())) {
            if (isAllowedMainHandF(source, clickType, button, player)) {
                return;
            }

            notifyLockedBaby(player);
            ci.cancel();
            return;
        }

        // F is only a Baby-hand shortcut. If a Baby is being carried in either
        // hand, pressing F on another inventory slot must never move that item
        // into/out of the Baby's locked position.
        if (clickType == ClickType.SWAP && button == 40
                && (BabyNPCPlayerItem.isBabyStack(player.getMainHandItem())
                || BabyNPCPlayerItem.isBabyStack(player.getOffhandItem()))) {
            notifyLockedBaby(player);
            ci.cancel();
            return;
        }

        // Never allow a Baby Item to exist on the menu cursor. This covers
        // pickup/drag/double-click/quick-craft routes that reach the menu.
        if (BabyNPCPlayerItem.isBabyStack(menu.getCarried())) {
            notifyLockedBaby(player);
            ci.cancel();
        }
    }

    private static boolean isAllowedMainHandF(
            Slot source,
            ClickType clickType,
            int button,
            Player player
    ) {
        if (clickType != ClickType.SWAP || button != 40) return false;
        if (!(source.container instanceof Inventory)) return false;

        int slot = source.getContainerSlot();
        return slot >= 0 && slot < 9
                && slot == player.getInventory().selected
                && BabyNPCPlayerItem.isBabyStack(player.getMainHandItem());
    }

    /**
     * Final/common transfer guard. Any menu that reaches moveItemStackTo() with
     * a Baby stack is denied.
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

    private static void notifyLockedBaby(Player player) {
        if (player.level().isClientSide) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "You're carrying a Baby. Press (F) to switch hands and free your hand."
                    ),
                    true
            );
        }
    }
}
