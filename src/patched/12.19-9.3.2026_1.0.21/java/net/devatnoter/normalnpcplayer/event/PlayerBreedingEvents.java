package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.breeding.PlayerBreedingManager;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NormalNPCPlayer.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerBreedingEvents {
    private PlayerBreedingEvents() {}

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof ServerPlayer target)) return;
        if (player == target) return;

        // Player Baby hand-to-hand transfer:
        // Either biological parent can take a Player Baby directly from the
        // other parent's hand by right-clicking them with an empty hand.
        // This is intentionally checked before breeding because an empty hand
        // must never be interpreted as a breeding interaction.
        if (handlePlayerBabyHandTransfer(player, target, event.getHand())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        ItemStack stack = player.getItemInHand(event.getHand());
        if (stack.isEmpty() || stack.getFoodProperties(player) == null) return;

        // A player can only use food to start/respond to a breeding session.
        boolean handled;
        if (PlayerBreedingManager.isInSession(player)) {
            handled = PlayerBreedingManager.accept(player, target);
        } else {
            handled = PlayerBreedingManager.start(player, target);
        }

        if (!handled) return;

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static boolean handlePlayerBabyHandTransfer(
            ServerPlayer receiver,
            ServerPlayer holder,
            net.minecraft.world.InteractionHand receiverHand
    ) {
        // Receiver must use an empty hand.
        if (!receiver.getItemInHand(receiverHand).isEmpty()) {
            return false;
        }

        ItemStack babyStack = findPlayerBabyInHand(holder);
        if (!BabyNPCPlayerItem.isBabyStack(babyStack)
                || !isPlayerBabyStack(babyStack)) {
            return false;
        }

        // Both parents are equal owners. The receiver must be one of them,
        // and the current holder must also be authorized to possess the Baby.
        if (!isPlayerBabyOwner(babyStack, receiver)
                || !isPlayerBabyOwner(babyStack, holder)) {
            return false;
        }

        // Prevent accidental overwrite if the receiver's selected hand changed
        // between the interaction event and this server-side operation.
        if (!receiver.getItemInHand(receiverHand).isEmpty()) {
            return false;
        }

        ItemStack transferred = babyStack.copy();
        clearSpecificHand(holder, babyStack);
        receiver.setItemInHand(receiverHand, transferred);

        return true;
    }

    private static ItemStack findPlayerBabyInHand(ServerPlayer player) {
        // Check both hands for the Player Baby specifically.
        // A Hardcore Baby may occupy the other hand and must never block
        // transfer of the Player Baby.
        ItemStack main = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (BabyNPCPlayerItem.isBabyStack(main) && isPlayerBabyStack(main)) {
            return main;
        }

        ItemStack off = player.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND);
        if (BabyNPCPlayerItem.isBabyStack(off) && isPlayerBabyStack(off)) {
            return off;
        }

        return ItemStack.EMPTY;
    }

    private static void clearSpecificHand(ServerPlayer player, ItemStack stack) {
        if (player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND) == stack) {
            player.setItemInHand(
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    ItemStack.EMPTY
            );
        } else if (player.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND) == stack) {
            player.setItemInHand(
                    net.minecraft.world.InteractionHand.OFF_HAND,
                    ItemStack.EMPTY
            );
        }
    }

    private static boolean isPlayerBabyStack(ItemStack stack) {
        var data = BabyNPCPlayerItem.copyEntityData(stack);
        return !data.isEmpty()
                && "PLAYER".equalsIgnoreCase(data.getString("BabyType"));
    }

    private static boolean isPlayerBabyOwner(ItemStack stack, ServerPlayer player) {
        var data = BabyNPCPlayerItem.copyEntityData(stack);
        if (data.isEmpty()) return false;

        java.util.UUID id = player.getUUID();
        return (data.hasUUID("OwnerUUID") && id.equals(data.getUUID("OwnerUUID")))
                || (data.hasUUID("OwnerAUUID") && id.equals(data.getUUID("OwnerAUUID")))
                || (data.hasUUID("OwnerBUUID") && id.equals(data.getUUID("OwnerBUUID")))
                || (data.hasUUID("BiologicalParentAUUID") && id.equals(data.getUUID("BiologicalParentAUUID")))
                || (data.hasUUID("BiologicalParentBUUID") && id.equals(data.getUUID("BiologicalParentBUUID")));
    }

}
