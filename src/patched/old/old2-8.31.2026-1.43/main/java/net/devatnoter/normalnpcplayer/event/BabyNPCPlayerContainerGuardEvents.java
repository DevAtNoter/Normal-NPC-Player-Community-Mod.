package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class BabyNPCPlayerContainerGuardEvents {

    private BabyNPCPlayerContainerGuardEvents() {
    }

    /**
     * Server-authoritative safety net.
     *
     * If any container path (shift-click, drag, shortcut, custom menu logic,
     * etc.) manages to place Baby into an external Slot, immediately remove
     * it from that Slot and return it to the player's hotbar.
     *
     * This intentionally makes the result feel like the item "bounces back".
     */
    @SubscribeEvent
    public static void onPlayerTick(
            TickEvent.PlayerTickEvent event
    ) {
        if (event.phase != TickEvent.Phase.END
                || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        if (player.containerMenu == null) {
            return;
        }

        Inventory inventory = player.getInventory();

        for (Slot slot : player.containerMenu.slots) {

            Container container = slot.container;

            if (container == inventory) {
                continue;
            }

            ItemStack stack = slot.getItem();

            if (!BabyNPCPlayerItem.isBabyStack(stack)) {
                continue;
            }

            /*
             * Remove from the external container first.
             */
            slot.set(ItemStack.EMPTY);

            /*
             * Return the EXACT stack to the hotbar.
             */
            if (!putIntoHotbar(player, stack.copy())) {
                /*
                 * There is no valid hotbar slot. Keep it in the player's
                 * selected hand rather than losing it.
                 */
                player.getInventory().setItem(
                        player.getInventory().selected,
                        stack.copy()
                );
            }
        }

        player.containerMenu.broadcastChanges();
    }

    private static boolean putIntoHotbar(
            ServerPlayer player,
            ItemStack stack
    ) {
        Inventory inventory = player.getInventory();

        int selected = inventory.selected;

        if (inventory.getItem(selected).isEmpty()) {
            inventory.setItem(selected, stack);
            return true;
        }

        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i).isEmpty()) {
                inventory.setItem(i, stack);
                return true;
            }
        }

        /*
         * Baby is already in hotbar; do not overwrite normal player items.
         */
        for (int i = 0; i < 9; i++) {
            if (BabyNPCPlayerItem.isBabyStack(
                    inventory.getItem(i)
            )) {
                return true;
            }
        }

        return false;
    }}
