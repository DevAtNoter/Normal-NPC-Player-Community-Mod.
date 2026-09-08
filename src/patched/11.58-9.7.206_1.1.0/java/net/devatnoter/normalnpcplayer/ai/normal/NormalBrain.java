package net.devatnoter.normalnpcplayer.ai.normal;

import net.devatnoter.normalnpcplayer.entity.AdultPlayerMobEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

/**
 * Survival-oriented player-like brain for PlayerMobTrait.NORMAL.
 *
 * The entity remains a PathfinderMob, so this class deliberately emulates
 * player decision/state transitions instead of pretending that it is a
 * vanilla ServerPlayer. The design is intentionally stateful and conservative:
 * movement goals handle navigation while this brain handles hands, tools,
 * inventory, survival, crafting, building, farming, mining and social behavior.
 */
public final class NormalBrain {
    private static final int ACTION_INTERVAL = 5;
    private static final int SOCIAL_INTERVAL = 12;
    private static final int BUILD_INTERVAL = 8;
    private static final int MINE_INTERVAL = 8;
    private static final int CRAFT_INTERVAL = 30;
    private static final int CHAT_INTERVAL = 240;

    private NormalBrain() {}

    public static void registerGoals(AdultPlayerMobEntity e) {
        e.goalSelector.addGoal(2, new MeleeAttackGoal(e, 1.15D, true) {
            @Override public boolean canUse() {
                return isNormal(e) && super.canUse();
            }
            @Override public boolean canContinueToUse() {
                return isNormal(e) && super.canContinueToUse();
            }
        });

        e.goalSelector.addGoal(3, new RangedAttackGoal(e, 1.0D, 20, 15.0F) {
            @Override public boolean canUse() {
                return isNormalBow(e) && super.canUse();
            }
            @Override public boolean canContinueToUse() {
                return isNormalBow(e) && super.canContinueToUse();
            }
        });

        e.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(e, 1.0D) {
            @Override public boolean canUse() {
                return isNormal(e) && super.canUse();
            }
        });

        e.goalSelector.addGoal(5, new LookAtPlayerGoal(e, Player.class, 10.0F) {
            @Override public boolean canUse() {
                return isNormal(e) && super.canUse();
            }
        });

        e.goalSelector.addGoal(6, new RandomLookAroundGoal(e) {
            @Override public boolean canUse() {
                return isNormal(e) && super.canUse();
            }
        });

