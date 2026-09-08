package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.advancement.AdvancementManager;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NormalNPCPlayer.MOD_ID)
public final class PlayerAttackEvents {

    private PlayerAttackEvents() {}

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!(event.getTarget() instanceof BabyNPCPlayerEntity baby)) {
            return;
        }

        /*
         * A Baby riding this Player is inside the Player's own first-person
         * interaction space. It must not become the local Player's attack
         * target. This is an interaction filter only; the Baby remains a
         * normal, damageable entity for every other attacker.
         *
         * Do this server-side as the authoritative backstop for the client
         * raycast filter. Without this guard, an already-selected Baby can
         * still arrive through the attack packet and trigger damage/events.
         */
        if (baby.getVehicle() == player && player.hasPassenger(baby)) {
            event.setCanceled(true);
            return;
        }

        if (baby.getOwnerUUID() == null || !baby.getOwnerUUID().equals(player.getUUID())) {
            return;
        }

        AdvancementManager.grantWhyHitBabyForNoReason(player);
    }
}
