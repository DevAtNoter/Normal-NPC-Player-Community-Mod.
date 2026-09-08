package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Sneak + right-click on the TOP face of a block while carrying a Baby on
 * the player's head: put the Baby down. This is server-authoritative and
 * therefore syncs the real passenger relationship to all clients.
 */
@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class BabyHeadDismountEvents {

    private BabyHeadDismountEvents() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!player.isShiftKeyDown() || event.getFace() != Direction.UP) {
            return;
        }

        BabyNPCPlayerEntity baby = null;
        for (var passenger : player.getPassengers()) {
            if (passenger instanceof BabyNPCPlayerEntity candidate) {
                baby = candidate;
                break;
            }
        }

        if (baby == null) {
            return;
        }

        baby.stopRiding();
        event.setCanceled(true);
    }
}