        e.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(e, Monster.class, true) {
            @Override public boolean canUse() {
                return isNormal(e) && super.canUse();
            }
        });
    }

    public static void tick(AdultPlayerMobEntity e) {
        if (e.level().isClientSide || !e.isAlive()) return;

        e.incrementBrainActionTicks();

        // Locomotion is continuous, while cognition is decision-based.
        updateLocomotion(e);
        dangerCrouch(e);

        // Re-plan often enough to react, but do not thrash the current intention.
        if (e.getBrainActionTicks() >= ACTION_INTERVAL
                || e.getBrainAction() == AdultPlayerMobEntity.BrainAction.NONE) {
            thinkAndAct(e);
        }

        // Combat/fleeing remains reactive even while a longer task is running.
        if (shouldFlee(e, e.getStyle())) {
            setMind(e, AdultPlayerMobEntity.BrainAction.FLEE, AdultPlayerMobEntity.BrainState.FLEEING);
            flee(e);
        }
    }

    private enum MindIntent {
        FLEE,
        COMBAT,
        EAT,
        COLLECT,
        LIGHT,
        FARM,
        CRAFT,
        MINE,
        STORE,
        SOCIAL,
        BUILD,
        EXPLORE,
        OBSERVE
    }

    /**
     * Utility-style cognition. Every candidate receives a score from needs,
     * danger, personality, current context and opportunity. The winner becomes
     * the next intention. This prevents the old "execute every subsystem every
     * few ticks" behavior where mining, building, farming and chatting all
     * compete simultaneously.
     */
    private static void thinkAndAct(AdultPlayerMobEntity e) {
        List<double[]> scores = new ArrayList<>();
        for (MindIntent intent : MindIntent.values()) {
            scores.add(new double[] {intent.ordinal(), scoreIntent(e, intent)});
        }

        MindIntent best = MindIntent.OBSERVE;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (double[] pair : scores) {
            MindIntent intent = MindIntent.values()[(int) pair[0]];
            if (pair[1] > bestScore) {
                bestScore = pair[1];
                best = intent;
            }
        }

        executeIntent(e, best);
    }

    private static double scoreIntent(AdultPlayerMobEntity e, MindIntent intent) {
        double s = 0.0D;
        AdultPlayerMobEntity.Expression ex = e.getExpression();
        AdultPlayerMobEntity.Style style = e.getStyle();

        boolean injured = e.getHealth() < e.getMaxHealth() * 0.70F;
        boolean critical = e.getHealth() < e.getMaxHealth() * 0.35F;
        boolean dark = e.level().getMaxLocalRawBrightness(e.blockPosition()) <= 7;
        boolean hasFood = hasEdible(e);
        boolean hasTarget = e.getTarget() != null && e.getTarget().isAlive();
        boolean hasNearbyItem = hasCollectableNearby(e);
        boolean hasPlayer = e.level().getNearestPlayer(e, 10.0D) != null;

        switch (intent) {
            case FLEE -> s += critical ? 150.0D : 0.0D;
            case COMBAT -> s += hasTarget ? 120.0D : 0.0D;
            case EAT -> s += injured && hasFood ? 95.0D : 0.0D;
            case COLLECT -> s += hasNearbyItem ? 75.0D : 0.0D;
            case LIGHT -> s += dark ? 82.0D : 0.0D;
            case FARM -> s += ex == AdultPlayerMobEntity.Expression.FARMER ? 68.0D : 0.0D;
            case CRAFT -> s += needsCrafting(e) ? 64.0D : 0.0D;
            case MINE -> s += (hasTarget ? 0.0D : miningOpportunity(e)) + expressionMiningBias(ex);
            case STORE -> s += inventoryPressure(e) * 0.75D;
            case SOCIAL -> s += hasPlayer ? (30.0D + e.getChatAffinity() * 15.0D) : 0.0D;
            case BUILD -> s += (ex == AdultPlayerMobEntity.Expression.BUILDER ? 58.0D : 14.0D);
            case EXPLORE -> s += hasTarget ? 0.0D : 22.0D;
            case OBSERVE -> s += 8.0D;

            default -> {}
        }

        // Style changes risk tolerance, not just flee threshold.
        switch (style) {
            case CASUAL -> {
                if (intent == MindIntent.SOCIAL || intent == MindIntent.EXPLORE) s += 10.0D;
                if (intent == MindIntent.MINE) s -= 3.0D;
            }
            case NORMAL -> {}
            case HARD -> {
                if (intent == MindIntent.MINE || intent == MindIntent.EXPLORE) s += 7.0D;
                if (intent == MindIntent.SOCIAL) s -= 3.0D;
            }
            case HARDCORE -> {
                if (intent == MindIntent.LIGHT || intent == MindIntent.STORE) s += 12.0D;
                if (intent == MindIntent.EXPLORE && e.getHealth() < e.getMaxHealth() * 0.65F) s -= 18.0D;
            }
        }

        // Continuity bonus: humans tend to finish an active intention unless a
        // higher-priority need interrupts it.
        MindIntent current = intentFromAction(e.getBrainAction());
        if (current == intent) s += 16.0D;

        // Small deterministic jitter prevents identical NPCs from becoming a
        // perfectly synchronized swarm without turning decisions into noise.
        s += ((e.getId() * 31L + e.tickCount * 17L) & 7L) * 0.35D;
        return s;
    }

    private static void executeIntent(AdultPlayerMobEntity e, MindIntent intent) {
        switch (intent) {
            case FLEE -> {
                setMind(e, AdultPlayerMobEntity.BrainAction.FLEE, AdultPlayerMobEntity.BrainState.FLEEING);
                flee(e);
            }
            case COMBAT -> {
                setMind(e, AdultPlayerMobEntity.BrainAction.COMBAT, AdultPlayerMobEntity.BrainState.COMBAT);
                chooseCombatHand(e);
                useShield(e);
            }
            case EAT -> {
                setMind(e, AdultPlayerMobEntity.BrainAction.EAT, AdultPlayerMobEntity.BrainState.RESTING);
                eatIfHungry(e);
            }
            case COLLECT -> {
                setMind(e, AdultPlayerMobEntity.BrainAction.COLLECT, AdultPlayerMobEntity.BrainState.GATHERING);
                pullNearbyItems(e);
            }
            case LIGHT -> {
                setMind(e, AdultPlayerMobEntity.BrainAction.LIGHT, AdultPlayerMobEntity.BrainState.BUILDING);
                survivalLighting(e);
            }
            case FARM -> {
                setMind(e, AdultPlayerMobEntity.BrainAction.FARM, AdultPlayerMobEntity.BrainState.FARMING);
                farm(e);
            }
            case CRAFT -> {
                setMind(e, AdultPlayerMobEntity.BrainAction.CRAFT, AdultPlayerMobEntity.BrainState.CRAFTING);
                craftSurvivalKit(e);
                runFurnace(e);
            }
            case MINE -> {
                setMind(e, AdultPlayerMobEntity.BrainAction.MINE, AdultPlayerMobEntity.BrainState.MINING);
                mineUsefulBlock(e);
            }
            case STORE -> {
                setMind(e, AdultPlayerMobEntity.BrainAction.STORE, AdultPlayerMobEntity.BrainState.STORING);
                deposit(e);
            }
            case SOCIAL -> {
                setMind(e, AdultPlayerMobEntity.BrainAction.SOCIAL, AdultPlayerMobEntity.BrainState.SOCIALIZING);
                social(e);
                // Chat is a world-time event, not an action-duration counter.
                // This avoids resetting the timer whenever the utility planner
                // re-selects SOCIAL every few ticks.
                if (e.tickCount % CHAT_INTERVAL == 0) {
                    setMind(e, AdultPlayerMobEntity.BrainAction.TALK, AdultPlayerMobEntity.BrainState.TALKING);
                    autonomousChat(e);
                }
            }
            case BUILD -> {
                setMind(e, AdultPlayerMobEntity.BrainAction.BUILD, AdultPlayerMobEntity.BrainState.BUILDING);
                placeBlock(e);
            }
            case EXPLORE -> {
                setMind(e, AdultPlayerMobEntity.BrainAction.EXPLORE, AdultPlayerMobEntity.BrainState.TRAVELING);
                explorationStep(e);
            }
            case OBSERVE -> {
                setMind(e, AdultPlayerMobEntity.BrainAction.OBSERVE, AdultPlayerMobEntity.BrainState.OBSERVING);
                observationStep(e);
            }
        }
    }

    private static void setMind(
            AdultPlayerMobEntity e,
            AdultPlayerMobEntity.BrainAction action,
            AdultPlayerMobEntity.BrainState state
    ) {
        if (e.getBrainAction() != action) e.setBrainAction(action);
        e.setBrainState(state);
    }

    private static MindIntent intentFromAction(AdultPlayerMobEntity.BrainAction action) {
        return switch (action) {
            case FLEE -> MindIntent.FLEE;
            case COMBAT -> MindIntent.COMBAT;
            case EAT -> MindIntent.EAT;
            case COLLECT -> MindIntent.COLLECT;
            case LIGHT -> MindIntent.LIGHT;
            case FARM -> MindIntent.FARM;
            case CRAFT -> MindIntent.CRAFT;
            case MINE -> MindIntent.MINE;
            case STORE -> MindIntent.STORE;
            case SOCIAL, TALK -> MindIntent.SOCIAL;
            case BUILD -> MindIntent.BUILD;
            case EXPLORE -> MindIntent.EXPLORE;
            default -> MindIntent.OBSERVE;
        };
    }

    private static boolean hasEdible(AdultPlayerMobEntity e) {
        for (int i = 0; i < e.getTraitInventory().getContainerSize(); i++) {
            ItemStack s = e.getTraitInventory().getItem(i);
            if (!s.isEmpty() && s.getItem().isEdible()) return true;
        }
        return false;
    }

    private static boolean hasCollectableNearby(AdultPlayerMobEntity e) {
        return !e.level().getEntitiesOfClass(
                ItemEntity.class, e.getBoundingBox().inflate(6.0D),
                item -> item.isAlive() && !item.getItem().isEmpty() && shouldCollect(e, item.getItem())
        ).isEmpty();
    }

    private static boolean needsCrafting(AdultPlayerMobEntity e) {
        return countItem(e, Items.CRAFTING_TABLE) == 0
                || (countItem(e, Items.WOODEN_PICKAXE) == 0 && countItem(e, Items.STONE_PICKAXE) == 0)
                || (countItem(e, Items.TORCH) == 0 && (countItem(e, Items.COAL) + countItem(e, Items.CHARCOAL)) > 0);
    }

    private static double miningOpportunity(AdultPlayerMobEntity e) {
        if (!(e.level() instanceof ServerLevel level)) return 0.0D;
        BlockPos center = e.blockPosition();
        double best = 0.0D;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-3, -2, -3), center.offset(3, 2, 3))) {
            BlockState state = level.getBlockState(p);
            if (state.isAir() || state.getDestroySpeed(level, p) < 0 || !isMineableSurvivalTarget(state)) continue;
            double value = miningValue(state);
            if (isVisibleToBrain(e, p)) value += 12.0D;
            best = Math.max(best, value);
        }
        return Math.min(85.0D, best * 0.45D);
    }

    private static double expressionMiningBias(AdultPlayerMobEntity.Expression ex) {
        return switch (ex) {
            case AVENTURER, SURVIVOR, NORMAL, NONE -> 12.0D;
            case FARMER -> 2.0D;
            default -> 0.0D;
        };
    }

    private static double inventoryPressure(AdultPlayerMobEntity e) {
        int occupied = 0;
        for (int i = 0; i < e.getTraitInventory().getContainerSize(); i++) {
            if (!e.getTraitInventory().getItem(i).isEmpty()) occupied++;
        }
        return (occupied / 36.0D) * 100.0D;
    }

    private static void explorationStep(AdultPlayerMobEntity e) {
        Player p = e.level().getNearestPlayer(e, 14.0D);
        if (p != null && e.getRandom().nextFloat() < 0.35F) {
            e.getLookControl().setLookAt(p, 20.0F, 20.0F);
        }
        Vec3 forward = e.getLookAngle();
        Vec3 destination = e.position().add(forward.x * 6.0D, 0.0D, forward.z * 6.0D);
        e.getNavigation().moveTo(destination.x, destination.y, destination.z, 0.9D);
    }

    private static void observationStep(AdultPlayerMobEntity e) {
        Player p = e.level().getNearestPlayer(e, 12.0D);
        if (p != null) {
            e.getLookControl().setLookAt(p, 25.0F, 25.0F);
        } else {
            e.getNavigation().stop();
        }
    }


    private static boolean isNormal(AdultPlayerMobEntity e) {
        return e.getTrait() == AdultPlayerMobEntity.PlayerMobTrait.NORMAL;
    }

    private static boolean isNormalBow(AdultPlayerMobEntity e) {
        return isNormal(e)
                && e.getItemBySlot(EquipmentSlot.MAINHAND).getItem() instanceof BowItem
                && hasArrow(e);
    }

    private static boolean hasArrow(AdultPlayerMobEntity e) {
        for (int i = 0; i < e.getTraitInventory().getContainerSize(); i++) {
            if (e.getTraitInventory().getItem(i).getItem() instanceof ArrowItem) return true;
        }
        return false;
    }

    /* --------------------------------------------------------------------- */
    /* Player-like locomotion                                                */
    /* --------------------------------------------------------------------- */

    private static void updateLocomotion(AdultPlayerMobEntity e) {
        if (e.getTarget() != null && e.getTarget().isAlive()) {
            double d = e.distanceToSqr(e.getTarget());
            e.setSprinting(d > 9.0D);
            if (d < 3.0D && e.onGround() && e.getRandom().nextFloat() < 0.12F) {
                e.getJumpControl().jump();
            }
        } else {
            e.setSprinting(e.getDeltaMovement().horizontalDistanceSqr() > 0.02D
                    && e.getStyle() != AdultPlayerMobEntity.Style.CASUAL);
        }

        // PathfinderMob already handles ladders, but keeping the flag explicit
        // makes the intended player-like climbing state visible to future code.
        if (e.onClimbable()) {
            e.setSprinting(false);
        }
    }

    private static void social(AdultPlayerMobEntity e) {
        Player p = e.level().getNearestPlayer(e, 7.0D);
        if (p != null && p.isAlive()) {
            e.setShiftKeyDown(true);
            e.getLookControl().setLookAt(p, 30.0F, 30.0F);
            if (e.onGround() && e.getRandom().nextFloat() < 0.55F) {
                e.getJumpControl().jump();
            }
        } else if (!isNearDanger(e)) {
            e.setShiftKeyDown(false);
        }
    }

    private static void dangerCrouch(AdultPlayerMobEntity e) {
        if (!e.onGround()) return;

        Vec3 look = e.getLookAngle();
        int dx = (int) Math.round(look.x);
        int dz = (int) Math.round(look.z);
        if (dx == 0 && dz == 0) return;

        BlockPos ahead = e.blockPosition().offset(dx, 0, dz);
        int floorY = findFloor(e, ahead, 6);

        if (floorY == Integer.MIN_VALUE) {
            e.setShiftKeyDown(true);
            return;
        }

        int drop = e.blockPosition().getY() - floorY;
        if (drop >= 2 || !e.level().getBlockState(ahead.below()).isSolid()) {
            e.setShiftKeyDown(true);
        }
    }

    private static boolean isNearDanger(AdultPlayerMobEntity e) {
        BlockPos pos = e.blockPosition();
        return findFloor(e, pos.relative(e.getDirection()), 4) == Integer.MIN_VALUE;
    }

    private static int findFloor(AdultPlayerMobEntity e, BlockPos pos, int maxDrop) {
        for (int y = pos.getY(); y >= Math.max(e.level().getMinBuildHeight(), pos.getY() - maxDrop); y--) {
            BlockPos p = new BlockPos(pos.getX(), y, pos.getZ());
            if (!e.level().getBlockState(p).isAir()) return y;
        }
        return Integer.MIN_VALUE;
    }

    private static void flee(AdultPlayerMobEntity e) {
        if (e.getTarget() == null) return;

        Vec3 away = e.position().subtract(e.getTarget().position());
        if (away.lengthSqr() < .01D) away = new Vec3(1, 0, 0);
        away = away.normalize();

        // A player-like emergency jump is useful when the path is blocked.
        Vec3 destination = e.position().add(away.scale(10.0D));
        e.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.35D);
        e.setSprinting(true);

        if (e.onGround() && e.getRandom().nextFloat() < 0.15F) {
            e.getJumpControl().jump();
        }
    }

    private static boolean shouldFlee(AdultPlayerMobEntity e, AdultPlayerMobEntity.Style style) {
        if (e.getTarget() == null || !e.getTarget().isAlive()) return false;
        if (style == AdultPlayerMobEntity.Style.HARDCORE) return e.getHealth() <= e.getMaxHealth() * .10F;
        if (style == AdultPlayerMobEntity.Style.HARD) return e.getHealth() <= e.getMaxHealth() * .18F;
        if (style == AdultPlayerMobEntity.Style.CASUAL) return e.getHealth() <= e.getMaxHealth() * .40F;
        return e.getHealth() <= e.getMaxHealth() * .30F;
    }

    /* --------------------------------------------------------------------- */
    /* Item collection and equipment                                         */
    /* --------------------------------------------------------------------- */

    private static void pullNearbyItems(AdultPlayerMobEntity e) {
        SimpleContainer inv = e.getTraitInventory();

        for (ItemEntity item : e.level().getEntitiesOfClass(
                ItemEntity.class,
                e.getBoundingBox().inflate(6.0D))) {

            if (!item.isAlive() || item.getItem().isEmpty()) continue;

            ItemStack stack = item.getItem();
            if (!shouldCollect(e, stack)) continue;

            double distance = e.distanceToSqr(item);
            if (distance > 1.0D) {
                Vec3 delta = e.position().subtract(item.position());
                if (delta.lengthSqr() > 0.001D) {
                    Vec3 pull = delta.normalize().scale(
                            Math.min(0.35D, 0.08D + Math.sqrt(distance) * 0.025D)
                    );
                    item.setDeltaMovement(
                            item.getDeltaMovement().scale(0.65D).add(pull)
                    );
                }
                continue;
            }

            ItemStack remaining = insertBestEffort(e, stack.copy());
            if (remaining.isEmpty()) {
                item.discard();
            } else {
                item.setItem(remaining);
            }
        }
    }

    private static ItemStack insertBestEffort(AdultPlayerMobEntity e, ItemStack incoming) {
        SimpleContainer inv = e.getTraitInventory();

        // Prefer existing stacks first, matching vanilla player inventory feel.
        for (int i = 0; i < inv.getContainerSize() && !incoming.isEmpty(); i++) {
            ItemStack current = inv.getItem(i);
            if (current.isEmpty()) continue;
            if (!ItemStack.isSameItemSameTags(current, incoming)) continue;

            int room = Math.min(current.getMaxStackSize(), incoming.getMaxStackSize()) - current.getCount();
            if (room <= 0) continue;

            int moved = Math.min(room, incoming.getCount());
            current.grow(moved);
            incoming.shrink(moved);
        }

        for (int i = 0; i < inv.getContainerSize() && !incoming.isEmpty(); i++) {
            if (!inv.getItem(i).isEmpty()) continue;

            int moved = Math.min(incoming.getMaxStackSize(), incoming.getCount());
            inv.setItem(i, incoming.split(moved));
        }

        return incoming;
    }

    private static boolean shouldCollect(AdultPlayerMobEntity e, ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (stack.getItem() instanceof ArmorItem armor) {
            return armor.getDefense() > currentArmorScore(e, armor.getEquipmentSlot());
        }

        return true;
    }

    private static double currentArmorScore(AdultPlayerMobEntity e, EquipmentSlot slot) {
        ItemStack equipped = e.getItemBySlot(slot);
        if (equipped.isEmpty() || !(equipped.getItem() instanceof ArmorItem armor)) return 0;
        return armor.getDefense() + armor.getToughness();
    }

    private static void equipBestGear(AdultPlayerMobEntity e) {
        SimpleContainer inv = e.getTraitInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof ArmorItem armor) {
                EquipmentSlot slot = armor.getEquipmentSlot();
                ItemStack current = e.getItemBySlot(slot);
                double candidateScore = armor.getDefense() + armor.getToughness();
                double currentScore = current.getItem() instanceof ArmorItem a
                        ? a.getDefense() + a.getToughness() : 0.0D;

                if (candidateScore > currentScore) {
                    if (!current.isEmpty()) {
                        ItemStack remainder = insertBestEffort(e, current.copy());
                        if (!remainder.isEmpty()) {
                            // Inventory is genuinely full: discard only the inferior item.
                            e.spawnAtLocation(remainder);
                        }
                    }
                    e.setItemSlot(slot, stack.copyWithCount(1));
                    stack.shrink(1);
                    inv.setItem(i, stack);
                }
            }
        }
    }

    private static void chooseCombatHand(AdultPlayerMobEntity e) {
        if (e.getTarget() == null || !e.getTarget().isAlive()) return;

        ItemStack main = e.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack off = e.getItemBySlot(EquipmentSlot.OFFHAND);
        double distance = e.distanceToSqr(e.getTarget());

        if (distance > 8.0D * 8.0D && off.getItem() instanceof BowItem && !(main.getItem() instanceof BowItem)) {
            swapHands(e);
        } else if (distance <= 8.0D * 8.0D && off.getItem() instanceof SwordItem && !(main.getItem() instanceof SwordItem)) {
            swapHands(e);
        }
    }

    private static void swapHands(AdultPlayerMobEntity e) {
        ItemStack main = e.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack off = e.getItemBySlot(EquipmentSlot.OFFHAND);
        e.setItemSlot(EquipmentSlot.MAINHAND, off.copy());
        e.setItemSlot(EquipmentSlot.OFFHAND, main.copy());
    }

    private static void useShield(AdultPlayerMobEntity e) {
        ItemStack off = e.getItemBySlot(EquipmentSlot.OFFHAND);
        if (!(off.getItem() instanceof ShieldItem)) return;

        boolean threat = e.getTarget() != null
                && e.getTarget().isAlive()
                && e.distanceToSqr(e.getTarget()) < 8.0D * 8.0D;

        if ((e.hurtTime > 0 || threat) && !e.isUsingItem()) {
            e.startUsingItem(InteractionHand.OFF_HAND);
        } else if (!threat && e.hurtTime == 0 && e.isUsingItem()
                && e.getUsedItemHand() == InteractionHand.OFF_HAND) {
            e.stopUsingItem();
        }
    }

    private static void eatIfHungry(AdultPlayerMobEntity e) {
        // PathfinderMob has no vanilla food/saturation system. We emulate the
        // survival decision by using edible items as emergency healing.
        if (e.getHealth() >= e.getMaxHealth() * .75F) return;

        SimpleContainer inv = e.getTraitInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !stack.getItem().isEdible()) continue;

            float heal = stack.getItem().getFoodProperties().getNutrition() * 0.5F;
            e.heal(Math.max(1.0F, heal));
            stack.shrink(1);
            inv.setItem(i, stack);
            return;
        }
    }

    /* --------------------------------------------------------------------- */
    /* Mining / farming / building                                           */
    /* --------------------------------------------------------------------- */

    private static void mineUsefulBlock(AdultPlayerMobEntity e) {
        if (!(e.level() instanceof ServerLevel level)) return;
        if (e.getTarget() != null && e.getTarget().isAlive()) return;

        BlockPos target = e.getBrainMineTarget() == Long.MIN_VALUE
                ? findBestMiningTarget(e, level)
                : BlockPos.of(e.getBrainMineTarget());

        if (target == null || !isValidMiningTarget(level, target)) {
            e.clearBrainMineSession();
            target = findBestMiningTarget(e, level);
            if (target == null) return;
            e.setBrainMineSession(target.asLong(), 0.0F);
        }

        BlockState state = level.getBlockState(target);
        e.getLookControl().setLookAt(
                target.getX() + .5D,
                target.getY() + .5D,
                target.getZ() + .5D,
                30.0F,
                30.0F
        );

        if (e.distanceToSqr(Vec3.atCenterOf(target)) > 6.25D) {
            e.getNavigation().moveTo(target.getX() + .5D, target.getY(), target.getZ() + .5D, 1.0D);
            return;
        }

        ItemStack tool = bestToolFor(e, state);
        if (!tool.isEmpty() && !ItemStack.isSameItem(e.getItemBySlot(EquipmentSlot.MAINHAND), tool)) {
            putInMainHandFromInventory(e, tool);
        }

        ItemStack held = e.getItemBySlot(EquipmentSlot.MAINHAND);
        float speed = Math.max(1.0F, held.isEmpty() ? 1.0F : held.getDestroySpeed(state));
        float hardness = Math.max(0.05F, state.getDestroySpeed(level, target));

        // Deliberately model break time over multiple ticks. A block is never
        // destroyed merely because the brain looked at it once.
        float progressPerTick = Math.max(0.004F, (speed / hardness) * 0.032F);
        float progress = e.getBrainMineProgress() + progressPerTick;
        e.setBrainMineSession(target.asLong(), progress);

        e.swingMainHand();

        if (progress >= 1.0F) {
            level.destroyBlock(target, true, e);
            e.clearBrainMineSession();
            e.setBrainState(AdultPlayerMobEntity.BrainState.GATHERING);
            e.setBrainAction(AdultPlayerMobEntity.BrainAction.COLLECT);
        }
    }

    private static BlockPos findBestMiningTarget(AdultPlayerMobEntity e, ServerLevel level) {
        BlockPos center = e.blockPosition();
        BlockPos best = null;
        int bestScore = Integer.MIN_VALUE;

        for (BlockPos p : BlockPos.betweenClosed(
                center.offset(-3, -2, -3),
                center.offset(3, 2, 3))) {
            BlockState state = level.getBlockState(p);
            if (!isValidMiningTarget(level, p)) continue;

            int score = miningValue(state);
            if (isVisibleToBrain(e, p)) score += 8;
            double distance = Math.sqrt(e.distanceToSqr(Vec3.atCenterOf(p)));
            score -= (int) Math.min(10.0D, distance);

            if (score > bestScore) {
                bestScore = score;
                best = p.immutable();
            }
        }
        return best;
    }

    private static boolean isValidMiningTarget(ServerLevel level, BlockPos p) {
        BlockState state = level.getBlockState(p);
        return !state.isAir()
                && state.getDestroySpeed(level, p) >= 0
                && isMineableSurvivalTarget(state);
    }


    private static boolean isMineableSurvivalTarget(BlockState state) {
        if (state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER)) return false;
        if (state.getBlock() == Blocks.END_PORTAL || state.getBlock() == Blocks.END_GATEWAY) return false;
        return state.is(BlockTags.LOGS)

                || state.is(Blocks.STONE)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.COAL_ORE)
                || state.is(Blocks.DEEPSLATE_COAL_ORE)
                || state.is(Blocks.IRON_ORE)
                || state.is(Blocks.DEEPSLATE_IRON_ORE)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.SAND)
                || state.is(Blocks.GRAVEL);
    }

    private static int miningValue(BlockState state) {
        if (state.is(BlockTags.LOGS)) return 100;
        if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) return 95;
        if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) return 90;
        if (state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)) return 70;
        if (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)) return 30;
        return 10;
    }

    private static boolean isVisibleToBrain(AdultPlayerMobEntity e, BlockPos p) {
        return e.level().clip(new net.minecraft.world.level.ClipContext(
                e.getEyePosition(),
                Vec3.atCenterOf(p),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                e
        )).getBlockPos().equals(p);
    }

    private static ItemStack bestToolFor(AdultPlayerMobEntity e, BlockState state) {
        ItemStack best = ItemStack.EMPTY;
        float bestSpeed = 1.0F;

        ItemStack main = e.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!main.isEmpty()) {
            float speed = main.getDestroySpeed(state);
            if (speed > bestSpeed) {
                best = main;
                bestSpeed = speed;
            }
        }

        SimpleContainer inv = e.getTraitInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                best = stack;
                bestSpeed = speed;
            }
        }

        return best;
    }

    private static void putInMainHandFromInventory(AdultPlayerMobEntity e, ItemStack desired) {
        SimpleContainer inv = e.getTraitInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !ItemStack.isSameItemSameTags(stack, desired)) continue;

            ItemStack old = e.getItemBySlot(EquipmentSlot.MAINHAND);
            e.setItemSlot(EquipmentSlot.MAINHAND, stack.copyWithCount(1));
            stack.shrink(1);
            inv.setItem(i, stack);

            if (!old.isEmpty()) {
                ItemStack rem = insertBestEffort(e, old);
                if (!rem.isEmpty()) e.spawnAtLocation(rem);
            }
            return;
        }
    }

    private static void farm(AdultPlayerMobEntity e) {
        if (!(e.level() instanceof ServerLevel level)) return;

        BlockPos below = e.blockPosition().below();
        BlockState state = level.getBlockState(below);
        ItemStack hoe = findItem(e, HoeItem.class);

        if (!hoe.isEmpty()
                && (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK))
                && level.getBlockState(below.above()).isAir()) {

            putInMainHandFromInventory(e, hoe);
            level.setBlock(below, Blocks.FARMLAND.defaultBlockState(), 3);
            damageMainHand(e);
            e.swingMainHand();
            return;
        }

        // Harvest mature wheat/carrots/potatoes/beetroots and replant from the drop.
        BlockPos cropPos = below;
        BlockState crop = level.getBlockState(cropPos);
        if (crop.getBlock() instanceof net.minecraft.world.level.block.CropBlock cropBlock
                && cropBlock.isMaxAge(crop)) {
            level.destroyBlock(cropPos, true, e);
        }
    }

    private static ItemStack findItem(AdultPlayerMobEntity e, Class<?> type) {
        ItemStack main = e.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!main.isEmpty() && type.isInstance(main.getItem())) return main;

        SimpleContainer inv = e.getTraitInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && type.isInstance(stack.getItem())) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void damageMainHand(AdultPlayerMobEntity e) {
        ItemStack stack = e.getItemBySlot(EquipmentSlot.MAINHAND);
        if (stack.isEmpty()) return;
        stack.hurtAndBreak(1, e, living -> living.broadcastBreakEvent(InteractionHand.MAIN_HAND));
    }

    private static void placeBlock(AdultPlayerMobEntity e) {
        if (!(e.level() instanceof ServerLevel level)) return;

        BlockPos target = e.blockPosition().relative(e.getDirection());

        // Allow a simple player-like "step/place" and "pillar" behavior.
        if (!level.getBlockState(target).canBeReplaced()) {
            target = target.above();
        }

        SimpleContainer inv = e.getTraitInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) continue;

            BlockState desired = blockItem.getBlock().defaultBlockState();
            if (!desired.canSurvive(level, target)) continue;
            if (!level.getBlockState(target).canBeReplaced()) continue;

            // Do NOT use BlockPlaceContext here:
            // Forge 1.20.1's BlockPlaceContext requires a Player,
            // while AdultPlayerMobEntity is a PathfinderMob.
            if (level.setBlock(target, desired, 3)) {
                e.swingMainHand();
                stack.shrink(1);
                inv.setItem(i, stack);
            }
            return;
        }
    }

    /* --------------------------------------------------------------------- */
    /* Survival lighting, crafting and furnace                                */
    /* --------------------------------------------------------------------- */

    private static void survivalLighting(AdultPlayerMobEntity e) {
        if (!(e.level() instanceof ServerLevel level)) return;

        boolean dark = level.getMaxLocalRawBrightness(e.blockPosition()) <= 5;
        if (!dark) return;

        if (countItem(e, Items.TORCH) == 0) {
            craftTorches(e);
        }

        if (countItem(e, Items.TORCH) > 0) {
            placeTorch(e);
        }
    }

    private static void placeTorch(AdultPlayerMobEntity e) {
        if (!(e.level() instanceof ServerLevel level)) return;
        if (level.getMaxLocalRawBrightness(e.blockPosition()) > 7) return;

        int slot = findInventorySlot(e, Items.TORCH);
        if (slot < 0) return;

        BlockPos target = e.blockPosition();
        if (!level.getBlockState(target).canBeReplaced()) {
            target = target.relative(e.getDirection());
        }
        if (!level.getBlockState(target).canBeReplaced()) return;

        BlockPos support = target.below();
        if (!level.getBlockState(support).isSolid()) return;

        Block torchBlock = ((BlockItem) Items.TORCH).getBlock();
        BlockState torchState = torchBlock.defaultBlockState();

        if (!torchState.canSurvive(level, target)) return;

        ItemStack stack = e.getTraitInventory().getItem(slot);

        // Direct server-side placement: no Player-only BlockPlaceContext.
        if (level.setBlock(target, torchState, 3)) {
            e.swingMainHand();
            stack.shrink(1);
            e.getTraitInventory().setItem(slot, stack);
        }
    }

    private static void craftSurvivalKit(AdultPlayerMobEntity e) {
        SimpleContainer inv = e.getTraitInventory();

        // Logs -> planks (player's 2x2 crafting equivalent).
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(ItemTags.LOGS)) {
                int amount = Math.min(4, Math.max(0, stack.getCount() - 1));
                ItemStack planks = new ItemStack(plankForLog(stack), amount * 4);
                stack.shrink(amount);
                inv.setItem(i, stack);
                insertBestEffort(e, planks);
                break;
            }
        }

        craftRecipe(e, Items.STICK, 4, Items.OAK_PLANKS, 2);
        craftRecipe(e, Items.CRAFTING_TABLE, 1, Items.OAK_PLANKS, 4);
        craftRecipe(e, Items.WOODEN_PICKAXE, 1, Items.OAK_PLANKS, 3, Items.STICK, 2);
        craftRecipe(e, Items.WOODEN_AXE, 1, Items.OAK_PLANKS, 3, Items.STICK, 2);
        craftRecipe(e, Items.WOODEN_SWORD, 1, Items.OAK_PLANKS, 2, Items.STICK, 1);
        craftRecipe(e, Items.WOODEN_HOE, 1, Items.OAK_PLANKS, 2, Items.STICK, 2);
        craftRecipe(e, Items.FURNACE, 1, Items.COBBLESTONE, 8);
        placeFunctionalBlock(e, Items.CRAFTING_TABLE);
        placeFunctionalBlock(e, Items.FURNACE);
        craftTorches(e);
    }

    private static Item plankForLog(ItemStack log) {
        if (log.is(Items.SPRUCE_LOG) || log.is(Items.STRIPPED_SPRUCE_LOG)) return Items.SPRUCE_PLANKS;
        if (log.is(Items.BIRCH_LOG) || log.is(Items.STRIPPED_BIRCH_LOG)) return Items.BIRCH_PLANKS;
        if (log.is(Items.JUNGLE_LOG) || log.is(Items.STRIPPED_JUNGLE_LOG)) return Items.JUNGLE_PLANKS;
        if (log.is(Items.ACACIA_LOG) || log.is(Items.STRIPPED_ACACIA_LOG)) return Items.ACACIA_PLANKS;
        if (log.is(Items.DARK_OAK_LOG) || log.is(Items.STRIPPED_DARK_OAK_LOG)) return Items.DARK_OAK_PLANKS;
        if (log.is(Items.MANGROVE_LOG) || log.is(Items.STRIPPED_MANGROVE_LOG)) return Items.MANGROVE_PLANKS;
        if (log.is(Items.CHERRY_LOG) || log.is(Items.STRIPPED_CHERRY_LOG)) return Items.CHERRY_PLANKS;
        return Items.OAK_PLANKS;
    }

    private static void craftTorches(AdultPlayerMobEntity e) {
        int fuel = countItem(e, Items.COAL) + countItem(e, Items.CHARCOAL);
        if (fuel <= 0) return;
        craftRecipe(e, Items.TORCH, 4, Items.STICK, 1, Items.COAL, 1);
        if (countItem(e, Items.TORCH) == 0) {
            craftRecipe(e, Items.TORCH, 4, Items.STICK, 1, Items.CHARCOAL, 1);
        }
    }

    private static void craftRecipe(
            AdultPlayerMobEntity e,
            Item result,
            int resultCount,
            Object... ingredients
    ) {
        if (ingredients.length % 2 != 0) return;

        int pairs = ingredients.length / 2;
        Item[] items = new Item[pairs];
        int[] counts = new int[pairs];

        for (int i = 0; i < pairs; i++) {
            if (!(ingredients[i * 2] instanceof Item item)
                    || !(ingredients[i * 2 + 1] instanceof Integer count)) return;
            items[i] = item;
            counts[i] = count;
            if (countItem(e, item) < count) return;
        }

        for (int i = 0; i < pairs; i++) removeItems(e, items[i], counts[i]);
        insertBestEffort(e, new ItemStack(result, resultCount));
    }

    private static void placeFunctionalBlock(AdultPlayerMobEntity e, Item item) {
        if (!(e.level() instanceof ServerLevel level)) return;
        if (!(item instanceof BlockItem blockItem)) return;

        int slot = findInventorySlot(e, item);
        if (slot < 0) return;

        ItemStack invStack = e.getTraitInventory().getItem(slot);
        if (invStack.isEmpty()) return;

        BlockState desired = blockItem.getBlock().defaultBlockState();

        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = e.blockPosition().relative(d);
            if (!level.getBlockState(p).canBeReplaced()) continue;

            BlockPos support = p.below();
            if (!level.getBlockState(support).isSolid()) continue;
            if (!desired.canSurvive(level, p)) continue;

            // Direct placement avoids BlockPlaceContext, which is Player-only in 1.20.1.
            if (level.setBlock(p, desired, 3)) {
                e.swingMainHand();
                invStack.shrink(1);
                e.getTraitInventory().setItem(slot, invStack);
            }
            return;
        }
    }

    private static void runFurnace(AdultPlayerMobEntity e) {
        if (!(e.level() instanceof ServerLevel level)) return;

        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockEntity be = level.getBlockEntity(e.blockPosition().relative(d));
            if (!(be instanceof FurnaceBlockEntity furnace)) continue;

            if (furnace.getItem(0).isEmpty()) {
                ItemStack log = firstLog(e);
                if (!log.isEmpty()) {
                    ItemStack one = log.split(1);
                    furnace.setItem(0, one);
                    if (log.isEmpty()) {
                        // no-op; container was updated by split
                    }
                }
            }

            if (furnace.getItem(1).isEmpty()) {
                ItemStack fuel = findFuel(e);
                if (!fuel.isEmpty()) furnace.setItem(1, fuel.split(1));
            }

            furnace.setChanged();
        }
    }

    private static ItemStack firstLog(AdultPlayerMobEntity e) {
        SimpleContainer inv = e.getTraitInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(ItemTags.LOGS)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findFuel(AdultPlayerMobEntity e) {
        SimpleContainer inv = e.getTraitInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && (s.is(ItemTags.LOGS) || s.is(Items.COAL) || s.is(Items.CHARCOAL))) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    /* --------------------------------------------------------------------- */
    /* Container / utility                                                    */
    /* --------------------------------------------------------------------- */

    private static void deposit(AdultPlayerMobEntity e) {
        if (!(e.level() instanceof ServerLevel sl)) return;

        SimpleContainer inv = e.getTraitInventory();

        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockEntity be = sl.getBlockEntity(e.blockPosition().relative(d));
            if (!(be instanceof Container c)) continue;

            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty()) continue;

                for (int j = 0; j < c.getContainerSize() && !s.isEmpty(); j++) {
                    ItemStack x = c.getItem(j);
                    if (!x.isEmpty() && !ItemStack.isSameItemSameTags(x, s)) continue;

                    int room = x.isEmpty()
                            ? Math.min(s.getMaxStackSize(), c.getMaxStackSize())
                            : Math.min(x.getMaxStackSize(), c.getMaxStackSize()) - x.getCount();

                    if (room <= 0) continue;

                    int moved = Math.min(room, s.getCount());
                    if (x.isEmpty()) c.setItem(j, s.split(moved));
                    else {
                        x.grow(moved);
                        s.shrink(moved);
                        c.setItem(j, x);
                    }
                }

                inv.setItem(i, s);
            }

            c.setChanged();
            return;
        }
    }

    private static int countItem(AdultPlayerMobEntity e, Item item) {
        int count = 0;
        SimpleContainer inv = e.getTraitInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(item)) count += s.getCount();
        }
        return count;
    }

    private static ItemStack findItemByItem(AdultPlayerMobEntity e, Item item) {
        SimpleContainer inv = e.getTraitInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(item)) return s;
        }
        return ItemStack.EMPTY;
    }

    private static int findInventorySlot(AdultPlayerMobEntity e, Item item) {
        SimpleContainer inv = e.getTraitInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) return i;
        }
        return -1;
    }

    private static void removeItems(AdultPlayerMobEntity e, Item item, int amount) {
        SimpleContainer inv = e.getTraitInventory();
        int remaining = amount;
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(item)) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                remaining -= take;
                inv.setItem(i, s);
            }
        }
    }

    /* --------------------------------------------------------------------- */
    /* Autonomous NPC speech                                                 */
    /* --------------------------------------------------------------------- */

    private static void autonomousChat(AdultPlayerMobEntity e) {
        Player nearby = e.level().getNearestPlayer(e, 10.0D);
        if (nearby == null || !nearby.isAlive()) return;

        String message = thinkConversation(e, nearby);
        if (message.isBlank()) return;

        e.chatNearby(message, 12.0D);
    }

    /**
     * Simulated conversational mind:
     * topic weight -> mood -> style weight -> memory continuity -> sentence.
     *
     * This is intentionally local and deterministic. It does not pretend to be
     * an external LLM; the NPC actually makes a weighted in-game choice and
     * persists the resulting social memory.
     */
    private static String thinkConversation(AdultPlayerMobEntity e, Player p) {
        String[] topics = {
                "SURVIVAL", "WORLD", "BUILDING", "RESOURCE", "DANGER",
                "FOOD", "ADVENTURE", "TRADE", "PLAYER", "CASUAL"
        };

        double[] weights = new double[topics.length];
        boolean dark = e.level().getMaxLocalRawBrightness(e.blockPosition()) <= 7;
        boolean hurt = e.getHealth() < e.getMaxHealth() * 0.55F;
        boolean hasTarget = e.getTarget() != null && e.getTarget().isAlive();
        boolean hasFood = hasEdible(e);
        boolean crowded = inventoryPressure(e) > 75.0D;

        for (int i = 0; i < topics.length; i++) {
            weights[i] = 1.0D + ((e.getId() * 13L + e.tickCount * (i + 3L)) & 7L) * 0.08D;
        }

        weights[0] += dark ? 32 : 3;                 // SURVIVAL
        weights[1] += 10;                            // WORLD
        weights[2] += e.getExpression() == AdultPlayerMobEntity.Expression.BUILDER ? 28 : 4;
        weights[3] += crowded ? 22 : miningOpportunity(e) * 0.35;
        weights[4] += hasTarget ? 35 : 0;
        weights[5] += hasFood ? 4 : 24;
        weights[6] += e.getExpression() == AdultPlayerMobEntity.Expression.AVENTURER ? 30 : 8;
        weights[7] += e.getExpression() == AdultPlayerMobEntity.Expression.TRADER ? 32 : 3;
        weights[8] += 14 + Math.max(0.0F, e.getChatAffinity() * 12.0F);
        weights[9] += 12;

        // Conversation continuity is strong: changing subject every sentence
        // would feel like a random text generator rather than a person.
        if (!e.getChatTopic().isBlank()) {
            for (int i = 0; i < topics.length; i++) {
                if (topics[i].equals(e.getChatTopic())) weights[i] += 24;
            }
        }

        int winner = 0;
        for (int i = 1; i < weights.length; i++) {
            if (weights[i] > weights[winner]) winner = i;
        }

        String topic = topics[winner];
        String mood = deriveChatMood(e, topic, hurt, dark);
        e.setChatTopic(topic);
        e.setChatMood(mood);
        e.setChatLastPlayer(p.getGameProfile().getName());
        e.setChatAffinity(Math.min(1.0F, e.getChatAffinity() + 0.015F));
        e.incrementChatTurn();

        return composeStyleWeightedSentence(e, p, topic, mood, hasTarget);
    }

    private static String deriveChatMood(
            AdultPlayerMobEntity e,
            String topic,
            boolean hurt,
            boolean dark
    ) {
        if (hurt) return "CAUTIOUS";
        if (dark || "DANGER".equals(topic)) return "ALERT";
        if ("ADVENTURE".equals(topic)) return "CURIOUS";
        if ("TRADE".equals(topic) || "RESOURCE".equals(topic)) return "FOCUSED";
        if (e.getChatAffinity() > 0.55F) return "FRIENDLY";
        return "NEUTRAL";
    }

    private static String composeStyleWeightedSentence(
            AdultPlayerMobEntity e,
            Player p,
            String topic,
            String mood,
            boolean hasTarget
    ) {
        AdultPlayerMobEntity.Style style = e.getStyle();
        AdultPlayerMobEntity.Expression ex = e.getExpression();
        String name = p.getGameProfile().getName();

        // Style controls cadence/directness/verbosity, not just word choice.
        // CASUAL = loose, NORMAL = balanced, HARD = blunt, HARDCORE = terse.
        if (style == AdultPlayerMobEntity.Style.HARDCORE) {
            return switch (topic) {
                case "SURVIVAL" -> "Night's coming. Shelter first.";
                case "DANGER" -> hasTarget ? "Hostile nearby. Stay sharp." : "Something feels wrong.";
                case "ADVENTURE" -> "I'm checking the area.";
                case "RESOURCE" -> "I need better resources.";
                case "PLAYER" -> "You doing alright, " + name + "?";
                default -> "I'll handle it.";
            };
        }

        if (style == AdultPlayerMobEntity.Style.HARD) {
            return switch (topic) {
                case "SURVIVAL" -> "It's getting dark. I'm securing a safe spot.";
                case "DANGER" -> hasTarget ? "There's a threat nearby. Keep your distance." : "I don't like this area.";
                case "BUILDING" -> "That space could use a proper build.";
                case "RESOURCE" -> "I'm short on useful materials.";
                case "ADVENTURE" -> "There's probably something worth checking out.";
                case "PLAYER" -> "What are you working on, " + name + "?";
                default -> "I'll keep moving.";
            };
        }

        if (style == AdultPlayerMobEntity.Style.CASUAL) {
            return switch (topic) {
                case "SURVIVAL" -> "Kinda getting dark. We should probably get some light up.";
                case "BUILDING" -> "I might mess around with a build here.";
                case "FOOD" -> "I should probably grab something to eat.";
                case "ADVENTURE" -> "Wonder what's over there.";
                case "PLAYER" -> "Hey " + name + ", what're you up to?";
                case "TRADE" -> "Got anything interesting to trade?";
                default -> "Hmm. What should I do next?";
            };
        }

        return switch (topic) {
            case "SURVIVAL" -> "It's getting dark. I should make sure we're safe.";
            case "WORLD" -> "This area is interesting. I wonder what is nearby.";
            case "BUILDING" -> "I have an idea for what I could build here.";
            case "RESOURCE" -> "I should gather a few more useful materials.";
            case "DANGER" -> hasTarget
                    ? "There's a threat nearby. I need to deal with it carefully."
                    : "Something about this area feels dangerous.";
            case "FOOD" -> "I should keep some food ready before going farther.";
            case "ADVENTURE" -> "I'm curious what's beyond this area.";
            case "TRADE" -> "I should sort my supplies and see what might be worth trading.";
            case "PLAYER" -> "What are you working on, " + name + "?";
            default -> mood.equals("FRIENDLY")
                    ? "It's good having someone around."
                    : "I'm deciding what makes the most sense to do next.";
        };
    }

    /**
     * Optional direct hook for a chat/event bridge. Feeding real player text
     * here updates the same persistent topic/mood/affinity memory used by the
     * autonomous simulator. No external AI service is required.
     */
    public static void observePlayerChat(
            AdultPlayerMobEntity e,
            Player player,
            String playerMessage
    ) {
        if (player == null || playerMessage == null || playerMessage.isBlank()) return;

        String text = playerMessage.toLowerCase(Locale.ROOT);
        e.setChatLastPlayer(player.getGameProfile().getName());

        String topic = e.getChatTopic();
        if (text.contains("build") || text.contains("house")) topic = "BUILDING";
        else if (text.contains("food") || text.contains("eat")) topic = "FOOD";
        else if (text.contains("mine") || text.contains("ore") || text.contains("iron")
                || text.contains("diamond")) topic = "RESOURCE";
        else if (text.contains("trade") || text.contains("buy") || text.contains("sell")) topic = "TRADE";
        else if (text.contains("danger") || text.contains("help") || text.contains("monster")) topic = "DANGER";
        else if (text.contains("adventure") || text.contains("explore")) topic = "ADVENTURE";
        else if (text.contains("hello") || text.contains("hi") || text.contains("hey")) topic = "PLAYER";
        else if (text.contains("where") || text.contains("here") || text.contains("world")) topic = "WORLD";

        e.setChatTopic(topic);
        e.setChatMood("FRIENDLY");
        e.setChatAffinity(Math.min(1.0F, e.getChatAffinity() + 0.06F));
        e.incrementChatTurn();
    }

}