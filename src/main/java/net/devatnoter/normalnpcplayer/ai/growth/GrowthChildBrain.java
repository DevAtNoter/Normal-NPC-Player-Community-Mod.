package net.devatnoter.normalnpcplayer.ai.growth;

import net.devatnoter.normalnpcplayer.entity.GrowthChildPlayerMobEntity;
import net.devatnoter.normalnpcplayer.event.GrowthChildCombatEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** Dedicated survival brain. It never delegates to NormalBrain/HunterBrain. */
public final class GrowthChildBrain {
    private static final String ROCKET_COOLDOWN = "GrowthChildRocketCooldown";
    private static final String ELYTRA_ACTIVE = "GrowthChildElytraActive";
    private static final String ATTACK_COOLDOWN = "GrowthChildAttackCooldown";
    private static final String THINK_COOLDOWN = "GrowthChildThinkCooldown";
    private static final String REPORT_COOLDOWN = "GrowthChildReportCooldown";
    private static final String BED_SEARCH_COOLDOWN = "GrowthChildBedSearchCooldown";
    private static final String MINE_COOLDOWN = "GrowthChildMineCooldown";
    private static final String CHEST_OPEN_COOLDOWN = "GrowthChildChestOpenCooldown";
    private static final String MINE_PROGRESS = "GrowthChildMineProgress";
    private static final String WORK_SEARCH_COOLDOWN = "GrowthChildWorkSearchCooldown";
    private static final String COMBAT_TARGET_UUID = "GrowthChildCombatTargetUUID";
    private static final String DEFAULT_RANDOM_COOLDOWN = "GrowthChildDefaultRandomCooldown";
    private static final String FOLLOW_REQUESTER_UUID = "GrowthChildFollowRequesterUUID";
    private static final String FOLLOW_REQUEST_TICKS = "GrowthChildFollowRequestTicks";
    private static final String FOLLOW_REQUEST_COOLDOWN = "GrowthChildFollowRequestCooldown";
    private static final String MINE_X = "GrowthChildMineX";
    private static final String MINE_Y = "GrowthChildMineY";
    private static final String MINE_Z = "GrowthChildMineZ";
    private static final String HOLD_X = "GrowthChildHoldX";
    private static final String HOLD_Y = "GrowthChildHoldY";
    private static final String HOLD_Z = "GrowthChildHoldZ";
    private static final String OPEN_PASSAGE_POS = "GrowthChildOpenPassagePos";
    private static final String OPEN_PASSAGE_TICKS = "GrowthChildOpenPassageTicks";
    private static final String HOME_ALARM_COOLDOWN = "GrowthChildHomeAlarmCooldown";
    private static final String HOME_ALARM_BELL_X = "GrowthChildHomeAlarmBellX";
    private static final String HOME_ALARM_BELL_Y = "GrowthChildHomeAlarmBellY";
    private static final String HOME_ALARM_BELL_Z = "GrowthChildHomeAlarmBellZ";
    private static final String HOME_ALARM_ACTIVE = "GrowthChildHomeAlarmActive";
    private static final String BELL_SEARCH_COOLDOWN = "GrowthChildBellSearchCooldown";
    private static final String FOLLOW_PATH_FAIL_TICKS = "GrowthChildFollowPathFailTicks";
    private static final String FOLLOW_SURFACE_MODE = "GrowthChildFollowSurfaceMode";
    private static final double HOME_AREA_RADIUS = 64.0D;
    private static final double COMBAT_LEASH_MIN = 5.0D;
    private static final double COMBAT_LEASH_MAX = 24.0D;
    private static final double HOLD_VISIBILITY_RADIUS = 32.0D;
    private static final double FOLLOW_COMBAT_RADIUS = 10.0D;
    private static final double FOLLOW_COMBAT_SELF_RADIUS = 12.0D;
    private static final double HOME_ALARM_SCAN_RADIUS = 32.0D;

    private GrowthChildBrain() {}

    public static void registerGoals(GrowthChildPlayerMobEntity e) {
        e.goalSelector.addGoal(0, new EmergencyThreatGoal(e));
        e.goalSelector.addGoal(1, new FleeGoal(e));
        e.goalSelector.addGoal(2, new CombatGoal(e));
        e.goalSelector.addGoal(3, new HomeAlarmGoal(e));
        e.goalSelector.addGoal(4, new FollowRequestGoal(e));
        e.goalSelector.addGoal(5, new FollowGoal(e));
        e.goalSelector.addGoal(6, new HoldPositionGoal(e));
        e.goalSelector.addGoal(7, new WanderHomeGoal(e));
        e.goalSelector.addGoal(8, new GuardGoal(e));
        e.goalSelector.addGoal(9, new BedGoal(e));
        e.goalSelector.addGoal(10, new LunchBreakGoal(e));
        e.goalSelector.addGoal(11, new StorageGoal(e));
        e.goalSelector.addGoal(12, new ProcessingGoal(e));
        e.goalSelector.addGoal(13, new HomeGoal(e));
        e.goalSelector.addGoal(14, new WorkGoal(e));
        e.goalSelector.addGoal(15, new ExploreGoal(e));
        e.goalSelector.addGoal(16, new LookAtPlayerGoal(e, net.minecraft.world.entity.player.Player.class, 10.0F));
        e.goalSelector.addGoal(17, new RandomLookAroundGoal(e));
    }

    public static void tick(GrowthChildPlayerMobEntity e) {
        if (!(e.level() instanceof ServerLevel level) || e.isDeadOrDying()) return;
        e.tickGrowthState();
        decrement(e, THINK_COOLDOWN);
        decrement(e, ATTACK_COOLDOWN);
        decrement(e, REPORT_COOLDOWN);
        decrement(e, BED_SEARCH_COOLDOWN);
        decrement(e, MINE_COOLDOWN);
        decrement(e, WORK_SEARCH_COOLDOWN);
        decrement(e, CHEST_OPEN_COOLDOWN);
        decrement(e, FOLLOW_REQUEST_COOLDOWN);
        decrement(e, HOME_ALARM_COOLDOWN);
        decrement(e, BELL_SEARCH_COOLDOWN);
        tickFollowRequest(e, level);
        e.tickOpenedChestVisual();
        handleBuriedEmergency(e);
        handleDoorAndFenceGateTraversal(e);
        placeSafetyTorchIfNeeded(e);
        updateHostileTargets(e, level);
        if (e.getCurrentIntent() == GrowthChildPlayerMobEntity.AIIntent.IDLE && e.getTarget() == null && !e.isFollowing() && !e.isGuarding() && !hasFollowRequest(e)) {
            applyDefaultRandomBehavior(e);
        }
        // Sleeping is a hard movement lock. Threat detection above is deliberately
        // first, so a real known threat may wake the child before this lock applies.
        if (e.isSleeping()) {
            e.getNavigation().stop();
            e.setSprinting(false);
            e.setShiftKeyDown(false);
            e.setDeltaMovement(0.0D, e.getDeltaMovement().y, 0.0D);
        }
        handleFood(e);
        handleArmor(e);
        updateCombatEquipment(e);
        updateDailyState(e);
        handleElytraEquipment(e);
        tickElytra(e);
        updateMovementSkills(e);
        explainBlockedTask(e, level);
        attractNearbyItems(e, level);
        attractNearbyExperience(e, level);
        reportEnvironmentAndNeeds(e, level);
    }

