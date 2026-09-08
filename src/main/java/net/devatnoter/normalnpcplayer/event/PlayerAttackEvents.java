package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.advancement.AdvancementManager;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.equipment.BabyCombatController;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Player combat intent used by Baby Combat Mode. */
@Mod.EventBusSubscriber(modid = NormalNPCPlayer.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerAttackEvents {

    private PlayerAttackEvents() {}

    /**
     * Direct melee attack: the Mob the Player actually clicked becomes the
     * Baby's explicit "attack my target" priority target.
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (event.getTarget() instanceof BabyNPCPlayerEntity baby) {
            /*
             * A Baby riding this Player is inside the Player's own first-person
             * interaction space. It must not become the local Player's attack
             * target.
             */
            if (baby.getVehicle() == player && player.hasPassenger(baby)) {
                event.setCanceled(true);
                return;
            }

            if (baby.getOwnerUUID() == null
                    && !baby.isPlayerBabyOwner(player)) {
                return;
            }
            if (baby.getOwnerUUID() != null
                    && !baby.getOwnerUUID().equals(player.getUUID())
                    && !baby.isPlayerBabyOwner(player)) {
                return;
            }

            AdvancementManager.grantWhyHitBabyForNoReason(player);
            return;
        }

        if (event.getTarget() instanceof Mob mob && mob.isAlive()) {
            BabyCombatController.setPlayerCommandTargetForOwner(player, mob);
        }
    }

    /**
     * Projectile damage: when a Player's arrow/crossbow/trident actually hits
     * a Mob, that Mob becomes the same explicit priority target. This covers
     * ranged attacks without trying to guess the Player's crosshair at launch.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel)) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob) || !mob.isAlive()) {
            return;
        }

        ServerPlayer player = null;
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer serverPlayer) {
            player = serverPlayer;
        }

        if (player == null) {
            Entity direct = event.getSource().getDirectEntity();
            if (direct instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer serverPlayer) {
                player = serverPlayer;
            }
        }

        if (player != null) {
            BabyCombatController.setPlayerCommandTargetForOwner(player, mob);
        }
    }
}
