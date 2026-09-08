package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
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

        // Diagnostic only: this method is reached when the container click
        // actually reaches AbstractContainerMenu (normally on the server
        // after the client sends the container-click packet).
        if (!player.level().isClientSide) {
            Slot debugSource = null;
            if (slotId >= 0 && slotId < menu.slots.size()) {
                debugSource = menu.slots.get(slotId);
            }
            ItemStack debugItem = debugSource == null ? ItemStack.EMPTY : debugSource.getItem();
            NormalNPCPlayer.LOGGER.info(
                    "[BABY-CONTAINER-DEBUG] clicked SERVER | player={} menu={} slotId={} button={} clickType={} source={} containerSlot={} item={} count={} carried={} selected={} main={} offhand={}",
                    player.getGameProfile().getName(),
                    menu.getClass().getSimpleName(),
                    slotId,
                    button,
                    clickType,
                    debugSource == null ? "null" : debugSource.getClass().getSimpleName(),
                    debugSource == null ? -1 : debugSource.getContainerSlot(),
                    debugItem.getItem(),
                    debugItem.getCount(),
                    menu.getCarried().getItem(),
                    player.getInventory().selected,
                    player.getMainHandItem().getItem(),
                    player.getOffhandItem().getItem()
            );
            if (!debugItem.isEmpty() && BabyNPCPlayerItem.isBabyStack(debugItem)) {
                NormalNPCPlayer.LOGGER.info(
                        "[BABY-CONTAINER-DEBUG] clicked SERVER -> THIS IS BABY | decision=BabyItemContainerMixin will evaluate this click"
                );
            }
        }

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
                && BabyNPCPlayerItem.isBabyStack(player.getOffhandItem())) {
            /*
             * If the Baby is in OFF_HAND, F may move it into an EMPTY hotbar
             * slot. This is the deliberate way to free the left hand without
             * stealing/replacing another hotbar item.
             */
            if (source != null
                    && source.container instanceof Inventory
                    && source.getContainerSlot() >= 0
                    && source.getContainerSlot() < 9
                    && source.getItem().isEmpty()) {
                return;
            }

            notifyLockedBaby(player);
            ci.cancel();
            return;
        }

        if (clickType == ClickType.SWAP && button == 40
                && BabyNPCPlayerItem.isBabyStack(player.getMainHandItem())) {
            /*
             * Main-hand Baby: the normal F swap remains allowed so the Baby
             * can be moved into OFF_HAND. The carrier slot tag is cleared
             * immediately after the vanilla swap below.
             */
            if (isAllowedMainHandF(source, clickType, button, player)) {
                return;
            }

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

    /**
     * Keep the remembered carrier slot synchronized with the actual hand.
     *
     * MAIN_HAND Baby -> remember the selected hotbar slot.
     * OFF_HAND Baby -> forget the hotbar lock (-1), so an empty hotbar slot
     * can receive it on a later F swap.
     */
    @Inject(
            method = "clicked",
            at = @At("RETURN")
    )
    private void normalnpcplayer$syncBabyCarrierSlotAfterSwap(
            int slotId,
            int button,
            ClickType clickType,
            Player player,
            CallbackInfo ci
    ) {
        if (clickType != ClickType.SWAP || button != 40 || player == null) {
            return;
        }

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        if (BabyNPCPlayerItem.isBabyStack(main)) {
            main.getOrCreateTag().putInt(
                    BabyNPCPlayerItem.CARRIER_SLOT_TAG,
                    player.getInventory().selected
            );
            player.getInventory().setChanged();
        } else if (BabyNPCPlayerItem.isBabyStack(off)) {
            off.getOrCreateTag().putInt(
                    BabyNPCPlayerItem.CARRIER_SLOT_TAG,
                    -1
            );
            player.getInventory().setChanged();
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
