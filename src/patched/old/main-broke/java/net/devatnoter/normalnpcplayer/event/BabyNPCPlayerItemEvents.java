package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
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

        if (baby.getOwnerUUID() == null
                || !baby.getOwnerUUID()
                        .equals(player.getUUID())) {
            return;
        }

        /*
         * SHIFT + right-click with an empty hand is a direct care action.
         * It feeds exactly 1.5 hunger points instead of picking the Baby up.
         */
        if (player.isShiftKeyDown()
                && player.getItemInHand(event.getHand()).isEmpty()) {
            if (!baby.feedMilk()) {
                return;
            }

            player.level().playSound(
                    null,
                    baby.blockPosition(),
                    net.minecraft.sounds.SoundEvents.GENERIC_EAT,
                    net.minecraft.sounds.SoundSource.NEUTRAL,
                    1.0F,
                    1.0F
                            + (baby.getRandom().nextFloat()
                            - baby.getRandom().nextFloat()) * 0.2F
            );

            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "Baby hunger +1.5"
                    ),
                    true
            );

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        ItemStack carrier =
                BabyNPCPlayerItem.createFromEntity(baby);

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
