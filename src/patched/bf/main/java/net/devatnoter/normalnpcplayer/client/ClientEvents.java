package net.devatnoter.normalnpcplayer.client;

import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = "normalnpcplayer",
        value = net.minecraftforge.api.distmarker.Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class ClientEvents {

    private ClientEvents() {
    }

    /**
     * GAMEPLAY HUD RULE:
     *
     * If a Baby carrier exists in the hotbar, Baby owns MAIN_HAND.
     * Normal 1-9 selection still happens, but this restores the Baby slot.
     *
     * Q is consumed here at the START of the client tick, before the normal
     * gameplay keybind processing can call LocalPlayer.drop().
     *
     * The check is intentionally disabled while a Screen is open, so the
     * Inventory GUI keeps its already-working Q behavior.
     */
    @SubscribeEvent
    public static void onClientTick(
            TickEvent.ClientTickEvent event
    ) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        /*
         * Inventory/container GUI: do not touch Q here.
         */
        if (minecraft.screen != null) {
            return;
        }

        int babySlot = -1;

        for (int slot = 0; slot < 9; slot++) {
            if (BabyNPCPlayerItem.isBabyStack(
                    minecraft.player.getInventory()
                            .getItem(slot)
            )) {
                babySlot = slot;
                break;
            }
        }

        if (babySlot < 0) {
            return;
        }

        /*
         * Baby in the hotbar always has MAIN_HAND priority.
         */
        if (!BabyNPCPlayerItem.isBabyStack(
                minecraft.player.getItemInHand(
                        InteractionHand.OFF_HAND
                )
        )) {
            minecraft.player.getInventory().selected =
                    babySlot;
        }

        /*
         * Consume Q only when Baby is actually in MAIN_HAND.
         * This happens BEFORE LocalPlayer.drop() is reached.
         */
        if (BabyNPCPlayerItem.isBabyStack(
                minecraft.player.getItemInHand(
                        InteractionHand.MAIN_HAND
                )
        )) {
            while (
                    minecraft.options.keyDrop.consumeClick()
            ) {
                // Never remove the Baby from the hotbar.
            }
        }
    }
}
