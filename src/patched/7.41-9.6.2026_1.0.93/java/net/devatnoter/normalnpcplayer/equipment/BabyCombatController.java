package net.devatnoter.normalnpcplayer.equipment;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.item.BabyFoodItem;
import net.devatnoter.normalnpcplayer.item.TotemOfBabyCombatItem;
import net.devatnoter.normalnpcplayer.registry.ModItems;
import net.devatnoter.normalnpcplayer.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.UUID;
import java.util.EnumSet;

/**
 * Dedicated combat state for a Baby holding Totem of Baby Combat.
 * It deliberately reads only the Baby's own inventory; it never consumes
 * anything from a Player inventory.
 */
public final class BabyCombatController {
    public static final double COMBAT_RANGE = 24.0D;
    public static final int DEFAULT_TOTEM_DURABILITY = 100;
    private static final String ACTIVE_TAG = "BabyCombatActive";
    private static final String TARGET_TAG = "BabyCombatTarget";
    private static final String PLAYER_COMMAND_TARGET_TAG = "BabyCombatPlayerCommandTarget";
    private static final String ATTACK_COOLDOWN_TAG = "BabyCombatAttackCooldown";
    private static final String RANGED_COOLDOWN_TAG = "BabyCombatRangedCooldown";
    private static final String FOOD_COOLDOWN_TAG = "BabyCombatFoodCooldown";
    private static final float AUTO_EAT_HUNGER_THRESHOLD = 12.0F;
    private static final String BOW_DRAW_TICKS_TAG = "BabyCombatBowDrawTicks";
    private static final String BOW_STRAFE_DIR_TAG = "BabyCombatBowStrafeDir";
    private static final String RETREAT_TICKS_TAG = "BabyCombatRetreatTicks";
    private static final String CRIT_PENDING_TAG = "BabyCombatCritPending";
    private static final String MELEE_COMBO_TAG = "BabyCombatMeleeCombo";
    private static final String MELEE_RETREAT_TAG = "BabyCombatMeleeRetreatTicks";
    private static final String MELEE_APPROACH_TAG = "BabyCombatMeleeApproachTicks";
    private static final String THROWN_TRIDENT_UUID_TAG = "BabyCombatThrownTridentUUID";
    private static final String THROWN_TRIDENT_ITEM_TAG = "BabyCombatThrownTridentItem";
    private static final String THROWN_TRIDENT_LOYALTY_TAG = "BabyCombatThrownTridentLoyalty";
    private static final String THROWN_TRIDENT_X_TAG = "BabyCombatThrownTridentX";
    private static final String THROWN_TRIDENT_Y_TAG = "BabyCombatThrownTridentY";
    private static final String THROWN_TRIDENT_Z_TAG = "BabyCombatThrownTridentZ";
    private static final String THROWN_TRIDENT_AGE_TAG = "BabyCombatThrownTridentAge";

    private BabyCombatController() {}

    public static boolean hasCombatTotem(BabyNPCPlayerEntity baby) {
        return isCombatTotem(baby.getItemBySlot(EquipmentSlot.MAINHAND))
                || isCombatTotem(baby.getItemBySlot(EquipmentSlot.OFFHAND))
                || findCombatTotem(baby.getBabyInventory()) >= 0;
    }

    public static boolean isHoldingCombatTotem(BabyNPCPlayerEntity baby) {
        return isCombatTotem(baby.getItemBySlot(EquipmentSlot.MAINHAND))
                || isCombatTotem(baby.getItemBySlot(EquipmentSlot.OFFHAND));
    }

