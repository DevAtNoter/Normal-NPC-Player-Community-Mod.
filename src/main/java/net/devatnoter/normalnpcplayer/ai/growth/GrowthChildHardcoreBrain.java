package net.devatnoter.normalnpcplayer.ai.growth;

import net.devatnoter.normalnpcplayer.entity.GrowthChildHardcorePlayerMobEntity;
import net.devatnoter.normalnpcplayer.entity.GrowthChildPlayerMobEntity;
import net.devatnoter.normalnpcplayer.event.GrowthChildCombatEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Minimal post-growth brain for Hardcore Babies.
 *
 * Deliberately excludes the full GrowthChild systems: no work, storage, food,
 * sleep schedule, Elytra, autonomous chat, family-defense planner, item/XP
 * attraction or chunk loading. Only the three requested movement modes remain.
 */
public final class GrowthChildHardcoreBrain {
    private static final String COMBAT_TARGET_UUID = "HardcoreChildCombatTargetUUID";

    private GrowthChildHardcoreBrain() {}

    public static void registerGoals(GrowthChildHardcorePlayerMobEntity e) {
        e.goalSelector.addGoal(0, new FloatGoal(e));

        // Hardcore Growth Child fights back when a hostile mob attacks it.
        // This is the only autonomous combat behavior added to the otherwise
        // minimal Hardcore brain.
        e.targetSelector.addGoal(1, new HurtByTargetGoal(e, Monster.class, Slime.class));
        e.goalSelector.addGoal(1, new MeleeAttackGoal(e, 1.0D, true));

        e.goalSelector.addGoal(2, new FollowGoal(e));
        e.goalSelector.addGoal(3, new HoldPositionGoal(e));
        e.goalSelector.addGoal(4, new WanderGoal(e));
        e.goalSelector.addGoal(8, new LookAtPlayerGoal(e, Player.class, 10.0F));
        e.goalSelector.addGoal(9, new RandomLookAroundGoal(e));
    }

    public static void tick(GrowthChildHardcorePlayerMobEntity e) {
        if (!(e.level() instanceof ServerLevel level) || e.isDeadOrDying()) return;

        // Keep the existing Player-like hostile targeting bridge active for this
        // subtype too. This does not import any GrowthChild AI behavior; it only
        // makes hostile mobs recognize the Hardcore Growth Child as a valid target.
        for (net.minecraft.world.entity.Mob hostile : level.getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class,
                e.getBoundingBox().inflate(48.0D),
                mob -> mob.isAlive() && (mob instanceof Monster || mob instanceof Slime))) {
            GrowthChildCombatEvents.ensureTargetGoal(hostile);
        }

        rememberHostileTarget(e, level);
    }

    private static void rememberHostileTarget(GrowthChildHardcorePlayerMobEntity e, ServerLevel level) {
        LivingEntity current = e.getTarget();
        if (current != null && current.isAlive() && isHostile(current)) {
            e.getPersistentData().putUUID(COMBAT_TARGET_UUID, current.getUUID());
            return;
        }

        if (e.getPersistentData().hasUUID(COMBAT_TARGET_UUID)) {
            UUID id = e.getPersistentData().getUUID(COMBAT_TARGET_UUID);
            Entity entity = level.getEntity(id);
            if (entity instanceof LivingEntity hostile && hostile.isAlive() && isHostile(hostile)
                    && hostile.distanceToSqr(e) <= 48.0D * 48.0D) {
                e.setTarget(hostile);
                return;
            }
            e.getPersistentData().remove(COMBAT_TARGET_UUID);
        }
    }

    private static boolean isHostile(LivingEntity entity) {
        return entity instanceof Monster || entity instanceof Slime;
    }

    private static final class FollowGoal extends Goal {
        private final GrowthChildHardcorePlayerMobEntity e;

        private FollowGoal(GrowthChildHardcorePlayerMobEntity e) {
            this.e = e;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return e.getTarget() == null
                    && e.getCurrentIntent() == GrowthChildPlayerMobEntity.AIIntent.FOLLOW
                    && e.isFollowing()
                    && e.getFollowTarget() != null
                    && e.getFollowTarget().isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            LivingEntity target = e.getFollowTarget();
            if (target == null) return;
            double distance = e.distanceToSqr(target);
            if (distance > 9.0D) {
                e.getNavigation().moveTo(target, 1.0D);
                if (e.onGround() && e.horizontalCollision) e.getJumpControl().jump();
            } else {
                e.getNavigation().stop();
                e.setSprinting(false);
            }
            e.setCurrentTask("following " + target.getName().getString());
            e.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
    }

    private static final class HoldPositionGoal extends Goal {
        private final GrowthChildHardcorePlayerMobEntity e;

        private HoldPositionGoal(GrowthChildHardcorePlayerMobEntity e) {
            this.e = e;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return e.getTarget() == null
                    && e.getCurrentIntent() == GrowthChildPlayerMobEntity.AIIntent.HOLD_POSITION;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            e.setCurrentTask("holding position");
            e.getNavigation().stop();
            e.setSprinting(false);
        }
    }

    private static final class WanderGoal extends Goal {
        private final GrowthChildHardcorePlayerMobEntity e;
        private BlockPos destination;
        private int cooldown;

        private WanderGoal(GrowthChildHardcorePlayerMobEntity e) {
            this.e = e;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (e.getTarget() != null
                    || e.getCurrentIntent() != GrowthChildPlayerMobEntity.AIIntent.WANDER_HOME) {
                return false;
            }
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            destination = findDestination();
            return destination != null;
        }

        @Override
        public boolean canContinueToUse() {
            return e.getTarget() == null
                    && e.getCurrentIntent() == GrowthChildPlayerMobEntity.AIIntent.WANDER_HOME
                    && destination != null
                    && !e.getNavigation().isDone();
        }

        @Override
        public void start() {
            e.setCurrentTask("wandering");
        }

        @Override
        public void tick() {
            if (destination == null) return;
            e.setCurrentTask("wandering");
            e.getNavigation().moveTo(
                    destination.getX() + 0.5D,
                    destination.getY(),
                    destination.getZ() + 0.5D,
                    0.9D
            );
            e.getLookControl().setLookAt(
                    destination.getX() + 0.5D,
                    destination.getY(),
                    destination.getZ() + 0.5D,
                    30.0F,
                    30.0F
            );
            if (e.distanceToSqr(
                    destination.getX() + 0.5D,
                    destination.getY(),
                    destination.getZ() + 0.5D
            ) < 3.0D) {
                destination = null;
                cooldown = 80;
            }
        }

        private BlockPos findDestination() {
            BlockPos base = e.blockPosition();
            for (int attempt = 0; attempt < 12; attempt++) {
                int x = base.getX() + e.getRandom().nextInt(17) - 8;
                int z = base.getZ() + e.getRandom().nextInt(17) - 8;
                BlockPos p = new BlockPos(x, base.getY(), z);
                if (e.level().getBlockState(p).getCollisionShape(e.level(), p).isEmpty()
                        && e.level().getBlockState(p.below()).isFaceSturdy(
                        e.level(), p.below(), net.minecraft.core.Direction.UP)) {
                    return p;
                }
            }
            return null;
        }
    }
}
