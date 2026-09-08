package net.devatnoter.normalnpcplayer.equipment;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.item.BabyFoodItem;
import net.devatnoter.normalnpcplayer.item.TotemOfBabyCombatItem;
import net.devatnoter.normalnpcplayer.registry.ModItems;
import net.devatnoter.normalnpcplayer.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidUtil;
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
import net.minecraft.tags.FluidTags;
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
    private static final String TRIDENT_CHARGE_TICKS_TAG = "BabyCombatTridentChargeTicks";
    private static final String TRIDENT_COOLDOWN_TAG = "BabyCombatTridentCooldown";
    private static final String TRIDENT_ACTIVE_TAG = "BabyCombatTridentActive";
    private static final String TRIDENT_ACTIVE_ITEM_TAG = "BabyCombatActiveTridentItem";
    private static final int TRIDENT_MIN_CHARGE_TICKS = 10;
    private static final int TRIDENT_THROW_COOLDOWN = 18;
    private static final double TRIDENT_RANGED_MIN_DISTANCE = 6.0D;
    private static final double TRIDENT_RANGED_MAX_DISTANCE = 24.0D;
    private static final String BOW_STRAFE_DIR_TAG = "BabyCombatBowStrafeDir";
    private static final String CROSSBOW_CHARGE_TICKS_TAG = "BabyCombatCrossbowChargeTicks";
    private static final String THROWABLE_COOLDOWN_TAG = "BabyCombatThrowableCooldown";
    private static final int THROWABLE_COOLDOWN_TICKS = 12;
    /**
     * Ranged accuracy is applied only AFTER an exact ballistic solution has
     * been found. 0.0 means perfect aim; larger values are angular spread in
     * degrees. The projectile is never given vanilla inaccuracy in addition
     * to this value, so this is the single accuracy control for Baby Combat.
     */
    private static final double RANGED_ACCURACY_DEGREES = 2.0D;
    private static final int BALLISTIC_PREDICTION_ITERATIONS = 6;
    private static final int BALLISTIC_MAX_TICKS = 200;
    private static final int BALLISTIC_ANGLE_SAMPLES = 96;
    private static final double BALLISTIC_AIR_DRAG = 0.99D;
    private static final double BALLISTIC_MIN_HORIZONTAL_SPEED = 1.0E-4D;
    private static final String RETREAT_TICKS_TAG = "BabyCombatRetreatTicks";
    private static final String CRIT_PENDING_TAG = "BabyCombatCritPending";
    private static final String MELEE_COMBO_TAG = "BabyCombatMeleeCombo";
    private static final String MELEE_RETREAT_TAG = "BabyCombatMeleeRetreatTicks";
    private static final String MELEE_APPROACH_TAG = "BabyCombatMeleeApproachTicks";
    private static final String LAVA_TICKS_TAG = "BabyCombatLavaTicks";
    private static final String LAVA_POS_TAG = "BabyCombatLavaPos";
    private static final String LAVA_TARGET_TAG = "BabyCombatLavaTarget";
    private static final String LAVA_RETREAT_TICKS_TAG = "BabyCombatLavaRetreatTicks";
    private static final int LAVA_WAIT_TICKS = 40; // 2 seconds fallback if the target is not actually ignited
    private static final int LAVA_RETREAT_TICKS = 12;
    private static final int LAVA_SCOOP_RECHECK_TICKS = 2;
    private static final int LAVA_FLOW_DECISION_DISTANCE = 2;
    private static final double LAVA_DANGER_PER_FLOW_BLOCK = 1.35D;
    private static final double LAVA_RESUPPLY_RADIUS = 8.0D;
    private static final double FLUID_PLACEMENT_MAX_RANGE = 4.0D;
    private static final double FLUID_TARGET_MIN_DISTANCE = 0.75D;
    private static final double FLUID_TARGET_MAX_DISTANCE = 3.25D;
    private static final double LAVA_RESUPPLY_PICKUP_DISTANCE = 2.75D;
    private static final int LAVA_RESUPPLY_RECHECK_TICKS = 10;
    private static final String LAVA_RESUPPLY_POS_TAG = "BabyCombatLavaResupplyPos";
    private static final String LAVA_RESUPPLY_COOLDOWN_TAG = "BabyCombatLavaResupplyCooldown";
    private static final double MOB_DANGER_RADIUS = 10.0D;
    private static final String WATER_TICKS_TAG = "BabyCombatWaterTicks";
    private static final String WATER_POS_TAG = "BabyCombatWaterPos";
    private static final int WATER_RETURN_TICKS = 2; // let the placed source exist for a server tick or two
    private static final String EMERGENCY_COOLDOWN_TAG = "BabyCombatEmergencyCooldown";
    private static final String VANILLA_TOTEM_EMERGENCY_TAG = "BabyCombatVanillaTotemEmergency";
    private static final String VANILLA_TOTEM_RETURN_TAG = "BabyCombatVanillaTotemReturn";
    private static final String ELYTRA_FLIGHT_TAG = "BabyCombatElytraFlight";
    private static final String ELYTRA_ROCKET_COOLDOWN_TAG = "BabyCombatElytraRocketCooldown";
    private static final String ELYTRA_LAUNCH_TICKS_TAG = "BabyCombatElytraLaunchTicks";
    private static final String ELYTRA_ATTACK_COOLDOWN_TAG = "BabyCombatElytraAttackCooldown";
    private static final int ELYTRA_ROCKET_INTERVAL = 18;
    private static final int ELYTRA_ATTACK_INTERVAL = 8;
    private static final int ELYTRA_SWAP_NONE = 0;
    private static final int ELYTRA_SWAP_START_ARMOR_TO_MAIN = 1;
    private static final int ELYTRA_SWAP_START_ELYTRA_TO_MAIN = 2;
    private static final int ELYTRA_SWAP_START_SWAP_WITH_ARMOR = 3;
    private static final int ELYTRA_SWAP_START_RESTORE_WEAPON = 4;
    private static final int ELYTRA_SWAP_LAND_ELYTRA_TO_MAIN = 10;
    private static final int ELYTRA_SWAP_LAND_ARMOR_TO_CHEST = 11;
    private static final int ELYTRA_SWAP_LAND_RESTORE_WEAPON = 12;
    private static final String ELYTRA_SWAP_STAGE_TAG = "BabyCombatElytraSwapStage";
    private static final String ELYTRA_OWNER_RANGE_GRACE_TAG = "BabyCombatElytraOwnerRangeGrace";
    private static final double ELYTRA_OWNER_RANGE = 64.0D;
    private static final int ELYTRA_OWNER_RANGE_GRACE_TICKS = 20 * 5;

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

        // If a vanilla Totem of Undying was used as an emergency life-save,
        // immediately give control back to Combat Mode by restoring the Baby
        // Combat Totem from the Baby's own inventory.
        tickVanillaTotemReturn(baby);

        if (!isHoldingCombatTotem(baby)) {
            equipTotemFromInventory(baby);
        }

        if (!isHoldingCombatTotem(baby)) {
            deactivate(baby);
            return;
        }

        tickTemporaryFireAndLava(baby);

        // Emergency fire/lava survival is resolved before normal combat food.
        // This is still strictly inventory-local to the Baby.
        if (tickEmergencyFireAndLava(baby)) {
            return;
        }

        // A real empty bucket can be replenished from nearby natural lava while
        // Combat Mode is active. This is separate from the tactical lava loop:
        // it only searches for an existing lava SOURCE, never flowing lava, and
        // uses Forge's real bucket pickup path. If the source is not immediately
        // reachable, the Baby walks toward it instead of manufacturing lava.
        if (tickLavaBucketResupply(baby)) {
            return;
        }

        LivingEntity target = findTarget(baby);
        if (target == null) {
            deactivate(baby);
            return;
        }

        activate(baby, target);

        // Trident projectile lifecycle owns the weapon until the thrown trident
        // is gone or has been recovered. This prevents the normal melee/ranged
        // selector from replacing a trident that is still in flight.
        if (tickTridentLifecycle(baby)) {
            tickElytraOwnerRangeGrace(baby);
            tickOwnerDistance(baby);
            return;
        }

        tickElytraOwnerRangeGrace(baby);
        tickOwnerDistance(baby);

        // Elytra is a Combat Mode capability. When the Baby owns an Elytra and
        // rockets, it can leave ground combat, boost toward the target, swap
        // weapons in flight, shoot a bow at range, and dive into melee range.
        if (tickElytraCombat(baby, target)) {
            return;
        }

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
        baby.getPersistentData().putInt(CROSSBOW_CHARGE_TICKS_TAG, 0);
        baby.getPersistentData().putInt(TRIDENT_CHARGE_TICKS_TAG, 0);
        baby.getPersistentData().putInt(TRIDENT_COOLDOWN_TAG, 0);
        baby.getPersistentData().putBoolean(TRIDENT_ACTIVE_TAG, false);
        baby.getPersistentData().putInt(FOOD_COOLDOWN_TAG, 0);
        baby.getPersistentData().putInt(RETREAT_TICKS_TAG, 0);
        baby.getPersistentData().putBoolean(CRIT_PENDING_TAG, false);
        baby.getPersistentData().putInt(MELEE_RETREAT_TAG, 0);
        baby.getPersistentData().putInt(MELEE_APPROACH_TAG, 0);
        baby.getPersistentData().putInt(MELEE_COMBO_TAG, 0);
        baby.getPersistentData().putInt(WATER_TICKS_TAG, 0);
        baby.getPersistentData().remove(LAVA_RESUPPLY_POS_TAG);
        baby.getPersistentData().remove(LAVA_TARGET_TAG);
        baby.getPersistentData().putInt(LAVA_RESUPPLY_COOLDOWN_TAG, 0);
        baby.getPersistentData().putInt(EMERGENCY_COOLDOWN_TAG, 0);
        baby.getPersistentData().putBoolean(ELYTRA_FLIGHT_TAG, false);
        baby.getPersistentData().putInt(ELYTRA_ROCKET_COOLDOWN_TAG, 0);
        baby.getPersistentData().putInt(ELYTRA_LAUNCH_TICKS_TAG, 0);
        baby.getPersistentData().putInt(ELYTRA_ATTACK_COOLDOWN_TAG, 0);
        baby.getPersistentData().putInt(ELYTRA_SWAP_STAGE_TAG, ELYTRA_SWAP_NONE);
        baby.getPersistentData().putInt(ELYTRA_OWNER_RANGE_GRACE_TAG, 0);
        if (baby.isFallFlying()) {
            baby.setCombatFallFlying(false);
        }
        baby.getPersistentData().putBoolean(VANILLA_TOTEM_EMERGENCY_TAG, false);
        baby.getPersistentData().putBoolean(VANILLA_TOTEM_RETURN_TAG, false);
        baby.getPersistentData().remove(WATER_POS_TAG);
        if (baby.isUsingItem()) baby.stopUsingItem();
    }

    public static boolean isCombatActive(BabyNPCPlayerEntity baby) {
        return baby.getPersistentData().getBoolean(ACTIVE_TAG) && isHoldingCombatTotem(baby);
    }

    /**
     * Elytra combat gets a larger owner-distance leash. While the Elytra is
     * actually equipped, the leash stays at 64 blocks. Once it is removed,
     * keep that larger leash for five seconds so the landing/armor swap can
     * finish without an immediate owner teleport. Re-equipping the Elytra
     * refreshes the full grace window.
     */
    private static void tickElytraOwnerRangeGrace(BabyNPCPlayerEntity baby) {
        CompoundTag data = baby.getPersistentData();
        if (!data.getBoolean(ACTIVE_TAG) || !isHoldingCombatTotem(baby)) {
            data.putInt(ELYTRA_OWNER_RANGE_GRACE_TAG, 0);
            return;
        }

        ItemStack chest = baby.getItemBySlot(EquipmentSlot.CHEST);
        boolean equipped = chest.getItem() instanceof net.minecraft.world.item.ElytraItem
                && net.minecraft.world.item.ElytraItem.isFlyEnabled(chest);

        if (equipped) {
            // Refresh every combat tick while the Elytra is actually in the
            // chest slot. This makes the 64-block leash effectively permanent
            // for continued Elytra use.
            data.putInt(ELYTRA_OWNER_RANGE_GRACE_TAG, ELYTRA_OWNER_RANGE_GRACE_TICKS);
            return;
        }

        int grace = Math.max(0, data.getInt(ELYTRA_OWNER_RANGE_GRACE_TAG));
        data.putInt(ELYTRA_OWNER_RANGE_GRACE_TAG, Math.max(0, grace - 1));
    }

    public static boolean hasElytraOwnerRange(BabyNPCPlayerEntity baby) {
        return baby.getPersistentData().getInt(ELYTRA_OWNER_RANGE_GRACE_TAG) > 0
                && baby.getPersistentData().getBoolean(ACTIVE_TAG)
                && isHoldingCombatTotem(baby);
    }

    public static double getOwnerDistanceLimit(BabyNPCPlayerEntity baby) {
        if (hasElytraOwnerRange(baby)) return ELYTRA_OWNER_RANGE;
        if (baby.getPersistentData().getBoolean(ACTIVE_TAG) && isHoldingCombatTotem(baby)) {
            return COMBAT_RANGE;
        }
        return 20.0D;
    }

    private static void tickOwnerDistance(BabyNPCPlayerEntity baby) {
        ServerPlayer owner = baby.getBehaviorOwner();
        if (owner == null || owner.level() != baby.level()) return;

        double ownerRange = getOwnerDistanceLimit(baby);
        if (baby.distanceToSqr(owner) > ownerRange * ownerRange) {
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

    /**
     * Full Elytra combat loop. This is deliberately isolated from the normal
     * ground combat path so existing sword/bow/crossbow/lava logic remains the
     * fallback whenever Elytra combat is unavailable.
     */
    private static boolean tickElytraCombat(BabyNPCPlayerEntity baby, LivingEntity target) {
        if (!isCombatActive(baby) && !isHoldingCombatTotem(baby)) return false;

        CompoundTag data = baby.getPersistentData();
        int stage = data.getInt(ELYTRA_SWAP_STAGE_TAG);

        // Equipment exchange is deliberately ticked as a small state machine.
        // This makes the combat decision observable: weapon -> chest armor in
        // main hand -> Elytra in chest -> weapon, and the reverse on landing.
        if (stage != ELYTRA_SWAP_NONE) {
            if (tickElytraEquipmentSwap(baby, stage)) return true;
        }

        ItemStack chest = baby.getItemBySlot(EquipmentSlot.CHEST);
        boolean hasEquippedElytra = chest.getItem() instanceof net.minecraft.world.item.ElytraItem
                && net.minecraft.world.item.ElytraItem.isFlyEnabled(chest);
        boolean hasInventoryElytra = findFlightElytra(baby) >= 0;
        boolean hasRocket = hasFireworkRocket(baby);

        // If the Baby is already airborne, let vanilla Elytra physics finish the
        // glide even if the last rocket was consumed. Landing is handled below.
        if (baby.isFallFlying()) {
            data.putBoolean(ELYTRA_FLIGHT_TAG, true);
            steerElytraTowardTarget(baby, target);

            int rocketCooldown = Math.max(0, data.getInt(ELYTRA_ROCKET_COOLDOWN_TAG) - 1);
            int attackCooldown = Math.max(0, data.getInt(ELYTRA_ATTACK_COOLDOWN_TAG) - 1);
            data.putInt(ELYTRA_ROCKET_COOLDOWN_TAG, rocketCooldown);
            data.putInt(ELYTRA_ATTACK_COOLDOWN_TAG, attackCooldown);

            if (rocketCooldown == 0 && hasRocket) {
                boostWithFirework(baby);
                data.putInt(ELYTRA_ROCKET_COOLDOWN_TAG, ELYTRA_ROCKET_INTERVAL);
            }

            double distance = baby.distanceToSqr(target);
            if (distance <= 5.0D * 5.0D) {
                if (equipBestMelee(baby) && attackCooldown == 0) {
                    performMeleeAttack(baby, target, false);
                    data.putInt(ELYTRA_ATTACK_COOLDOWN_TAG, ELYTRA_ATTACK_INTERVAL);
                }
            } else if (hasArrow(baby) && equipBestFlightBow(baby)) {
                rangedBowWhileFlying(baby, target);
            } else {
                equipBestMelee(baby);
            }

            // Do not forcibly cancel fall-flying in mid-air. Vanilla landing
            // determines the actual touchdown velocity and fall distance.
            if (baby.onGround()) {
                baby.setCombatFallFlying(false);
                data.putBoolean(ELYTRA_FLIGHT_TAG, false);
                if (hasEquippedElytra) {
                    data.putInt(ELYTRA_SWAP_STAGE_TAG, ELYTRA_SWAP_LAND_ELYTRA_TO_MAIN);
                }
            }
            return true;
        }

        // If the Baby is already holding an Elytra, equip that exact stack
        // immediately, even before checking whether a Firework Rocket exists.
        // Holding the Elytra is enough to justify putting it into the chest slot;
        // rockets are only required for autonomous takeoff afterward.
        if (equipHeldElytra(baby)) {
            return true;
        }

        // No rocket means no autonomous takeoff. This fixes the old behavior
        // where the Baby could start flying without a Firework Rocket.
        if (!hasRocket) {
            data.putBoolean(ELYTRA_FLIGHT_TAG, false);
            if (baby.onGround() && hasEquippedElytra) {
                data.putInt(ELYTRA_SWAP_STAGE_TAG, ELYTRA_SWAP_LAND_ELYTRA_TO_MAIN);
                return true;
            }
            return false;
        }

        // Elytra already equipped: launch only when the target is far enough
        // away to justify aerial combat.
        if (hasEquippedElytra) {
            if (baby.onGround() && baby.distanceToSqr(target) > 10.0D * 10.0D) {
                launchElytra(baby, target);
                return true;
            }
            return false;
        }

        // Elytra is still in the Baby inventory. Begin the explicit armor/Elytra
        // swap instead of silently deleting or replacing chest armor.
        if (hasInventoryElytra && baby.distanceToSqr(target) > 10.0D * 10.0D) {
            if (beginElytraEquipmentSwap(baby)) return true;
        }
        return false;
    }

    private static void launchElytra(BabyNPCPlayerEntity baby, LivingEntity target) {
        CompoundTag data = baby.getPersistentData();
        Vec3 launch = target.position().subtract(baby.position());
        Vec3 horizontal = new Vec3(launch.x, 0.0D, launch.z);
        if (horizontal.lengthSqr() < 0.0001D) horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        horizontal = horizontal.normalize();

        // A normal jump, not a teleport/velocity override to Elytra speed.
        // Vanilla fall-flying physics takes over once the Baby is airborne.
        baby.getJumpControl().jump();
        baby.setDeltaMovement(horizontal.scale(0.28D).add(0.0D, 0.42D, 0.0D));
        baby.setOnGround(false);
        data.putInt(ELYTRA_LAUNCH_TICKS_TAG, 3);
        data.putBoolean(ELYTRA_FLIGHT_TAG, true);

        // The first rocket is what actually supplies the takeoff boost.
        baby.setCombatFallFlying(true);
        if (hasFireworkRocket(baby)) {
            boostWithFirework(baby);
            data.putInt(ELYTRA_ROCKET_COOLDOWN_TAG, ELYTRA_ROCKET_INTERVAL);
        }
    }

    private static boolean equipHeldElytra(BabyNPCPlayerEntity baby) {
        ItemStack chest = baby.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof net.minecraft.world.item.ElytraItem) return false;

        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);
        ItemStack held = ItemStack.EMPTY;
        EquipmentSlot handSlot = null;

        if (main.getItem() instanceof net.minecraft.world.item.ElytraItem) {
            held = main.copy();
            handSlot = EquipmentSlot.MAINHAND;
        } else if (off.getItem() instanceof net.minecraft.world.item.ElytraItem) {
            held = off.copy();
            handSlot = EquipmentSlot.OFFHAND;
        }

        if (held.isEmpty()) return false;

        // Preserve the existing chest item. The same real stack is moved to
        // Baby inventory; nothing is created or deleted.
        if (!chest.isEmpty() && !storeForElytraSwap(baby, chest.copy())) {
            return false;
        }

        baby.setItemSlot(EquipmentSlot.CHEST, held);
        baby.setItemSlot(handSlot, ItemStack.EMPTY);
        return true;
    }

    private static boolean beginElytraEquipmentSwap(BabyNPCPlayerEntity baby) {
        ItemStack chest = baby.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof net.minecraft.world.item.ElytraItem) return true;

        // The combat totem remains protected in whichever hand currently holds
        // it. Only a real weapon/non-totem item is moved out of main hand.
        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!main.isEmpty() && isCombatTotem(main)) {
            ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);
            if (!off.isEmpty() && !isCombatTotem(off) && !storeForElytraSwap(baby, off.copy())) return false;
            baby.setItemSlot(EquipmentSlot.OFFHAND, main.copy());
            baby.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            main = ItemStack.EMPTY;
        }

        if (!chest.isEmpty()) {
            if (!storeForElytraSwap(baby, main.copy())) return false;
            baby.setItemSlot(EquipmentSlot.MAINHAND, chest.copy());
            baby.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        } else if (!main.isEmpty()) {
            // Keep the current weapon in storage while the Elytra enters chest.
            if (!storeForElytraSwap(baby, main.copy())) return false;
            baby.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        baby.getPersistentData().putInt(ELYTRA_SWAP_STAGE_TAG, ELYTRA_SWAP_START_ELYTRA_TO_MAIN);
        return true;
    }

    private static boolean tickElytraEquipmentSwap(BabyNPCPlayerEntity baby, int stage) {
        CompoundTag data = baby.getPersistentData();
        Container inv = baby.getBabyInventory();

        if (stage == ELYTRA_SWAP_START_ELYTRA_TO_MAIN) {
            int slot = findFlightElytra(baby);
            if (slot < 0) {
                data.putInt(ELYTRA_SWAP_STAGE_TAG, ELYTRA_SWAP_NONE);
                return false;
            }
            // Deliberately hold the Elytra in the main hand for one combat tick.
            // The next state performs the actual chest-slot exchange.
            ItemStack elytra = inv.getItem(slot).copyWithCount(1);
            inv.getItem(slot).shrink(1);
            baby.setItemSlot(EquipmentSlot.MAINHAND, elytra);
            data.putInt(ELYTRA_SWAP_STAGE_TAG, ELYTRA_SWAP_START_SWAP_WITH_ARMOR);
            return true;
        }

        if (stage == ELYTRA_SWAP_START_SWAP_WITH_ARMOR) {
            ItemStack elytra = baby.getItemBySlot(EquipmentSlot.MAINHAND);
            ItemStack armor = baby.getItemBySlot(EquipmentSlot.CHEST);
            if (!(elytra.getItem() instanceof net.minecraft.world.item.ElytraItem)) {
                data.putInt(ELYTRA_SWAP_STAGE_TAG, ELYTRA_SWAP_NONE);
                return false;
            }
            baby.setItemSlot(EquipmentSlot.MAINHAND, armor.copy());
            baby.setItemSlot(EquipmentSlot.CHEST, elytra.copy());
            data.putInt(ELYTRA_SWAP_STAGE_TAG, ELYTRA_SWAP_START_RESTORE_WEAPON);
            return true;
        }

        if (stage == ELYTRA_SWAP_START_RESTORE_WEAPON) {
            ItemStack held = baby.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!held.isEmpty() && held.getItem() instanceof ArmorItem) {
                if (!storeForElytraSwap(baby, held.copy())) return true;
                baby.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
            equipBestMelee(baby);
            data.putInt(ELYTRA_SWAP_STAGE_TAG, ELYTRA_SWAP_NONE);
            return true;
        }

        if (stage == ELYTRA_SWAP_LAND_ELYTRA_TO_MAIN) {
            ItemStack chest = baby.getItemBySlot(EquipmentSlot.CHEST);
            if (!(chest.getItem() instanceof net.minecraft.world.item.ElytraItem)) {
                data.putInt(ELYTRA_SWAP_STAGE_TAG, ELYTRA_SWAP_NONE);
                return false;
            }
            ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!main.isEmpty() && !isCombatTotem(main) && !storeForElytraSwap(baby, main.copy())) return true;
            baby.setItemSlot(EquipmentSlot.MAINHAND, chest.copy());
            baby.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            data.putInt(ELYTRA_SWAP_STAGE_TAG, ELYTRA_SWAP_LAND_ARMOR_TO_CHEST);
            return true;
        }

        if (stage == ELYTRA_SWAP_LAND_ARMOR_TO_CHEST) {
            int armorSlot = findChestArmor(baby);
            if (armorSlot >= 0) {
                ItemStack armor = inv.getItem(armorSlot).copyWithCount(1);
                inv.getItem(armorSlot).shrink(1);
                baby.setItemSlot(EquipmentSlot.CHEST, armor);
            }
            data.putInt(ELYTRA_SWAP_STAGE_TAG, ELYTRA_SWAP_LAND_RESTORE_WEAPON);
            return true;
        }

        if (stage == ELYTRA_SWAP_LAND_RESTORE_WEAPON) {
            ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
            if (main.getItem() instanceof net.minecraft.world.item.ElytraItem) {
                if (!storeForElytraSwap(baby, main.copy())) return true;
                baby.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
            equipBestMelee(baby);
            data.putInt(ELYTRA_SWAP_STAGE_TAG, ELYTRA_SWAP_NONE);
            return true;
        }

        data.putInt(ELYTRA_SWAP_STAGE_TAG, ELYTRA_SWAP_NONE);
        return false;
    }

    private static boolean storeForElytraSwap(BabyNPCPlayerEntity baby, ItemStack stack) {
        if (stack.isEmpty()) return true;
        Container inv = baby.getBabyInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack existing = inv.getItem(i);
            if (existing.isEmpty()) {
                inv.setItem(i, stack.copy());
                return true;
            }
            if (ItemStack.isSameItemSameTags(existing, stack)
                    && existing.getCount() < Math.min(existing.getMaxStackSize(), inv.getMaxStackSize())) {
                int space = Math.min(existing.getMaxStackSize(), inv.getMaxStackSize()) - existing.getCount();
                int moved = Math.min(space, stack.getCount());
                existing.grow(moved);
                inv.setItem(i, existing);
                return moved == stack.getCount();
            }
        }
        return false;
    }

    private static int findFlightElytra(BabyNPCPlayerEntity baby) {
        Container inv = baby.getBabyInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof net.minecraft.world.item.ElytraItem
                    && net.minecraft.world.item.ElytraItem.isFlyEnabled(stack)) return i;
        }
        return -1;
    }

    private static int findChestArmor(BabyNPCPlayerEntity baby) {
        Container inv = baby.getBabyInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof ArmorItem armor
                    && armor.getEquipmentSlot() == EquipmentSlot.CHEST
                    && !(stack.getItem() instanceof net.minecraft.world.item.ElytraItem)) return i;
        }
        return -1;
    }

    private static boolean ensureElytraEquipped(BabyNPCPlayerEntity baby) {
        // Kept as a narrow compatibility helper for callers outside the Elytra
        // state machine. Combat itself uses beginElytraEquipmentSwap().
        ItemStack chest = baby.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof net.minecraft.world.item.ElytraItem
                && net.minecraft.world.item.ElytraItem.isFlyEnabled(chest)) return true;
        return findFlightElytra(baby) >= 0 && beginElytraEquipmentSwap(baby);
    }

    private static void steerElytraTowardTarget(BabyNPCPlayerEntity baby, LivingEntity target) {
        Vec3 aim = target.getEyePosition().subtract(baby.getEyePosition());
        if (aim.lengthSqr() < 0.0001D) return;
        Vec3 direction = aim.normalize();

        // Do not overwrite Elytra velocity. The vanilla fall-flying travel path
        // uses look direction, gravity and drag to determine the actual speed.
        float yaw = (float)(Mth.atan2(-direction.x, direction.z) * (180.0D / Math.PI));
        float pitch = (float)(-(Mth.atan2(direction.y,
                Math.sqrt(direction.x * direction.x + direction.z * direction.z)) * (180.0D / Math.PI)));
        baby.setYRot(yaw);
        baby.setYHeadRot(yaw);
        baby.setXRot(Mth.clamp(pitch, -75.0F, 75.0F));
        baby.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private static boolean hasFireworkRocket(BabyNPCPlayerEntity baby) {
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            if (baby.getBabyInventory().getItem(i).is(Items.FIREWORK_ROCKET)) return true;
        }
        return false;
    }

    private static void boostWithFirework(BabyNPCPlayerEntity baby) {
        if (!(baby.level() instanceof ServerLevel level)) return;
        Container inv = baby.getBabyInventory();
        int slot = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.FIREWORK_ROCKET)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return;

        ItemStack rocket = inv.getItem(slot).copyWithCount(1);
        inv.getItem(slot).shrink(1);
        FireworkRocketEntity firework = new FireworkRocketEntity(level, rocket, baby);
        level.addFreshEntity(firework);
        baby.level().playSound(null, baby.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH,
                baby.getSoundSource(), 0.65F, 1.05F);
    }

    private static boolean equipBestFlightBow(BabyNPCPlayerEntity baby) {
        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (main.getItem() instanceof BowItem && hasArrow(baby)) return true;

        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            ItemStack stack = baby.getBabyInventory().getItem(i);
            if (stack.getItem() instanceof BowItem && hasArrow(baby)) {
                return equipFromInventoryAsMain(baby, i);
            }
        }
        return false;
    }

    private static void rangedBowWhileFlying(BabyNPCPlayerEntity baby, LivingEntity target) {
        ItemStack bow = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!(bow.getItem() instanceof BowItem) || !hasArrow(baby)) return;
        baby.getLookControl().setLookAt(target, 30.0F, baby.getMaxHeadXRot());

        if (baby.getPersistentData().getInt(RANGED_COOLDOWN_TAG) > 0) return;
        if (!baby.isUsingItem()) {
            baby.startUsingItem(InteractionHand.MAIN_HAND);
            baby.getPersistentData().putInt(BOW_DRAW_TICKS_TAG, 0);
            return;
        }

        int draw = baby.getPersistentData().getInt(BOW_DRAW_TICKS_TAG) + 1;
        baby.getPersistentData().putInt(BOW_DRAW_TICKS_TAG, draw);
        if (draw >= BowItem.MAX_DRAW_DURATION) {
            baby.stopUsingItem();
            if (fireArrow(baby, target, bow, draw)) {
                damageHeldCombatItem(baby, InteractionHand.MAIN_HAND, 1);
                baby.getPersistentData().putInt(RANGED_COOLDOWN_TAG, 10);
                baby.swingMainHand();
                baby.triggerAnim("combat", "bow_shot");
            }
            baby.getPersistentData().putInt(BOW_DRAW_TICKS_TAG, 0);
        }
    }

    private static void tickCombat(BabyNPCPlayerEntity baby, LivingEntity target) {
        int attackCooldown = Math.max(0, baby.getPersistentData().getInt(ATTACK_COOLDOWN_TAG) - 1);
        int rangedCooldown = Math.max(0, baby.getPersistentData().getInt(RANGED_COOLDOWN_TAG) - 1);
        baby.getPersistentData().putInt(ATTACK_COOLDOWN_TAG, attackCooldown);
        baby.getPersistentData().putInt(RANGED_COOLDOWN_TAG, rangedCooldown);
        int throwableCooldown = Math.max(0, baby.getPersistentData().getInt(THROWABLE_COOLDOWN_TAG) - 1);
        baby.getPersistentData().putInt(THROWABLE_COOLDOWN_TAG, throwableCooldown);

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

        // Lava bucket combat is a dedicated loop: place -> leave the lava
        // for 1.5 seconds -> scoop the source back up -> sprint backwards ->
        // repeat. While the placed lava is active, do not switch weapons or
        // perform another combat action.
        int lavaWaitTicks = baby.getPersistentData().getInt(LAVA_TICKS_TAG);
        if (lavaWaitTicks > 0) {
            baby.getNavigation().stop();
            baby.setSprinting(false);
            return;
        }

        int lavaRetreatTicks = baby.getPersistentData().getInt(LAVA_RETREAT_TICKS_TAG);
        if (lavaRetreatTicks > 0) {
            moveAwayFromTarget(baby, target, 1.6D);
            baby.setSprinting(true);
            baby.getPersistentData().putInt(LAVA_RETREAT_TICKS_TAG, lavaRetreatTicks - 1);
            return;
        }

        if (distance <= 8.0D * 8.0D
                && !(main.getItem() instanceof FlintAndSteelItem)
                && !(main.is(Items.FIRE_CHARGE))
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
        if (main.is(Items.FIRE_CHARGE) && distance <= 6.0D * 6.0D) {
            if (tacticalFireCharge(baby, target)) return;
        }
        if (main.is(Items.LAVA_BUCKET) && distance <= 8.0D * 8.0D) {
            if (tacticalLava(baby, target)) return;
        }

        // Throwable items use the same ballistic trajectory system as bows,
        // crossbows and tridents. They are deliberately not fired as a flat
        // straight line: near/mid/far targets select progressively higher arcs.
        if (distance > 3.0D * 3.0D && hasThrowable(baby)) {
            if (rangedThrowable(baby, target)) return;
        }

        // Ranged weapons are preferred when their ammunition rule is satisfied.
        if (distance > TRIDENT_RANGED_MIN_DISTANCE * TRIDENT_RANGED_MIN_DISTANCE) {
            if (isUsableTrident(baby, main)) {
                rangedTrident(baby, target);
                return;
            }
            if (isUsableTrident(baby, off)) {
                swapHandsPreservingTotem(baby);
                return;
            }
            if (equipBestTrident(baby)) {
                return;
            }
            if (isUsableBow(baby, main)) {
                rangedBow(baby, target);
                return;
            }
            if (isUsableCrossbow(baby, main)) {
                rangedCrossbow(baby, target);
                return;
            }
            if (isUsableBow(baby, off) || isUsableCrossbow(baby, off)) {
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

    /**
     * Emergency survival while Combat Mode is active.
     *
     * Priority:
     *  - Burning / shallow lava (2 blocks or less): use a Baby-owned Water Bucket
     *    if one exists, place water at the Baby's feet, then scoop that same source
     *    back up.
     *  - Lava deeper than 2 blocks: never waste the Water Bucket; use only an
     *    Enchanted Golden Milk Bottle if available.
     *  - If no valid emergency item exists, leave the normal combat state alone.
     */
    private static boolean tickEmergencyFireAndLava(BabyNPCPlayerEntity baby) {
        CompoundTag data = baby.getPersistentData();

        int emergencyCooldown = Math.max(0, data.getInt(EMERGENCY_COOLDOWN_TAG) - 1);
        data.putInt(EMERGENCY_COOLDOWN_TAG, emergencyCooldown);

        // Finish the temporary water-bucket cycle first. The Baby must recover
        // the exact source it placed with its real empty bucket. Never conjure a
        // new Water Bucket and never delete flowing water as if it were a source.
        int waterTicks = data.getInt(WATER_TICKS_TAG);
        if (waterTicks > 0) {
            waterTicks--;
            data.putInt(WATER_TICKS_TAG, waterTicks);
            return true;
        }
        if (data.contains(WATER_POS_TAG)) {
            BlockPos waterPos = BlockPos.of(data.getLong(WATER_POS_TAG));

            // Do not scoop while the Baby is still burning or touching lava.
            // The placed source stays in the world until it is actually safe.
            if (baby.isOnFire()
                    || baby.isInLava()
                    || baby.getFluidHeight(FluidTags.LAVA) > 0.0D) {
                data.putInt(WATER_TICKS_TAG, 5);
                return true;
            }

            ItemStack bucket = baby.getItemBySlot(EquipmentSlot.MAINHAND);
            net.minecraft.world.level.material.FluidState fluid = baby.level().getFluidState(waterPos);

            // Use Forge's real fluid pickup path. This performs the same source
            // validation as a real bucket and returns the filled container only
            // when the world actually accepted the pickup. We never delete the
            // block ourselves and never manufacture a Water Bucket.
            if (bucket.is(Items.BUCKET) && fluid.is(FluidTags.WATER) && fluid.isSource()) {
                FluidActionResult result = FluidUtil.tryPickUpFluid(
                        bucket, null, baby.level(), waterPos, Direction.UP);
                if (result.isSuccess()) {
                    baby.setItemSlot(EquipmentSlot.MAINHAND, result.getResult());
                    baby.level().playSound(null, waterPos, SoundEvents.BUCKET_FILL,
                            baby.getSoundSource(), 0.8F, 1.0F);
                    data.remove(WATER_POS_TAG);
                    return true;
                }
                data.putInt(WATER_TICKS_TAG, LAVA_SCOOP_RECHECK_TICKS);
                return true;
            }

            // The source no longer exists, so there is nothing for the real
            // bucket to scoop. Keep the empty bucket; never create another one.
            if (!fluid.is(FluidTags.WATER)) {
                data.remove(WATER_POS_TAG);
            }
            return true;
        }

        boolean inLava = baby.isInLava() || baby.getFluidHeight(FluidTags.LAVA) > 0.0D;
        boolean burning = baby.isOnFire();
        if (!inLava && !burning) return false;
        if (emergencyCooldown > 0) return true;

        // Once enchanted golden milk has granted Fire Resistance, do not keep
        // drinking another bottle merely because the old fire animation has
        // not finished yet.
        if (baby.hasEffect(MobEffects.FIRE_RESISTANCE) && burning && !inLava) return true;

        int lavaDepth = inLava ? getLavaDepthBelow(baby) : 0;

        // A 3+ block lava column is explicitly a milk-only emergency. Water
        // placement would be unsafe/useless here and must not be attempted.
        if (inLava && lavaDepth > 2) {
            boolean consumed = consumeEnchantedGoldenMilk(baby);
            if (consumed) data.putInt(EMERGENCY_COOLDOWN_TAG, 40);
            return consumed;
        }

        int waterSlot = findWaterBucket(baby);
        if (waterSlot >= 0 || waterSlot == -2 || waterSlot == -3) {
            return placeAndRecoverWater(baby, waterSlot);
        }

        // No water available: enchanted golden milk is the only fallback.
        boolean consumed = consumeEnchantedGoldenMilk(baby);
        if (consumed) data.putInt(EMERGENCY_COOLDOWN_TAG, 40);
        return consumed;
    }

    private static int findWaterBucket(BabyNPCPlayerEntity baby) {
        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (main.is(Items.WATER_BUCKET)) return -2; // hand-held; no inventory slot needed
        ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);
        if (off.is(Items.WATER_BUCKET) && !isCombatTotem(off)) return -3; // offhand
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            if (baby.getBabyInventory().getItem(i).is(Items.WATER_BUCKET)) return i;
        }
        return -1;
    }

    private static boolean placeAndRecoverWater(BabyNPCPlayerEntity baby, int waterSlot) {
        BlockPos pos = BlockPos.containing(baby.getX(), baby.getY(), baby.getZ());
        BlockState state = baby.level().getBlockState(pos);

        // Keep every real fluid placement within the Baby's 4-block reach.
        // Water emergency placement is normally at the Baby's feet, but keep
        // the explicit range guard here so this method can never place farther.
        if (!isFluidPlacementWithinRange(baby, pos)) return false;

        // Water is placed in the air/fire/lava block immediately under the
        // Baby's feet. Never overwrite a normal solid floor block.
        if (!(state.isAir() || state.is(Blocks.FIRE) || state.is(Blocks.LAVA))) {
            return consumeEnchantedGoldenMilk(baby);
        }

        // If the bucket lives in inventory, move exactly one real bucket to the
        // main hand while preserving the combat totem. No bucket is created.
        if (waterSlot >= 0) {
            if (!equipFromInventoryAsMain(baby, waterSlot)) return consumeEnchantedGoldenMilk(baby);
        } else if (waterSlot == -3) {
            swapHands(baby);
        }

        ItemStack held = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!held.is(Items.WATER_BUCKET)) return consumeEnchantedGoldenMilk(baby);

        var water = FluidUtil.getFluidContained(held);
        if (water.isEmpty() || water.get().getFluid() != net.minecraft.world.level.material.Fluids.WATER) {
            return consumeEnchantedGoldenMilk(baby);
        }

        FluidActionResult result = FluidUtil.tryPlaceFluid(
                null, baby.level(), InteractionHand.MAIN_HAND, pos, held, water.get());
        if (!result.isSuccess()) {
            return consumeEnchantedGoldenMilk(baby);
        }

        baby.setItemSlot(EquipmentSlot.MAINHAND, result.getResult());
        baby.level().playSound(null, pos, SoundEvents.BUCKET_EMPTY,
                baby.getSoundSource(), 0.8F, 1.0F);
        baby.clearFire();

        // Track the exact block that the real bucket placed. Recovery must use
        // that same empty bucket and the real Forge pickup path.
        baby.getPersistentData().putLong(WATER_POS_TAG, pos.asLong());
        baby.getPersistentData().putInt(WATER_TICKS_TAG, WATER_RETURN_TICKS);
        return true;
    }

    private static int getLavaDepthBelow(BabyNPCPlayerEntity baby) {
        BlockPos pos = BlockPos.containing(baby.getX(), baby.getY(), baby.getZ());
        int depth = 0;
        // Count contiguous lava blocks from the Baby's current feet block
        // downward. Three or more contiguous lava blocks = milk only.
        for (int i = 0; i < 8; i++) {
            BlockPos check = pos.below(i);
            if (!baby.level().getFluidState(check).is(FluidTags.LAVA)) break;
            depth++;
        }
        return depth;
    }

    private static boolean consumeEnchantedGoldenMilk(BabyNPCPlayerEntity baby) {
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            ItemStack stack = baby.getBabyInventory().getItem(i);
            if (!stack.is(ModItems.ENCHANTED_GOLDEN_MILK_BOTTLE.get())) continue;
            if (!(stack.getItem() instanceof BabyFoodItem)) continue;

            BabyFoodItem.FoodData food = BabyFoodItem.getFoodData(stack);
            if (!baby.foodFeed(food)) return false;

            stack.shrink(1);
            baby.getBabyInventory().setItem(i, stack);
            if (!food.emptyReturn().isEmpty()) {
                addToInventoryOrDrop(baby, food.emptyReturn().copy());
            }
            baby.getPersistentData().putInt(FOOD_COOLDOWN_TAG, 20);
            return true;
        }
        return false;
    }

    private static boolean tacticalFireCharge(BabyNPCPlayerEntity baby, LivingEntity target) {
        BlockPos base = target.blockPosition();
        Vec3 away = target.position().subtract(baby.position());
        if (away.lengthSqr() < 0.01D) return false;
        away = away.normalize();
        BlockPos firePos = new BlockPos(
                base.getX() + (int)Math.round(away.x),
                base.getY(),
                base.getZ() + (int)Math.round(away.z));
        if (!baby.level().getBlockState(firePos).isAir()) return false;
        if (baby.distanceToSqr(firePos.getX() + 0.5D, firePos.getY(), firePos.getZ() + 0.5D) > 6.0D * 6.0D) return false;
        if (!Blocks.FIRE.defaultBlockState().canSurvive(baby.level(), firePos)) return false;

        baby.level().setBlock(firePos, Blocks.FIRE.defaultBlockState(), 11);
        baby.level().playSound(null, firePos, SoundEvents.FIRECHARGE_USE,
                baby.getSoundSource(), 0.8F, 1.0F);

        ItemStack charge = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!charge.is(Items.FIRE_CHARGE)) return false;
        charge.shrink(1);
        baby.setItemSlot(EquipmentSlot.MAINHAND, charge);
        baby.getNavigation().moveTo(baby.getX() - away.x * 2.5D, baby.getY(), baby.getZ() - away.z * 2.5D, 1.4D);
        baby.getPersistentData().putLong("BabyCombatFirePos", firePos.asLong());
        baby.getPersistentData().putInt("BabyCombatFireTicks", 40);
        return true;
    }

    private static boolean hasThrowable(BabyNPCPlayerEntity baby) {
        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (isThrowableItem(main)) return true;
        ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);
        if (isThrowableItem(off) && !isCombatTotem(off)) return true;
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            if (isThrowableItem(baby.getBabyInventory().getItem(i))) return true;
        }
        return false;
    }

    private static boolean isThrowableItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(Items.SNOWBALL)
                || stack.is(Items.EGG)
                || stack.is(Items.ENDER_PEARL)
                || stack.is(Items.EXPERIENCE_BOTTLE)
                || stack.getItem() instanceof net.minecraft.world.item.SplashPotionItem
                || stack.getItem() instanceof net.minecraft.world.item.LingeringPotionItem;
    }

    private static boolean equipBestThrowable(BabyNPCPlayerEntity baby) {
        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (isThrowableItem(main)) return true;
        ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);
        if (isThrowableItem(off) && !isCombatTotem(off)) {
            swapHandsPreservingTotem(baby);
            return true;
        }
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            if (isThrowableItem(baby.getBabyInventory().getItem(i))) {
                return equipFromInventoryAsMain(baby, i);
            }
        }
        return false;
    }

    private static boolean rangedThrowable(BabyNPCPlayerEntity baby, LivingEntity target) {
        if (baby.getPersistentData().getInt(THROWABLE_COOLDOWN_TAG) > 0) return true;
        if (!equipBestThrowable(baby)) return false;
        ItemStack stack = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!isThrowableItem(stack) || isOwnerBlockingShot(baby, target)) return false;
        if (!(baby.level() instanceof ServerLevel level)) return true;

        Vec3 from = baby.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();
        Vec3 direction;
        double speed;
        double gravity;
        if (stack.is(Items.SNOWBALL) || stack.is(Items.EGG)) {
            speed = 1.5D;
            gravity = 0.03D;
        } else if (stack.is(Items.ENDER_PEARL)) {
            speed = 1.5D;
            gravity = 0.03D;
        } else {
            speed = 0.7D;
            gravity = 0.05D;
        }
        direction = calculateBallisticDirection(from, to, target.getDeltaMovement(), speed, gravity);
        if (direction == null) return false;

        if (stack.is(Items.SNOWBALL)) {
            Snowball projectile = new Snowball(level, baby);
            projectile.setPos(from.x, from.y - 0.1D, from.z);
            shootWithAccuracy(projectile, direction, (float)speed, RANGED_ACCURACY_DEGREES, baby.getRandom());
            level.addFreshEntity(projectile);
        } else if (stack.is(Items.EGG)) {
            ThrownEgg projectile = new ThrownEgg(level, baby);
            projectile.setPos(from.x, from.y - 0.1D, from.z);
            shootWithAccuracy(projectile, direction, (float)speed, RANGED_ACCURACY_DEGREES, baby.getRandom());
            level.addFreshEntity(projectile);
        } else if (stack.is(Items.ENDER_PEARL)) {
            ThrownEnderpearl projectile = new ThrownEnderpearl(level, baby);
            projectile.setPos(from.x, from.y - 0.1D, from.z);
            shootWithAccuracy(projectile, direction, (float)speed, RANGED_ACCURACY_DEGREES, baby.getRandom());
            level.addFreshEntity(projectile);
        } else if (stack.is(Items.EXPERIENCE_BOTTLE)) {
            ThrownExperienceBottle projectile = new ThrownExperienceBottle(level, baby);
            projectile.setItem(stack.copyWithCount(1));
            projectile.setPos(from.x, from.y - 0.1D, from.z);
            shootWithAccuracy(projectile, direction, (float)speed, RANGED_ACCURACY_DEGREES, baby.getRandom());
            level.addFreshEntity(projectile);
        } else if (stack.getItem() instanceof net.minecraft.world.item.PotionItem) {
            ThrownPotion projectile = new ThrownPotion(level, baby);
            projectile.setItem(stack.copyWithCount(1));
            projectile.setPos(from.x, from.y - 0.1D, from.z);
            shootWithAccuracy(projectile, direction, (float)speed, RANGED_ACCURACY_DEGREES, baby.getRandom());
            level.addFreshEntity(projectile);
        } else {
            return false;
        }

        stack.shrink(1);
        baby.setItemSlot(EquipmentSlot.MAINHAND, stack);
        baby.getPersistentData().putInt(THROWABLE_COOLDOWN_TAG, THROWABLE_COOLDOWN_TICKS);
        baby.swingMainHand();
        return true;
    }

    /**
     * Solves the trajectory against Minecraft's discrete projectile motion:
     * position advances by velocity, then velocity receives the normal 0.99
     * air drag and gravity. This is intentionally simulation-based instead of
     * using a continuous parabola, so the AI's aim follows the same curve the
     * spawned projectile will actually fly.
     *
     * If no physically reachable trajectory exists, null is returned. There is
     * deliberately no "far distance" fallback angle; that was the source of
     * the old behaviour where an unreachable target produced a shot that
     * climbed almost vertically into the sky.
     */
    private static Vec3 calculateBallisticDirection(
            Vec3 from, Vec3 target, Vec3 targetVelocity, double speed, double gravity) {
        if (speed <= 0.0D) return null;

        Vec3 predictedTarget = target;
        Vec3 solution = null;
        double flightTime = Math.max(1.0D, from.distanceTo(target) / speed);

        for (int iteration = 0; iteration < BALLISTIC_PREDICTION_ITERATIONS; iteration++) {
            predictedTarget = target.add(targetVelocity.scale(flightTime));
            solution = solveDiscreteBallisticDirection(from, predictedTarget, speed, gravity);
            if (solution == null) return null;

            double horizontalDistance = Math.sqrt(
                    (predictedTarget.x - from.x) * (predictedTarget.x - from.x)
                    + (predictedTarget.z - from.z) * (predictedTarget.z - from.z));
            double horizontalSpeed = Math.sqrt(solution.x * solution.x + solution.z * solution.z) * speed;
            if (horizontalSpeed < BALLISTIC_MIN_HORIZONTAL_SPEED) {
                flightTime = 1.0D;
            } else {
                flightTime = Math.max(1.0D, horizontalDistance / horizontalSpeed);
            }
        }
        return solution;
    }

    /**
     * Finds the lowest natural firing angle whose tick-by-tick Minecraft
     * trajectory intersects the target point. Lower angles are preferred, so
     * the Baby does not use an unnecessary high arc.
     */
    private static Vec3 solveDiscreteBallisticDirection(
            Vec3 from, Vec3 to, double speed, double gravity) {
        Vec3 delta = to.subtract(from);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 0.001D) {
            if (Math.abs(delta.y) < 0.001D) return new Vec3(0.0D, 0.0D, 1.0D);
            return new Vec3(0.0D, delta.y > 0.0D ? 1.0D : -1.0D, 0.0D);
        }

        Vec3 horizontalDirection = new Vec3(delta.x / horizontal, 0.0D, delta.z / horizontal);
        double bestPitch = Double.NaN;
        double bestError = Double.POSITIVE_INFINITY;

        // Coarse pass: find the lowest viable region first.
        for (int i = 0; i <= BALLISTIC_ANGLE_SAMPLES; i++) {
            double pitch = Math.toRadians(-10.0D + 100.0D * i / BALLISTIC_ANGLE_SAMPLES);
            double error = simulateBallisticVerticalError(
                    from, to, horizontal, horizontalDirection, speed, gravity, pitch);
            if (error < bestError) {
                bestError = error;
                bestPitch = pitch;
            }
        }

        if (Double.isNaN(bestPitch)) return null;

        // Fine pass around the coarse solution. This makes the solved curve
        // much tighter than the visual angle step while keeping CPU cost low.
        double fineHalfRange = Math.toRadians(1.25D);
        for (int i = 0; i <= 20; i++) {
            double pitch = bestPitch - fineHalfRange
                    + (fineHalfRange * 2.0D) * i / 20.0D;
            double error = simulateBallisticVerticalError(
                    from, to, horizontal, horizontalDirection, speed, gravity, pitch);
            if (error < bestError) {
                bestError = error;
                bestPitch = pitch;
            }
        }

        // No artificial fallback. An unreachable target means the Baby must
        // reposition or wait for the target to move into the real envelope.
        if (Double.isNaN(bestPitch) || bestError > 0.20D) return null;

        double cos = Math.cos(bestPitch);
        double sin = Math.sin(bestPitch);
        return horizontalDirection.scale(cos).add(0.0D, sin, 0.0D).normalize();
    }

    private static double simulateBallisticVerticalError(
            Vec3 from, Vec3 to, double horizontal, Vec3 horizontalDirection,
            double speed, double gravity, double pitch) {
        double cos = Math.cos(pitch);
        double sin = Math.sin(pitch);
        Vec3 velocity = horizontalDirection.scale(speed * cos).add(0.0D, speed * sin, 0.0D);
        Vec3 position = from;
        double previousHorizontal = 0.0D;
        double previousVerticalError = from.y - to.y;

        for (int tick = 0; tick < BALLISTIC_MAX_TICKS; tick++) {
            position = position.add(velocity);
            double travelledHorizontal = Math.sqrt(
                    (position.x - from.x) * (position.x - from.x)
                    + (position.z - from.z) * (position.z - from.z));
            double verticalError = position.y - to.y;

            if (travelledHorizontal >= horizontal) {
                double span = travelledHorizontal - previousHorizontal;
                double fraction = span > 1.0E-9D
                        ? (horizontal - previousHorizontal) / span
                        : 0.0D;
                double interpolatedError = previousVerticalError
                        + (verticalError - previousVerticalError)
                        * Mth.clamp(fraction, 0.0D, 1.0D);
                return Math.abs(interpolatedError);
            }

            previousHorizontal = travelledHorizontal;
            previousVerticalError = verticalError;
            velocity = velocity.scale(BALLISTIC_AIR_DRAG).add(0.0D, -gravity, 0.0D);
        }

        return Double.POSITIVE_INFINITY;
    }

    /**
     * Applies the Baby's configurable combat accuracy after the exact
     * trajectory has been solved. This deliberately replaces vanilla's
     * inaccuracy argument so there is only one source of miss distance.
     */
    private static Vec3 applyAccuracy(Vec3 exactDirection, double accuracyDegrees, RandomSource random) {
        Vec3 direction = exactDirection.normalize();
        if (accuracyDegrees <= 0.0D) return direction;

        double yawOffset = (random.nextDouble() * 2.0D - 1.0D)
                * Math.toRadians(accuracyDegrees);
        double pitchOffset = (random.nextDouble() * 2.0D - 1.0D)
                * Math.toRadians(accuracyDegrees);
        return direction
                .yRot((float) yawOffset)
                .xRot((float) pitchOffset)
                .normalize();
    }

    private static void shootWithAccuracy(
            net.minecraft.world.entity.projectile.Projectile projectile,
            Vec3 exactDirection, float speed, double accuracyDegrees, RandomSource random) {
        Vec3 direction = applyAccuracy(exactDirection, accuracyDegrees, random);
        projectile.shoot(direction.x, direction.y, direction.z, speed, 0.0F);
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

    private static boolean isFluidPlacementWithinRange(BabyNPCPlayerEntity baby, BlockPos pos) {
        double dx = pos.getX() + 0.5D - baby.getX();
        double dy = pos.getY() + 0.5D - baby.getY();
        double dz = pos.getZ() + 0.5D - baby.getZ();
        return dx * dx + dy * dy + dz * dz <= FLUID_PLACEMENT_MAX_RANGE * FLUID_PLACEMENT_MAX_RANGE;
    }

    private static boolean tacticalLava(BabyNPCPlayerEntity baby, LivingEntity target) {
        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        var lava = FluidUtil.getFluidContained(main);
        if (lava.isEmpty() || lava.get().getFluid() != net.minecraft.world.level.material.Fluids.LAVA) return false;

        // Do not stall by maintaining a large Baby-to-target distance. The
        // placement point itself is validated against the target and the real
        // 4-block fluid-placement reach below. If a valid supported point exists,
        // place the lava now even when the Baby is already close to the target.

        Vec3 awayFromBaby = target.position().subtract(baby.position());
        Vec3 horizontal = new Vec3(awayFromBaby.x, 0.0D, awayFromBaby.z);
        if (horizontal.lengthSqr() < 0.0001D) horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        horizontal = horizontal.normalize();

        // Prefer a point in front of the mob, away from the Baby. The point
        // must be close enough to the mob to matter, but never beyond the
        // Baby's actual 4-block fluid-placement reach.
        BlockPos base = target.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int[] offsets = new int[]{1, 2, 3};
        for (int offset : offsets) {
            int x = base.getX() + Mth.floor(horizontal.x * offset + 0.5D);
            int z = base.getZ() + Mth.floor(horizontal.z * offset + 0.5D);
            BlockPos candidate = new BlockPos(x, base.getY(), z);
            if (!isGoodFluidCombatPlacement(baby, target, candidate, false)) continue;
            double td = Math.sqrt(target.distanceToSqr(candidate.getX() + 0.5D, candidate.getY() + 0.5D, candidate.getZ() + 0.5D));
            double bd = Math.sqrt(baby.distanceToSqr(candidate.getX() + 0.5D, candidate.getY() + 0.5D, candidate.getZ() + 0.5D));
            double score = Math.abs(td - 1.75D) + Math.abs(bd - 3.25D) * 0.25D;
            if (score < bestScore) { bestScore = score; best = candidate; }
        }

        // If the ideal front point has no support, look for a nearby wall/floor
        // surface. We never place into an unsupported air column.
        if (best == null) {
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos candidate = base.relative(dir).above(dy);
                    if (!isGoodFluidCombatPlacement(baby, target, candidate, true)) continue;
                    double td = Math.sqrt(target.distanceToSqr(candidate.getX() + 0.5D, candidate.getY() + 0.5D, candidate.getZ() + 0.5D));
                    double score = Math.abs(td - 1.75D);
                    if (score < bestScore) { bestScore = score; best = candidate; }
                }
            }
        }

        // No valid surface within reach: retreat rather than placing lava on
        // an unsupported air block or wasting the bucket.
        if (best == null) {
            moveAwayFromTarget(baby, target, 1.25D);
            return true;
        }

        FluidActionResult result = FluidUtil.tryPlaceFluid(
                null, baby.level(), InteractionHand.MAIN_HAND, best, main, lava.get());
        if (!result.isSuccess()) return false;

        baby.setItemSlot(EquipmentSlot.MAINHAND, result.getResult());
        baby.level().playSound(null, best, SoundEvents.BUCKET_EMPTY_LAVA,
                baby.getSoundSource(), 0.8F, 1.0F);
        baby.getPersistentData().putLong(LAVA_POS_TAG, best.asLong());
        baby.getPersistentData().putUUID(LAVA_TARGET_TAG, target.getUUID());
        baby.getPersistentData().putInt(LAVA_TICKS_TAG, 40);
        baby.getPersistentData().remove(LAVA_RETREAT_TICKS_TAG);
        baby.getNavigation().stop();
        return true;
    }

    /** A fluid combat position must be real, reachable and supported by a floor or wall. */
    private static boolean isGoodFluidCombatPlacement(BabyNPCPlayerEntity baby, LivingEntity target, BlockPos pos, boolean wallFallback) {
        if (!baby.level().getBlockState(pos).isAir()) return false;
        if (!isFluidPlacementWithinRange(baby, pos)) return false;

        double targetDistance = Math.sqrt(target.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
        if (targetDistance < FLUID_TARGET_MIN_DISTANCE || targetDistance > FLUID_TARGET_MAX_DISTANCE) return false;

        BlockState below = baby.level().getBlockState(pos.below());
        if (below.isFaceSturdy(baby.level(), pos.below(), Direction.UP)) return true;

        if (wallFallback) {
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                BlockPos wall = pos.relative(dir);
                if (baby.level().getBlockState(wall).isFaceSturdy(baby.level(), wall, dir.getOpposite())) return true;
            }
        }
        return false;
    }

    private static boolean tickLavaBucketResupply(BabyNPCPlayerEntity baby) {
        CompoundTag data = baby.getPersistentData();
        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);

        int bucketSource = findEmptyBucketForLava(baby);
        if (bucketSource == -1) {
            data.remove(LAVA_RESUPPLY_POS_TAG);
            return false;
        }

        int cooldown = Math.max(0, data.getInt(LAVA_RESUPPLY_COOLDOWN_TAG) - 1);
        data.putInt(LAVA_RESUPPLY_COOLDOWN_TAG, cooldown);

        BlockPos source = data.contains(LAVA_RESUPPLY_POS_TAG)
                ? BlockPos.of(data.getLong(LAVA_RESUPPLY_POS_TAG))
                : null;

        if (source == null || !isNearbyLavaSource(baby, source)) {
            if (cooldown > 0) return false;
            source = findNearestLavaSource(baby, LAVA_RESUPPLY_RADIUS);
            if (source == null) {
                data.remove(LAVA_RESUPPLY_POS_TAG);
                data.putInt(LAVA_RESUPPLY_COOLDOWN_TAG, LAVA_RESUPPLY_RECHECK_TICKS);
                return false;
            }
            data.putLong(LAVA_RESUPPLY_POS_TAG, source.asLong());
            data.putInt(LAVA_RESUPPLY_COOLDOWN_TAG, LAVA_RESUPPLY_RECHECK_TICKS);
        }

        // Move one real empty bucket to the main hand while preserving the
        // combat totem. -2 = main hand, -3 = offhand, >=0 = Baby inventory.
        if (bucketSource >= 0) {
            if (!equipFromInventoryAsMain(baby, bucketSource)) return false;
        } else if (bucketSource == -3) {
            if (isCombatTotem(main)) return false;
            swapHands(baby);
        }

        source = BlockPos.of(data.getLong(LAVA_RESUPPLY_POS_TAG));
        if (!isNearbyLavaSource(baby, source)) {
            data.remove(LAVA_RESUPPLY_POS_TAG);
            return false;
        }

        double distance = baby.distanceToSqr(
                source.getX() + 0.5D, source.getY() + 0.5D, source.getZ() + 0.5D);

        if (distance > LAVA_RESUPPLY_PICKUP_DISTANCE * LAVA_RESUPPLY_PICKUP_DISTANCE) {
            // Stop at a safe approach distance. The Baby never needs to enter
            // the lava source block to use a real bucket pickup.
            Vec3 direction = new Vec3(
                    baby.getX() - (source.getX() + 0.5D),
                    0.0D,
                    baby.getZ() - (source.getZ() + 0.5D));
            if (direction.lengthSqr() < 0.0001D) direction = new Vec3(1.0D, 0.0D, 0.0D);
            direction = direction.normalize();
            Vec3 approach = new Vec3(
                    source.getX() + 0.5D + direction.x * 2.0D,
                    source.getY(),
                    source.getZ() + 0.5D + direction.z * 2.0D);
            baby.getNavigation().moveTo(approach.x, approach.y, approach.z, 1.15D);
            baby.setSprinting(false);
            return true;
        }

        if (baby.isInLava() || baby.getFluidHeight(FluidTags.LAVA) > 0.0D || baby.isOnFire()) {
            baby.getNavigation().stop();
            return true;
        }

        ItemStack bucket = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!bucket.is(Items.BUCKET)) return false;

        FluidActionResult result = FluidUtil.tryPickUpFluid(
                bucket, null, baby.level(), source, Direction.UP);
        if (result.isSuccess()) {
            baby.setItemSlot(EquipmentSlot.MAINHAND, result.getResult());
            baby.level().playSound(null, source, SoundEvents.BUCKET_FILL_LAVA,
                    baby.getSoundSource(), 0.8F, 1.0F);
            data.remove(LAVA_RESUPPLY_POS_TAG);
            data.putInt(LAVA_RESUPPLY_COOLDOWN_TAG, LAVA_RESUPPLY_RECHECK_TICKS);
            return true;
        }

        data.putInt(LAVA_RESUPPLY_COOLDOWN_TAG, 2);
        return true;
    }

    private static int findEmptyBucketForLava(BabyNPCPlayerEntity baby) {
        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (main.is(Items.BUCKET)) return -2;

        ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);
        if (off.is(Items.BUCKET) && !isCombatTotem(off)) return -3;

        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            if (baby.getBabyInventory().getItem(i).is(Items.BUCKET)) return i;
        }
        return -1;
    }

    private static BlockPos findNearestLavaSource(BabyNPCPlayerEntity baby, double radius) {
        int r = Mth.ceil(radius);
        BlockPos center = baby.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int vertical = Math.min(4, r);

        for (int x = center.getX() - r; x <= center.getX() + r; x++) {
            for (int y = center.getY() - vertical; y <= center.getY() + vertical; y++) {
                for (int z = center.getZ() - r; z <= center.getZ() + r; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    double distance = baby.distanceToSqr(
                            x + 0.5D, y + 0.5D, z + 0.5D);
                    if (distance > radius * radius || distance >= bestDistance) continue;

                    net.minecraft.world.level.material.FluidState fluid = baby.level().getFluidState(pos);
                    if (!fluid.is(FluidTags.LAVA) || !fluid.isSource()) continue;

                    bestDistance = distance;
                    best = pos;
                }
            }
        }
        return best;
    }

    private static boolean isNearbyLavaSource(BabyNPCPlayerEntity baby, BlockPos source) {
        if (source == null) return false;
        if (baby.distanceToSqr(source.getX() + 0.5D, source.getY() + 0.5D, source.getZ() + 0.5D)
                > LAVA_RESUPPLY_RADIUS * LAVA_RESUPPLY_RADIUS) return false;
        net.minecraft.world.level.material.FluidState fluid = baby.level().getFluidState(source);
        return fluid.is(FluidTags.LAVA) && fluid.isSource();
    }

    private static void tickTemporaryFireAndLava(BabyNPCPlayerEntity baby) {
        if (!(baby.level() instanceof ServerLevel level)) return;
        CompoundTag data = baby.getPersistentData();
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
        int lavaTicks = baby.getPersistentData().getInt(LAVA_TICKS_TAG);
        if (lavaTicks > 0) {
            lavaTicks--;
            baby.getPersistentData().putInt(LAVA_TICKS_TAG, lavaTicks);
            return;
        }

        if (baby.getPersistentData().contains(LAVA_POS_TAG)) {
            BlockPos p = BlockPos.of(baby.getPersistentData().getLong(LAVA_POS_TAG));

            // Never walk into the lava source just to recover it. Wait until the
            // Baby is definitely not burning and is not touching lava.
            if (baby.isOnFire()
                    || baby.isInLava()
                    || baby.getFluidHeight(FluidTags.LAVA) > 0.0D) {
                baby.getPersistentData().putInt(LAVA_TICKS_TAG, 5);
                return;
            }

            ItemStack bucket = baby.getItemBySlot(EquipmentSlot.MAINHAND);
            net.minecraft.world.level.material.FluidState fluid = level.getFluidState(p);

            // The lava must actually do its job before the Baby takes it back.
            // If the target is visibly on fire, recover immediately (once the
            // Baby itself is safe). If the target was not ignited, keep the lava
            // for the full 2-second fallback window, then recover it anyway.
            if (bucket.is(Items.BUCKET) && fluid.is(FluidTags.LAVA) && fluid.isSource()) {
                boolean targetOnFire = false;
                if (data.hasUUID(LAVA_TARGET_TAG)) {
                    Entity tracked = level.getEntity(data.getUUID(LAVA_TARGET_TAG));
                    targetOnFire = tracked instanceof LivingEntity living && living.isAlive() && living.isOnFire();
                }

                int remaining = data.getInt(LAVA_TICKS_TAG);
                if (!targetOnFire && remaining > 0) {
                    dataPutLavaRecheck(baby);
                    return;
                }

                // Real bucket pickup. Forge checks the source block and performs
                // the actual world mutation. If it fails, the lava remains and
                // the empty bucket remains empty.
                FluidActionResult result = FluidUtil.tryPickUpFluid(
                        bucket, null, level, p, Direction.UP);
                if (result.isSuccess()) {
                    baby.setItemSlot(EquipmentSlot.MAINHAND, result.getResult());
                    level.playSound(null, p, SoundEvents.BUCKET_FILL_LAVA,
                            baby.getSoundSource(), 0.8F, 1.0F);
                    baby.getPersistentData().remove(LAVA_POS_TAG);
                    baby.getPersistentData().putInt(LAVA_RETREAT_TICKS_TAG, LAVA_RETREAT_TICKS);
                    return;
                }
                dataPutLavaRecheck(baby);
                return;
            }

            // If the source is gone, there is nothing to scoop. Keep the empty
            // bucket and never manufacture a replacement.
            if (!fluid.is(FluidTags.LAVA)) {
                baby.getPersistentData().remove(LAVA_POS_TAG);
            }
        }
    }

    private static void dataPutLavaRecheck(BabyNPCPlayerEntity baby) {
        baby.getPersistentData().putInt(LAVA_TICKS_TAG, LAVA_SCOOP_RECHECK_TICKS);
    }

    /** Returns the furthest connected horizontal lava-flow distance from the placed source. */
    private static int getLavaFlowDistance(ServerLevel level, BlockPos source, int maxDistance) {
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        visited.add(source);
        queue.add(source);
        int furthest = 0;
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            int distance = Math.abs(current.getX() - source.getX()) + Math.abs(current.getZ() - source.getZ());
            furthest = Math.max(furthest, distance);
            if (distance >= maxDistance) continue;
            for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                BlockPos next = current.relative(direction);
                if (visited.add(next) && level.getFluidState(next).is(FluidTags.LAVA)) {
                    queue.addLast(next);
                }
            }
        }
        return Math.min(furthest, maxDistance);
    }

    private static double getLavaDanger(BabyNPCPlayerEntity baby, BlockPos source, int flowDistance) {
        double danger = 1.0D + flowDistance * LAVA_DANGER_PER_FLOW_BLOCK;
        double distance = Math.sqrt(baby.distanceToSqr(source.getX() + 0.5D, source.getY(), source.getZ() + 0.5D));
        if (distance <= 4.0D) danger += 1.5D;
        else if (distance <= 8.0D) danger += 0.75D;
        return danger;
    }

    /**
     * Scores the strongest nearby hostile mob as an immediate combat hazard.
     * This is intentionally comparable to lavaDanger: the Baby does not get a
     * hard-coded "always scoop" bias just because lava exists.
     */
    private static double getNearestMobDanger(BabyNPCPlayerEntity baby) {
        AABB box = baby.getBoundingBox().inflate(MOB_DANGER_RADIUS);
        LivingEntity threat = baby.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != baby && e.isAlive() && e instanceof Enemy)
                .stream()
                .min(Comparator.comparingDouble(baby::distanceToSqr))
                .orElse(null);
        if (threat == null) return 0.0D;

        double distance = Math.sqrt(baby.distanceToSqr(threat));
        double proximity = Mth.clamp((MOB_DANGER_RADIUS - distance) / 2.5D, 0.0D, 4.0D);
        double damage = 1.0D;
        AttributeInstance attack = threat.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (attack != null) damage = Mth.clamp(attack.getValue() / 3.0D, 0.5D, 4.0D);
        return proximity + damage;
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

    private static boolean isUsableTrident(BabyNPCPlayerEntity baby, ItemStack stack) {
        if (!(stack.getItem() instanceof TridentItem)) return false;
        return stack.getDamageValue() < stack.getMaxDamage() - 1;
    }

    private static boolean equipBestTrident(BabyNPCPlayerEntity baby) {
        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (isUsableTrident(baby, main)) return true;
        ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);
        if (isUsableTrident(baby, off) && !isCombatTotem(off)) {
            swapHandsPreservingTotem(baby);
            return true;
        }
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            if (isUsableTrident(baby, baby.getBabyInventory().getItem(i))) {
                return equipFromInventoryAsMain(baby, i);
            }
        }
        return false;
    }

    /**
     * Full survival-oriented Trident state. The projectile itself is vanilla
     * ThrownTrident, so Loyalty/Channeling/Impaling and collision stay inside
     * vanilla mechanics. The controller only owns decision, charge and recovery.
     */
    /**
     * Stable Trident lifecycle. Vanilla owns the projectile physics and Loyalty
     * return. The Baby only performs the inventory pickup for Loyalty tridents
     * once the returning projectile actually reaches it. Non-Loyalty tridents
     * are intentionally never recovered by the controller.
     */
    private static boolean tickTridentLifecycle(BabyNPCPlayerEntity baby) {
        CompoundTag data = baby.getPersistentData();
        int cooldown = Math.max(0, data.getInt(TRIDENT_COOLDOWN_TAG) - 1);
        data.putInt(TRIDENT_COOLDOWN_TAG, cooldown);

        AABB search = baby.getBoundingBox().inflate(128.0D);
        java.util.List<ThrownTrident> owned = baby.level().getEntitiesOfClass(
                ThrownTrident.class, search,
                trident -> trident.isAlive() && trident.getOwner() == baby);

        ItemStack pickup = ItemStack.EMPTY;
        CompoundTag storedTrident = data.getCompound(TRIDENT_ACTIVE_ITEM_TAG);
        if (!storedTrident.isEmpty()) {
            pickup = ItemStack.of(storedTrident);
        }

        for (ThrownTrident trident : owned) {
            // Do not cast the vanilla ThrownTrident to a mixin accessor here.
            // The controller already owns the exact ItemStack used to spawn the
            // projectile, so keep that stack in Baby persistent data instead.
            // This makes the lifecycle safe even if the accessor mixin is not
            // present in a transformed runtime class.
            boolean loyalty = !pickup.isEmpty()
                    && EnchantmentHelper.getItemEnchantmentLevel(
                            net.minecraft.world.item.enchantment.Enchantments.LOYALTY, pickup) > 0;

            // Ordinary thrown tridents are deliberately left in the world.
            // No search/navigation/recovery is performed for them.
            if (!loyalty) continue;

            data.putBoolean(TRIDENT_ACTIVE_TAG, true);

            // Vanilla Loyalty steers the returning trident toward the owner's
            // eye/hand area rather than toward the center of a full-sized hitbox.
            // Baby entities have a much smaller model, so use the Baby's eye
            // position as the return target and only accept it once it reaches
            // that point. Do not alter the projectile speed.
            Vec3 returnTarget = baby.getEyePosition();
            double returnRadius = Math.max(0.35D, baby.getBbHeight() * 0.28D);
            if (trident.position().distanceToSqr(returnTarget) <= returnRadius * returnRadius
                    && !pickup.isEmpty()) {
                addToInventoryOrDrop(baby, pickup.copy());
                trident.discard();
                data.putBoolean(TRIDENT_ACTIVE_TAG, false);
                data.putInt(TRIDENT_CHARGE_TICKS_TAG, 0);
                data.remove(TRIDENT_ACTIVE_ITEM_TAG);
                return false;
            }

            baby.getNavigation().stop();
            baby.setSprinting(false);
            return true;
        }

        if (data.getBoolean(TRIDENT_ACTIVE_TAG)) {
            data.putBoolean(TRIDENT_ACTIVE_TAG, false);
            data.putInt(TRIDENT_CHARGE_TICKS_TAG, 0);
            data.remove(TRIDENT_ACTIVE_ITEM_TAG);
            if (baby.isUsingItem()) baby.stopUsingItem();
        }
        return false;
    }

    private static void rangedTrident(BabyNPCPlayerEntity baby, LivingEntity target) {
        ItemStack trident = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!isUsableTrident(baby, trident)) return;

        baby.getLookControl().setLookAt(target, 30.0F, baby.getMaxHeadXRot());

        int riptide = EnchantmentHelper.getRiptide(trident);
        if (riptide > 0 && baby.isInWaterOrRain() && baby.distanceToSqr(target) >= 4.0D) {
            if (tryRiptideAttack(baby, target, trident, riptide)) return;
        }

        if (isOwnerBlockingShot(baby, target)) {
            repositionForRangedShot(baby, target);
            return;
        }

        int cooldown = baby.getPersistentData().getInt(TRIDENT_COOLDOWN_TAG);
        if (cooldown > 0) return;

        if (!baby.isUsingItem()) {
            baby.startUsingItem(InteractionHand.MAIN_HAND);
            baby.getPersistentData().putInt(TRIDENT_CHARGE_TICKS_TAG, 0);
            return;
        }

        int charge = baby.getPersistentData().getInt(TRIDENT_CHARGE_TICKS_TAG) + 1;
        baby.getPersistentData().putInt(TRIDENT_CHARGE_TICKS_TAG, charge);
        if (charge < TRIDENT_MIN_CHARGE_TICKS) return;

        baby.stopUsingItem();
        if (throwTrident(baby, target, trident)) {
            baby.getPersistentData().putInt(TRIDENT_CHARGE_TICKS_TAG, 0);
            baby.getPersistentData().putInt(TRIDENT_COOLDOWN_TAG, TRIDENT_THROW_COOLDOWN);
            baby.getPersistentData().putBoolean(TRIDENT_ACTIVE_TAG, true);
            baby.swingMainHand();
            baby.triggerAnim("combat", "trident_throw");
        } else {
            baby.getPersistentData().putInt(TRIDENT_CHARGE_TICKS_TAG, 0);
        }
    }

    private static boolean throwTrident(BabyNPCPlayerEntity baby, LivingEntity target, ItemStack held) {
        if (!(baby.level() instanceof ServerLevel level)) return false;
        if (!isUsableTrident(baby, held) || isOwnerBlockingShot(baby, target)) return false;

        ItemStack projectileStack = held.copyWithCount(1);
        int nextDamage = held.getDamageValue() + 1;
        projectileStack.setDamageValue(Math.min(nextDamage, projectileStack.getMaxDamage() - 1));
        ThrownTrident projectile = new ThrownTrident(level, baby, projectileStack);
        Vec3 from = baby.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();
        Vec3 direction = calculateBallisticDirection(
                from, to, target.getDeltaMovement(), 2.5D, 0.05D);
        if (direction == null) return false;

        baby.getPersistentData().put(TRIDENT_ACTIVE_ITEM_TAG, projectileStack.save(new CompoundTag()));
        shootWithAccuracy(projectile, direction, 2.5F, RANGED_ACCURACY_DEGREES, baby.getRandom());
        projectile.pickup = AbstractArrow.Pickup.ALLOWED;
        level.addFreshEntity(projectile);
        level.playSound(null, projectile, SoundEvents.TRIDENT_THROW,
                baby.getSoundSource(), 1.0F, 1.0F);

        // The durability step has already been applied to the projectile copy.
        // The active hand becomes empty exactly like a survival Player after
        // throwing a non-stackable trident.
        held.setDamageValue(Math.min(held.getMaxDamage(), held.getDamageValue() + 1));
        held.setCount(0);
        baby.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        return true;
    }

    private static boolean tryRiptideAttack(BabyNPCPlayerEntity baby, LivingEntity target,
                                             ItemStack trident, int riptideLevel) {
        if (!(baby.level() instanceof ServerLevel)) return false;
        Vec3 direction = target.getEyePosition().subtract(baby.getEyePosition());
        if (direction.lengthSqr() < 0.0001D) return false;
        direction = direction.normalize();

        float strength = 1.5F * riptideLevel;
        baby.push(direction.x * strength, direction.y * strength, direction.z * strength);
        baby.hasImpulse = true;
        // BabyNPCPlayerEntity is a PathfinderMob rather than a Player, so it
        // does not expose Player#startAutoSpinAttack(). The survival-equivalent
        // movement is applied directly; vanilla projectile mechanics remain
        // untouched for the normal throw path.
        trident.hurtAndBreak(1, baby, entity -> entity.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        if (trident.isEmpty()) baby.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        baby.swingMainHand();
        baby.getPersistentData().putInt(TRIDENT_COOLDOWN_TAG, 20);
        return true;
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
        ItemStack crossbow = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!(crossbow.getItem() instanceof CrossbowItem)) return;

        baby.getLookControl().setLookAt(target, 30.0F, baby.getMaxHeadXRot());

        // A crossbow must actually be charged before it fires. Previously this
        // controller inserted the projectile and fired on the next combat tick,
        // which made the Baby appear to "tap" the crossbow instead of holding
        // it in the vanilla charging state. Keep the entity in use for the real
        // vanilla charge duration (Quick Charge included), then release and fire.
        if (!CrossbowItem.isCharged(crossbow)) {
            if (baby.getPersistentData().getInt(RANGED_COOLDOWN_TAG) > 0) return;

            if (!baby.isUsingItem()) {
                if (findArrowStack(baby).isEmpty()) return;
                baby.startUsingItem(InteractionHand.MAIN_HAND);
                baby.getPersistentData().putInt(CROSSBOW_CHARGE_TICKS_TAG, 0);
                baby.triggerAnim("combat", "crossbow_load");
                return;
            }

            // Do not manually load the projectile during the charge. The vanilla
            // CrossbowItem charging path is represented by isUsingItem() and its
            // use animation; the projectile is inserted only after the full
            // charge duration has elapsed.
            int chargeTicks = baby.getPersistentData().getInt(CROSSBOW_CHARGE_TICKS_TAG) + 1;
            baby.getPersistentData().putInt(CROSSBOW_CHARGE_TICKS_TAG, chargeTicks);

            int requiredTicks = CrossbowItem.getChargeDuration(crossbow);
            if (chargeTicks < requiredTicks) {
                return;
            }

            ItemStack ammo = findArrowStack(baby);
            if (ammo.isEmpty()) {
                baby.stopUsingItem();
                baby.getPersistentData().putInt(CROSSBOW_CHARGE_TICKS_TAG, 0);
                return;
            }

            if (!addVanillaChargedProjectile(crossbow, new ItemStack(Items.ARROW))) {
                baby.stopUsingItem();
                baby.getPersistentData().putInt(CROSSBOW_CHARGE_TICKS_TAG, 0);
                return;
            }
            ammo.shrink(1);
            CrossbowItem.setCharged(crossbow, true);
            baby.stopUsingItem();
            baby.getPersistentData().putInt(CROSSBOW_CHARGE_TICKS_TAG, 0);
        }

        if (!CrossbowItem.isCharged(crossbow) || baby.getPersistentData().getInt(RANGED_COOLDOWN_TAG) > 0) return;

        float oldXRot = baby.getXRot();
        float oldYRot = baby.getYRot();
        Vec3 crossbowDirection = calculateBallisticDirection(
                baby.getEyePosition(),
                target.getBoundingBox().getCenter(),
                target.getDeltaMovement(),
                1.6D, 0.05D);
        if (crossbowDirection == null) {
            // The target is outside the real ballistic envelope from the
            // current position. Do not fire an artificial upward shot.
            CrossbowItem.setCharged(crossbow, false);
            clearVanillaChargedProjectiles(crossbow);
            return;
        }
        Vec3 firingDirection = applyAccuracy(
                crossbowDirection, RANGED_ACCURACY_DEGREES, baby.getRandom());
        float ballisticPitch = (float)(-Math.toDegrees(Math.atan2(
                firingDirection.y, Math.sqrt(firingDirection.x * firingDirection.x + firingDirection.z * firingDirection.z))));
        float ballisticYaw = (float)(Math.toDegrees(Math.atan2(
                -firingDirection.x, firingDirection.z)));
        try {
            baby.setXRot(ballisticPitch);
            baby.setYRot(ballisticYaw);
            CrossbowItem.performShooting(
                    baby.level(),
                    baby,
                    InteractionHand.MAIN_HAND,
                    crossbow,
                    1.6F,
                    0.0F);
        } catch (RuntimeException ex) {
            clearVanillaChargedProjectiles(crossbow);
            CrossbowItem.setCharged(crossbow, false);
            baby.stopUsingItem();
            baby.getPersistentData().putInt(CROSSBOW_CHARGE_TICKS_TAG, 0);
            NormalNPCPlayer.LOGGER.error(
                    "Baby crossbow firing failed: id={}, item={}, target={}",
                    baby.getId(), crossbow, target.getType().toShortString(), ex);
            baby.setXRot(oldXRot);
            baby.setYRot(oldYRot);
            return;
        }
        baby.setXRot(oldXRot);
        baby.setYRot(oldYRot);

        // Vanilla firing consumes the charged projectile. Explicitly clear the
        // stack state as well because this is a LivingEntity-driven controller,
        // not Player#releaseUsing().
        clearVanillaChargedProjectiles(crossbow);
        CrossbowItem.setCharged(crossbow, false);
        baby.getPersistentData().putInt(CROSSBOW_CHARGE_TICKS_TAG, 0);

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
        arrow.setEnchantmentEffectsFromEntity(baby, power * 3.0F);

        Vec3 from = baby.getEyePosition();
        Vec3 direction = calculateBallisticDirection(
                from, target.getBoundingBox().getCenter(), target.getDeltaMovement(),
                power * 3.0D, 0.05D);
        if (direction == null) return false;

        arrow.setPos(from.x, from.y - 0.1D, from.z);
        shootWithAccuracy(arrow, direction, power * 3.0F, RANGED_ACCURACY_DEGREES, baby.getRandom());
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

    private static void equipSpecialFromInventory(BabyNPCPlayerEntity baby) {
        ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);
        if ((off.getItem() instanceof FlintAndSteelItem || off.is(Items.FIRE_CHARGE) || off.is(Items.LAVA_BUCKET)) && !isCombatTotem(off)) {
            swapHands(baby);
            return;
        }
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            ItemStack s = baby.getBabyInventory().getItem(i);
            if (s.getItem() instanceof FlintAndSteelItem || s.is(Items.FIRE_CHARGE) || s.is(Items.LAVA_BUCKET)) {
                equipFromInventoryAsMain(baby, i);
                return;
            }
        }
    }

    private static boolean equipBestRangedWeapon(BabyNPCPlayerEntity baby) {
        int slot = -1;
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            ItemStack s = baby.getBabyInventory().getItem(i);
            if (isUsableBow(baby, s) || isUsableCrossbow(baby, s)) { slot = i; break; }
        }
        return slot >= 0 && equipFromInventoryAsMain(baby, slot);
    }

    /**
     * Ensures the Baby has a melee weapon when one is available. If no melee
     * weapon exists, combat deliberately falls back to the Baby's natural
     * attack damage (a bare-fist attack) instead of refusing to attack.
     */
    private static boolean equipBestMelee(BabyNPCPlayerEntity baby) {
        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);

        // A real melee weapon already in the main hand always wins.
        if (isMeleeWeapon(main)) return true;

        // Keep the combat totem out of the attacking hand. This matters for
        // setups where the totem was manually placed in the main hand.
        if (isCombatTotem(main)) {
            ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);
            if (off.isEmpty()) {
                baby.setItemSlot(EquipmentSlot.OFFHAND, main.copy());
                baby.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                return true;
            }
            if (isMeleeWeapon(off) && !isCombatTotem(off)) {
                swapHands(baby);
                return true;
            }
        }

        // Search the Baby's own inventory for a melee weapon. If one exists,
        // use it; otherwise leave the hand empty/non-weapon and punch.
        int best = -1;
        int score = -1;
        for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
            ItemStack s = baby.getBabyInventory().getItem(i);
            int v = s.getItem() instanceof SwordItem ? 20
                    : s.getItem() instanceof TridentItem ? 18
                    : s.getItem() instanceof AxeItem ? 15
                    : -1;
            if (v > score) {
                score = v;
                best = i;
            }
        }
        if (best >= 0) return equipFromInventoryAsMain(baby, best);

        // No melee weapon anywhere: clear a non-weapon from the attacking
        // hand so the Baby visibly attacks with an empty hand/fist. Keep the
        // item instead of deleting it.
        main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!main.isEmpty() && !isCombatTotem(main)) {
            addToInventoryOrDrop(baby, main.copy());
            baby.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        // No weapon anywhere: allow the normal Mob attack path to deal the
        // Baby's natural ATTACK_DAMAGE. This is the bare-fist combat state.
        return true;
    }

    private static boolean isMeleeWeapon(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem);
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

    /**
     * Quick-response emergency swap used only while Combat Mode is active.
     * The vanilla Totem of Undying is moved into the offhand just before a
     * predicted lethal hit. The Baby Combat Totem is stored in the Baby's
     * inventory so vanilla's normal totem-death-protection code can consume
     * the vanilla totem without replacing or consuming the combat totem.
     */
    public static boolean prepareVanillaTotemForLethalDamage(
            BabyNPCPlayerEntity baby,
            net.minecraft.world.damagesource.DamageSource source,
            float amount
    ) {
        if (baby == null || !baby.isAlive() || !isCombatActive(baby)) return false;
        if (source == null || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (amount <= 0.0F) return false;

        // Absorption is consumed before health, so do not waste the vanilla
        // totem on damage that cannot actually kill the Baby.
        float remaining = Math.max(0.0F, amount - baby.getAbsorptionAmount());
        if (remaining < baby.getHealth()) return false;

        int vanillaSlot = findVanillaTotem(baby.getBabyInventory());
        if (vanillaSlot < 0) return false;

        ItemStack main = baby.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);

        // If a vanilla totem is already held, vanilla can handle it directly.
        // The recovery flag still makes Combat Mode resume automatically after
        // the totem is consumed.
        if (off.is(Items.TOTEM_OF_UNDYING) || main.is(Items.TOTEM_OF_UNDYING)) {
            baby.getPersistentData().putBoolean(VANILLA_TOTEM_EMERGENCY_TAG, true);
            baby.getPersistentData().putBoolean(VANILLA_TOTEM_RETURN_TAG, true);
            return true;
        }

        // Preserve both hands before the emergency swap. Combat Mode may be
        // holding a weapon, water bucket, lava bucket, shield, etc. Nothing
        // is discarded just to make room for the vanilla totem.
        if (!off.isEmpty()) {
            if (!storeInBabyInventory(baby, off.copy())) return false;
            baby.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
        if (!main.isEmpty()) {
            if (!storeInBabyInventory(baby, main.copy())) return false;
            baby.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        ItemStack vanilla = baby.getBabyInventory().getItem(vanillaSlot);
        if (vanilla.isEmpty() || !vanilla.is(Items.TOTEM_OF_UNDYING)) return false;

        baby.setItemSlot(EquipmentSlot.OFFHAND, vanilla.copyWithCount(1));
        vanilla.shrink(1);
        baby.getBabyInventory().setItem(vanillaSlot, vanilla);

        baby.getPersistentData().putBoolean(VANILLA_TOTEM_EMERGENCY_TAG, true);
        baby.getPersistentData().putBoolean(VANILLA_TOTEM_RETURN_TAG, true);
        return true;
    }

    private static int findVanillaTotem(Container inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    private static boolean storeInBabyInventory(BabyNPCPlayerEntity baby, ItemStack stack) {
        if (stack.isEmpty()) return true;
        Container inv = baby.getBabyInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack existing = inv.getItem(i);
            if (existing.isEmpty()) {
                inv.setItem(i, stack);
                return true;
            }
            if (ItemStack.isSameItemSameTags(existing, stack)) {
                int space = Math.min(existing.getMaxStackSize(), inv.getMaxStackSize()) - existing.getCount();
                if (space >= stack.getCount()) {
                    existing.grow(stack.getCount());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Runs on the next Baby tick after vanilla's death-protection check. If
     * the Baby is still alive and the vanilla totem was consumed, restore the
     * Baby Combat Totem to the offhand. If the hit actually killed the Baby,
     * there is deliberately no recovery path.
     */
    private static void tickVanillaTotemReturn(BabyNPCPlayerEntity baby) {
        CompoundTag data = baby.getPersistentData();
        if (!data.getBoolean(VANILLA_TOTEM_RETURN_TAG)) return;

        data.putBoolean(VANILLA_TOTEM_RETURN_TAG, false);
        if (!baby.isAlive()) {
            data.putBoolean(VANILLA_TOTEM_EMERGENCY_TAG, false);
            return;
        }

        // If the vanilla totem is still in a hand, vanilla did not consume it
        // (for example because another damage hook reduced/cancelled the hit).
        // Put it back into the Baby inventory and restore Combat Mode instead
        // of leaving the emergency swap active.
        ItemStack heldVanilla = baby.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.TOTEM_OF_UNDYING)
                ? baby.getItemBySlot(EquipmentSlot.OFFHAND)
                : baby.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.TOTEM_OF_UNDYING)
                    ? baby.getItemBySlot(EquipmentSlot.MAINHAND)
                    : ItemStack.EMPTY;

        if (!heldVanilla.isEmpty()) {
            storeInBabyInventory(baby, heldVanilla.copy());
            if (baby.getItemBySlot(EquipmentSlot.OFFHAND) == heldVanilla) {
                baby.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            } else if (baby.getItemBySlot(EquipmentSlot.MAINHAND) == heldVanilla) {
                baby.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
        }

        // The vanilla totem is gone after a successful death-protection save.
        // Bring the Baby Combat Totem back into the offhand so the next tick
        // is immediately controlled by Combat Mode again.
        int combatSlot = findCombatTotem(baby.getBabyInventory());
        if (combatSlot >= 0) {
            ItemStack combat = baby.getBabyInventory().getItem(combatSlot);
            ItemStack off = baby.getItemBySlot(EquipmentSlot.OFFHAND);
            if (!off.isEmpty()) {
                if (!storeInBabyInventory(baby, off.copy())) {
                    data.putBoolean(VANILLA_TOTEM_EMERGENCY_TAG, false);
                    return;
                }
            }
            baby.setItemSlot(EquipmentSlot.OFFHAND, combat.copyWithCount(1));
            combat.shrink(1);
            baby.getBabyInventory().setItem(combatSlot, combat);
        }
        data.putBoolean(VANILLA_TOTEM_EMERGENCY_TAG, false);
    }

    private static void playTotemBreakEffect(BabyNPCPlayerEntity baby) {
        if (!(baby.level() instanceof ServerLevel level)) return;
        baby.level().playSound(null, baby.blockPosition(), SoundEvents.TOTEM_USE, baby.getSoundSource(), 1.0F, 1.0F);
        // Vanilla Totem-style burst, using only the Baby Combat particle.
        // The custom particle owns its animation and color; no vanilla particle
        // or unrelated particle system is modified.
        level.sendParticles(
                ModParticles.TOTEM_OF_BABY_COMBAT.get(),
                baby.getX(), baby.getY() + baby.getBbHeight() * 0.35D, baby.getZ(),
                90, 0.55D, 0.55D, 0.55D, 0.20D
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