    /** Opens a normal door/fence gate when it blocks the child's immediate route, then closes it after passage.
     * Iron doors are deliberately excluded because they require redstone/player interaction semantics.
     */
    private static void handleDoorAndFenceGateTraversal(GrowthChildPlayerMobEntity e) {
        var data = e.getPersistentData();
        int ticks = data.getInt(OPEN_PASSAGE_TICKS);
        if (ticks > 0) {
            data.putInt(OPEN_PASSAGE_TICKS, ticks - 1);
            if (data.contains(OPEN_PASSAGE_POS)) {
                BlockPos opened = BlockPos.of(data.getLong(OPEN_PASSAGE_POS));
                if (e.distanceToSqr(opened.getX() + .5D, opened.getY(), opened.getZ() + .5D) > 3.5D && ticks <= 1) {
                    closePassageIfSafe(e, opened);
                    data.remove(OPEN_PASSAGE_POS);
                    data.remove(OPEN_PASSAGE_TICKS);
                }
            }
        }

        // Only manipulate a block that is immediately in/around the movement corridor.
        BlockPos base = e.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 1, 1))) {
            BlockState state = e.level().getBlockState(p);
            boolean openableDoor = state.getBlock() instanceof DoorBlock && !state.is(Blocks.IRON_DOOR);
            boolean openableGate = state.getBlock() instanceof FenceGateBlock;
            if (!openableDoor && !openableGate) continue;

            boolean open = state.hasProperty(DoorBlock.OPEN) && state.getValue(DoorBlock.OPEN);
            if (!open) {
                if (openableDoor) {
                    e.getLookControl().setLookAt(p.getX() + .5D, p.getY() + .5D, p.getZ() + .5D, 30.0F, 30.0F);
                    e.swingInteraction();
                    e.level().setBlock(p, state.setValue(DoorBlock.OPEN, true), 10);
                    e.level().playSound(null, p, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
                } else {
                    e.getLookControl().setLookAt(p.getX() + .5D, p.getY() + .5D, p.getZ() + .5D, 30.0F, 30.0F);
                    e.swingInteraction();
                    e.level().setBlock(p, state.setValue(FenceGateBlock.OPEN, true), 10);
                    e.level().playSound(null, p, SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
            }
            data.putLong(OPEN_PASSAGE_POS, p.asLong());
            data.putInt(OPEN_PASSAGE_TICKS, 60);
            return;
        }
    }

    private static void closePassageIfSafe(GrowthChildPlayerMobEntity e, BlockPos p) {
        BlockState state = e.level().getBlockState(p);
        boolean door = state.getBlock() instanceof DoorBlock && !state.is(Blocks.IRON_DOOR);
        boolean gate = state.getBlock() instanceof FenceGateBlock;
        if (!door && !gate) return;
        if (e.distanceToSqr(p.getX() + .5D, p.getY(), p.getZ() + .5D) <= 3.5D) return;
        var nearby = e.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(p).inflate(1.25D),
                living -> living != e && living.isAlive());
        if (!nearby.isEmpty()) return;
        e.getLookControl().setLookAt(p.getX() + .5D, p.getY() + .5D, p.getZ() + .5D, 30.0F, 30.0F);
        e.swingInteraction();
        if (door) {
            e.level().setBlock(p, state.setValue(DoorBlock.OPEN, false), 10);
            e.level().playSound(null, p, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            e.level().setBlock(p, state.setValue(FenceGateBlock.OPEN, false), 10);
            e.level().playSound(null, p, SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private static void decrement(GrowthChildPlayerMobEntity e, String key) {
        int v = e.getPersistentData().getInt(key);
        if (v > 0) e.getPersistentData().putInt(key, v - 1);
    }

    private static void handleBuriedEmergency(GrowthChildPlayerMobEntity e) {
        BlockPos head = BlockPos.containing(e.getEyePosition());
        BlockState state = e.level().getBlockState(head);
        if (!state.is(Blocks.GRAVEL) && !state.is(Blocks.SAND)) return;
        equipSpecificTool(e, ShovelItem.class);
        if (e.getMainHandItem().getItem() instanceof ShovelItem) {
            e.setCurrentTask("digging out of gravel/sand");
            e.swingMainHand();
            if (e.level().destroyBlock(head, true, e)) {
                damageMainHand(e, 1);
            }
        } else {
            e.setCurrentTask("blocked: need a shovel");
            e.sayFamily("I am trapped under gravel or sand. I need a shovel to get out.", 18.0D);
        }
    }

    private static void equipSpecificTool(GrowthChildPlayerMobEntity e, Class<?> toolClass) {
        if (toolClass.isInstance(e.getMainHandItem().getItem())) return;
        SimpleContainer inv=e.getTraitInventory();
        for(int i=0;i<inv.getContainerSize();i++){ItemStack s=inv.getItem(i);if(toolClass.isInstance(s.getItem())){ItemStack old=e.getMainHandItem().copy();e.setItemInHand(InteractionHand.MAIN_HAND,s.split(1));if(!old.isEmpty())inv.addItem(old);return;}}
    }

    /**
     * Player-like threat handling plus family protection.
     *
     * GrowthChild does not attack hostile mobs merely because they are nearby.
     * It reacts when a hostile Mob has actually targeted/hurt the GrowthChild,
     * or has actually targeted/hurt one of its registered Player family members.
     *
     * Slime/MagmaCube are included even though they are not subclasses of
     * Monster in vanilla 1.20.1.
     */
    private static void updateHostileTargets(GrowthChildPlayerMobEntity e, ServerLevel level) {
        LivingEntity locked = resolveCombatTarget(e, level);

        // A known combat target is authoritative until it dies or leaves the
        // combat radius. Do not let vanilla retargeting erase the child's
        // combat intention after the first successful hit.
        if (locked != null) {
            e.setTarget(locked);
            e.setStealthThreat(false);
            if (e.isSleeping()) {
                e.stopSleeping();
                e.getNavigation().stop();
            }
            e.setCombatState(GrowthChildPlayerMobEntity.CombatState.FIGHT);
            e.setCurrentTask("fighting");
            return;
        }

        e.setTarget(null);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        boolean protectingFamily = false;

        for (net.minecraft.world.entity.Mob hostile : level.getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class,
                e.getBoundingBox().inflate(48.0D),
                mob -> mob.isAlive() && (mob instanceof Monster || mob instanceof Slime))) {

            GrowthChildCombatEvents.ensureTargetGoal(hostile);
            LivingEntity mobTarget = hostile.getTarget();
            boolean attacksChild = mobTarget == e
                    || hostile.getLastHurtByMob() == e
                    || e.getLastHurtByMob() == hostile;

            boolean attacksFamily = mobTarget instanceof ServerPlayer
                    && e.isFamilyMember(mobTarget);

            // FOLLOW is a social escort mode, not a free-roaming combat mode.
            // Only engage a hostile that is actually threatening the followed
            // player/child in the player's immediate area.
            if (e.isFollowing()) {
                Player followed = e.getFollowTarget();
                if (followed == null) continue;
                boolean relevantToFollow = hostile.distanceToSqr(followed) <= FOLLOW_COMBAT_RADIUS * FOLLOW_COMBAT_RADIUS
                        && hostile.distanceToSqr(e) <= 12.0D * 12.0D;
                if (!relevantToFollow) continue;
            }
            // HOLD POSITION is deliberately more perceptive: the child reacts
            // to hostile mobs it can actually see within 32 blocks, but does not
            // acquire unseen mobs through walls or across the world.
            boolean visibleHoldThreat = e.getCurrentIntent() == GrowthChildPlayerMobEntity.AIIntent.HOLD_POSITION
                    && hostile.distanceToSqr(e) <= HOLD_VISIBILITY_RADIUS * HOLD_VISIBILITY_RADIUS
                    && e.hasLineOfSight(hostile);

            // Home/guard defense also reacts to hostile mobs that are actually
            // visible near the responsibility center. This is intentionally
            // separate from FOLLOW so escort combat is never expanded into
            // general hostile hunting.
            Vec3 responsibilityCenter = getResponsibilityCenter(e);
            boolean visibleHomeDefenseThreat = !e.isFollowing()
                    && e.hasHome()
                    && (e.isGuarding()
                        || e.getCurrentIntent() == GrowthChildPlayerMobEntity.AIIntent.IDLE
                        || e.getCurrentIntent() == GrowthChildPlayerMobEntity.AIIntent.WANDER_HOME)
                    && hostile.distanceToSqr(e) <= HOLD_VISIBILITY_RADIUS * HOLD_VISIBILITY_RADIUS
                    && responsibilityCenter != null
                    && horizontalDistanceSqr(hostile.position(), responsibilityCenter) <= COMBAT_LEASH_MAX * COMBAT_LEASH_MAX
                    && e.hasLineOfSight(hostile);

            if (!attacksFamily) {
                for (ServerPlayer familyPlayer : level.getEntitiesOfClass(
                        ServerPlayer.class, hostile.getBoundingBox().inflate(32.0D),
                        p -> p.isAlive() && e.isFamilyMember(p))) {
                    if (hostile.getLastHurtByMob() == familyPlayer) {
                        attacksFamily = true;
                        break;
                    }
                }
            }

            if (attacksChild || attacksFamily || visibleHoldThreat || visibleHomeDefenseThreat) {
                if (!e.isFollowing() && !attacksChild) {
                    Vec3 center = getResponsibilityCenter(e);
                    double leash = getDynamicCombatLeash(e, hostile);
                    if (center != null && horizontalDistanceSqr(hostile.position(), center) > leash * leash) continue;
                }
                double d = e.distanceToSqr(hostile);
                if (d < bestD) {
                    bestD = d;
                    best = hostile;
                    protectingFamily = attacksFamily && !attacksChild;
                }
            }
        }

        e.setStealthThreat(false);
        if (best != null) {
            rememberCombatTarget(e, best);
            if (e.isSleeping()) {
                e.stopSleeping();
                e.getNavigation().stop();
                e.setCurrentTask(protectingFamily ? "waking to protect family" : "waking to defend myself");
            }
            e.setTarget(best);
            e.setCombatState(GrowthChildPlayerMobEntity.CombatState.FIGHT);
            e.setCurrentTask(protectingFamily ? "protecting family" : "defending / retaliating");
        } else if (!e.isGuarding() && !e.isFollowing()
                && e.getCurrentIntent() != GrowthChildPlayerMobEntity.AIIntent.HOLD_POSITION
                && e.getCurrentIntent() != GrowthChildPlayerMobEntity.AIIntent.WANDER_HOME) {
            e.setCombatState(GrowthChildPlayerMobEntity.CombatState.NEUTRAL);
        }
    }

    private static void rememberCombatTarget(GrowthChildPlayerMobEntity e, LivingEntity target) {
        if (target != null) e.getPersistentData().putUUID(COMBAT_TARGET_UUID, target.getUUID());
    }

    private static LivingEntity resolveCombatTarget(GrowthChildPlayerMobEntity e, ServerLevel level) {
        if (!e.getPersistentData().hasUUID(COMBAT_TARGET_UUID)) return null;
        UUID id = e.getPersistentData().getUUID(COMBAT_TARGET_UUID);
        Entity entity = level.getEntity(id);
        if (!(entity instanceof LivingEntity target) || !target.isAlive()
                || target.distanceToSqr(e) > 48.0D * 48.0D
                || !(target instanceof Monster || target instanceof Slime)) {
            e.getPersistentData().remove(COMBAT_TARGET_UUID);
            return null;
        }

        // FOLLOW only protects the player immediately around them. Never chase
        // a random hostile that is merely somewhere in the world.
        if (e.isFollowing()) {
            Player followed = e.getFollowTarget();
            if (followed == null
                    || target.distanceToSqr(followed) > FOLLOW_COMBAT_RADIUS * FOLLOW_COMBAT_RADIUS
                    || target.distanceToSqr(e) > 12.0D * 12.0D) {
                e.getPersistentData().remove(COMBAT_TARGET_UUID);
                return null;
            }
        }

        // FOLLOW combat is centered on the followed player. For WORKING,
        // direct self-defense is allowed at the work location; the Home combat
        // leash must never make the child ignore something that is attacking it.
        boolean directSelfDefense = (target instanceof Mob mobTarget && mobTarget.getTarget() == e)
                || target.getLastHurtByMob() == e
                || e.getLastHurtByMob() == target;

        if (!e.isFollowing() && !directSelfDefense) {
            Vec3 center = getResponsibilityCenter(e);
            double leash = getDynamicCombatLeash(e, target);
            if (center != null && horizontalDistanceSqr(target.position(), center) > leash * leash) {
                e.getPersistentData().remove(COMBAT_TARGET_UUID);
                return null;
            }
        }
        return target;
    }

    private static void clearCombatTarget(GrowthChildPlayerMobEntity e) {
        e.getPersistentData().remove(COMBAT_TARGET_UUID);
        e.setTarget(null);
    }

    /**
     * Item magnet only. The GrowthChild never walks toward dropped items and
     * never uses a pickup Goal. Items are attracted into the GrowthChild's
     * body, then absorbed into its inventory when they reach the body.
     */
    private static void attractNearbyItems(GrowthChildPlayerMobEntity e, ServerLevel level) {
        final double maxDistance = 2.0D;
        final double absorbDistance = 0.90D;
        Vec3 targetPoint = e.position().add(0.0D, e.getBbHeight() * 0.55D, 0.0D);

        for (ItemEntity item : level.getEntitiesOfClass(
                ItemEntity.class,
                e.getBoundingBox().inflate(maxDistance),
                x -> x.isAlive() && !x.getItem().isEmpty() && !x.hasPickUpDelay())) {

            if (isProtectedFromPlayer(e, item)) continue;

            Vec3 toChild = targetPoint.subtract(item.position());
            double distance = toChild.length();

            if (distance <= absorbDistance) {
                absorbItem(e, item);
                continue;
            }

            if (distance <= maxDistance && distance > 0.0001D) {
                // Attraction is applied every server tick. It is deliberately
                // independent of navigation, combat goals, or movement state.
                double pull = Math.min(0.72D, Math.max(0.22D,
                        0.22D + (2.0D - distance) * 0.25D));
                Vec3 velocity = item.getDeltaMovement().scale(0.30D)
                        .add(toChild.normalize().scale(pull));
                if (velocity.lengthSqr() > 0.72D * 0.72D) {
                    velocity = velocity.normalize().scale(0.72D);
                }
                item.setDeltaMovement(velocity);
                item.hasImpulse = true;
            }
        }
    }

    private static boolean isProtectedFromPlayer(GrowthChildPlayerMobEntity e, ItemEntity item) {
        if (!(e.level() instanceof ServerLevel level)) return true;

        for (ServerPlayer player : level.getEntitiesOfClass(
                ServerPlayer.class,
                item.getBoundingBox().inflate(5.0D),
                ServerPlayer::isAlive)) {
            double playerDistance = player.distanceToSqr(item);
            double childDistance = e.distanceToSqr(item);

            // An unowned item is contestable: the child may collect it only
            // when it is strictly closer than the real Player. This prevents
            // the child from taking an item out from under a Player while still
            // allowing the child to collect ordinary world drops near a Player.
            if (playerDistance <= childDistance + 0.25D) return true;
        }
        return false;
    }

    private static void absorbItem(GrowthChildPlayerMobEntity e, ItemEntity item) {
        if (!item.isAlive() || item.hasPickUpDelay() || isProtectedFromPlayer(e, item)) return;

        SimpleContainer inv = e.getTraitInventory();
        ItemStack incoming = item.getItem().copy();
        ItemStack remaining = inv.addItem(incoming);
        item.setItem(remaining);
        inv.setChanged();

        if (remaining.isEmpty()) {
            item.discard();
            e.level().playSound(null, e.blockPosition(), SoundEvents.ITEM_PICKUP,
                    e.getSoundSource(), 0.20F,
                    0.90F + e.getRandom().nextFloat() * 0.20F);
        }
    }

    /**
     * Player-state XP pickup. The GrowthChild receives XP through its own
     * player-like XP state (addExperience), but never races a real Player for
     * an orb that the Player is closer to or is already able to pick up.
     */
    private static void attractNearbyExperience(GrowthChildPlayerMobEntity e, ServerLevel level) {
        final double maxDist = 8.0D;
        final double pickupRadius = 0.60D;
        Vec3 childTarget = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D);

        for (ExperienceOrb orb : level.getEntitiesOfClass(
                ExperienceOrb.class,
                e.getBoundingBox().inflate(maxDist),
                ExperienceOrb::isAlive)) {
            if (orb.isRemoved()) continue;

            Vec3 orbPos = orb.position();
            double childDistance = childTarget.distanceTo(orbPos);
            if (childDistance > maxDist) continue;

            // Find the real Player who is closest to this exact orb.
            double nearestPlayerDistance = Double.POSITIVE_INFINITY;
            for (ServerPlayer player : level.getEntitiesOfClass(
                    ServerPlayer.class,
                    orb.getBoundingBox().inflate(maxDist),
                    ServerPlayer::isAlive)) {
                double d = player.position()
                        .add(0.0D, player.getBbHeight() * 0.5D, 0.0D)
                        .distanceTo(orbPos);
                if (d < nearestPlayerDistance) nearestPlayerDistance = d;
            }

            // A real Player always gets priority. The child only collects when
            // it is strictly closer, with a small tolerance to prevent races.
            if (nearestPlayerDistance <= childDistance + 0.05D) continue;

            if (childDistance <= pickupRadius) {
                int value = Math.max(0, orb.getValue());
                if (value <= 0) {
                    orb.discard();
                    continue;
                }

                // XP belongs to the GrowthChild's Player-like state.
                e.addExperience(value);
                orb.discard();
                level.playSound(null, e.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                        e.getSoundSource(), 0.10F,
                        0.90F + e.getRandom().nextFloat() * 0.20F);
                e.setCurrentTask("picked up experience");
                continue;
            }

            // Only attract an orb when no real Player currently has priority.
            Vec3 toChild = childTarget.subtract(orbPos);
            double proximity = Math.max(0.0D, 1.0D - childDistance / maxDist);

            // Keep XP movement smooth, but make the pull clearly accelerate
            // as the orb gets closer instead of moving at nearly one constant speed.
            double pull = 0.035D + 0.52D * proximity * proximity;
            Vec3 currentVelocity = orb.getDeltaMovement();
            Vec3 desiredVelocity = toChild.normalize().scale(pull);
            Vec3 smoothVelocity = currentVelocity.scale(0.55D).add(desiredVelocity.scale(0.45D));
            double maxSpeed = 0.55D;
            if (smoothVelocity.lengthSqr() > maxSpeed * maxSpeed) {
                smoothVelocity = smoothVelocity.normalize().scale(maxSpeed);
            }
            orb.setDeltaMovement(smoothVelocity);
            orb.hasImpulse = true;
        }
    }

    private static void placeSafetyTorchIfNeeded(GrowthChildPlayerMobEntity e) {
        if (e.getMainHandItem().is(Items.TORCH) || findItem(e, Items.TORCH) >= 0) {
            if (e.level().getMaxLocalRawBrightness(e.blockPosition()) > 7) return;
            BlockPos base=e.blockPosition();
            for (net.minecraft.core.Direction dir : new net.minecraft.core.Direction[]{
                    net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                    net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST}) {
                BlockPos p=base.relative(dir);
                if (!e.level().getBlockState(p).isAir()) continue;
                BlockPos floor=p.below();
                if (!e.level().getBlockState(floor).isFaceSturdy(e.level(),floor,net.minecraft.core.Direction.UP)) continue;
                ItemStack old=e.getMainHandItem().copy();
                ItemStack torch;
                if (old.is(Items.TORCH)) {
                    torch = old.split(1);
                } else {
                    int slot=findItem(e,Items.TORCH);
                    if(slot<0) return;
                    torch=e.getTraitInventory().getItem(slot).split(1);
                }
                e.setItemInHand(InteractionHand.MAIN_HAND,torch);
                e.getLookControl().setLookAt(p.getX() + .5D, p.getY() + .5D, p.getZ() + .5D, 30.0F, 30.0F);
                e.swingInteraction();
                if(e.level().setBlock(p,Blocks.TORCH.defaultBlockState(),3)){
                    e.level().playSound(null, p, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
                    e.getMainHandItem().shrink(1);
                } else if (!e.getMainHandItem().isEmpty()) {
                    e.getTraitInventory().addItem(e.getMainHandItem().copy());
                }
                if (!old.isEmpty() && !old.is(Items.TORCH)) e.getTraitInventory().addItem(old);
                return;
            }
        }
    }

    private static void handleFood(GrowthChildPlayerMobEntity e) {
        if (e.getHealth() > e.getMaxHealth() * 0.50F) return;
        SimpleContainer inv = e.getTraitInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !s.getItem().isEdible()) continue;
            e.setCurrentTask("eating");
            ItemStack oldHand=e.getMainHandItem().copy();
            e.setItemInHand(InteractionHand.MAIN_HAND, s.split(1));
            e.startUsingItem(InteractionHand.MAIN_HAND);
            e.swingInteraction();
            e.heal(Math.max(1, e.getMainHandItem().getItem().getFoodProperties().getNutrition()));
            e.stopUsingItem();
            e.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
            inv.setChanged();
            return;
        }
    }

    private static void handleArmor(GrowthChildPlayerMobEntity e) {
        SimpleContainer inv = e.getTraitInventory();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack current = e.getItemBySlot(slot);
            int best = -1, score = armorScore(current, slot);
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!(s.getItem() instanceof ArmorItem a) || a.getEquipmentSlot() != slot || s.getItem() instanceof ElytraItem) continue;
                int candidate = a.getDefense() * 10 + (int)a.getToughness() + enchantValue(s);
                if (candidate > score) { score = candidate; best = i; }
            }
            if (best >= 0) {
                if (!current.isEmpty()) { ItemStack left = inv.addItem(current.copy()); if (!left.isEmpty()) continue; }
                ItemStack chosen = inv.getItem(best).split(1);
                e.setItemSlot(slot, chosen);
                e.setCurrentTask("equipping armor");
            }
        }
    }

    private static int armorScore(ItemStack s, EquipmentSlot slot) {
        if (!(s.getItem() instanceof ArmorItem a) || a.getEquipmentSlot() != slot) return -1;
        return a.getDefense() * 10 + (int)a.getToughness() + enchantValue(s);
    }

    private static int enchantValue(ItemStack s) {
        return s.getEnchantmentTags().size() * 2;
    }

    /** Chooses the actual item for the situation, including off-hand shield/totem. */
    private static void updateCombatEquipment(GrowthChildPlayerMobEntity e) {
        LivingEntity target = e.getTarget();
        boolean danger = target != null && target.isAlive();
        if (!danger) return;
        SimpleContainer inv = e.getTraitInventory();
        boolean ranged = target instanceof Creeper
                ? target.distanceToSqr(e) > 25.0D
                : target.distanceToSqr(e) > 64.0D;
        int best = -1, bestScore = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            int score = weaponScore(s, ranged, target);
            if (score > bestScore) { bestScore = score; best = i; }
        }
        ItemStack main = e.getMainHandItem();
        if (best >= 0 && (!isCombatWeapon(main) || weaponScore(main, ranged, target) < bestScore)) {
            ItemStack old = main.copy();
            e.setItemInHand(InteractionHand.MAIN_HAND, inv.getItem(best).split(1));
            if (!old.isEmpty()) inv.addItem(old);
            e.setCurrentTask(ranged ? "choosing ranged weapon" : "choosing melee weapon");
        } else if (best < 0 && !main.isEmpty() && !isCombatWeapon(main)) {
            // No combat weapon exists in the GrowthChild inventory: use the
            // vanilla empty-hand attack instead of fighting with a random
            // tool/item left in the main hand.
            ItemStack old = main.copy();
            e.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            inv.addItem(old);
            e.setCurrentTask("fighting bare-handed");
        }
        updateOffhand(e, danger);
    }

    private static void updateOffhand(GrowthChildPlayerMobEntity e, boolean danger) {
        ItemStack off = e.getOffhandItem();
        if (danger && e.getHealth() < e.getMaxHealth() * .35F && findItem(e, Items.TOTEM_OF_UNDYING) >= 0) {
            moveInventoryToOffhand(e, Items.TOTEM_OF_UNDYING);
            e.stopUsingItem();
            return;
        }
        if (danger && findItem(e, Items.SHIELD) >= 0) {
            if (!(off.getItem() instanceof ShieldItem)) moveInventoryToOffhand(e, Items.SHIELD);
            if (e.distanceToSqr(e.getTarget()) < 16.0D) e.startUsingItem(InteractionHand.OFF_HAND);
            return;
        }
        if (off.getItem() instanceof ShieldItem) e.stopUsingItem();
    }

    private static void moveInventoryToOffhand(GrowthChildPlayerMobEntity e, net.minecraft.world.item.Item item) {
        SimpleContainer inv = e.getTraitInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).is(item)) continue;
            ItemStack old = e.getOffhandItem().copy();
            e.setItemInHand(InteractionHand.OFF_HAND, inv.getItem(i).split(1));
            if (!old.isEmpty()) inv.addItem(old);
            e.setCurrentTask(item == Items.SHIELD ? "raising shield" : "equipping totem");
            return;
        }
    }

    private static int findItem(GrowthChildPlayerMobEntity e, net.minecraft.world.item.Item item) {
        if (e.getMainHandItem().is(item) || e.getOffhandItem().is(item)) return -2;
        SimpleContainer inv = e.getTraitInventory();
        for (int i=0;i<inv.getContainerSize();i++) if (inv.getItem(i).is(item)) return i;
        return -1;
    }

    private static int weaponScore(ItemStack s, boolean ranged, LivingEntity target) {
        if (s.isEmpty()) return -1;
        if (s.getItem() instanceof BowItem) return ranged ? 130 + enchantValue(s) : 55 + enchantValue(s);
        if (s.getItem() instanceof SwordItem) return ranged ? 75 + enchantValue(s) : 120 + enchantValue(s);
        if (s.getItem() instanceof AxeItem) return ranged ? 65 + enchantValue(s) : 115 + enchantValue(s);
        return -1;
    }

    private static boolean isCombatWeapon(ItemStack s) { return s.getItem() instanceof SwordItem || s.getItem() instanceof AxeItem || s.getItem() instanceof BowItem; }

    private static void updateDailyState(GrowthChildPlayerMobEntity e) {
        if (e.isFollowing() || e.isGuarding() || e.getTarget() != null) return;
        long day = e.level().getDayTime() % 24000L;

        // Real daily routine:
        // 0..1000      wake up / prepare
        // 1000..5500   morning work
        // 5500..7000   lunch break under a tree
        // 7000..11500  afternoon work
        // 11500..12500 return home
        // 12500..24000 sleep/night
        // Combat remains independent and can interrupt any part of this routine.
        if (day >= 12500L) {
            e.setDailyState(GrowthChildPlayerMobEntity.DailyState.NIGHT);
            if (!e.isSleeping()) e.tryClaimAndSleep();
            return;
        }

        // Start the evening return early enough to cover the actual travel
        // distance instead of waiting for nightfall.
        if (e.hasHome() && day >= 7000L) {
            double distance=Math.sqrt(horizontalDistanceSqr(e.position(), e.getHomePos()));
            long travelTicks=(long)Math.ceil(distance * 12.0D);
            if (day + travelTicks >= 12500L) {
                e.setDailyState(GrowthChildPlayerMobEntity.DailyState.RETURN_HOME);
                e.setCurrentTask("returning home before night");
                return;
            }
        }

        if (e.isShiftKeyDown()) e.setShiftKeyDown(false);
        if (e.isSleeping()) {
            e.stopSleeping();
            e.setCurrentTask("waking up");
            e.sayFamily("Good morning. I am getting up.", 20.0D);
        }

        if (day < 1000L) {
            e.setDailyState(GrowthChildPlayerMobEntity.DailyState.MORNING);
        } else if (day < 5500L) {
            e.setDailyState(GrowthChildPlayerMobEntity.DailyState.WORK_MORNING);
        } else if (day < 7000L) {
            e.setDailyState(GrowthChildPlayerMobEntity.DailyState.LUNCH_BREAK);
        } else if (day < 11500L) {
            e.setDailyState(GrowthChildPlayerMobEntity.DailyState.WORK_AFTERNOON);
        } else {
            e.setDailyState(GrowthChildPlayerMobEntity.DailyState.RETURN_HOME);
        }
    }

    private static void handleElytraEquipment(GrowthChildPlayerMobEntity e) {
        ItemStack chest = e.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof ElytraItem) return;
        SimpleContainer inv = e.getTraitInventory();
        for (int i=0;i<inv.getContainerSize();i++) {
            ItemStack s=inv.getItem(i);
            if (!(s.getItem() instanceof ElytraItem) || !ElytraItem.isFlyEnabled(s)) continue;
            if (!chest.isEmpty()) {
                ItemStack left=inv.addItem(chest.copy());
                if (!left.isEmpty()) continue;
            }
            e.setItemSlot(EquipmentSlot.CHEST, inv.getItem(i).split(1));
            e.setCurrentTask("equipping Elytra");
            return;
        }
    }

    private static void tickElytra(GrowthChildPlayerMobEntity e) {
        ItemStack chest = e.getItemBySlot(EquipmentSlot.CHEST);
        boolean equipped = chest.getItem() instanceof ElytraItem && ElytraItem.isFlyEnabled(chest);
        if (!equipped) {
            if (e.isFallFlying()) e.setCombatFallFlying(false);
            return;
        }

        LivingEntity target = e.getTarget();
        if (target == null || !target.isAlive()) target = e.getFollowTarget();

        if (e.isFallFlying()) {
            e.getNavigation().stop();
            if (target != null && target.isAlive()) steerToward(e, target);
            if (e.getPersistentData().getInt(ROCKET_COOLDOWN) == 0 && findRocket(e) >= 0) {
                boostWithFirework(e);
                e.getPersistentData().putInt(ROCKET_COOLDOWN, 18);
            }
            if (e.onGround()) {
                e.setCombatFallFlying(false);
                e.getPersistentData().putBoolean(ELYTRA_ACTIVE, false);
                e.setSprinting(false);
            }
            return;
        }

        // In Follow mode, mirror a Player who is already Elytra-gliding.
        // The child first gets airborne normally; vanilla fall-flying physics
        // then takes over instead of teleporting or fabricating flight.
        if (e.isFollowing() && target != null && target.isAlive() && target.isFallFlying()) {
            if (e.onGround()) {
                e.getNavigation().stop();
                e.getJumpControl().jump();
                e.setSprinting(true);
                e.setCurrentTask("jumping to follow Elytra flight");
                return;
            }
            if (e.getDeltaMovement().y < -0.08D || e.getY() > target.getY() - 2.0D) {
                e.getNavigation().stop();
                e.setSprinting(true);
                e.setCombatFallFlying(true);
                e.getPersistentData().putBoolean(ELYTRA_ACTIVE, true);
                e.setCurrentTask("flying to follow you");
            }
            return;
        }

        // Existing non-Follow Elytra behavior remains intact.
        if (target != null && e.distanceToSqr(target) > 100.0D
                && e.getDeltaMovement().y < -0.2D && e.isSprinting()) {
            e.setCombatFallFlying(true);
            e.getPersistentData().putBoolean(ELYTRA_ACTIVE, true);
            e.setCurrentTask("taking flight");
        }
    }

    private static int findRocket(GrowthChildPlayerMobEntity e) { SimpleContainer inv=e.getTraitInventory(); for(int i=0;i<inv.getContainerSize();i++)if(inv.getItem(i).is(Items.FIREWORK_ROCKET))return i; return -1; }
    private static void boostWithFirework(GrowthChildPlayerMobEntity e) {
        if (!(e.level() instanceof ServerLevel level) || !e.isFallFlying()) return;
        int slot=findRocket(e); if(slot<0)return; ItemStack rocket=e.getTraitInventory().getItem(slot); if(rocket.isEmpty())return;
        rocket.shrink(1); e.getTraitInventory().setItem(slot,rocket); e.getDeltaMovement().add(0,e.getLookAngle().y*.35D,0).add(e.getLookAngle().scale(.65D));
        level.addFreshEntity(new FireworkRocketEntity(level, rocket.copyWithCount(1), e));
        level.playSound(null,e.blockPosition(),SoundEvents.FIREWORK_ROCKET_LAUNCH,e.getSoundSource(),.65F,1.05F);
    }
    private static void steerToward(GrowthChildPlayerMobEntity e, LivingEntity target) { Vec3 a=target.getEyePosition().subtract(e.getEyePosition()); if(a.lengthSqr()<.001)return; Vec3 d=a.normalize(); float yaw=(float)(Math.atan2(-d.x,d.z)*180/Math.PI); float pitch=(float)(-(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))*180/Math.PI)); e.setYRot(yaw);e.setYHeadRot(yaw);e.setXRot(Math.max(-75,Math.min(75,pitch))); }

    private static void updateMovementSkills(GrowthChildPlayerMobEntity e) {
        if (e.isSleeping()) {
            e.setSprinting(false);
            e.getNavigation().stop();
            return;
        }
        // Movement is intentional: never inject random jumping/crouching into an
        // otherwise idle child.  Jumping is reserved for navigation/combat, while
        // swimming is handled continuously so the child can actually surface.
        if (e.isInWater()) {
            e.setShiftKeyDown(false);
            if (e.getDeltaMovement().y < 0.08D) e.getJumpControl().jump();
        } else {
            // Never leave the swimming animation/state latched after the child
            // has actually exited the water.
            e.setSwimming(false);
            if (e.getCombatState() != GrowthChildPlayerMobEntity.CombatState.FLEE
                && e.getCombatState() != GrowthChildPlayerMobEntity.CombatState.FIGHT) {
            e.setShiftKeyDown(false);
            }
        }
        if (e.getCombatState() == GrowthChildPlayerMobEntity.CombatState.FIGHT) {
            e.setSprinting(true);
        }
    }

    private static void explainBlockedTask(GrowthChildPlayerMobEntity e, ServerLevel level) {
        if (e.getPersistentData().getInt(THINK_COOLDOWN) > 0) return;
        String reason = null;
        if (e.getDailyState() == GrowthChildPlayerMobEntity.DailyState.NIGHT && !e.hasOwnBed()) reason = "I cannot sleep because I do not have my own bed yet.";
        else if ((e.getDailyState() == GrowthChildPlayerMobEntity.DailyState.WORK_MORNING || e.getDailyState() == GrowthChildPlayerMobEntity.DailyState.WORK_AFTERNOON) && e.hasHome() && findWorkBlock(e) == null) reason = "I cannot work here because I cannot find a useful block or the right tool.";
        else if (e.getDailyState() == GrowthChildPlayerMobEntity.DailyState.RETURN_HOME && e.hasHome() && !hasNearbyContainer(e)) reason = "I cannot store my supplies because I cannot find a chest near home.";
        else if (e.getTarget() == null && (e.getDailyState() == GrowthChildPlayerMobEntity.DailyState.WORK_MORNING || e.getDailyState() == GrowthChildPlayerMobEntity.DailyState.WORK_AFTERNOON) && e.getTraitInventory().isEmpty()) reason = "I cannot work because my inventory is empty.";
        if (reason != null) { e.setCurrentTask("blocked"); e.sayFamily(reason, 20.0D); e.getPersistentData().putInt(THINK_COOLDOWN, 240); }
    }

    private static void reportEnvironmentAndNeeds(GrowthChildPlayerMobEntity e, ServerLevel level) {
        if (e.getPersistentData().getInt(REPORT_COOLDOWN) > 0) return;

        // Combat reports describe what the child actually sees and whether its
        // current state says it can reasonably fight it.
        Monster threat = nearestThreat(e);
        if (threat != null) {
            if (e.getHealth() <= e.getMaxHealth() * 0.32F) {
                e.setCombatState(GrowthChildPlayerMobEntity.CombatState.FLEE);
                e.setCurrentTask("low health: retreating from " + threat.getType().getDescription().getString());
                e.sayFamily("I see a " + threat.getType().getDescription().getString() + ". My health is low, so I am going to retreat.", 20.0D);
                e.getPersistentData().putInt(REPORT_COOLDOWN, 180);
                return;
            }
            if (e.getCombatState() != GrowthChildPlayerMobEntity.CombatState.FLEE
                    && e.getCombatState() != GrowthChildPlayerMobEntity.CombatState.FIGHT
                    && e.getTarget() == null) {
                String name = threat.getType().getDescription().getString();
                boolean warden = threat instanceof Warden;
                boolean outnumbered = level.getEntitiesOfClass(Monster.class, e.getBoundingBox().inflate(10.0D), Monster::isAlive).size() >= 3;
                boolean equipped = isCombatWeapon(e.getMainHandItem()) || hasCombatWeapon(e);
                boolean canFight = !warden && !outnumbered && equipped && e.getHealth() > e.getMaxHealth() * 0.50F;
                e.setCurrentTask(canFight ? "assessing " + name + ": can fight" : "assessing " + name + ": will retreat");
                e.sayFamily("I see a " + name + ". " + (canFight ? "I can fight it." : "I do not think I can safely fight it, so I will keep my distance."), 20.0D);
                e.getPersistentData().putInt(REPORT_COOLDOWN, 180);
                return;
            }
        }

        if (e.getDailyState() == GrowthChildPlayerMobEntity.DailyState.NIGHT && !e.isSleeping()) {
            if (!e.hasOwnBed()) {
                e.setCurrentTask("looking for a bed");
                e.sayFamily("It is nighttime. I need a bed so I can sleep and set my respawn point.", 20.0D);
                e.getPersistentData().putInt(REPORT_COOLDOWN, 200);
                return;
            }
        }

        // Announce nearby dropped items that the physical pickup goal can reach.
        var items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, e.getBoundingBox().inflate(2.0D),
                x -> x.isAlive() && !x.getItem().isEmpty() && !x.hasPickUpDelay());
        if (!items.isEmpty()) {
            ItemStack found = items.get(0).getItem();
            e.setCurrentTask("found " + found.getHoverName().getString());
            e.sayFamily("I found " + found.getHoverName().getString() + ". I am going to pick it up.", 20.0D);
            e.getPersistentData().putInt(REPORT_COOLDOWN, 180);
            return;
        }

        BlockPos work = findWorkBlock(e);
        if ((e.getDailyState() == GrowthChildPlayerMobEntity.DailyState.WORK_MORNING || e.getDailyState() == GrowthChildPlayerMobEntity.DailyState.WORK_AFTERNOON) && work != null) {
            BlockState state = level.getBlockState(work);
            String block = state.getBlock().getName().getString();
            e.setCurrentTask("found " + block);
            if (!hasCorrectTool(e, state)) {
                e.sayFamily("I found " + block + ", but I need the correct tool to mine it.", 20.0D);
            } else {
                e.sayFamily("I found " + block + ". I am going to work on it.", 20.0D);
            }
            e.getPersistentData().putInt(REPORT_COOLDOWN, 200);
            return;
        }
    }

    private static boolean hasCombatWeapon(GrowthChildPlayerMobEntity e) {
        for (int i = 0; i < e.getTraitInventory().getContainerSize(); i++) {
            if (isCombatWeapon(e.getTraitInventory().getItem(i))) return true;
        }
        return false;
    }

    private static boolean shouldFlee(GrowthChildPlayerMobEntity e) {
        if (e.isStealthThreat()) return true;
        if (e.getHealth() <= e.getMaxHealth()*.32F) return true;

        List<Monster> ms = e.level().getEntitiesOfClass(
                Monster.class, e.getBoundingBox().inflate(12.0D), Monster::isAlive);
        long creepers = ms.stream().filter(m -> m instanceof Creeper).count();

        // Two or more creepers are treated as an emergency: do not tunnel into
        // one target. Back away while considering the whole threat group.
        if (creepers >= 2) return true;
        return ms.size() >= 3
                || (e.getTarget()!=null && e.getTarget().getHealth()>e.getMaxHealth()*2.2F);
    }
    private static Monster nearestThreat(GrowthChildPlayerMobEntity e) { return e.level().getEntitiesOfClass(Monster.class,e.getBoundingBox().inflate(16),Monster::isAlive).stream().min((a,b)->Double.compare(a.distanceToSqr(e),b.distanceToSqr(e))).orElse(null); }
    private static Vec3 safeFleePoint(GrowthChildPlayerMobEntity e, Monster threat) {
        List<Monster> threats = e.level().getEntitiesOfClass(
                Monster.class, e.getBoundingBox().inflate(12.0D), Monster::isAlive);

        Vec3 away = new Vec3(0, 0, 0);
        for (Monster m : threats) {
            Vec3 delta = e.position().subtract(m.position());
            double d2 = Math.max(1.0D, delta.lengthSqr());
            away = away.add(delta.normalize().scale(1.0D / Math.min(d2, 64.0D)));
        }
        if (away.lengthSqr() < .001D) away = e.position().subtract(threat.position());
        if (away.lengthSqr() < .001D) away = new Vec3(1,0,0);
        away = away.normalize();

        BlockPos best=null; double bestScore=-Double.MAX_VALUE;
        for(int i=0;i<24;i++){
            double angle=i*Math.PI/12.0D;
            Vec3 d=new Vec3(
                    away.x*Math.cos(angle)-away.z*Math.sin(angle),
                    0,
                    away.x*Math.sin(angle)+away.z*Math.cos(angle)).normalize();
            Vec3 candidate=clampToResponsibilityCenter(e,e.position().add(d.scale(5.0D + (i % 3) * 2.0D)),0.25D);
            BlockPos p=BlockPos.containing(candidate);
            BlockState state=e.level().getBlockState(p);
            if(!state.getFluidState().isEmpty() || state.is(Blocks.LAVA) || state.is(Blocks.FIRE)) continue;

            double nearestThreat=Double.MAX_VALUE;
            for(Monster m: threats) nearestThreat=Math.min(nearestThreat, p.distSqr(m.blockPosition()));
            double score=nearestThreat*2.0D-p.distSqr(e.blockPosition());
            if(score>bestScore){bestScore=score;best=p;}
        }
        return best==null ? clampToResponsibilityCenter(e,e.position().add(away.scale(5.0D)),0.25D)
                : Vec3.atCenterOf(best);
    }

    private static void applyDefaultRandomBehavior(GrowthChildPlayerMobEntity e) {
        int cd = e.getPersistentData().getInt(DEFAULT_RANDOM_COOLDOWN);
        if (cd > 0) return;
        // Default never chooses FOLLOW. Follow is an explicit social request/acceptance flow.
        int pick = e.getRandom().nextInt(4);
        switch (pick) {
            case 0 -> {
                e.setCurrentIntent(GrowthChildPlayerMobEntity.AIIntent.HOLD_POSITION);
                e.setCurrentTask("holding position");
            }
            case 1 -> {
                e.setCurrentIntent(GrowthChildPlayerMobEntity.AIIntent.WANDER_HOME);
                e.setCurrentTask("wandering around home");
            }
            case 2 -> {
                e.setCurrentIntent(GrowthChildPlayerMobEntity.AIIntent.WORKING);
                e.setCurrentTask("working");
            }
            default -> {
                e.setCurrentIntent(GrowthChildPlayerMobEntity.AIIntent.GO_HOME);
                e.setCurrentTask("returning home");
            }
        }
        e.getPersistentData().putInt(DEFAULT_RANDOM_COOLDOWN, 1200 + e.getRandom().nextInt(1200));
    }

    private static boolean hasFollowRequest(GrowthChildPlayerMobEntity e) {
        return e.getPersistentData().hasUUID(FOLLOW_REQUESTER_UUID)
                && e.getPersistentData().getInt(FOLLOW_REQUEST_TICKS) > 0;
    }

    private static void tickFollowRequest(GrowthChildPlayerMobEntity e, ServerLevel level) {
        if (!hasFollowRequest(e)) {
            e.getPersistentData().remove(FOLLOW_REQUESTER_UUID);
            e.getPersistentData().remove(FOLLOW_REQUEST_TICKS);
            return;
        }
        int ticks = e.getPersistentData().getInt(FOLLOW_REQUEST_TICKS) - 1;
        e.getPersistentData().putInt(FOLLOW_REQUEST_TICKS, ticks);
        UUID id = e.getPersistentData().getUUID(FOLLOW_REQUESTER_UUID);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
        if (player == null || !player.isAlive() || !e.isAuthorized(player)) {
            e.getPersistentData().remove(FOLLOW_REQUESTER_UUID);
            e.getPersistentData().remove(FOLLOW_REQUEST_TICKS);
            return;
        }
        // Stand in front of the player, face them, and wait for one right-click acceptance.
        Vec3 front = player.position().add(player.getLookAngle().normalize().scale(1.55D));
        double d = e.distanceToSqr(front);
        if (d > 2.25D) {
            e.getNavigation().moveTo(front.x, front.y, front.z, 1.0D);
        } else {
            e.getNavigation().stop();
            e.setSprinting(false);
            e.getLookControl().setLookAt(player, 30, 30);
        }
        if (ticks <= 0) {
            e.getPersistentData().remove(FOLLOW_REQUESTER_UUID);
            e.getPersistentData().remove(FOLLOW_REQUEST_TICKS);
            e.getPersistentData().putInt(FOLLOW_REQUEST_COOLDOWN, 1200 + e.getRandom().nextInt(1200));
            e.setCurrentTask("returning to routine");
        }
    }

    private static final class FollowRequestGoal extends Goal {
        final GrowthChildPlayerMobEntity e;
        FollowRequestGoal(GrowthChildPlayerMobEntity e){this.e=e;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        public boolean canUse(){
            if(e.getTarget()!=null||e.isFollowing()||e.isGuarding()||e.isSleeping()||hasFollowRequest(e)) return false;
            if(e.getPersistentData().getInt(FOLLOW_REQUEST_COOLDOWN)>0) return false;
            if(e.getCurrentIntent()!=GrowthChildPlayerMobEntity.AIIntent.IDLE) return false;
            if(e.getRandom().nextInt(2400)!=0) return false;
            ServerLevel level = (ServerLevel)e.level();
            ServerPlayer p = level.getServer().getPlayerList().getPlayers().stream()
                    .filter(x -> x.isAlive() && e.isAuthorized(x) && x.distanceToSqr(e) <= 12.0D*12.0D)
                    .min((a,b)->Double.compare(a.distanceToSqr(e),b.distanceToSqr(e))).orElse(null);
            if(p==null) return false;
            e.getPersistentData().putUUID(FOLLOW_REQUESTER_UUID,p.getUUID());
            e.getPersistentData().putInt(FOLLOW_REQUEST_TICKS,200);
            e.setCurrentTask("asking to follow " + p.getName().getString());
            return true;
        }
        public boolean canContinueToUse(){return hasFollowRequest(e);}
        public void start(){
            if(!(e.level() instanceof ServerLevel level)||!hasFollowRequest(e))return;
            ServerPlayer p=level.getServer().getPlayerList().getPlayer(e.getPersistentData().getUUID(FOLLOW_REQUESTER_UUID));
            if(p==null)return;
            e.getLookControl().setLookAt(p,30,30);
            e.swingMainHand();
            e.chatToPlayer(p,"May I follow you?");
        }
        public void tick(){tickFollowRequest(e,(ServerLevel)e.level());}
    }

    private static final class FollowGoal extends Goal {
        final GrowthChildPlayerMobEntity e;
        FollowGoal(GrowthChildPlayerMobEntity e){this.e=e;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}

        public boolean canUse(){
            return e.getTarget()==null
                    && e.getCurrentIntent()==GrowthChildPlayerMobEntity.AIIntent.FOLLOW
                    && e.isFollowing()
                    && e.getFollowTarget()!=null
                    && e.getFollowTarget().isAlive();
        }

        public boolean canContinueToUse(){
            return e.getCurrentIntent()==GrowthChildPlayerMobEntity.AIIntent.FOLLOW
                    && e.isFollowing()
                    && e.getFollowTarget()!=null
                    && e.getFollowTarget().isAlive()
                    && e.getTarget()==null;
        }

        public void start(){
            e.getPersistentData().putInt(FOLLOW_PATH_FAIL_TICKS, 0);
            e.getPersistentData().putBoolean(FOLLOW_SURFACE_MODE, false);
        }

        public void stop(){
            e.getNavigation().stop();
            e.setSprinting(false);
            e.setSwimming(false);
            }

        public void tick(){
            LivingEntity t=e.getFollowTarget();
            if(t==null || !t.isAlive()){
                failFollowToHome(e, "I lost you and could not continue following. I am returning home.");
                return;
            }

            // Survival has priority over following. Never stay underwater when
            // the remaining air is insufficient to reach a real water surface.
            if (e.isUnderWater() || e.isInWater()) {
                if (followNeedsSurface(e)) {
                    followSurface(e);
                    return;
                }
                e.getPersistentData().putBoolean(FOLLOW_SURFACE_MODE, false);
                e.setSwimming(true);
                e.getNavigation().stop();
                e.setSprinting(false);
                moveInWaterToward(e, t);
                e.getLookControl().setLookAt(t,30,30);
                return;
            }

            // Once the child has surfaced, recover enough air before trying to
            // descend after the player again. This prevents threshold oscillation.
            if (e.getPersistentData().getBoolean(FOLLOW_SURFACE_MODE)) {
                if (e.getAirSupply() < 225) {
                    followSurface(e);
                    return;
                }
                e.getPersistentData().putBoolean(FOLLOW_SURFACE_MODE, false);
            }

            // A following child with an equipped Elytra is allowed to join the
            // player's actual fall-flying state. tickElytra owns the transition
            // into vanilla fall-flying physics; the Follow goal only relinquishes
            // ground navigation while that state is active.
            if (e.isFallFlying()) {
                e.getNavigation().stop();
                e.setSprinting(true);
                e.setCurrentTask("flying to follow you");
                e.getLookControl().setLookAt(t,30,30);
                return;
            }

            double d=e.distanceToSqr(t);
            e.setCurrentTask(d>16.0D?"running to follow you":"following you");
            if(d>9.0D){
                boolean farEnoughToSprint = d > 16.0D;
                e.setSprinting(farEnoughToSprint);
                e.getNavigation().moveTo(t, 1.0D);

                var path=e.getNavigation().getPath();
                if (e.onGround() && path != null && !path.isDone()) {
                    BlockPos next=path.getNextNodePos();
                    if(next != null && next.getY() > e.blockPosition().getY()) e.getJumpControl().jump();
                    else if(e.horizontalCollision) e.getJumpControl().jump();
                }

                if (e.getNavigation().isDone()) {
                    int failed=e.getPersistentData().getInt(FOLLOW_PATH_FAIL_TICKS)+1;
                    e.getPersistentData().putInt(FOLLOW_PATH_FAIL_TICKS, failed);
                    if (failed >= 200) {
                        failFollowToHome(e, "I could not reach you after trying to follow. I am returning home.");
                        return;
                    }
                } else {
                    e.getPersistentData().putInt(FOLLOW_PATH_FAIL_TICKS, 0);
                }
            }else{
                e.setSprinting(false);
                e.getNavigation().stop();
                e.getPersistentData().putInt(FOLLOW_PATH_FAIL_TICKS, 0);
            }
            e.getLookControl().setLookAt(t,30,30);
        }
    }

    private static void failFollowToHome(GrowthChildPlayerMobEntity e, String message) {
        e.getNavigation().stop();
        e.setSprinting(false);
        e.setSwimming(false);
        if (e.isFallFlying()) e.setCombatFallFlying(false);
        e.getPersistentData().putBoolean(FOLLOW_SURFACE_MODE, false);
        e.getPersistentData().putInt(FOLLOW_PATH_FAIL_TICKS, 0);
        e.setFollowTarget(null);
        e.setCurrentIntent(e.hasHome()
                ? GrowthChildPlayerMobEntity.AIIntent.GO_HOME
                : GrowthChildPlayerMobEntity.AIIntent.IDLE);
        e.setCurrentTask(e.hasHome()?"follow failed: returning home":"follow failed");
        if (message != null) e.sayFamily(message, 20.0D);
    }

    private static boolean followNeedsSurface(GrowthChildPlayerMobEntity e) {
        int air=Math.max(0,e.getAirSupply());
        int maxAir=300;
        if (air <= 40) return true;

        BlockPos surface=findWaterSurface(e);
        if(surface==null) return true;

        double distance=Math.sqrt(e.distanceToSqr(surface.getX()+0.5D,surface.getY()+1.0D,surface.getZ()+0.5D));
        // Conservative swimming estimate plus a fixed safety reserve. One
        // air unit is one vanilla tick, so this deliberately errs on the safe side.
        double swimSpeed=0.18D;
        int travelTicks=(int)Math.ceil(distance/swimSpeed);
        int reserve=Math.max(40,(int)(maxAir*0.12D));
        boolean need=air <= travelTicks+reserve;
        if(need) e.getPersistentData().putBoolean(FOLLOW_SURFACE_MODE,true);
        return need;
    }

    private static void followSurface(GrowthChildPlayerMobEntity e) {
        BlockPos surface=findWaterSurface(e);
        if(surface==null){
            failFollowToHome(e,"I could not find a safe water surface. I am returning home.");
            return;
        }
        e.getPersistentData().putBoolean(FOLLOW_SURFACE_MODE,true);
        e.setSwimming(true);
        e.setSprinting(false);
        e.getNavigation().stop();
        e.getMoveControl().setWantedPosition(surface.getX()+0.5D,surface.getY()+1.0D,surface.getZ()+0.5D,1.0D);
        e.setCurrentTask("surfacing to recover oxygen");
    }

    private static void moveInWaterToward(GrowthChildPlayerMobEntity e, LivingEntity target) {
        Vec3 destination=target.position().add(0.0D, target.isUnderWater()?0.0D:0.5D, 0.0D);
        e.getMoveControl().setWantedPosition(destination.x,destination.y,destination.z,1.0D);
    }

    private static BlockPos findWaterSurface(GrowthChildPlayerMobEntity e) {
        BlockPos base=e.blockPosition();
        if(e.level().getBlockState(base).getFluidState().isEmpty()) return base;
        for(int dy=0;dy<=128;dy++){
            BlockPos p=base.above(dy);
            BlockPos above=p.above();
            if(e.level().getBlockState(p).getFluidState().isEmpty()) return null;
            if(e.level().getBlockState(above).getFluidState().isEmpty()) return p;
        }
        return null;
    }
    private static final class HoldPositionGoal extends Goal {
        final GrowthChildPlayerMobEntity e;
        HoldPositionGoal(GrowthChildPlayerMobEntity e){this.e=e;setFlags(EnumSet.of(Flag.MOVE));}
        public boolean canUse(){return e.getCurrentIntent()==GrowthChildPlayerMobEntity.AIIntent.HOLD_POSITION&&!e.isSleeping();}
        public boolean canContinueToUse(){return canUse();}
        public void tick(){
            Vec3 hold = getHoldPosition(e);
            if (hold == null) hold = e.position();
            double d2=e.distanceToSqr(hold.x,hold.y,hold.z);
            if(d2 > 1.0D){
                Vec3 destination=hold;
                e.setCurrentTask("returning to held position");
                e.setSprinting(false);
                e.getNavigation().moveTo(destination.x,destination.y,destination.z,1.0D);
            }else{
                e.setCurrentTask("holding position");
                e.getNavigation().stop();
                e.setSprinting(false);
            }
        }
    }

    private static final class WanderHomeGoal extends Goal {
        final GrowthChildPlayerMobEntity e; BlockPos destination; int cooldown;
        WanderHomeGoal(GrowthChildPlayerMobEntity e){this.e=e;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        public boolean canUse(){
            if(e.getCurrentIntent()!=GrowthChildPlayerMobEntity.AIIntent.WANDER_HOME||e.getTarget()!=null||e.isSleeping())return false;
            if(cooldown>0){cooldown--;return false;}
            destination=findDestination();
            return destination!=null;
        }
        public boolean canContinueToUse(){return e.getCurrentIntent()==GrowthChildPlayerMobEntity.AIIntent.WANDER_HOME&&e.getTarget()==null&&!e.isSleeping()&&destination!=null&&!e.getNavigation().isDone();}
        public void start(){e.setCurrentTask("wandering around home");}
        public void tick(){
            if(destination==null)return;
            e.setCurrentTask("wandering around home");
            e.getNavigation().moveTo(destination.getX()+.5D,destination.getY(),destination.getZ()+.5D,1.0D);
            e.getLookControl().setLookAt(destination.getX()+.5D,destination.getY(),destination.getZ()+.5D,30,30);
            if(e.distanceToSqr(destination.getX()+.5D,destination.getY(),destination.getZ()+.5D)<3.0D){destination=null;cooldown=80;}
        }
        private BlockPos findDestination(){
            BlockPos base=e.hasHome()?e.getHomePos():e.blockPosition();
            for(int attempt=0;attempt<12;attempt++){
                double angle=e.getRandom().nextDouble()*Math.PI*2.0D;
                double radius=Math.sqrt(e.getRandom().nextDouble())*HOME_AREA_RADIUS;
                int x=base.getX()+(int)Math.round(Math.cos(angle)*radius);
                int z=base.getZ()+(int)Math.round(Math.sin(angle)*radius);
                int y=base.getY();
                BlockPos p=new BlockPos(x,y,z);
                if(e.level().getBlockState(p).getCollisionShape(e.level(),p).isEmpty()
                        && e.level().getBlockState(p.below()).isFaceSturdy(e.level(),p.below(),net.minecraft.core.Direction.UP)) return p;
            }
            return null;
        }
    }

    private static final class GuardGoal extends Goal { final GrowthChildPlayerMobEntity e; GuardGoal(GrowthChildPlayerMobEntity e){this.e=e;setFlags(EnumSet.of(Flag.MOVE));} public boolean canUse(){return e.getTarget()==null&&e.isGuarding()&&e.getGuardAnchor()!=null;} public boolean canContinueToUse(){return canUse();} public void tick(){e.setCombatState(GrowthChildPlayerMobEntity.CombatState.GUARD);e.setCurrentTask("guarding");BlockPos p=e.getGuardAnchor();if(p!=null&&e.distanceToSqr(p.getX()+.5,p.getY(),p.getZ()+.5)>4)e.getNavigation().moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,1.0D);}}
    private static final class EmergencyThreatGoal extends Goal { final GrowthChildPlayerMobEntity e; EmergencyThreatGoal(GrowthChildPlayerMobEntity e){this.e=e;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));} public boolean canUse(){return e.isStealthThreat();} public boolean canContinueToUse(){return e.isStealthThreat();} public void tick(){e.setTarget(null);e.setShiftKeyDown(true);Monster t=nearestThreat(e);if(t!=null){Vec3 flee=safeFleePoint(e,t);e.getNavigation().moveTo(flee.x,flee.y,flee.z,1.2D);}}}
    /**
     * Home alarm interaction: when the child has a home and can actually see a
     * Pillager from inside that home area, it uses a nearby bell as a real
     * interaction. Direct combat/self-defense always wins because this goal only
     * starts while no combat target is locked.
     */
    private static final class HomeAlarmGoal extends Goal {
        final GrowthChildPlayerMobEntity e;
        BlockPos bell;
        Pillager threat;
        HomeAlarmGoal(GrowthChildPlayerMobEntity e) {
            this.e = e;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        public boolean canUse() {
            if (!e.hasHome() || e.getTarget() != null || shouldFlee(e) || e.isSleeping()) return false;
            if (e.getPersistentData().getInt(HOME_ALARM_COOLDOWN) > 0) return false;
            if (horizontalDistanceSqr(e.position(), Vec3.atCenterOf(e.getHomePos())) > HOME_AREA_RADIUS * HOME_AREA_RADIUS) return false;
            threat = findVisibleHomeAlarmThreat(e);
            if (threat == null) return false;
            bell = findHomeBell(e);
            return bell != null;
        }
        public boolean canContinueToUse() {
            return e.getPersistentData().getInt(HOME_ALARM_ACTIVE) != 0
                    && e.getTarget() == null && !shouldFlee(e) && bell != null
                    && e.hasHome();
        }
        public void start() {
            e.getPersistentData().putInt(HOME_ALARM_ACTIVE, 1);
            e.setCurrentTask("running to the home bell");
            e.setCombatState(GrowthChildPlayerMobEntity.CombatState.GUARD);
        }
        public void stop() {
            e.getPersistentData().putInt(HOME_ALARM_ACTIVE, 0);
            e.getNavigation().stop();
        }
        public void tick() {
            if (e.getTarget() != null || shouldFlee(e) || bell == null) { stop(); return; }
            double d = e.distanceToSqr(bell.getX() + .5D, bell.getY() + .5D, bell.getZ() + .5D);
            if (d > 4.0D) {
                e.setCurrentTask("running to the home bell");
                e.setSprinting(d > 36.0D);
                e.getNavigation().moveTo(bell.getX() + .5D, bell.getY(), bell.getZ() + .5D, 1.2D);
                return;
            }
            e.setSprinting(false);
            e.getNavigation().stop();
            e.getLookControl().setLookAt(bell.getX() + .5D, bell.getY() + .5D, bell.getZ() + .5D, 30.0F, 30.0F);
            if (e.level().getBlockState(bell).getBlock() instanceof BellBlock) {
                e.swingInteraction();
                boolean rang = ((BellBlock) e.level().getBlockState(bell).getBlock()).attemptToRing(e, e.level(), bell, Direction.UP);
                if (rang) {
                    e.setCurrentTask("sounding the home alarm");
                    e.sayFamily("I saw a Pillager. I am sounding the home bell.", 24.0D);
                    e.getPersistentData().putInt(HOME_ALARM_COOLDOWN, 400);
                }
            }
            stop();
        }
    }

    private static Pillager findVisibleHomeAlarmThreat(GrowthChildPlayerMobEntity e) {
        Vec3 home = Vec3.atCenterOf(e.getHomePos());
        return e.level().getEntitiesOfClass(Pillager.class, e.getBoundingBox().inflate(HOME_ALARM_SCAN_RADIUS),
                p -> p.isAlive()
                        && horizontalDistanceSqr(p.position(), home) <= HOME_AREA_RADIUS * HOME_AREA_RADIUS
                        && e.distanceToSqr(p) <= HOME_ALARM_SCAN_RADIUS * HOME_ALARM_SCAN_RADIUS
                        && e.hasLineOfSight(p))
                .stream().min((a, b) -> Double.compare(a.distanceToSqr(e), b.distanceToSqr(e))).orElse(null);
    }

    private static BlockPos findHomeBell(GrowthChildPlayerMobEntity e) {
        var tag = e.getPersistentData();
        if (tag.getInt(BELL_SEARCH_COOLDOWN) > 0 && tag.contains(HOME_ALARM_BELL_X)) {
            BlockPos saved = new BlockPos(tag.getInt(HOME_ALARM_BELL_X), tag.getInt(HOME_ALARM_BELL_Y), tag.getInt(HOME_ALARM_BELL_Z));
            if (e.level().getBlockState(saved).getBlock() instanceof BellBlock) return saved;
        }
        BlockPos home = e.getHomePos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(home.offset(-32, -4, -32), home.offset(32, 4, 32))) {
            if (!(e.level().getBlockState(p).getBlock() instanceof BellBlock)) continue;
            if (horizontalDistanceSqr(p.getCenter(), Vec3.atCenterOf(home)) > HOME_AREA_RADIUS * HOME_AREA_RADIUS) continue;
            double d = e.distanceToSqr(p.getX() + .5D, p.getY() + .5D, p.getZ() + .5D);
            if (d < bestD) { bestD = d; best = p.immutable(); }
        }
        tag.putInt(BELL_SEARCH_COOLDOWN, 40);
        if (best != null) {
            tag.putInt(HOME_ALARM_BELL_X, best.getX());
            tag.putInt(HOME_ALARM_BELL_Y, best.getY());
            tag.putInt(HOME_ALARM_BELL_Z, best.getZ());
        }
        return best;
    }

    private static final class FleeGoal extends Goal { final GrowthChildPlayerMobEntity e; FleeGoal(GrowthChildPlayerMobEntity e){this.e=e;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));} public boolean canUse(){return shouldFlee(e)&&!e.isStealthThreat();} public boolean canContinueToUse(){return shouldFlee(e);} public void tick(){Monster t=nearestThreat(e);if(t==null)return;BlockPos p=BlockPos.containing(safeFleePoint(e,t));e.setCombatState(GrowthChildPlayerMobEntity.CombatState.FLEE);e.setCurrentTask("fleeing");e.getNavigation().moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,1.18D);}}
    private static final class CombatGoal extends Goal {
        final GrowthChildPlayerMobEntity e;
        CombatGoal(GrowthChildPlayerMobEntity e){this.e=e;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        public boolean canUse(){return resolveCombatTarget(e,(ServerLevel)e.level())!=null&&!shouldFlee(e);}
        public boolean canContinueToUse(){return canUse();}
        public void tick(){
            LivingEntity t=resolveCombatTarget(e,(ServerLevel)e.level());
            if(t==null||!t.isAlive()){ clearCombatTarget(e); return;}
            e.setCombatState(GrowthChildPlayerMobEntity.CombatState.FIGHT);
            e.setCurrentTask("fighting");
            e.getLookControl().setLookAt(t,30,30);
            double d=e.distanceToSqr(t);

            Vec3 combatDestination = clampToResponsibilityCenter(e, t.position(), 0.25D);
            boolean creeper = t instanceof Creeper;
            boolean hasBow = e.getMainHandItem().getItem() instanceof BowItem || hasBow(e);

            if (creeper && shouldFlee(e)) return;

            if (creeper && !hasBow && d < 49.0D) {
                Vec3 delta=e.position().subtract(t.position());
                if(delta.lengthSqr()<.001D) delta=new Vec3(1,0,0);
                Vec3 retreat=clampToResponsibilityCenter(e,e.position().add(delta.normalize().scale(4.5D)),0.25D);
                e.getNavigation().moveTo(retreat.x,retreat.y,retreat.z,1.0D);
                e.setSprinting(true);
            } else if(d > 3.5D) {
                e.getNavigation().moveTo(combatDestination.x,combatDestination.y,combatDestination.z,1.0D);
                e.setSprinting(d > 16.0D && !creeper);
            } else {
                e.getNavigation().stop();
                e.setSprinting(false);
            }

            if (!creeper && d < 16.0D && d > 5.0D && e.getRandom().nextInt(8) == 0) {
                Vec3 side = new Vec3(-(t.getZ()-e.getZ()), 0, t.getX()-e.getX()).normalize();
                double sign = e.getRandom().nextBoolean() ? 1.0D : -1.0D;
                Vec3 step = clampToResponsibilityCenter(e,e.position().add(side.scale(sign * 2.5D)),0.25D);
                e.getNavigation().moveTo(step.x, step.y, step.z, 1.0D);
            }
            int cd=e.getPersistentData().getInt(ATTACK_COOLDOWN);
            if(cd>0) return;

            ItemStack w=e.getMainHandItem();
            if(w.getItem() instanceof BowItem && hasArrow(e) && d<=256
                    && (!creeper || d > 5.0D)){
                e.performRangedAttack(t,1.6F);
                e.swingMainHand();
                damageMainHand(e,1);
                e.getPersistentData().putInt(ATTACK_COOLDOWN,20);
                return;
            }

            if(d<=3.5D){
                // Keep the visible player-like hand swing on every melee attack.
                // A sword also gets the vanilla-style sweep behavior on a normal
                // grounded hit. A hit made while descending from a jump is a crit.
                e.swingMainHand();
                boolean critical = isCriticalHit(e);
                boolean hit = performPlayerLikeMeleeAttack(e, t, critical);
                if(hit) damageMainHand(e,1);
                e.getPersistentData().putInt(ATTACK_COOLDOWN,10);
            } else if(d<9D&&e.getRandom().nextInt(5)==0) {
                e.getJumpControl().jump();
            }
        }
        private boolean hasArrow(GrowthChildPlayerMobEntity e){for(int i=0;i<e.getTraitInventory().getContainerSize();i++)if(e.getTraitInventory().getItem(i).is(Items.ARROW))return true;return false;}
    }
    private static boolean isCriticalHit(GrowthChildPlayerMobEntity e) {
        return !e.onGround()
                && e.fallDistance > 0.0F
                && !e.isInWater()
                && !e.onClimbable()
                && !e.isPassenger()
                && !e.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                && !e.isSprinting();
    }

    private static boolean performPlayerLikeMeleeAttack(GrowthChildPlayerMobEntity e, LivingEntity target, boolean critical) {
        ItemStack weapon = e.getMainHandItem();
        float baseDamage = (float)e.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (baseDamage <= 0.0F) baseDamage = 1.0F;

        // Use the normal Mob attack path first so enchantments, knockback and
        // weapon durability continue to behave like ordinary Minecraft combat.
        boolean hit = e.doHurtTarget(target);
        if (!hit) return false;

        if (critical) {
            // Mob.doHurtTarget already dealt the normal hit. Add the vanilla-like
            // critical bonus so the total is 1.5x base attack damage.
            target.hurt(e.damageSources().mobAttack(e), baseDamage * 0.5F);
            e.level().broadcastEntityEvent(e, (byte)4);
        } else if (weapon.getItem() instanceof SwordItem && !e.isSprinting() && e.onGround()) {
            // Vanilla-style sword sweep: damage nearby hostile mobs around the
            // primary target, but never family/authorized players.
            List<LivingEntity> nearby = e.level().getEntitiesOfClass(
                    LivingEntity.class,
                    target.getBoundingBox().inflate(1.0D, 0.25D, 1.0D),
                    other -> other.isAlive()
                            && other != e
                            && other != target
                            && (other instanceof Monster || other instanceof Slime));
            for (LivingEntity other : nearby) {
                if (!(other instanceof Monster) && !(other instanceof Slime)) continue;
                if (other.distanceToSqr(e) > 9.0D) continue;
                other.hurt(e.damageSources().mobAttack(e), baseDamage * 0.2F);
                double dx = other.getX() - e.getX();
                double dz = other.getZ() - e.getZ();
                other.knockback(0.4D, dx, dz);
            }
            if (e.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5D,
                        target.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        return true;
    }

    private static final class BedGoal extends Goal { final GrowthChildPlayerMobEntity e; BedGoal(GrowthChildPlayerMobEntity e){this.e=e;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));} public boolean canUse(){return e.getDailyState()==GrowthChildPlayerMobEntity.DailyState.NIGHT&&e.getTarget()==null&&!e.isSleeping();} public boolean canContinueToUse(){return canUse()&&!e.isSleeping();} public void tick(){e.setCurrentTask("finding bed");e.tryClaimAndSleep();if(e.hasOwnBed())e.setCurrentTask("sleeping");}}
    private static final class LunchBreakGoal extends Goal {
        final GrowthChildPlayerMobEntity e;
        BlockPos tree;
        LunchBreakGoal(GrowthChildPlayerMobEntity e){this.e=e;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        public boolean canUse(){
            if(e.getDailyState()!=GrowthChildPlayerMobEntity.DailyState.LUNCH_BREAK) return false;
            if(e.getTarget()!=null||e.isFollowing()||e.isGuarding()||e.isSleeping()) return false;
            tree=findNearbyTree(e);
            return tree!=null;
        }
        public boolean canContinueToUse(){
            return e.getDailyState()==GrowthChildPlayerMobEntity.DailyState.LUNCH_BREAK
                    && e.getTarget()==null && !e.isFollowing() && !e.isGuarding() && !e.isSleeping();
        }
        public void start(){e.setCurrentTask("going to lunch under a tree");}
        public void tick(){
            if(tree==null) tree=findNearbyTree(e);
            if(tree==null) return;
            if(e.hasHome() && horizontalDistanceSqr(e.position(), e.getHomePos()) > 8.0D*8.0D){
                BlockPos h=e.getHomePos();
                e.setCurrentTask("returning home for midday break");
                e.getNavigation().moveTo(h.getX()+.5D,h.getY(),h.getZ()+.5D,1.0D);
                return;
            }
            double x=tree.getX()+.5D, y=tree.getY(), z=tree.getZ()+.5D;
            if(e.distanceToSqr(x,y,z)>4.0D){
                e.getNavigation().moveTo(x,y,z,1.0D);
                return;
            }
            e.getNavigation().stop();
            e.setSprinting(false);
            e.setShiftKeyDown(true);
            if(e.hasHome() && horizontalDistanceSqr(e.position(), e.getHomePos()) <= 8.0D*8.0D && !e.getTraitInventory().isEmpty()){
                e.openAndDepositNearbyContainers();
            }
            e.setCurrentTask("taking lunch under a tree");
            e.getLookControl().setLookAt(x, y+2.0D, z, 20.0F, 20.0F);
        }
        private static BlockPos findNearbyTree(GrowthChildPlayerMobEntity e){
            BlockPos base=e.hasHome()?e.getHomePos():e.blockPosition();
            BlockPos best=null; double bestD=Double.MAX_VALUE;
            for(BlockPos p:BlockPos.betweenClosed(base.offset(-16,-2,-16),base.offset(16,6,16))){
                BlockState s=e.level().getBlockState(p);
                if(!s.is(BlockTags.LOGS)) continue;
                BlockPos above=p.above();
                boolean hasLeaves=false;
                for(BlockPos q:BlockPos.betweenClosed(above.offset(-2,0,-2),above.offset(2,4,2))){
                    if(e.level().getBlockState(q).is(BlockTags.LEAVES)){hasLeaves=true;break;}
                }
                if(!hasLeaves) continue;
                BlockPos stand=p.below();
                if(!e.level().getBlockState(stand).getCollisionShape(e.level(),stand).isEmpty()) continue;
                double d=e.distanceToSqr(p.getX()+.5D,p.getY(),p.getZ()+.5D);
                if(d<bestD){bestD=d;best=p.immutable();}
            }
            return best;
        }
    }

    private static final class HomeGoal extends Goal { final GrowthChildPlayerMobEntity e; HomeGoal(GrowthChildPlayerMobEntity e){this.e=e;setFlags(EnumSet.of(Flag.MOVE));} public boolean canUse(){return (e.getCurrentIntent()==GrowthChildPlayerMobEntity.AIIntent.GO_HOME || e.getDailyState()==GrowthChildPlayerMobEntity.DailyState.RETURN_HOME)&&e.hasHome()&&e.getTarget()==null&&!e.isFollowing()&&!e.isGuarding()&&e.getCurrentIntent()!=GrowthChildPlayerMobEntity.AIIntent.HOLD_POSITION&&e.getCurrentIntent()!=GrowthChildPlayerMobEntity.AIIntent.WANDER_HOME;} public boolean canContinueToUse(){return canUse();} public void tick(){BlockPos p=e.getHomePos();e.setCurrentTask("returning home");e.getNavigation().moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,1.0D);}}
    private static final class StorageGoal extends Goal { final GrowthChildPlayerMobEntity e; StorageGoal(GrowthChildPlayerMobEntity e){this.e=e;setFlags(EnumSet.of(Flag.MOVE));} public boolean canUse(){return e.getDailyState()==GrowthChildPlayerMobEntity.DailyState.RETURN_HOME&&e.hasHome()&&e.getTarget()==null&&!e.getTraitInventory().isEmpty()&&e.getCurrentIntent()!=GrowthChildPlayerMobEntity.AIIntent.HOLD_POSITION&&e.getCurrentIntent()!=GrowthChildPlayerMobEntity.AIIntent.WANDER_HOME;} public boolean canContinueToUse(){return canUse();} public void tick(){e.setCurrentTask("storing supplies");e.openAndDepositNearbyContainers();}}
    private static final class ProcessingGoal extends Goal {
        final GrowthChildPlayerMobEntity e;
        BlockPos machine;
        ProcessingGoal(GrowthChildPlayerMobEntity e){this.e=e;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        public boolean canUse(){
            if(e.getTarget()!=null||e.isFollowing()||e.isGuarding()||e.isSleeping()) return false;
            if(!(e.getDailyState()==GrowthChildPlayerMobEntity.DailyState.WORK_MORNING
                    ||e.getDailyState()==GrowthChildPlayerMobEntity.DailyState.WORK_AFTERNOON
                    ||e.getDailyState()==GrowthChildPlayerMobEntity.DailyState.LUNCH_BREAK
                    ||e.getDailyState()==GrowthChildPlayerMobEntity.DailyState.RETURN_HOME)) return false;
            machine=findMachine(e);
            return machine!=null && hasProcessableInput(e,machine);
        }
        public boolean canContinueToUse(){
            return machine!=null && e.getTarget()==null && !e.isFollowing() && !e.isGuarding() && !e.isSleeping()
                    && (e.getDailyState()==GrowthChildPlayerMobEntity.DailyState.WORK_MORNING
                    ||e.getDailyState()==GrowthChildPlayerMobEntity.DailyState.WORK_AFTERNOON
                    ||e.getDailyState()==GrowthChildPlayerMobEntity.DailyState.LUNCH_BREAK
                    ||e.getDailyState()==GrowthChildPlayerMobEntity.DailyState.RETURN_HOME);
        }
        public void tick(){
            if(machine==null) return;
            double d=e.distanceToSqr(machine.getX()+.5D,machine.getY(),machine.getZ()+.5D);
            if(d>3.0D){
                e.setCurrentTask("walking to furnace/smoker");
                e.getNavigation().moveTo(machine.getX()+.5D,machine.getY(),machine.getZ()+.5D,1.0D);
                return;
            }
            e.getNavigation().stop();
            e.getLookControl().setLookAt(machine.getX()+.5D,machine.getY()+.5D,machine.getZ()+.5D,30,30);
            BlockEntity be=e.level().getBlockEntity(machine);
            if(!(be instanceof Container c)) return;
            ItemStack input=findProcessableStack(e,c,machine);
            if(input.isEmpty()){machine=null;return;}
            int inputSlot=0;
            ItemStack slot=c.getItem(inputSlot);
            if(slot.isEmpty()){
                int invSlot=findStack(e,input);
                if(invSlot>=0){
                    ItemStack moved=e.getTraitInventory().getItem(invSlot).split(Math.min(16,e.getTraitInventory().getItem(invSlot).getCount()));
                    c.setItem(inputSlot,moved);
                    e.swingInteraction();
                    e.level().playSound(null, machine, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.45F, 0.9F);
                    e.setCurrentTask("loading furnace");
                }
            }
            if(c.getItem(1).isEmpty()){
                int fuel=findFuel(e);
                if(fuel>=0){
                    ItemStack moved=e.getTraitInventory().getItem(fuel).split(Math.min(8,e.getTraitInventory().getItem(fuel).getCount()));
                    c.setItem(1,moved);
                    e.swingInteraction();
                    e.level().playSound(null, machine, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.45F, 1.0F);
                }
            }
        }
        private static BlockPos findMachine(GrowthChildPlayerMobEntity e){
            BlockPos base=e.hasHome()?e.getHomePos():e.blockPosition();
            BlockPos best=null;double bd=Double.MAX_VALUE;
            for(BlockPos p:BlockPos.betweenClosed(base.offset(-8,-2,-8),base.offset(8,4,8))){
                if(!(e.level().getBlockEntity(p) instanceof Container)) continue;
                BlockState s=e.level().getBlockState(p);
                if(!(s.is(Blocks.FURNACE)||s.is(Blocks.BLAST_FURNACE)||s.is(Blocks.SMOKER))) continue;
                double d=e.distanceToSqr(p.getX()+.5D,p.getY(),p.getZ()+.5D);
                if(d<bd){bd=d;best=p.immutable();}
            }
            return best;
        }
        private static boolean hasProcessableInput(GrowthChildPlayerMobEntity e,BlockPos p){
            BlockEntity be=e.level().getBlockEntity(p);
            if(!(be instanceof Container c)) return false;
            return !findProcessableStack(e,c,p).isEmpty();
        }
        private static ItemStack findProcessableStack(GrowthChildPlayerMobEntity e,Container c,BlockPos machine){
            boolean smoker=e.level().getBlockState(machine).is(Blocks.SMOKER);
            for(int i=0;i<e.getTraitInventory().getContainerSize();i++){
                ItemStack s=e.getTraitInventory().getItem(i);
                if(s.isEmpty()) continue;
                if(smoker && isFoodForSmoking(s)) return s;
                if(!smoker && isOreForFurnace(s)) return s;
            }
            return ItemStack.EMPTY;
        }
        private static boolean isFoodForSmoking(ItemStack s){
            return s.is(Items.BEEF)||s.is(Items.CHICKEN)||s.is(Items.PORKCHOP)||s.is(Items.MUTTON)
                    ||s.is(Items.RABBIT)||s.is(Items.COD)||s.is(Items.SALMON)||s.is(Items.POTATO);
        }
        private static boolean isOreForFurnace(ItemStack s){
            return s.is(Items.RAW_IRON)||s.is(Items.RAW_GOLD)||s.is(Items.RAW_COPPER)
                    ||s.is(Items.IRON_ORE)||s.is(Items.GOLD_ORE)||s.is(Items.COPPER_ORE)
                    ||s.is(Items.SAND)||s.is(Items.COBBLESTONE)||s.is(Items.NETHERRACK);
        }
        private static int findStack(GrowthChildPlayerMobEntity e,ItemStack target){
            for(int i=0;i<e.getTraitInventory().getContainerSize();i++)
                if(ItemStack.isSameItemSameTags(e.getTraitInventory().getItem(i),target)) return i;
            return -1;
        }
        private static int findFuel(GrowthChildPlayerMobEntity e){
            for(int i=0;i<e.getTraitInventory().getContainerSize();i++){
                ItemStack s=e.getTraitInventory().getItem(i);
                if(s.is(Items.COAL)||s.is(Items.CHARCOAL)||s.is(Items.OAK_PLANKS)||s.is(Items.SPRUCE_PLANKS)
                        ||s.is(Items.BIRCH_PLANKS)||s.is(Items.JUNGLE_PLANKS)||s.is(Items.ACACIA_PLANKS)
                        ||s.is(Items.DARK_OAK_PLANKS)) return i;
            }
            return -1;
        }
    }

    private static final class WorkGoal extends Goal {
        final GrowthChildPlayerMobEntity e;
        WorkGoal(GrowthChildPlayerMobEntity e){this.e=e;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        public boolean canUse(){return (e.getDailyState()==GrowthChildPlayerMobEntity.DailyState.WORK_MORNING || e.getDailyState()==GrowthChildPlayerMobEntity.DailyState.WORK_AFTERNOON)&&!e.isFollowing()&&!e.isGuarding()&&e.getTarget()==null&&e.getCurrentIntent()!=GrowthChildPlayerMobEntity.AIIntent.HOLD_POSITION&&e.getCurrentIntent()!=GrowthChildPlayerMobEntity.AIIntent.WANDER_HOME;}
        public boolean canContinueToUse(){return canUse();}
        public void tick(){
            BlockPos p=readMiningTarget(e);
            if(p==null || e.getPersistentData().getInt(WORK_SEARCH_COOLDOWN)<=0){
                p=findWorkBlock(e);
                e.getPersistentData().putInt(WORK_SEARCH_COOLDOWN,10);
            }
            if(p==null){e.setCurrentTask("looking for useful work");return;}
            if(e.distanceToSqr(p.getX()+.5,p.getY(),p.getZ()+.5)>9){
                resetMining(e);
                Vec3 workDestination=new Vec3(p.getX()+.5,p.getY(),p.getZ()+.5);
                e.getNavigation().moveTo(workDestination.x,workDestination.y,workDestination.z,1.0D);
                return;
            }
            BlockState state=e.level().getBlockState(p);
            if(state.isAir()){resetMining(e);return;}
            equipToolForBlock(e,p);
            if(!hasCorrectTool(e,state)){
                resetMining(e);
                e.setCurrentTask("looking for a block matching my tools");
                return;
            }
            if(e.getPersistentData().getInt(MINE_COOLDOWN)>0)return;
            int hardness=(int)Math.ceil(Math.max(1.0F,state.getDestroySpeed(e.level(),p))*8.0F/Math.max(1.0F,e.getMainHandItem().getDestroySpeed(state)));
            hardness=Math.max(2,Math.min(80,hardness));
            CompoundTagLikeProgress progress=readMining(e,p);
            progress.value++;
            e.getPersistentData().putInt(MINE_PROGRESS,progress.value);
            e.swingMainHand();
            e.setCurrentTask("mining " + state.getBlock().getName().getString());
            if(progress.value>=hardness){
                if(e.level().destroyBlock(p,true,e)){
                    e.addExperience(1);
                    damageMainHand(e, 1);
                    // Mature crops are harvested and immediately replanted so
                    // farm work becomes a real repeatable job.
                    if(state.is(Blocks.WHEAT)||state.is(Blocks.CARROTS)||state.is(Blocks.POTATOES)||state.is(Blocks.BEETROOTS)){
                        e.level().setBlock(p,state.getBlock().defaultBlockState(),3);
                    }
                    e.setCurrentTask("gathering materials");
                }
                resetMining(e);
                e.getPersistentData().putInt(MINE_COOLDOWN,4);
            }
        }
    }

    /** Apply one real durability point to the item being actively used. */
    private static void damageMainHand(GrowthChildPlayerMobEntity e, int amount) {
        ItemStack stack = e.getMainHandItem();
        if (stack.isEmpty() || amount <= 0 || !stack.isDamageableItem()) return;
        stack.hurtAndBreak(amount, e, entity -> entity.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        e.setItemSlot(EquipmentSlot.MAINHAND, stack);
    }

    private static final class CompoundTagLikeProgress { int value; CompoundTagLikeProgress(int value){this.value=value;} }
    private static CompoundTagLikeProgress readMining(GrowthChildPlayerMobEntity e, BlockPos p){
        var tag=e.getPersistentData();
        if(tag.getInt(MINE_X)!=p.getX()||tag.getInt(MINE_Y)!=p.getY()||tag.getInt(MINE_Z)!=p.getZ()){
            tag.putInt(MINE_X,p.getX());tag.putInt(MINE_Y,p.getY());tag.putInt(MINE_Z,p.getZ());tag.putInt(MINE_PROGRESS,0);
        }
        return new CompoundTagLikeProgress(tag.getInt(MINE_PROGRESS));
    }
    private static void resetMining(GrowthChildPlayerMobEntity e){
        e.getPersistentData().putInt(MINE_PROGRESS,0);
        e.getPersistentData().remove(MINE_X);e.getPersistentData().remove(MINE_Y);e.getPersistentData().remove(MINE_Z);
    }
    private static boolean hasCorrectTool(GrowthChildPlayerMobEntity e,BlockState state){
        ItemStack main=e.getMainHandItem();
        if(state.is(BlockTags.MINEABLE_WITH_AXE))return main.getItem() instanceof AxeItem;
        if(state.is(BlockTags.MINEABLE_WITH_SHOVEL))return main.getItem() instanceof ShovelItem;
        if(state.is(BlockTags.MINEABLE_WITH_PICKAXE))return main.getItem() instanceof PickaxeItem;
        return !main.isEmpty();
    }
    private static BlockPos readMiningTarget(GrowthChildPlayerMobEntity e){
        var tag=e.getPersistentData();
        if(!tag.contains(MINE_X)||!tag.contains(MINE_Y)||!tag.contains(MINE_Z)) return null;
        BlockPos p=new BlockPos(tag.getInt(MINE_X),tag.getInt(MINE_Y),tag.getInt(MINE_Z));
        BlockState s=e.level().getBlockState(p);
        return s.isAir()?null:p;
    }

    private static BlockPos findWorkBlock(GrowthChildPlayerMobEntity e){
        BlockPos base=e.hasHome()?e.getHomePos():e.blockPosition();
        BlockPos best=null;double bd=Double.MAX_VALUE;
        // Work has no combat/home leash. Sample a broad 128-block work field
        // without scanning tens of thousands of blocks every tick.
        for(int i=0;i<900;i++){
            int x=base.getX()+e.getRandom().nextInt(257)-128;
            int z=base.getZ()+e.getRandom().nextInt(257)-128;
            int y=base.getY()+e.getRandom().nextInt(17)-8;
            BlockPos p=new BlockPos(x,y,z);
            BlockState s=e.level().getBlockState(p);
            boolean primary=s.is(BlockTags.LOGS)||s.is(Blocks.WHEAT)||s.is(Blocks.CARROTS)||s.is(Blocks.POTATOES)||s.is(Blocks.BEETROOTS)
                    ||s.is(Blocks.COAL_ORE)||s.is(Blocks.IRON_ORE)||s.is(Blocks.COPPER_ORE)
                    ||s.is(Blocks.STONE)||s.is(Blocks.COBBLESTONE)||s.is(Blocks.DEEPSLATE);
            boolean terrain=s.is(Blocks.DIRT)||s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.GRAVEL)||s.is(Blocks.SAND)||s.is(Blocks.RED_SAND);
            if(terrain && p.getY()<=base.getY()+1) terrain=false;
            if(!(primary||terrain)||!e.level().getBlockState(p.above()).getCollisionShape(e.level(),p.above()).isEmpty()) continue;
            double d=e.distanceToSqr(p.getX()+.5D,p.getY(),p.getZ()+.5D);
            if(primary)d-=1000.0D;
            if(d<bd){bd=d;best=p.immutable();}
        }
        return best;
    }

    private static void equipToolForBlock(GrowthChildPlayerMobEntity e, BlockPos p){
        BlockState state=e.level().getBlockState(p);
        boolean axe=state.is(BlockTags.MINEABLE_WITH_AXE);
        boolean shovel=state.is(BlockTags.MINEABLE_WITH_SHOVEL);
        boolean pick=state.is(BlockTags.MINEABLE_WITH_PICKAXE);
        ItemStack main=e.getMainHandItem();
        if((axe&&main.getItem() instanceof AxeItem)||(shovel&&main.getItem() instanceof ShovelItem)||(pick&&main.getItem() instanceof PickaxeItem))return;
        SimpleContainer inv=e.getTraitInventory();
        int best=-1,bestScore=-1;
        for(int i=0;i<inv.getContainerSize();i++){
            ItemStack s=inv.getItem(i);
            boolean ok=(axe&&s.getItem() instanceof AxeItem)||(shovel&&s.getItem() instanceof ShovelItem)||(pick&&s.getItem() instanceof PickaxeItem);
            if(!ok)continue;
            int score=s.getMaxDamage()-s.getDamageValue()+enchantValue(s);
            if(score>bestScore){bestScore=score;best=i;}
        }
        if(best>=0){ItemStack old=main.copy();e.setItemInHand(InteractionHand.MAIN_HAND,inv.getItem(best).split(1));if(!old.isEmpty())inv.addItem(old);e.setCurrentTask(axe?"choosing axe":(shovel?"choosing shovel":"choosing pickaxe"));}
    }
    private static final class ExploreGoal extends Goal {
        final GrowthChildPlayerMobEntity e; BlockPos destination; int cooldown;
        ExploreGoal(GrowthChildPlayerMobEntity e){this.e=e;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        public boolean canUse(){
            if(e.getTarget()!=null||e.isFollowing()||e.isGuarding()||e.getCurrentIntent()==GrowthChildPlayerMobEntity.AIIntent.HOLD_POSITION||e.getCurrentIntent()==GrowthChildPlayerMobEntity.AIIntent.WANDER_HOME||!(e.getDailyState()==GrowthChildPlayerMobEntity.DailyState.WORK_MORNING || e.getDailyState()==GrowthChildPlayerMobEntity.DailyState.WORK_AFTERNOON))return false;
            if(cooldown>0){cooldown--;return false;}
            destination=findUsefulDestination(e);
            return destination!=null;
        }
        public boolean canContinueToUse(){return destination!=null&&e.getTarget()==null&&!e.isFollowing()&&!e.isGuarding()&&e.getCurrentIntent()!=GrowthChildPlayerMobEntity.AIIntent.HOLD_POSITION&&e.getCurrentIntent()!=GrowthChildPlayerMobEntity.AIIntent.WANDER_HOME&&!e.getNavigation().isDone();}
        public void start(){e.setCurrentTask("exploring for useful resources");}
        public void tick(){if(destination==null)return;e.getNavigation().moveTo(destination.getX()+.5D,destination.getY(),destination.getZ()+.5D,1.0D);if(e.distanceToSqr(destination.getX()+.5D,destination.getY(),destination.getZ()+.5D)<4){destination=null;cooldown=200;e.setCurrentTask("exploration complete");}}
        private static BlockPos findUsefulDestination(GrowthChildPlayerMobEntity e){
            BlockPos base=e.hasHome()?e.getHomePos():e.blockPosition(); BlockPos best=null; double bd=Double.MAX_VALUE;
            for(BlockPos p:BlockPos.betweenClosed(base.offset(-64,-8,-64),base.offset(64,8,64))){
                BlockState s=e.level().getBlockState(p);
                boolean useful=s.is(BlockTags.LOGS)||s.is(Blocks.COAL_ORE)||s.is(Blocks.IRON_ORE)||s.is(Blocks.COPPER_ORE)
                        ||s.is(Blocks.WHEAT)||s.is(Blocks.CARROTS)||s.is(Blocks.POTATOES)||s.is(Blocks.BEETROOTS)
                        ||s.is(Blocks.STONE)||s.is(Blocks.COBBLESTONE)||s.is(Blocks.DEEPSLATE)
                        ||s.is(BlockTags.MINEABLE_WITH_SHOVEL)||s.is(BlockTags.MINEABLE_WITH_PICKAXE);
                if(!useful||!e.level().getBlockState(p.above()).getCollisionShape(e.level(),p.above()).isEmpty())continue;
                double d=e.distanceToSqr(p.getX()+.5D,p.getY(),p.getZ()+.5D);
                if(d<bd&&d>16){bd=d;best=p.immutable();}
            }
            return best;
        }
    }

    private static boolean hasBow(GrowthChildPlayerMobEntity e) {
        for (int i=0;i<e.getTraitInventory().getContainerSize();i++)
            if (e.getTraitInventory().getItem(i).getItem() instanceof BowItem) return true;
        return false;
    }

    private static double horizontalDistanceSqr(Vec3 p, BlockPos base) {
        double dx=p.x-(base.getX()+.5D);
        double dz=p.z-(base.getZ()+.5D);
        return dx*dx+dz*dz;
    }
    private static double horizontalDistanceSqr(Vec3 a, Vec3 b) {
        double dx=a.x-b.x;
        double dz=a.z-b.z;
        return dx*dx+dz*dz;
    }

    private static Vec3 getResponsibilityCenter(GrowthChildPlayerMobEntity e) {
        // FOLLOW combat belongs to the player being escorted, never to the
        // child's Home center. Otherwise the combat destination clamp can
        // force a following child to run back home before it can attack.
        if (e.isFollowing()) {
            LivingEntity followed = e.getFollowTarget();
            if (followed != null && followed.isAlive()) return followed.position();
        }
        if (e.getCurrentIntent() == GrowthChildPlayerMobEntity.AIIntent.HOLD_POSITION) {
            Vec3 hold = getHoldPosition(e);
            if (hold != null) return hold;
        }
        return e.hasHome() ? Vec3.atCenterOf(e.getHomePos()) : e.position();
    }

    /**
     * Dynamic combat leash: 5 blocks for a low-pressure/local threat and up to
     * 24 blocks when the situation justifies a wider defensive response.
     * It is a combat boundary only; work has no such leash.
     */
    private static double getDynamicCombatLeash(GrowthChildPlayerMobEntity e, LivingEntity target) {
        double leash = COMBAT_LEASH_MIN;
        if (target instanceof Creeper) leash = 12.0D;
        if (e.getHealth() < e.getMaxHealth() * 0.70F) leash = 8.0D;
        if (target.distanceToSqr(e) > 9.0D) leash = 16.0D;
        if (target instanceof Warden || target.getMaxHealth() >= 40.0F) leash = COMBAT_LEASH_MAX;
        if (shouldFlee(e)) leash = COMBAT_LEASH_MIN;
        return Math.max(COMBAT_LEASH_MIN, Math.min(COMBAT_LEASH_MAX, leash));
    }

    private static Vec3 clampToResponsibilityCenter(GrowthChildPlayerMobEntity e, Vec3 desired, double margin) {
        Vec3 center=getResponsibilityCenter(e);
        if(center==null) return desired;
        double max=COMBAT_LEASH_MAX-Math.max(0.0D,margin);
        double dx=desired.x-center.x, dz=desired.z-center.z;
        double d=Math.sqrt(dx*dx+dz*dz);
        if(d<=max||d<.001D) return desired;
        double scale=max/d;
        return new Vec3(center.x+dx*scale,desired.y,center.z+dz*scale);
    }

    private static Vec3 getHoldPosition(GrowthChildPlayerMobEntity e) {
        var tag=e.getPersistentData();
        if(!tag.contains(HOLD_X) || !tag.contains(HOLD_Y) || !tag.contains(HOLD_Z)) return null;
        return new Vec3(tag.getDouble(HOLD_X),tag.getDouble(HOLD_Y),tag.getDouble(HOLD_Z));
    }

    private static void setHoldPosition(GrowthChildPlayerMobEntity e) {
        var p=e.position();
        var tag=e.getPersistentData();
        tag.putDouble(HOLD_X,p.x);
        tag.putDouble(HOLD_Y,p.y);
        tag.putDouble(HOLD_Z,p.z);
    }

    private static boolean hasNearbyContainer(GrowthChildPlayerMobEntity e){BlockPos b=e.hasHome()?e.getHomePos():e.blockPosition();for(BlockPos p:BlockPos.betweenClosed(b.offset(-4,-1,-4),b.offset(4,2,4)))if(e.level().getBlockEntity(p) instanceof Container)return true;return false;}
}
