package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Fair protector attribution for Baby NPC Players.
 *
 * A protector is credited only when:
 * - the killed entity is a hostile mob that could actually attack the Baby;
 * - the Baby could directly see that mob at the kill moment;
 * - the killer resolves to a Player;
 * - for a non-owner killer, the current owner is also directly visible to the
 *   Baby and is strictly closer than the non-owner killer.
 *
 * The last rule prevents a remote third party from farming protector credit
 * while the owner is not the Baby's more prominent/visible nearby caregiver.
 */
@Mod.EventBusSubscriber(modid = NormalNPCPlayer.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BabyProtectorEvents {
    private static final double PROTECTION_VIEW_RANGE = 32.0D;

    private BabyProtectorEvents() {}

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob deadMob)) return;
        if (!(deadMob.level() instanceof net.minecraft.server.level.ServerLevel)) return;

        Player killer = resolvePlayerKiller(event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (killer == null || !killer.isAlive() || killer.isSpectator()) return;

        for (BabyNPCPlayerEntity baby : deadMob.level().getEntitiesOfClass(
                BabyNPCPlayerEntity.class,
                deadMob.getBoundingBox().inflate(PROTECTION_VIEW_RANGE),
                BabyNPCPlayerEntity::isAlive)) {

            if (!baby.isProtectableThreat(deadMob)) continue;
            if (!baby.canSeeForProtection(deadMob)) continue;
            if (!killer.level().equals(baby.level())) continue;
            if (baby.distanceToSqr(killer) > PROTECTION_VIEW_RANGE * PROTECTION_VIEW_RANGE) continue;
            if (!baby.hasLineOfSight(killer)) continue;

            if (isCurrentOwner(baby, killer)) {
                baby.recordProtector(killer);
                continue;
            }

            // Non-owner credit is deliberately stricter: the owner must be
            // visible and strictly closer to the Baby than the non-owner.
            // Equal distance is rejected to avoid ambiguous attribution.
            Player owner = baby.getBehaviorOwner();
            if (owner == null || !owner.isAlive() || owner.isSpectator()) continue;
            if (!owner.level().equals(baby.level())) continue;
            if (owner.distanceToSqr(baby) > PROTECTION_VIEW_RANGE * PROTECTION_VIEW_RANGE) continue;
            if (!baby.hasLineOfSight(owner)) continue;

            double ownerDistance = baby.distanceToSqr(owner);
            double killerDistance = baby.distanceToSqr(killer);
            if (ownerDistance < killerDistance) {
                baby.recordProtector(killer);
            }
        }
    }

    private static boolean isCurrentOwner(BabyNPCPlayerEntity baby, Player player) {
        if (baby.isPlayerBabyOwner(player)) {
            return true;
        }
        return baby.getOwnerUUID() != null && baby.getOwnerUUID().equals(player.getUUID());
    }

    /**
     * Resolves direct player attacks and projectile kills back to their owning
     * player, following the standard Projectile owner relationship.
     */
    private static Player resolvePlayerKiller(Entity source, Entity direct) {
        Player player = resolvePlayer(source);
        if (player != null) return player;
        return resolvePlayer(direct);
    }

    private static Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile && projectile.getOwner() instanceof Player player) {
            return player;
        }
        if (entity instanceof LivingEntity living
                && living.getLastHurtByMob() instanceof Player player) {
            return player;
        }
        return null;
    }
}
