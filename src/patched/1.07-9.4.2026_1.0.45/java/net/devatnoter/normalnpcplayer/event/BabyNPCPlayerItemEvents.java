package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.advancement.AdvancementManager;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class BabyNPCPlayerItemEvents {

    private BabyNPCPlayerItemEvents() {
    }

    @SubscribeEvent
    public static void onBabyRightClick(
            PlayerInteractEvent.EntityInteractSpecific event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!(event.getTarget()
                instanceof BabyNPCPlayerEntity baby)) {
            return;
        }

        if (!player.getItemInHand(event.getHand()).isEmpty()) {
            return;
        }

        /*
         * Deliberate foster action:
         * Shift-right-clicking an orphaned Baby with an empty hand transfers
         * the current Owner to this player and marks Foster=1b.
         * Non-orphans can never be fostered.
         */
        if (baby.isOrphaned()) {
            if (!player.isShiftKeyDown()) {
                return;
            }

            if (baby.fosterTo(player)) {
                BabyNPCPlayerEntity.playFamilyHearts(player.serverLevel(), baby);
                AdvancementManager.grantSecondChild(player);

                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                                "This Baby is now under foster care of " + player.getGameProfile().getName() + "."
                        ),
                        true
                );
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }

        boolean authorized = baby.getOwnerUUID() != null
                && baby.getOwnerUUID().equals(player.getUUID());
        if (baby.isPlayerBabyOwner(player)) {
            authorized = true;
        }

        if (!authorized) {
            return;
        }

        // Shift + right-click by the owner is the head-ride action, NOT pickup.
        // This event fires before the entity's mobInteract path, so handle the
        // mount here explicitly; otherwise the normal pickup code would consume
        // the click and mobInteract() would never see it.
        if (player.isShiftKeyDown()) {
            baby.mountOnHead(player);
            if (baby.isInitialRideActive()) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }

        ItemStack carrier =
                BabyNPCPlayerItem.createFromEntity(baby);

        // Freeze the exact hotbar position where the Baby was picked up.
        // MAIN_HAND corresponds to the currently selected hotbar slot;
        // OFF_HAND has no hotbar slot of its own and uses -1.
        carrier.getOrCreateTag().putInt(
                BabyNPCPlayerItem.CARRIER_SLOT_TAG,
                event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                        ? player.getInventory().selected
                        : -1
        );

        player.setItemInHand(
                event.getHand(),
                carrier
        );

        /*
         * End head state before removing the Entity. Reset gravity,
         * velocity and fall distance so none of the old physics is retained.
         */
        baby.clearInitialRideState();

        baby.discard();

        event.setCanceled(true);
        event.setCancellationResult(
                InteractionResult.SUCCESS
        );
    }
}