    public static boolean isCombatTotem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof TotemOfBabyCombatItem;
    }

    public static int findCombatTotem(Container inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isCombatTotem(inventory.getItem(i))) return i;
        }
        return -1;
    }

    /** Called from Baby.tick on the server. */
    public static void tick(BabyNPCPlayerEntity baby) {
        if (baby.level().isClientSide || !baby.isAlive() || baby.isInventoryOpen() || baby.isInitialRideActive()) return;

        if (!isHoldingCombatTotem(baby)) {
            equipTotemFromInventory(baby);
        }

        if (!isHoldingCombatTotem(baby)) {
            deactivate(baby);
            return;
        }

        tickTemporaryFireAndLava(baby);

        // A thrown trident is a real projectile. While one is outstanding,
        // the Baby must resolve that projectile before choosing another
        // ranged attack. Without Loyalty it physically follows the trident
        // to the impact point and picks it up; with Loyalty it simply waits
        // for the returning projectile.
        if (tickThrownTridentRecovery(baby)) {
            return;
        }

        LivingEntity target = findTarget(baby);
        if (target == null) {
            deactivate(baby);
            return;
        }

        activate(baby, target);
        tickOwnerDistance(baby);

        // Combat mode also owns the Baby's survival-food decision. Only the
        // Baby's own inventory is searched, and only the four milk items in
        // the combat whitelist are ever consumed. Hunger is checked
        // independently from HP so the Baby can keep fighting while eating.
        if (tickCombatFood(baby)) {
            return;
        }

        tickHealing(baby);
        tickCombat(baby, target);
    }

    private static void activate(BabyNPCPlayerEntity baby, LivingEntity target) {
        baby.getPersistentData().putBoolean(ACTIVE_TAG, true);
        baby.getPersistentData().putUUID(TARGET_TAG, target.getUUID());
    }

    private static void deactivate(BabyNPCPlayerEntity baby) {
        baby.getPersistentData().putBoolean(ACTIVE_TAG, false);
        baby.getPersistentData().remove(TARGET_TAG);
        baby.getPersistentData().putInt(ATTACK_COOLDOWN_TAG, 0);
        baby.getPersistentData().putInt(RANGED_COOLDOWN_TAG, 0);
        baby.getPersistentData().putInt(FOOD_COOLDOWN_TAG, 0);
        baby.getPersistentData().putInt(RETREAT_TICKS_TAG, 0);
        baby.getPersistentData().putBoolean(CRIT_PENDING_TAG, false);
        baby.getPersistentData().putInt(MELEE_RETREAT_TAG, 0);
        baby.getPersistentData().putInt(MELEE_APPROACH_TAG, 0);
        baby.getPersistentData().putInt(MELEE_COMBO_TAG, 0);
        if (baby.isUsingItem()) baby.stopUsingItem();
    }

    public static boolean isCombatActive(BabyNPCPlayerEntity baby) {
        return baby.getPersistentData().getBoolean(ACTIVE_TAG) && isHoldingCombatTotem(baby);
    }

    private static void tickOwnerDistance(BabyNPCPlayerEntity baby) {
        ServerPlayer owner = baby.getBehaviorOwner();
        if (owner == null || owner.level() != baby.level()) return;
        if (baby.distanceToSqr(owner) > COMBAT_RANGE * COMBAT_RANGE) {
            baby.getNavigation().stop();
            teleportNearOwner(baby, owner);
        }
    }

    private static LivingEntity findTarget(BabyNPCPlayerEntity baby) {
        // Player-command target always has priority over the normal hostile
        // target selector. The command target is deliberately stored
        // separately from BabyCombatTarget so normal target acquisition cannot
        // overwrite it. It is cleared only when the Baby is picked up/carried.
        LivingEntity commanded = getPlayerCommandTarget(baby);
        if (commanded != null
                && commanded.isAlive()
                && commanded.level() == baby.level()
                && baby.distanceToSqr(commanded) <= COMBAT_RANGE * COMBAT_RANGE) {
            return commanded;
        }

        AABB box = baby.getBoundingBox().inflate(COMBAT_RANGE);
        return baby.level().getEntitiesOfClass(Mob.class, box, mob ->
                        mob != baby
                                && mob.isAlive()
                                && mob instanceof Enemy
                                && baby.hasLineOfSight(mob))
                .stream()
                .min(Comparator.comparingDouble(baby::distanceToSqr))
                .orElse(null);
    }

    /**
     * Give this Baby an explicit target selected by its Player owner.
     * The target remains the highest-priority combat target until the Baby is
     * physically picked up/carried again.
     */
    /**
     * Assign the same explicit target to Player Babies belonging to this
     * Player. Only entity Babies in the nearby world are touched; Hardcore
     * Babies and unrelated entities are never included.
     */
    public static void setPlayerCommandTargetForOwner(ServerPlayer owner, LivingEntity target) {
        if (owner == null || target == null || !target.isAlive() || !(target instanceof Mob)) {
            return;
        }

        AABB search = owner.getBoundingBox().inflate(64.0D);
        for (BabyNPCPlayerEntity baby : owner.level().getEntitiesOfClass(
                BabyNPCPlayerEntity.class, search,
                baby -> baby.isAlive()
                        && baby.getBabyType() == net.devatnoter.normalnpcplayer.breeding.BabyType.PLAYER
                        && baby.isPlayerBabyOwner(owner))) {
            setPlayerCommandTarget(baby, target);
        }
    }

    public static void setPlayerCommandTarget(BabyNPCPlayerEntity baby, LivingEntity target) {
        if (baby == null || target == null || !target.isAlive() || !(target instanceof Mob)) {
            return;
        }
        baby.getPersistentData().putUUID(PLAYER_COMMAND_TARGET_TAG, target.getUUID());
    }

    /** Clears the explicit Player-command target. Called by pickup/carrier flow. */
    public static void clearPlayerCommandTarget(BabyNPCPlayerEntity baby) {
        if (baby == null) return;
        baby.getPersistentData().remove(PLAYER_COMMAND_TARGET_TAG);
    }

    private static LivingEntity getPlayerCommandTarget(BabyNPCPlayerEntity baby) {
        CompoundTag data = baby.getPersistentData();
        if (!data.hasUUID(PLAYER_COMMAND_TARGET_TAG)) return null;
        UUID uuid = data.getUUID(PLAYER_COMMAND_TARGET_TAG);
        if (!(baby.level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(uuid);
        return entity instanceof Mob mob ? mob : null;
    }

    private static void tickCombat(BabyNPCPlayerEntity baby, LivingEntity target) {
        int attackCooldown = Math.max(0, baby.getPersistentData().getInt(ATTACK_COOLDOWN_TAG) - 1);
        int rangedCooldown = Math.max(0, baby.getPersistentData().getInt(RANGED_COOLDOWN_TAG) - 1);
        baby.getPersistentData().putInt(ATTACK_COOLDOWN_TAG, attackCooldown);
        baby.getPersistentData().putInt(RANGED_COOLDOWN_TAG, rangedCooldown);

        baby.getLookControl().setLookAt(target, 30.0F, baby.getMaxHeadXRot());
        double distance = baby.distanceToSqr(target);

        // Retreat instead of consuming food constantly. Healing is entirely
        // inventory-local and is handled above/below by tickHealing().
        if (shouldRetreatForHealing(baby)) {
            baby.getPersistentData().putInt(RETREAT_TICKS_TAG, 20);
            retreatToOwner(baby);
            return;
        }

        int retreat = Math.max(0, baby.getPersistentData().getInt(RETREAT_TICKS_TAG) - 1);
        baby.getPersistentData().putInt(RETREAT_TICKS_TAG, retreat);
        if (retreat > 0) {
            retreatToOwner(baby);
            return;
        }

        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);

        if (distance <= 8.0D * 8.0D
                && !(main.getItem() instanceof FlintAndSteelItem)
                && !(main.is(Items.LAVA_BUCKET))
                && !(main.getItem() instanceof SwordItem)
                && !(main.getItem() instanceof AxeItem)
                && !(main.getItem() instanceof BowItem)
                && !(main.getItem() instanceof CrossbowItem)
                && !(main.getItem() instanceof TridentItem)) {
            equipSpecialFromInventory(baby);
            main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        }

        if (main.getItem() instanceof FlintAndSteelItem && distance <= 6.0D * 6.0D) {
            if (tacticalIgnite(baby, target)) return;
        }
        if (main.is(Items.LAVA_BUCKET) && baby.getPersistentData().getInt("BabyCombatLavaTicks") <= 0 && distance <= 8.0D * 8.0D) {
            if (tacticalLava(baby, target)) return;
        }

        // Ranged weapons are preferred when their ammunition rule is satisfied.
        if (distance > 6.0D * 6.0D) {
            if (isUsableBow(baby, main)) {
                rangedBow(baby, target);
                return;
            }
            if (isUsableCrossbow(baby, main)) {
                rangedCrossbow(baby, target);
                return;
            }
            if (main.getItem() instanceof TridentItem) {
                rangedTrident(baby, target);
                return;
            }

            if (isUsableBow(baby, off) || isUsableCrossbow(baby, off) || off.getItem() instanceof TridentItem) {
                swapHandsPreservingTotem(baby);
                return;
            }
            if (equipBestRangedWeapon(baby)) return;
        }

        if (main.getItem() instanceof ShieldItem || off.getItem() instanceof ShieldItem) {
            boolean dangerousNow = target.distanceToSqr(baby) < 9.0D || target.getDeltaMovement().lengthSqr() > 0.08D;
            if (dangerousNow) {
                ensureUsableCombatHand(baby, ShieldItem.class);
                if (baby.getItemBySlot(EquipmentSlot.MAINHAND).getItem() instanceof ShieldItem) {
                    if (!baby.isUsingItem()) { baby.startUsingItem(InteractionHand.MAIN_HAND); baby.triggerAnim("combat", "shield_raise"); }
                    if (distance > 3.2D * 3.2D) return;
                    baby.stopUsingItem();
                } else if (baby.getItemBySlot(EquipmentSlot.OFFHAND).getItem() instanceof ShieldItem) {
                    if (!baby.isUsingItem()) { baby.startUsingItem(InteractionHand.OFF_HAND); baby.triggerAnim("combat", "shield_raise"); }
                    if (distance > 3.2D * 3.2D) return;
                    baby.stopUsingItem();
                }
            } else if (baby.isUsingItem()) {
                baby.stopUsingItem();
            }
        }

        // Melee footwork: after every hit the Baby briefly backs away, then
        // walks back in. This prevents the old "stand inside the target and
        // spam hit" behaviour and gives the attack sequence a player-like
        // rhythm.
        int meleeRetreat = Math.max(0, baby.getPersistentData().getInt(MELEE_RETREAT_TAG) - 1);
        int meleeApproach = Math.max(0, baby.getPersistentData().getInt(MELEE_APPROACH_TAG) - 1);
        baby.getPersistentData().putInt(MELEE_RETREAT_TAG, meleeRetreat);
        baby.getPersistentData().putInt(MELEE_APPROACH_TAG, meleeApproach);

        if (meleeRetreat > 0) {
            moveAwayFromTarget(baby, target, 1.25D);
            return;
        }
        if (meleeApproach > 0) {
            baby.getNavigation().moveTo(target, 1.15D);
            baby.setSprinting(false);
            return;
        }

        if (distance <= 3.2D * 3.2D) {
            if (attackCooldown == 0 && equipBestMelee(baby)) {
                ItemStack melee = baby.getItemBySlot(EquipmentSlot.MAINHAND);
                int combo = baby.getPersistentData().getInt(MELEE_COMBO_TAG);

                // Every fourth attack is a real jump critical. The jump is
                // scheduled first, then the hit is executed while descending.
                boolean criticalCycle = combo % 4 == 3
                        && melee.getItem() instanceof SwordItem;
                boolean pendingCrit = baby.getPersistentData().getBoolean(CRIT_PENDING_TAG);
                if (criticalCycle && !pendingCrit && baby.onGround()) {
                    baby.getPersistentData().putBoolean(CRIT_PENDING_TAG, true);
                    baby.getJumpControl().jump();
                    baby.setSprinting(false);
                    return;
                }
                if (pendingCrit && !baby.onGround() && baby.getDeltaMovement().y < 0.05D) {
                    performCriticalAttack(baby, target);
                    baby.getPersistentData().putBoolean(CRIT_PENDING_TAG, false);
                    baby.getPersistentData().putInt(MELEE_COMBO_TAG, combo + 1);
                    beginMeleeFootwork(baby);
                    return;
                }
                // If the Baby landed before the crit window was reached, do
                // not leave the pending flag stuck forever. Continue the combo
                // with a normal/sweep attack on the next eligible tick.
                if (pendingCrit && baby.onGround()) {
                    baby.getPersistentData().putBoolean(CRIT_PENDING_TAG, false);
                    pendingCrit = false;
                }

                // Every second attack is a sword sweep when possible; other
                // attacks remain ordinary vanilla-style melee hits.
                boolean sweepCycle = combo % 2 == 1
                        && melee.getItem() instanceof SwordItem
                        && !baby.isSprinting()
                        && baby.onGround();
                if (sweepCycle) {
                    int sweepLevel = EnchantmentHelper.getItemEnchantmentLevel(
                            net.minecraft.world.item.enchantment.Enchantments.SWEEPING_EDGE, melee);
                    NormalNPCPlayer.LOGGER.debug(
                            "Baby sweep attempt: id={}, combo={}, grounded={}, sprinting={}, sweepEdge={}, target={}",
                            baby.getId(), combo, baby.onGround(), baby.isSprinting(), sweepLevel,
                            target.getType().toShortString());
                }
                performMeleeAttack(baby, target, sweepCycle);
                baby.getPersistentData().putInt(MELEE_COMBO_TAG, combo + 1);
                beginMeleeFootwork(baby);
            }
        } else {
            baby.getNavigation().moveTo(target, 1.35D);
            baby.setSprinting(true);
            if (baby.onGround() && distance < 9.0D * 9.0D && baby.getRandom().nextFloat() < 0.16F) {
                baby.getJumpControl().jump();
            }
        }
    }

    /**
     * Autonomous hunger feeding while Combat Mode is active.
     *
     * This deliberately does not use the normal Baby food goals, because
     * Combat Mode must remain fully inventory-local. The whitelist is exactly
     * Milk Bucket, Milk Bottle, Golden Milk Bottle and Enchanted Golden Milk
     * Bottle; no other BabyFoodItem is accepted.
     */
    private static boolean tickCombatFood(BabyNPCPlayerEntity baby) {
        int cooldown = Math.max(0, baby.getPersistentData().getInt(FOOD_COOLDOWN_TAG) - 1);
        baby.getPersistentData().putInt(FOOD_COOLDOWN_TAG, cooldown);

        if (cooldown > 0 || baby.getFoodLevelExact() > AUTO_EAT_HUNGER_THRESHOLD) {
            return false;
        }

        int best = -1;
        int bestScore = -1;
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            ItemStack stack = baby.getBabyInventory().getItem(i);
            int score = combatFoodScore(stack);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }

        if (best < 0) {
            return false;
        }

        ItemStack stack = baby.getBabyInventory().getItem(best);
        boolean consumed = false;

        if (stack.is(Items.MILK_BUCKET)) {
            consumed = baby.feedMilk();
            if (consumed) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    baby.getBabyInventory().setItem(best, new ItemStack(Items.BUCKET));
                }
            }
        } else if (stack.getItem() instanceof BabyFoodItem
                && isAllowedCombatFood(stack)) {
            BabyFoodItem.FoodData data = BabyFoodItem.getFoodData(stack);
            consumed = baby.foodFeed(data);
            if (consumed) {
                stack.shrink(1);
                baby.getBabyInventory().setItem(best, stack);
                if (!data.emptyReturn().isEmpty()) {
                    addToInventoryOrDrop(baby, data.emptyReturn().copy());
                }
            }
        }

        if (consumed) {
            // Briefly yield the combat tick so the feeding action is discrete
            // instead of consuming an entire stack in one game tick.
            baby.getPersistentData().putInt(FOOD_COOLDOWN_TAG, 10);
        }
        return consumed;
    }

    private static boolean shouldRetreatForHealing(BabyNPCPlayerEntity baby) {
        return baby.getHealth() < baby.getMaxHealth() * 0.55F && hasHealingFood(baby);
    }

    private static boolean hasHealingFood(BabyNPCPlayerEntity baby) {
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            ItemStack s = baby.getBabyInventory().getItem(i);
            if (isAllowedCombatFood(s)) return true;
        }
        return false;
    }

    /**
     * Combat healing is a strict whitelist. Do not widen this to
     * BabyFoodItem.class: a future BabyFoodItem must not automatically become
     * combat food. Only these four milk items are legal here.
     */
    private static boolean isAllowedCombatFood(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(Items.MILK_BUCKET)) return true;
        if (!(stack.getItem() instanceof BabyFoodItem food)) return false;
        return switch (food.kind()) {
            case MILK_BOTTLE, GOLDEN_MILK_BOTTLE, ENCHANTED_GOLDEN_MILK_BOTTLE -> true;
        };
    }

    private static void tickHealing(BabyNPCPlayerEntity baby) {
        if (baby.getHealth() >= baby.getMaxHealth() * 0.72F) return;
        if (baby.isUsingItem()) return;

        int best = -1;
        int score = -1;
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            ItemStack stack = baby.getBabyInventory().getItem(i);
            int s = combatFoodScore(stack);
            if (s > score) { score = s; best = i; }
        }
        if (best < 0) return;

        ItemStack stack = baby.getBabyInventory().getItem(best);
        if (stack.is(Items.MILK_BUCKET)) {
            if (baby.feedMilk()) {
                stack.shrink(1);
                if (stack.isEmpty()) baby.getBabyInventory().setItem(best, new ItemStack(Items.BUCKET));
            }
            return;
        }
        if (isAllowedCombatFood(stack) && stack.getItem() instanceof BabyFoodItem food) {
            BabyFoodItem.FoodData data = BabyFoodItem.getFoodData(stack);
            if (baby.foodFeed(data)) {
                stack.shrink(1);
                baby.getBabyInventory().setItem(best, stack);
                if (!data.emptyReturn().isEmpty()) addToInventoryOrDrop(baby, data.emptyReturn().copy());
            }
        }
    }

    private static int combatFoodScore(ItemStack stack) {
        if (!isAllowedCombatFood(stack)) return -1;
        if (stack.is(Items.MILK_BUCKET)) return 10;
        if (!(stack.getItem() instanceof BabyFoodItem food)) return -1;
        return switch (food.kind()) {
            case MILK_BOTTLE -> 20;
            case GOLDEN_MILK_BOTTLE -> 40;
            case ENCHANTED_GOLDEN_MILK_BOTTLE -> 60;
        };
    }

    private static void retreatToOwner(BabyNPCPlayerEntity baby) {
        ServerPlayer owner = baby.getBehaviorOwner();
        if (owner == null) return;
        baby.setSprinting(true);
        baby.getLookControl().setLookAt(owner, 25.0F, baby.getMaxHeadXRot());
        baby.getNavigation().moveTo(owner, 1.5D);
        if (baby.distanceToSqr(owner) > COMBAT_RANGE * COMBAT_RANGE) teleportNearOwner(baby, owner);
    }

    private static void beginMeleeFootwork(BabyNPCPlayerEntity baby) {
        baby.getPersistentData().putInt(ATTACK_COOLDOWN_TAG, 10);
        baby.getPersistentData().putInt(MELEE_RETREAT_TAG, 5);
        baby.getPersistentData().putInt(MELEE_APPROACH_TAG, 7);
        baby.getNavigation().stop();
    }

    private static void moveAwayFromTarget(BabyNPCPlayerEntity baby, LivingEntity target, double speed) {
        Vec3 away = baby.position().subtract(target.position());
        Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
        if (horizontal.lengthSqr() < 0.0001D) {
            horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            horizontal = horizontal.normalize();
        }
        Vec3 destination = baby.position().add(horizontal.scale(2.0D));
        baby.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
        baby.getLookControl().setLookAt(target, 30.0F, baby.getMaxHeadXRot());
        baby.setSprinting(false);
    }

    private static void performMeleeAttack(BabyNPCPlayerEntity baby, LivingEntity target, boolean sweep) {
        baby.setSprinting(false);
        baby.getLookControl().setLookAt(target, 30.0F, baby.getMaxHeadXRot());
        baby.swingMainHand();

        boolean hit = baby.doHurtTarget(target);
        if (!hit) return;

        if (sweep) {
            // Vanilla performs the sweep after a successful primary hit even if
            // that hit killed the primary target. Do not gate the sweep on
            // target.isAlive(); otherwise ordinary mobs that die in one hit
            // never produce the sweep attack.
            performVanillaStyleSweep(baby, target);
        }

        // One successful swing = one durability use. Sweep secondary hits do not
        // consume another point, matching the single Player sword swing.
        damageHeldCombatItem(baby, InteractionHand.MAIN_HAND, 1);
    }

    /**
     * Vanilla-style Java sweep: only a grounded, non-sprinting sword swing can
     * sweep. The secondary-hit area is centered on the mob actually struck,
     * not on the attacker. Sweeping Edge is read through vanilla's
     * EnchantmentHelper, so level III naturally receives the vanilla 75% ratio.
     */
    private static void performVanillaStyleSweep(
            BabyNPCPlayerEntity baby,
            LivingEntity primaryTarget
    ) {
        ItemStack weapon = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!(weapon.getItem() instanceof SwordItem) || !baby.onGround() || baby.isSprinting()) {
            NormalNPCPlayer.LOGGER.debug(
                    "Baby sweep skipped: id={}, sword={}, grounded={}, sprinting={}",
                    baby.getId(), weapon.getItem() instanceof SwordItem, baby.onGround(), baby.isSprinting());
            return;
        }

        double baseAttack = baby.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        // Sharpness / Smite / Bane contribution is part of the weapon damage
        // used by the vanilla Player attack calculation before the sweep ratio.
        baseAttack += EnchantmentHelper.getDamageBonus(weapon, primaryTarget.getMobType());
        int sweepLevel = EnchantmentHelper.getItemEnchantmentLevel(
                net.minecraft.world.item.enchantment.Enchantments.SWEEPING_EDGE, weapon);
        float sweepRatio = EnchantmentHelper.getSweepingDamageRatio(baby);
        float sweepDamage = (float) (1.0D + baseAttack * sweepRatio);

        // Vanilla's ordinary sweep (no Sweeping Edge) still deals 1 damage to
        // secondary targets. Sweeping Edge changes that ratio to its vanilla
        // level-dependent value (I=50%, II=66.7%, III=75%).
        AABB area = primaryTarget.getBoundingBox().inflate(1.0D, 0.25D, 1.0D);
        int nearbyCount = 0;
        for (LivingEntity nearby : baby.level().getEntitiesOfClass(
                LivingEntity.class,
                area,
                entity -> entity != baby
                        && entity != primaryTarget
                        && entity.isAlive()
                        && !baby.isAlliedTo(entity)
                        && baby.distanceToSqr(entity) < 9.0D)) {
            nearbyCount++;
            if (nearby.hurt(baby.level().damageSources().mobAttack(baby), sweepDamage)) {
                EnchantmentHelper.doPostDamageEffects(baby, nearby);

                Vec3 knockback = nearby.position().subtract(baby.position());
                Vec3 horizontal = new Vec3(knockback.x, 0.0D, knockback.z);
                if (horizontal.lengthSqr() > 0.0001D) {
                    horizontal = horizontal.normalize();
                    nearby.push(horizontal.x * 0.4D, 0.1D, horizontal.z * 0.4D);
                }
            }
        }

        NormalNPCPlayer.LOGGER.debug(
                "Baby sweep executed: id={}, target={}, sweepEdge={}, ratio={}, secondaryDamage={}, nearby={}",
                baby.getId(), primaryTarget.getType().toShortString(), sweepLevel, sweepRatio, sweepDamage, nearbyCount);

        if (baby.level() instanceof ServerLevel level) {
            level.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    baby.getX(),
                    baby.getY() + baby.getBbHeight() * 0.5D,
                    baby.getZ(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
            baby.level().playSound(
                    null,
                    baby.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_SWEEP,
                    baby.getSoundSource(),
                    0.8F,
                    1.0F);
        }
    }

    private static void performCriticalAttack(BabyNPCPlayerEntity baby, LivingEntity target) {
        // The attack is scheduled from a real jump and executed while the Baby
        // is descending. This makes the attack state physically airborne instead
        // of faking a jump after the hit. The Mob damage path remains vanilla.
        baby.swingMainHand();
        AttributeInstance attack = baby.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        AttributeModifier criticalModifier = new AttributeModifier(
                java.util.UUID.randomUUID(),
                "Baby combat critical hit",
                Math.max(0.5D, attack.getValue() * 0.5D),
                AttributeModifier.Operation.ADDITION
        );
        attack.addTransientModifier(criticalModifier);
        boolean hit;
        try {
            hit = baby.doHurtTarget(target);
        } finally {
            attack.removeModifier(criticalModifier);
        }
        if (hit) {
            // Mob#doHurtTarget does not apply the Player held-item durability
            // step, so a successful melee hit costs exactly 1 durability.
            damageHeldCombatItem(baby, InteractionHand.MAIN_HAND, 1);

            ((ServerLevel) baby.level()).sendParticles(
                    net.minecraft.core.particles.ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.55D, target.getZ(),
                    8, 0.18D, 0.18D, 0.18D, 0.08D);
            baby.level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                    baby.getSoundSource(), 0.8F, 1.05F);
        }
    }

    private static boolean isUsableBow(BabyNPCPlayerEntity baby, ItemStack stack) {
        if (!(stack.getItem() instanceof BowItem)) return false;
        // Infinity changes consumption, not the Baby's ammunition requirement:
        // at least one real arrow must exist in Baby inventory before firing.
        return hasArrow(baby);
    }

    private static boolean isUsableCrossbow(BabyNPCPlayerEntity baby, ItemStack stack) {
        if (!(stack.getItem() instanceof CrossbowItem)) return false;
        if (CrossbowItem.isCharged(stack)) return true;
        return hasArrow(baby);
    }

    private static boolean hasArrow(BabyNPCPlayerEntity baby) {
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            if (baby.getBabyInventory().getItem(i).getItem() instanceof net.minecraft.world.item.ArrowItem) return true;
        }
        return false;
    }

    private static boolean tacticalIgnite(BabyNPCPlayerEntity baby, LivingEntity target) {
        BlockPos base = target.blockPosition();
        Vec3 away = target.position().subtract(baby.position());
        if (away.lengthSqr() < 0.01D) return false;
        away = away.normalize();
        int x = base.getX() + (int)Math.round(away.x);
        int z = base.getZ() + (int)Math.round(away.z);
        BlockPos firePos = new BlockPos(x, base.getY(), z);
        if (!baby.level().getBlockState(firePos).isAir()) return false;
        if (baby.distanceToSqr(firePos.getX() + 0.5D, firePos.getY(), firePos.getZ() + 0.5D) < 4.0D * 4.0D) return false;
        if (!Blocks.FIRE.defaultBlockState().canSurvive(baby.level(), firePos)) return false;
        baby.level().setBlock(firePos, Blocks.FIRE.defaultBlockState(), 11);
        baby.level().playSound(null, firePos, SoundEvents.FLINTANDSTEEL_USE, baby.getSoundSource(), 0.8F, 1.0F);
        baby.getItemBySlot(EquipmentSlot.MAINHAND).hurtAndBreak(1, baby, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        baby.getNavigation().moveTo(baby.getX() - away.x * 2.5D, baby.getY(), baby.getZ() - away.z * 2.5D, 1.4D);
        baby.getPersistentData().putLong("BabyCombatFirePos", firePos.asLong());
        baby.getPersistentData().putInt("BabyCombatFireTicks", 40);
        return true;
    }

    private static boolean tacticalLava(BabyNPCPlayerEntity baby, LivingEntity target) {
        BlockPos base = target.blockPosition();
        Vec3 away = target.position().subtract(baby.position());
        if (away.lengthSqr() < 0.01D) return false;
        away = away.normalize();
        int x = base.getX() + (int)Math.round(away.x);
        int z = base.getZ() + (int)Math.round(away.z);
        BlockPos lavaPos = new BlockPos(x, base.getY(), z);
        if (!baby.level().getBlockState(lavaPos).isAir()) return false;
        if (baby.distanceToSqr(lavaPos.getX() + 0.5D, lavaPos.getY(), lavaPos.getZ() + 0.5D) < 6.0D * 6.0D) return false;
        baby.level().setBlock(lavaPos, Blocks.LAVA.defaultBlockState(), 11);
        baby.level().playSound(null, lavaPos, SoundEvents.BUCKET_EMPTY_LAVA, baby.getSoundSource(), 0.8F, 1.0F);
        baby.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BUCKET));
        baby.getPersistentData().putLong("BabyCombatLavaPos", lavaPos.asLong());
        baby.getPersistentData().putInt("BabyCombatLavaTicks", 12);
        baby.getNavigation().moveTo(baby.getX() - away.x * 4.0D, baby.getY(), baby.getZ() - away.z * 4.0D, 1.6D);
        return true;
    }

    private static void tickTemporaryFireAndLava(BabyNPCPlayerEntity baby) {
        if (!(baby.level() instanceof ServerLevel level)) return;
        int fireTicks = baby.getPersistentData().getInt("BabyCombatFireTicks");
        if (fireTicks > 0) {
            fireTicks--;
            baby.getPersistentData().putInt("BabyCombatFireTicks", fireTicks);
            if (fireTicks == 0 && baby.getPersistentData().contains("BabyCombatFirePos")) {
                BlockPos p = BlockPos.of(baby.getPersistentData().getLong("BabyCombatFirePos"));
                if (level.getBlockState(p).is(Blocks.FIRE)) level.removeBlock(p, false);
                baby.getPersistentData().remove("BabyCombatFirePos");
            }
        }
        int lavaTicks = baby.getPersistentData().getInt("BabyCombatLavaTicks");
        if (lavaTicks > 0) {
            lavaTicks--;
            baby.getPersistentData().putInt("BabyCombatLavaTicks", lavaTicks);
            if (lavaTicks == 0 && baby.getPersistentData().contains("BabyCombatLavaPos")) {
                BlockPos p = BlockPos.of(baby.getPersistentData().getLong("BabyCombatLavaPos"));
                if (level.getBlockState(p).is(Blocks.LAVA)) level.removeBlock(p, false);
                addToInventoryOrDrop(baby, new ItemStack(Items.LAVA_BUCKET));
                baby.getPersistentData().remove("BabyCombatLavaPos");
            }
        }
    }

    /**
     * Applies the durability cost of one successful combat use to the actual
     * held stack. This fills the gap between Mob AI attacks and Player item
     * usage: sword/axe hits, bow shots and crossbow shots all cost 1 durability.
     */
    private static void damageHeldCombatItem(BabyNPCPlayerEntity baby, InteractionHand hand, int amount) {
        ItemStack stack = baby.getItemInHand(hand);
        if (stack.isEmpty() || !stack.isDamageableItem() || amount <= 0) return;

        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND
                : EquipmentSlot.OFFHAND;
        stack.hurtAndBreak(amount, baby, entity -> entity.broadcastBreakEvent(slot));
        if (stack.isEmpty()) {
            baby.setItemInHand(hand, ItemStack.EMPTY);
        } else {
            baby.setItemInHand(hand, stack);
        }
    }

    /**
     * A Baby must never fire a ranged projectile through its owner. If the
     * owner is physically between the Baby and the hostile target, move to a
     * lateral firing position first instead of taking a shot that can hit the
     * owner.
     */
    private static boolean isOwnerBlockingShot(BabyNPCPlayerEntity baby, LivingEntity target) {
        ServerPlayer owner = baby.getBehaviorOwner();
        if (owner == null || owner.level() != baby.level() || !owner.isAlive()) return false;

        double babyDistance = baby.distanceToSqr(owner);
        double targetDistance = baby.distanceToSqr(target);
        if (babyDistance >= targetDistance) return false;

        Vec3 from = baby.getEyePosition();
        Vec3 to = target.getEyePosition();
        return owner.getBoundingBox().inflate(0.25D).clip(from, to).isPresent();
    }

    private static void repositionForRangedShot(BabyNPCPlayerEntity baby, LivingEntity target) {
        ServerPlayer owner = baby.getBehaviorOwner();
        if (owner == null) return;

        Vec3 shot = target.getEyePosition().subtract(baby.getEyePosition());
        Vec3 horizontal = new Vec3(shot.x, 0.0D, shot.z);
        if (horizontal.lengthSqr() < 0.0001D) return;

        horizontal = horizontal.normalize();
        Vec3 side = new Vec3(-horizontal.z, 0.0D, horizontal.x);
        double sideDistance = 4.0D;

        Vec3 first = baby.position().add(side.scale(sideDistance));
        Vec3 second = baby.position().subtract(side.scale(sideDistance));
        Vec3 destination = first;
        if (!baby.level().getBlockState(BlockPos.containing(first)).isAir()
                || !baby.level().getBlockState(BlockPos.containing(first).above()).isAir()) {
            destination = second;
        }

        baby.getLookControl().setLookAt(target, 30.0F, baby.getMaxHeadXRot());
        baby.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.35D);
        baby.setSprinting(false);
    }

    private static void rangedBow(BabyNPCPlayerEntity baby, LivingEntity target) {
        ItemStack bow = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!(bow.getItem() instanceof BowItem) || !hasArrow(baby)) return;

        // Keep the Baby at a useful bow distance while it draws. Movement is
        // deliberately an orbit/strafe rather than a teleport or hard lock: it
        // behaves like alternating W/A/S/D corrections around the target.
        double distance = Math.sqrt(baby.distanceToSqr(target));
        Vec3 toTarget = target.position().subtract(baby.position());
        Vec3 horizontal = new Vec3(toTarget.x, 0.0D, toTarget.z);
        if (horizontal.lengthSqr() < 0.0001D) horizontal = new Vec3(1, 0, 0);
        horizontal = horizontal.normalize();
        Vec3 side = new Vec3(-horizontal.z, 0.0D, horizontal.x);
        int dir = baby.getPersistentData().getInt(BOW_STRAFE_DIR_TAG);
        if (dir == 0) {
            dir = baby.getRandom().nextBoolean() ? 1 : -1;
            baby.getPersistentData().putInt(BOW_STRAFE_DIR_TAG, dir);
        }

        double desired = 12.0D;
        Vec3 move = side.scale(dir * 0.85D);
        if (distance < 9.0D) move = move.subtract(horizontal.scale(1.0D));
        else if (distance > 15.0D) move = move.add(horizontal.scale(1.0D));

        Vec3 destination = baby.position().add(move);
        baby.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.15D);
        baby.setSprinting(distance > desired + 2.0D);
        baby.getLookControl().setLookAt(target, 30.0F, baby.getMaxHeadXRot());

        if (baby.getPersistentData().getInt(RANGED_COOLDOWN_TAG) > 0) return;

        if (!baby.isUsingItem()) {
            baby.startUsingItem(InteractionHand.MAIN_HAND);
            baby.getPersistentData().putInt(BOW_DRAW_TICKS_TAG, 0);
            return;
        }

        int draw = baby.getPersistentData().getInt(BOW_DRAW_TICKS_TAG) + 1;
        baby.getPersistentData().putInt(BOW_DRAW_TICKS_TAG, draw);

        // Full vanilla bow draw. releaseUsing() itself expects Player inventory
        // access in 1.20.1, so we retain the vanilla charge curve and projectile
        // construction while supplying ammunition from the Baby inventory.
        if (draw >= BowItem.MAX_DRAW_DURATION) {
            baby.stopUsingItem();
            if (fireArrow(baby, target, bow, draw)) {
                damageHeldCombatItem(baby, InteractionHand.MAIN_HAND, 1);
                baby.getPersistentData().putInt(RANGED_COOLDOWN_TAG, 12);
                baby.swingMainHand();
                baby.triggerAnim("combat", "bow_shot");
                baby.getPersistentData().putInt(BOW_STRAFE_DIR_TAG, -dir);
            }
            baby.getPersistentData().putInt(BOW_DRAW_TICKS_TAG, 0);
        }
    }

    private static void rangedCrossbow(BabyNPCPlayerEntity baby, LivingEntity target) {
        if (baby.getPersistentData().getInt(RANGED_COOLDOWN_TAG) > 0) return;
        ItemStack crossbow = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!isUsableCrossbow(baby, crossbow)) return;

        boolean wasAlreadyCharged = CrossbowItem.isCharged(crossbow);
        if (!wasAlreadyCharged) {
            ItemStack ammo = findArrowStack(baby);
            if (ammo.isEmpty()) return;

            if (!addVanillaChargedProjectile(crossbow, new ItemStack(Items.ARROW))) {
                return;
            }
            ammo.shrink(1);
            CrossbowItem.setCharged(crossbow, true);
            baby.triggerAnim("combat", "crossbow_load");
        }

        baby.getLookControl().setLookAt(target, 30.0F, baby.getMaxHeadXRot());

        try {
            CrossbowItem.performShooting(
                    baby.level(),
                    baby,
                    InteractionHand.MAIN_HAND,
                    crossbow,
                    1.6F,
                    1.0F);
        } catch (RuntimeException ex) {
            // Do not take the server down if a third-party mixin changes the
            // vanilla crossbow path. Log the exact Baby/crossbow state and
            // clear the charged flag so the next combat tick can recover.
            clearVanillaChargedProjectiles(crossbow);
            CrossbowItem.setCharged(crossbow, false);
            NormalNPCPlayer.LOGGER.error(
                    "Baby crossbow firing failed: id={}, chargedBefore={}, item={}, target={}",
                    baby.getId(), wasAlreadyCharged, crossbow, target.getType().toShortString(), ex);
            return;
        }

        // Vanilla firing consumes the charged projectile, but explicitly clear
        // both pieces of crossbow state here as well. Baby is not a Player and
        // must be able to reload the same CrossbowItemStack on the next cycle.
        clearVanillaChargedProjectiles(crossbow);
        CrossbowItem.setCharged(crossbow, false);

        damageHeldCombatItem(baby, InteractionHand.MAIN_HAND, 1);
        baby.getPersistentData().putInt(RANGED_COOLDOWN_TAG, 25);
        baby.swingMainHand();
        baby.triggerAnim("combat", "crossbow_shot");
    }

    private static void clearVanillaChargedProjectiles(ItemStack crossbow) {
        try {
            for (java.lang.reflect.Method method : CrossbowItem.class.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
                if (method.getReturnType() != void.class) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 1 || params[0] != ItemStack.class) continue;
                if (!method.getName().equals("clearChargedProjectiles")) continue;
                method.setAccessible(true);
                method.invoke(null, crossbow);
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            NormalNPCPlayer.LOGGER.error(
                    "Failed to clear Baby crossbow charged projectiles: {}", ex.toString(), ex);
        }
    }

    private static boolean addVanillaChargedProjectile(ItemStack crossbow, ItemStack projectile) {
        try {
            for (java.lang.reflect.Method method : CrossbowItem.class.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
                if (method.getReturnType() != void.class) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 2
                        || params[0] != ItemStack.class
                        || params[1] != ItemStack.class) continue;
                method.setAccessible(true);
                method.invoke(null, crossbow, projectile);
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            NormalNPCPlayer.LOGGER.error(
                    "Failed to load Baby crossbow using vanilla helper: {}", ex.toString(), ex);
        }
        return false;
    }

    private static boolean fireArrow(
            BabyNPCPlayerEntity baby,
            LivingEntity target,
            ItemStack weapon,
            int charge
    ) {
        ItemStack ammo = findArrowStack(baby);
        if (ammo.isEmpty() || !(weapon.getItem() instanceof BowItem)) return false;

        float power = BowItem.getPowerForTime(Math.min(charge, BowItem.MAX_DRAW_DURATION));
        if (power < 0.1F) return false;

        AbstractArrow arrow = net.minecraft.world.entity.projectile.ProjectileUtil
                .getMobArrow(baby, new ItemStack(Items.ARROW), power * 3.0F);
        arrow.setCritArrow(power >= 1.0F);

        // Aim at the target's current eye position. Gravity is then applied by
        // the normal AbstractArrow tick, so short shots are flatter and long
        // shots naturally form the same downward ballistic arc as a player
        // arrow. No artificial hit correction is performed.
        Vec3 delta = target.getEyePosition().subtract(arrow.position());
        arrow.shoot(delta.x, delta.y, delta.z, power * 3.0F, 1.0F);
        ((ServerLevel) baby.level()).addFreshEntity(arrow);

        boolean infinity = EnchantmentHelper.getItemEnchantmentLevel(
                net.minecraft.world.item.enchantment.Enchantments.INFINITY_ARROWS, weapon) > 0;
        if (!infinity) ammo.shrink(1);
        return true;
    }

    private static ItemStack findArrowStack(BabyNPCPlayerEntity baby) {
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            ItemStack s = baby.getBabyInventory().getItem(i);
            if (s.getItem() instanceof net.minecraft.world.item.ArrowItem) return s;
        }
        return ItemStack.EMPTY;
    }

    private static void rangedTrident(BabyNPCPlayerEntity baby, LivingEntity target) {
        if (baby.getPersistentData().getInt(RANGED_COOLDOWN_TAG) > 0) return;
        if (baby.getPersistentData().hasUUID(THROWN_TRIDENT_UUID_TAG)) return;

        ItemStack trident = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!(trident.getItem() instanceof TridentItem tridentItem)) return;

        // TridentItem.releaseUsing contains the real vanilla trident throw
        // implementation: durability, Loyalty/Riptide handling, projectile
        // creation and throw physics. We deliberately invoke that code rather
        // than maintaining a second projectile implementation here.
        ItemStack recoveryStack = trident.copy();
        int oldDamage = recoveryStack.getDamageValue();
        tridentItem.releaseUsing(
                trident,
                baby.level(),
                baby,
                0);

        ThrownTrident projectile = baby.level().getEntitiesOfClass(
                ThrownTrident.class,
                baby.getBoundingBox().inflate(4.0D),
                entity -> entity.isAlive() && entity.getOwner() == baby)
                .stream()
                .max(Comparator.comparingDouble(baby::distanceToSqr))
                .orElse(null);

        if (projectile == null) {
            // No projectile means vanilla refused the throw (for example a
            // Riptide-only use outside valid conditions). Do not manufacture a
            // replacement projectile or consume another durability point.
            baby.setItemSlot(EquipmentSlot.MAINHAND, trident);
            return;
        }

        // releaseUsing has already applied the vanilla durability step. Keep a
        // copy for Baby's physical pickup because Baby is not Player and cannot
        // enter the normal Player pickup path. The saved copy is exactly one
        // durability point beyond the original stack.
        if (recoveryStack.isDamageableItem()) {
            recoveryStack.setDamageValue(Math.min(
                    recoveryStack.getMaxDamage() - 1,
                    oldDamage + 1));
        }

        int loyalty = EnchantmentHelper.getItemEnchantmentLevel(
                net.minecraft.world.item.enchantment.Enchantments.LOYALTY,
                recoveryStack);

        CompoundTag recovery = recoveryStack.save(new CompoundTag());
        baby.getPersistentData().put(THROWN_TRIDENT_ITEM_TAG, recovery);
        baby.getPersistentData().putUUID(THROWN_TRIDENT_UUID_TAG, projectile.getUUID());
        baby.getPersistentData().putBoolean(THROWN_TRIDENT_LOYALTY_TAG, loyalty > 0);
        baby.getPersistentData().putDouble(THROWN_TRIDENT_X_TAG, projectile.getX());
        baby.getPersistentData().putDouble(THROWN_TRIDENT_Y_TAG, projectile.getY());
        baby.getPersistentData().putDouble(THROWN_TRIDENT_Z_TAG, projectile.getZ());
        baby.getPersistentData().putInt(THROWN_TRIDENT_AGE_TAG, 0);
        baby.getPersistentData().putInt(RANGED_COOLDOWN_TAG, 30);
        baby.triggerAnim("combat", "trident_throw");
    }

    /**
     * Resolves the Baby's outstanding thrown trident. No-Loyalty tridents
     * require a physical retrieval run; Loyalty tridents are collected when
     * the returning projectile reaches the Baby.
     */
    private static boolean tickThrownTridentRecovery(BabyNPCPlayerEntity baby) {
        var data = baby.getPersistentData();
        if (!data.hasUUID(THROWN_TRIDENT_UUID_TAG)) return false;

        int age = data.getInt(THROWN_TRIDENT_AGE_TAG) + 1;
        data.putInt(THROWN_TRIDENT_AGE_TAG, age);
        java.util.UUID thrownId = data.getUUID(THROWN_TRIDENT_UUID_TAG);
        ThrownTrident projectile = null;

        if (baby.level() instanceof ServerLevel level) {
            Entity direct = level.getEntity(thrownId);
            if (direct instanceof ThrownTrident trident && trident.isAlive() && trident.getOwner() == baby) {
                projectile = trident;
            } else {
                // Secondary lookup handles cases where the normal UUID lookup
                // is temporarily stale after the projectile changes chunks.
                BlockPos center = BlockPos.containing(
                        data.getDouble(THROWN_TRIDENT_X_TAG),
                        data.getDouble(THROWN_TRIDENT_Y_TAG),
                        data.getDouble(THROWN_TRIDENT_Z_TAG));
                projectile = level.getEntitiesOfClass(ThrownTrident.class,
                        new AABB(center).inflate(16.0D),
                        tr -> tr.isAlive() && tr.getUUID().equals(thrownId) && tr.getOwner() == baby)
                        .stream().findFirst().orElse(null);
            }
        }

        if (projectile == null) {
            // Keep looking instead of clearing the state. For a normal thrown
            // trident the entity remains in the world until it is collected;
            // the Baby walks toward the last known area to make the chunk
            // available and then the secondary lookup finds the projectile.
            if (age <= 2400) {
                BlockPos approach = findNearestTridentApproachPosition(baby,
                        data.getDouble(THROWN_TRIDENT_X_TAG),
                        data.getDouble(THROWN_TRIDENT_Y_TAG),
                        data.getDouble(THROWN_TRIDENT_Z_TAG));
                if (approach != null) {
                    baby.getNavigation().moveTo(approach.getX() + 0.5D, approach.getY(),
                            approach.getZ() + 0.5D, 1.35D);
                }
                return true;
            }
            clearThrownTridentState(data);
            return false;
        }

        data.putDouble(THROWN_TRIDENT_X_TAG, projectile.getX());
        data.putDouble(THROWN_TRIDENT_Y_TAG, projectile.getY());
        data.putDouble(THROWN_TRIDENT_Z_TAG, projectile.getZ());

        boolean loyalty = data.getBoolean(THROWN_TRIDENT_LOYALTY_TAG);
        double distanceSqr = baby.distanceToSqr(projectile);

        if (!loyalty) {
            boolean inGround = projectile instanceof net.devatnoter.normalnpcplayer.mixin.ThrownTridentGroundAccessor accessor
                    && accessor.normalNPCPlayer$isInGround();
            boolean settled = inGround
                    || (age >= 10 && projectile.getDeltaMovement().lengthSqr() <= 0.0025D);

            if (settled) {
                baby.getLookControl().setLookAt(projectile, 20.0F, baby.getMaxHeadXRot());
                if (distanceSqr > 6.25D) {
                    BlockPos approach = findNearestTridentApproachPosition(baby,
                            projectile.getX(), projectile.getY(), projectile.getZ());
                    if (approach != null) {
                        baby.getNavigation().moveTo(approach.getX() + 0.5D, approach.getY(),
                                approach.getZ() + 0.5D, 1.35D);
                    }
                    return true;
                }
                recoverThrownTrident(baby, projectile, data);
                return true;
            }

            baby.getNavigation().stop();
            return true;
        }

        // Loyalty projectiles return naturally; never make the Baby chase them.
        if (distanceSqr <= 2.25D) {
            recoverThrownTrident(baby, projectile, data);
            return true;
        }
        baby.getNavigation().stop();
        return true;
    }

    private static BlockPos findNearestTridentApproachPosition(
            BabyNPCPlayerEntity baby, double x, double y, double z) {
        BlockPos center = BlockPos.containing(x, y, z);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!safeTeleportPosition(baby, p)) continue;
                    double d = p.distSqr(center);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    private static void recoverThrownTrident(
            BabyNPCPlayerEntity baby,
            ThrownTrident projectile,
            net.minecraft.nbt.CompoundTag data
    ) {
        ItemStack recovered = data.contains(THROWN_TRIDENT_ITEM_TAG, 10)
                ? ItemStack.of(data.getCompound(THROWN_TRIDENT_ITEM_TAG))
                : ItemStack.EMPTY;

        projectile.discard();
        if (!recovered.isEmpty()) {
            addToInventoryOrDrop(baby, recovered);
        }
        clearThrownTridentState(data);
        baby.getPersistentData().putInt(RANGED_COOLDOWN_TAG, 10);
    }

    private static void clearThrownTridentState(net.minecraft.nbt.CompoundTag data) {
        data.remove(THROWN_TRIDENT_UUID_TAG);
        data.remove(THROWN_TRIDENT_ITEM_TAG);
        data.remove(THROWN_TRIDENT_LOYALTY_TAG);
        data.remove(THROWN_TRIDENT_X_TAG);
        data.remove(THROWN_TRIDENT_Y_TAG);
        data.remove(THROWN_TRIDENT_Z_TAG);
        data.remove(THROWN_TRIDENT_AGE_TAG);
    }

    private static void equipSpecialFromInventory(BabyNPCPlayerEntity baby) {
        ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);
        if ((off.getItem() instanceof FlintAndSteelItem || off.is(Items.LAVA_BUCKET)) && !isCombatTotem(off)) {
            swapHands(baby);
            return;
        }
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            ItemStack s = baby.getBabyInventory().getItem(i);
            if (s.getItem() instanceof FlintAndSteelItem || s.is(Items.LAVA_BUCKET)) {
                equipFromInventoryAsMain(baby, i);
                return;
            }
        }
    }

    private static boolean equipBestRangedWeapon(BabyNPCPlayerEntity baby) {
        int slot = -1;
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            ItemStack s = baby.getBabyInventory().getItem(i);
            if (isUsableBow(baby, s) || isUsableCrossbow(baby, s) || s.getItem() instanceof TridentItem) { slot = i; break; }
        }
        return slot >= 0 && equipFromInventoryAsMain(baby, slot);
    }

    private static boolean equipBestMelee(BabyNPCPlayerEntity baby) {
        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (main.getItem() instanceof SwordItem || main.getItem() instanceof AxeItem || main.getItem() instanceof TridentItem) return true;
        int best = -1;
        int score = -1;
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            ItemStack s = baby.getBabyInventory().getItem(i);
            int v = s.getItem() instanceof SwordItem ? 20 : s.getItem() instanceof AxeItem ? 15 : s.getItem() instanceof TridentItem ? 18 : -1;
            if (v > score) { score = v; best = i; }
        }
        return best >= 0 && equipFromInventoryAsMain(baby, best);
    }

    private static void ensureUsableCombatHand(BabyNPCPlayerEntity baby, Class<?> kind) {
        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);
        if (kind.isInstance(main.getItem())) return;
        if (kind.isInstance(off.getItem()) && !isCombatTotem(off)) {
            swapHands(baby);
            return;
        }
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            if (kind.isInstance(baby.getBabyInventory().getItem(i).getItem())) {
                equipFromInventoryAsMain(baby, i);
                return;
            }
        }
    }

    private static void swapHandsPreservingTotem(BabyNPCPlayerEntity baby) {
        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);
        if (isCombatTotem(main) && !isCombatTotem(off)) {
            swapHands(baby);
        } else if (!isCombatTotem(main) && isCombatTotem(off)) {
            // Already correct: weapon/main + totem/off.
        } else if (!isCombatTotem(main) && !isCombatTotem(off)) {
            swapHands(baby);
        }
    }

    private static boolean equipFromInventoryAsMain(BabyNPCPlayerEntity baby, int slot) {
        ItemStack candidate = baby.getBabyInventory().getItem(slot);
        if (candidate.isEmpty()) return false;
        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND).copy();
        ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND).copy();

        if (isCombatTotem(main)) {
            if (!off.isEmpty()) addToInventoryOrDrop(baby, off);
            baby.setItemSlot(EquipmentSlot.OFFHAND, main);
        } else if (isCombatTotem(off)) {
            if (!main.isEmpty()) addToInventoryOrDrop(baby, main);
        } else if (!main.isEmpty()) {
            addToInventoryOrDrop(baby, main);
        }
        baby.setItemSlot(EquipmentSlot.MAINHAND, candidate.copyWithCount(1));
        candidate.shrink(1);
        baby.getBabyInventory().setItem(slot, candidate);
        return true;
    }

    private static void equipTotemFromInventory(BabyNPCPlayerEntity baby) {
        int slot = findCombatTotem(baby.getBabyInventory());
        if (slot < 0) return;
        ItemStack totem = baby.getBabyInventory().getItem(slot);
        ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);
        if (off.isEmpty()) {
            baby.setItemSlot(EquipmentSlot.OFFHAND, totem.copyWithCount(1));
            totem.shrink(1);
            baby.getBabyInventory().setItem(slot, totem);
            return;
        }
        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (main.isEmpty()) {
            baby.setItemSlot(EquipmentSlot.MAINHAND, totem.copyWithCount(1));
            totem.shrink(1);
            baby.getBabyInventory().setItem(slot, totem);
        }
    }

    private static void swapHands(BabyNPCPlayerEntity baby) {
        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);
        baby.setItemSlot(EquipmentSlot.MAINHAND, off);
        baby.setItemSlot(EquipmentSlot.OFFHAND, main);
    }

    private static void addToInventoryOrDrop(BabyNPCPlayerEntity baby, ItemStack stack) {
        if (stack.isEmpty()) return;
        Container inv = baby.getBabyInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack existing = inv.getItem(i);
            if (existing.isEmpty()) { inv.setItem(i, stack); return; }
            if (ItemStack.isSameItemSameTags(existing, stack)) {
                int space = Math.min(existing.getMaxStackSize(), inv.getMaxStackSize()) - existing.getCount();
                if (space > 0) { int n = Math.min(space, stack.getCount()); existing.grow(n); stack.shrink(n); if (stack.isEmpty()) return; }
            }
        }
        baby.spawnAtLocation(stack);
    }

    private static void teleportNearOwner(BabyNPCPlayerEntity baby, ServerPlayer owner) {
        int bx = owner.blockPosition().getX();
        int by = owner.blockPosition().getY();
        int bz = owner.blockPosition().getZ();
        int[][] offsets = {{2,0},{-2,0},{0,2},{0,-2},{2,2},{2,-2},{-2,2},{-2,-2},{1,2},{-1,2},{1,-2},{-1,-2}};
        for (int[] o : offsets) {
            BlockPos p = new BlockPos(bx + o[0], by, bz + o[1]);
            if (safeTeleportPosition(baby, p)) {
                baby.teleportTo(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D);
                baby.getNavigation().stop();
                return;
            }
        }
    }

    private static boolean safeTeleportPosition(BabyNPCPlayerEntity baby, BlockPos pos) {
        BlockState feet = baby.level().getBlockState(pos);
        BlockState head = baby.level().getBlockState(pos.above());
        BlockState floor = baby.level().getBlockState(pos.below());
        return feet.isAir() && head.isAir() && floor.isFaceSturdy(baby.level(), pos.below(), Direction.UP)
                && !floor.liquid();
    }

    private static void playTotemBreakEffect(BabyNPCPlayerEntity baby) {
        if (!(baby.level() instanceof ServerLevel level)) return;
        baby.level().playSound(null, baby.blockPosition(), SoundEvents.TOTEM_USE, baby.getSoundSource(), 1.0F, 1.0F);
        // Start a 3-second client-side emitter. It continuously spits out
        // small red shards instead of launching one large burst at once.
        level.sendParticles(
                ModParticles.TOTEM_OF_BABY_COMBAT_EMITTER.get(),
                baby.getX(), baby.getY() + baby.getBbHeight() * 0.35D, baby.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D
        );
    }

    /** Durability cost derived from the actual defeated mob's combat stats. */
    public static int durabilityCostForKill(LivingEntity dead) {
        double health = Math.max(1.0D, dead.getMaxHealth());
        double attack = 0.0D;
        if (dead instanceof Mob mob && mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null) {
            attack = Math.max(0.0D, mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
        }
        double armor = dead.getArmorValue();
        int cost = 1 + (int)Math.ceil(health / 20.0D) + (int)Math.floor(attack / 4.0D) + (int)Math.floor(armor / 10.0D);
        return Math.max(1, Math.min(20, cost));
    }

    /** Damages the held combat totem and returns true if one was broken. */
    public static boolean damageTotemForKill(BabyNPCPlayerEntity baby, LivingEntity dead) {
        InteractionHand hand = isCombatTotem(baby.getItemBySlot(EquipmentSlot.MAINHAND))
                ? InteractionHand.MAIN_HAND
                : isCombatTotem(baby.getItemBySlot(EquipmentSlot.OFFHAND)) ? InteractionHand.OFF_HAND : null;
        if (hand == null) return false;
        ItemStack stack = baby.getItemInHand(hand);
        int amount = durabilityCostForKill(dead);
        stack.hurtAndBreak(amount, baby, e -> e.broadcastBreakEvent(hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND));
        if (stack.isEmpty()) {
            baby.setItemInHand(hand, ItemStack.EMPTY);
            playTotemBreakEffect(baby);
            ServerPlayer owner = baby.getBehaviorOwner();
            if (owner != null && owner.level() == baby.level()) {
                teleportNearOwner(baby, owner);
            }
            deactivate(baby);
            return true;
        }
        baby.setItemInHand(hand, stack);
        return false;
    }
}
