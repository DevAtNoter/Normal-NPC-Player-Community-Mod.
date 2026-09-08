package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = NormalNPCPlayer.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BabyPlayerStateDebugEvents {
    private static final Map<UUID, String> LAST = new HashMap<>();

    private BabyPlayerStateDebugEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        String state = buildState(player);
        UUID id = player.getUUID();
        String previous = LAST.put(id, state);
        if (state.equals(previous)) return;

        NormalNPCPlayer.LOGGER.info(
                "[BABY-PLAYER-STATE] CHANGE | player={} tick={} {}",
                player.getGameProfile().getName(),
                player.level().getGameTime(),
                state
        );
    }

    private static String buildState(ServerPlayer player) {
        boolean mainBaby = BabyNPCPlayerItem.isBabyStack(player.getMainHandItem());
        boolean offBaby = BabyNPCPlayerItem.isBabyStack(player.getOffhandItem());
        String vehicle = player.isPassenger() ? player.getVehicle().getClass().getSimpleName() : "none";
        String passenger = player.getPassengers().isEmpty() ? "none" : Integer.toString(player.getPassengers().size());
        return "gamemode=" + player.gameMode.getGameModeForPlayer()
                + " mainBaby=" + mainBaby
                + " offhandBaby=" + offBaby
                + " selected=" + player.getInventory().selected
                + " vehicle=" + vehicle
                + " passengers=" + passenger
                + " alive=" + player.isAlive()
                + " spectator=" + player.isSpectator()
                + " container=" + player.containerMenu.getClass().getSimpleName()
                + " containerId=" + player.containerMenu.containerId
                + " carried=" + player.containerMenu.getCarried().getItem();
    }
}
