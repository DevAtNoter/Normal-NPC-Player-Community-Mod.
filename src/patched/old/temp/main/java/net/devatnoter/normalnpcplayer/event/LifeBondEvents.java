package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class LifeBondEvents {

    private LifeBondEvents() {
    }

    /**
     * Player -> Baby
     *
     * When the owner dies, kill the bonded baby as well.
     *
     * We scan loaded entities on every server level so the baby does not
     * need to be close to the player and it may be in another dimension.
     *
     * Unloaded chunks do not contain active entity instances, so nothing
     * is forced to load merely because of Life Bond.
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID ownerUUID = player.getUUID();

        for (var level : player.server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (!(entity instanceof BabyNPCPlayerEntity baby)) {
                    continue;
                }

                if (ownerUUID.equals(baby.getOwnerUUID()) && baby.isAlive()) {
                    baby.killFromLifeBond();
                }
            }
        }
    }

    /**
     * Baby -> Player
     *
     * Called by BabyNPCPlayerEntity.die().
     *
     * Kept here so the Life Bond implementation is centralized instead
     * of putting player-death event logic inside the entity itself.
     */
    public static void killOwner(ServerPlayer owner) {
        if (owner == null || !owner.isAlive()) {
            return;
        }

        owner.hurt(
                owner.serverLevel().damageSources().genericKill(),
                Float.MAX_VALUE
        );
    }
}
