package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.entity.GrowthChildPlayerMobEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * GrowthChild-only bridge for vanilla Player-vs-Monster target selection.
 *
 * GrowthChild is a PathfinderMob rather than a Player, so vanilla Monster
 * target goals that are explicitly typed as Player.class cannot see it. This
 * bridge installs the same NearestAttackableTargetGoal mechanism with the
 * GrowthChild entity class as the candidate type. Family defense is handled
 * separately by GrowthChildBrain only after a hostile mob actually targets
 * or hurts a registered family Player.
 */
@Mod.EventBusSubscriber(modid="normalnpcplayer", bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class GrowthChildCombatEvents {
    private static final String TARGET_GOAL_INSTALLED = "NNP_GrowthChildPlayerTargetGoal";

    private GrowthChildCombatEvents() {}

    @SubscribeEvent
    public static void onMonsterJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Mob mob && isPlayerHostile(mob)) {
            installPlayerEquivalentTargetGoal(mob);
        }
    }

    /** Existing loaded monsters are handled when the GrowthChild sees them. */
    public static void ensureTargetGoal(Mob mob) {
        if (!mob.level().isClientSide() && isPlayerHostile(mob)) {
            installPlayerEquivalentTargetGoal(mob);
            if (mob instanceof Slime slime) {
                bridgeSlimePlayerTarget(slime);
            }
        }
    }

    /**
     * Slime/MagmaCube have a dedicated vanilla attack goal and are not
     * Monster subclasses. Their Player targeting therefore needs an explicit
     * Player-equivalent target bridge.
     *
     * The GrowthChild is eligible only inside the same combat range and
     * TargetingConditions used for a Player. A real Player remains preferred
     * when that Player is closer.
     */
    private static void bridgeSlimePlayerTarget(Slime slime) {
        if (!(slime.level() instanceof ServerLevel level) || !slime.isAlive()) return;

        LivingEntity current = slime.getTarget();
        if (current != null && current.isAlive()
                && !(current instanceof Player)
                && !(current instanceof GrowthChildPlayerMobEntity)) {
            return;
        }

        double range = 16.0D;
        TargetingConditions conditions = TargetingConditions.forCombat().range(range);

        Player nearestPlayer = level.getNearestPlayer(conditions, slime);
        GrowthChildPlayerMobEntity nearestChild = null;
        double childDistance = Double.MAX_VALUE;

        for (GrowthChildPlayerMobEntity child : level.getEntitiesOfClass(
                GrowthChildPlayerMobEntity.class,
                slime.getBoundingBox().inflate(range),
                GrowthChildPlayerMobEntity::isAlive)) {
            if (!conditions.test(slime, child)) continue;
            double d = slime.distanceToSqr(child);
            if (d < childDistance) {
                childDistance = d;
                nearestChild = child;
            }
        }

        if (nearestChild == null) return;

        if (nearestPlayer == null
                || childDistance < slime.distanceToSqr(nearestPlayer)) {
            slime.setTarget(nearestChild);
        }
    }

    private static void installPlayerEquivalentTargetGoal(Mob monster) {
        if (monster.getPersistentData().getBoolean(TARGET_GOAL_INSTALLED)) return;

        // Priority 2 matches the common vanilla Player target-goal priority.
        // The existing vanilla Player goal remains authoritative when a real
        // Player is selected; this goal supplies GrowthChild as an additional
        // Player-like candidate when no real Player target is selected.
        monster.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                monster, GrowthChildPlayerMobEntity.class, true));
        monster.getPersistentData().putBoolean(TARGET_GOAL_INSTALLED, true);
    }

    private static boolean isPlayerHostile(Mob mob) {
        return mob instanceof Monster || mob instanceof Slime;
    }

    /**
     * If vanilla selected a real Player, a closer GrowthChild that satisfies
     * the same combat TargetingConditions can occupy that target slot. This
     * makes Player + GrowthChild behave as one nearest-player population
     * instead of always forcing either side to win.
     */
    @SubscribeEvent
    public static void onMonsterChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob monster) || !monster.isAlive() || !isPlayerHostile(monster)) return;
        if (!(event.getNewTarget() instanceof Player selectedPlayer)) return;
        if (!(monster.level() instanceof ServerLevel level)) return;

        double followRange = monster.getAttributeValue(Attributes.FOLLOW_RANGE);
        TargetingConditions conditions = TargetingConditions.forCombat().range(followRange);

        GrowthChildPlayerMobEntity nearestChild = null;
        double nearestDistance = Double.MAX_VALUE;
        for (GrowthChildPlayerMobEntity child : level.getEntitiesOfClass(
                GrowthChildPlayerMobEntity.class,
                monster.getBoundingBox().inflate(followRange),
                GrowthChildPlayerMobEntity::isAlive)) {
            if (!conditions.test(monster, child)) continue;

            double distance = monster.distanceToSqr(child);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestChild = child;
            }
        }

        if (nearestChild != null
                && nearestDistance < monster.distanceToSqr(selectedPlayer)) {
            event.setNewTarget(nearestChild);
        }
    }
}
