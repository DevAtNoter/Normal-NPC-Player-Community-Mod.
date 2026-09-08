package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.capability.PlayerFamilyCapability;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Reconnects a saved Baby to its Player after the normal Minecraft entity
 * loading/login lifecycle has completed.
 *
 * Important: this class never writes Player NBT and never changes the Player
 * capability format. The only persistent state it reads is the Baby's own
 * NPPNeckRide flag, which represents only the NECK_RIDE lifecycle.
 * INITIAL rides are intentionally invisible to this restore path.
 */
@Mod.EventBusSubscriber(modid = NormalNPCPlayer.MOD_ID)
public final class BabyHeadRideRestoreEvents {

    private static final int RETRY_TICKS = 100;
    private static final Map<UUID, PendingRestore> PENDING = new HashMap<>();

    private BabyHeadRideRestoreEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Do not attempt the relink inside PlayerLoggedInEvent itself. At that
        // point the saved Baby's chunk/entity may not yet be available and the
        // Player's normal login/bootstrap lifecycle is still running.
        PENDING.put(
                player.getUUID(),
                new PendingRestore(RETRY_TICKS)
        );
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, PendingRestore>> iterator = PENDING.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingRestore> entry = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());

            if (player == null || !player.isAlive()) {
                iterator.remove();
                continue;
            }

            PendingRestore pending = entry.getValue();
            BabyNPCPlayerEntity baby = findPersistentBaby(player);

            if (baby != null && !baby.isRemoved()) {
                if (baby.getVehicle() == player && baby.isNeckRide()) {
                    // Already linked in PERSISTENT mode. Nothing else to do.
                    iterator.remove();
                    continue;
                }

                // A saved entity can retain its NECK_RIDE lifecycle marker while
                // the Player vehicle relationship is necessarily absent after
                // world serialization. Rebuild ONLY that runtime relationship.
                //
                // This is deliberately different from beginInitialRide():
                // restored/persistent riding has no movement-triggered
                // dismount and therefore must remain attached while the Player
                // walks.
                if (baby.getVehicle() != player) {
                    baby.stopRiding();
                }

                baby.beginNeckRide(player);

                if (baby.getVehicle() == player && baby.isNeckRide()) {
                    NormalNPCPlayer.LOGGER.info(
                            "Restored neck-riding Baby {} -> Player {} (NECK_RIDE mode)",
                            baby.getUUID(),
                            player.getGameProfile().getName()
                    );

                    iterator.remove();
                    continue;
                }
            }

            pending.remainingTicks--;
            if (pending.remainingTicks <= 0) {
                NormalNPCPlayer.LOGGER.warn(
                        "Could not relink neck-riding Baby to Player {} within {} ticks; leaving Player data untouched.",
                        player.getGameProfile().getName(),
                        RETRY_TICKS
                );
                iterator.remove();
            }
        }
    }

    private static BabyNPCPlayerEntity findPersistentBaby(ServerPlayer player) {
        // Fast/authoritative path: the family capability already stores the
        // Baby entity UUID. This is only read after login, never rewritten.
        var capability = player.getCapability(PlayerFamilyCapability.PLAYER_FAMILY);
        if (capability.isPresent()) {
            // getChildUUID() is legitimately null when the player has no
            // persistent child. Do not use LazyOptional.map() here: its mapper
            // must not return null, otherwise Forge throws an NPE internally.
            UUID childUUID = capability.resolve()
                    .map(data -> data.getChildUUID())
                    .orElse(null);
            if (childUUID != null) {
                var entity = player.serverLevel().getEntity(childUUID);
                if (entity instanceof BabyNPCPlayerEntity baby
                        && !baby.isRemoved()
                        && player.getUUID().equals(baby.getOwnerUUID())
                        && baby.isNeckRide()) {
                    return baby;
                }
            }
        }

        // Fallback for a saved Baby whose capability UUID is temporarily not
        // resolvable while entity/chunk loading catches up.
        return player.serverLevel().getEntitiesOfClass(
                        BabyNPCPlayerEntity.class,
                        player.getBoundingBox().inflate(128.0D),
                        baby -> !baby.isRemoved()
                                && player.getUUID().equals(baby.getOwnerUUID())
                                && baby.isNeckRide()
                )
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static final class PendingRestore {
        private int remainingTicks;

        private PendingRestore(int remainingTicks) {
            this.remainingTicks = remainingTicks;
        }
    }
}
