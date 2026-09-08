package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemStackedOnOtherEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class BabyNPCPlayerItemGuardEvents {

    private BabyNPCPlayerItemGuardEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onItemStackedOnOther(
            ItemStackedOnOtherEvent event
    ) {
        if (BabyNPCPlayerItem.isBabyStack(event.getCarriedItem())
                || BabyNPCPlayerItem.isBabyStack(
                        event.getStackedOnItem()
                )) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(
            TickEvent.PlayerTickEvent event
    ) {
        if (event.phase != TickEvent.Phase.END
                || event.player.level().isClientSide) {
            return;
        }

        Player player = event.player;
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();

        // OFF_HAND has no hotbar slot. Once the Baby is carried there,
        // forget the old pickup slot so an empty hotbar slot may receive it.
        ItemStack offhand = player.getOffhandItem();
        if (BabyNPCPlayerItem.isBabyStack(offhand)
                && offhand.hasTag()
                && offhand.getTag().contains(BabyNPCPlayerItem.CARRIER_SLOT_TAG, 3)) {
            if (offhand.getTag().getInt(BabyNPCPlayerItem.CARRIER_SLOT_TAG) != -1) {
                offhand.getOrCreateTag().putInt(
                        BabyNPCPlayerItem.CARRIER_SLOT_TAG,
                        -1
                );
                inventory.setChanged();
            }
        }

        /*
         * A carried Baby is physically locked to the exact hotbar slot from
         * which it was picked up. If some packet/container path manages to
         * move it elsewhere, swap it straight back instead of searching for
         * another free slot. This preserves the player's original layout.
         */
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!BabyNPCPlayerItem.isBabyStack(stack)) {
                continue;
            }

            // OFF_HAND is intentionally outside the hotbar lock. F may move
            // the Baby there so the main hand becomes free.
            if (i == 40) {
                continue;
            }

            int lockedSlot = getLockedSlot(stack, i, inventory);
            if (lockedSlot < 0 || lockedSlot >= 9) {
                continue;
            }

            if (i != lockedSlot) {
                ItemStack displaced = inventory.getItem(lockedSlot);
                inventory.setItem(lockedSlot, stack.copy());
                inventory.setItem(i, displaced);
                i = lockedSlot;
            }
        }

        ItemStack main = player.getMainHandItem();
        if (BabyNPCPlayerItem.isBabyStack(main)) {
            int lockedSlot = getLockedSlot(
                    main,
                    inventory.selected,
                    inventory
            );
            if (lockedSlot >= 0 && inventory.selected != lockedSlot) {
                inventory.selected = lockedSlot;
                inventory.setChanged();
            }
        }
    }

    private static int getLockedSlot(
            ItemStack stack,
            int fallback,
            net.minecraft.world.entity.player.Inventory inventory
    ) {
        if (!BabyNPCPlayerItem.isBabyStack(stack)) {
            return -1;
        }

        if (stack.hasTag()
                && stack.getTag().contains(
                        BabyNPCPlayerItem.CARRIER_SLOT_TAG, 3
                )) {
            int slot = stack.getTag().getInt(
                    BabyNPCPlayerItem.CARRIER_SLOT_TAG
            );
            if (slot >= 0 && slot < 9) {
                return slot;
            }
        }

        // Backward compatibility for Baby Items created before the slot-lock
        // tag existed. A Baby already in the hotbar keeps that exact slot; an
        // old Baby found in the main inventory is restored to the selected
        // hotbar slot rather than being scattered into a random free slot.
        int legacySlot = (fallback >= 0 && fallback < 9)
                ? fallback
                : inventory.selected;
        if (legacySlot >= 0 && legacySlot < 9) {
            stack.getOrCreateTag().putInt(
                    BabyNPCPlayerItem.CARRIER_SLOT_TAG,
                    legacySlot
            );
            inventory.setChanged();
            return legacySlot;
        }

        return -1;
    }

}
