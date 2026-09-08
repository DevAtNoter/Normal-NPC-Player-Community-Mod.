package net.devatnoter.normalnpcplayer.equipment;

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
    private static final String ATTACK_COOLDOWN_TAG = "BabyCombatAttackCooldown";
    private static final String RANGED_COOLDOWN_TAG = "BabyCombatRangedCooldown";
    private static final String RETREAT_TICKS_TAG = "BabyCombatRetreatTicks";
    private static final String CRIT_PENDING_TAG = "BabyCombatCritPending";

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

        LivingEntity target = findTarget(baby);
        if (target == null) {
            deactivate(baby);
            return;
        }

        activate(baby, target);
        tickOwnerDistance(baby);
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
        baby.getPersistentData().putInt(RETREAT_TICKS_TAG, 0);
        baby.getPersistentData().putBoolean(CRIT_PENDING_TAG, false);
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

        if (distance <= 3.2D * 3.2D) {
            if (attackCooldown == 0 && equipBestMelee(baby)) {
                boolean pendingCrit = baby.getPersistentData().getBoolean(CRIT_PENDING_TAG);
                if (!pendingCrit && baby.onGround()) {
                    baby.getPersistentData().putBoolean(CRIT_PENDING_TAG, true);
                    baby.getJumpControl().jump();
                    return;
                }
                if (pendingCrit && !baby.onGround() && baby.getDeltaMovement().y < 0.05D) {
                    performCriticalAttack(baby, target);
                    baby.getPersistentData().putBoolean(CRIT_PENDING_TAG, false);
                    baby.getPersistentData().putInt(ATTACK_COOLDOWN_TAG, 10);
                }
            }
        } else {
            baby.getNavigation().moveTo(target, 1.35D);
            baby.setSprinting(true);
            if (baby.onGround() && distance < 9.0D * 9.0D && baby.getRandom().nextFloat() < 0.16F) {
                baby.getJumpControl().jump();
            }
        }
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
        return hasArrow(baby) || EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.INFINITY_ARROWS, stack) > 0;
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

    private static void rangedBow(BabyNPCPlayerEntity baby, LivingEntity target) {
        if (baby.getPersistentData().getInt(RANGED_COOLDOWN_TAG) > 0) return;
        ItemStack bow = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!isUsableBow(baby, bow)) return;
        baby.startUsingItem(InteractionHand.MAIN_HAND);
        fireArrow(baby, target, bow);
        baby.stopUsingItem();
        baby.getPersistentData().putInt(RANGED_COOLDOWN_TAG, 20);
        baby.swingMainHand();
        baby.triggerAnim("combat", "bow_shot");
    }

    private static void rangedCrossbow(BabyNPCPlayerEntity baby, LivingEntity target) {
        if (baby.getPersistentData().getInt(RANGED_COOLDOWN_TAG) > 0) return;
        ItemStack crossbow = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!isUsableCrossbow(baby, crossbow)) return;
        if (!CrossbowItem.isCharged(crossbow)) {
            baby.startUsingItem(InteractionHand.MAIN_HAND);
            baby.triggerAnim("combat", "crossbow_load");
            baby.stopUsingItem();
            CrossbowItem.setCharged(crossbow, true);
        }
        fireArrow(baby, target, crossbow);
        CrossbowItem.setCharged(crossbow, false);
        baby.getPersistentData().putInt(RANGED_COOLDOWN_TAG, 25);
        baby.swingMainHand();
        baby.triggerAnim("combat", "crossbow_shot");
    }

    private static void fireArrow(BabyNPCPlayerEntity baby, LivingEntity target, ItemStack weapon) {
        ItemStack ammo = findArrowStack(baby);
        boolean infinity = weapon.getItem() instanceof BowItem
                && EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.INFINITY_ARROWS, weapon) > 0;
        if (ammo.isEmpty() && !infinity) return;

        net.minecraft.world.entity.projectile.AbstractArrow arrow = net.minecraft.world.entity.projectile.ProjectileUtil.getMobArrow(
                baby, new ItemStack(Items.ARROW), 1.0F);
        double dx = target.getX() - baby.getX();
        double dy = target.getY(0.35D) - arrow.getY();
        double dz = target.getZ() - baby.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.2D, dz, weapon.getItem() instanceof CrossbowItem ? 1.6F : 2.4F, 1.0F);
        ((ServerLevel) baby.level()).addFreshEntity(arrow);
        if (!ammo.isEmpty() && !infinity) ammo.shrink(1);
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
        ItemStack trident = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!(trident.getItem() instanceof TridentItem)) return;
        ThrownTrident projectile = new ThrownTrident(baby.level(), baby, trident.copy());
        projectile.setPos(baby.getX(), baby.getEyeY() - 0.1D, baby.getZ());
        Vec3 delta = target.getEyePosition().subtract(projectile.position());
        projectile.shoot(delta.x, delta.y, delta.z, 1.65F, 1.0F);
        ((ServerLevel) baby.level()).addFreshEntity(projectile);
        trident.hurtAndBreak(1, baby, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        baby.getPersistentData().putInt(RANGED_COOLDOWN_TAG, 30);
        baby.triggerAnim("combat", "trident_throw");
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
        if (main.getItem() instanceof SwordItem || main.getItem() instanceof AxeItem) return true;
        int best = -1;
        int score = -1;
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            ItemStack s = baby.getBabyInventory().getItem(i);
            int v = s.getItem() instanceof SwordItem ? 20 : s.getItem() instanceof AxeItem ? 15 : -1;
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
        level.sendParticles(
                ModParticles.TOTEM_OF_BABY_COMBAT.get(),
                baby.getX(), baby.getY() + baby.getBbHeight() * 0.5D, baby.getZ(),
                30, 0.35D, 0.45D, 0.35D, 0.25D
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
