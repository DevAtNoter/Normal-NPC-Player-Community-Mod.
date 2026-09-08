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

        if (baby.getOwnerUUID() == null
                || !baby.getOwnerUUID()
                        .equals(player.getUUID())) {
            return;
        }

        if (!player.getItemInHand(event.getHand()).isEmpty()) {
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
