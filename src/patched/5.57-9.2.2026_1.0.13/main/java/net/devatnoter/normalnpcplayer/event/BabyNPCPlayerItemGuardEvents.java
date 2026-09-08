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

        /*
         * Never let a Baby carrier remain in the main inventory.
         */
        for (int i = 9; i < 36; i++) {
            ItemStack stack =
                    player.getInventory().getItem(i);

            if (!BabyNPCPlayerItem.isBabyStack(stack)) {
                continue;
            }

            int hotbar = findEmptyHotbar(player);

            if (hotbar >= 0) {
                player.getInventory().setItem(
                        hotbar,
                        stack.copy()
                );
                player.getInventory().setItem(
                        i,
                        ItemStack.EMPTY
                );
            } else {
                /*
                 * If the hotbar is full, swap with the selected hotbar slot
                 * rather than leaving the Baby inside the main inventory.
                 */
                int selected = player.getInventory().selected;
                ItemStack oldHotbar =
                        player.getInventory().getItem(selected);

                player.getInventory().setItem(
                        selected,
                        stack.copy()
                );
                player.getInventory().setItem(i, oldHotbar);
            }
        }

        /*
         * While Baby is in MAIN_HAND, keep that hotbar slot selected.
         * F can move the Baby to OFF_HAND, after which this lock disappears.
         */
        ItemStack main = player.getMainHandItem();

        if (BabyNPCPlayerItem.isBabyStack(main)) {
            int carrierSlot = findCarrierHotbar(player);

            if (carrierSlot >= 0) {
                player.getInventory().selected =
                        carrierSlot;
            }
        }
    }

    private static int findEmptyHotbar(Player player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    private static int findCarrierHotbar(Player player) {
        for (int i = 0; i < 9; i++) {
            if (BabyNPCPlayerItem.isBabyStack(
                    player.getInventory().getItem(i)
            )) {
                return i;
            }
        }

        return -1;
    }
}
