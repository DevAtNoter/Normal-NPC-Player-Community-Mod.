package net.devatnoter.normalnpcplayer.entity;
import net.devatnoter.normalnpcplayer.equipment.BabyEquipmentLogic;

import net.minecraft.core.BlockPos;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;

import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;

import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.UUID;
import net.devatnoter.normalnpcplayer.variant.BabySpecialVariantRegistry;
import net.devatnoter.normalnpcplayer.breeding.BabyType;
import net.devatnoter.normalnpcplayer.registry.ModSounds;
import net.devatnoter.normalnpcplayer.item.BabyFoodItem;

public class BabyNPCPlayerEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Float> SYNCED_FOOD_LEVEL =
            SynchedEntityData.defineId(
                    BabyNPCPlayerEntity.class,
                    EntityDataSerializers.FLOAT
            );

    private static final EntityDataAccessor<Integer> SYNCED_TEXTURE_INDEX =
            SynchedEntityData.defineId(
                    BabyNPCPlayerEntity.class,
                    EntityDataSerializers.INT
            );

    private static final EntityDataAccessor<String> SYNCED_TEXTURE_ID =
            SynchedEntityData.defineId(
                    BabyNPCPlayerEntity.class,
                    EntityDataSerializers.STRING
            );
    private static final EntityDataAccessor<String> SYNCED_SPECIAL_VARIANT_ID =
            SynchedEntityData.defineId(
                    BabyNPCPlayerEntity.class,
                    EntityDataSerializers.STRING
            );

    /**
     * Single Source of Truth for this Baby's appearance.
     * The first randomized value is persisted and reused by the carrier Item.
     */
    public static final String TEXTURE_INDEX_TAG = "TextureIndex";
    public static final String TEXTURE_ID_TAG = "TextureId";
    public static final String PROFILE_NAME_TAG = "ProfileName";
    public static final String SPECIAL_VARIANT_TAG = "SpecialVariant";
    public static final int MIN_TEXTURE_INDEX = 1;
    public static final int MAX_TEXTURE_INDEX = 8;
    private static String textureIdFromIndex(int textureIndex) {
        int index = Math.max(
                MIN_TEXTURE_INDEX,
                Math.min(
                        MAX_TEXTURE_INDEX,
                        textureIndex
                )
        );

        return "baby" + index;
    }



    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);


    /**
     * Owner
     */
    private UUID ownerUUID;
    private String ownerName = "";

    /**
     * Immutable identity used only for appearance/profile-based systems.
     * This is captured when the Baby is first created and never overwritten
     * by foster ownership changes.
     */
    private String profileName = "";
    private String specialVariantId = "";

    /**
     * Relationship state.
     *
     * OwnerUUID/OwnerName remain the current legal caregiver/owner.
     * BiologicalParent* preserve the original parent identity when a
     * foster parent takes over.
     *
     * Foster is persisted as exactly 0b/1b in NBT.
     * Orphaned becomes true only when the current parent/caregiver dies.
     */
    public static final String FOSTER_TAG = "Foster";
    public static final String ORPHANED_TAG = "Orphaned";
    public static final String BIOLOGICAL_PARENT_UUID_TAG = "BiologicalParentUUID";
    public static final String BIOLOGICAL_PARENT_NAME_TAG = "BiologicalParentName";

    private boolean foster;
    private boolean orphaned;
    private UUID biologicalParentUUID;
    private String biologicalParentName = "";

    /** Player-bred lineage. Hardcore Babies continue using the legacy single-parent fields above. */
    public static final String BABY_TYPE_TAG = "BabyType";
    public static final String BIRTH_GAME_TIME_TAG = "BirthGameTime";
    public static final String BIOLOGICAL_PARENT_A_UUID_TAG = "BiologicalParentAUUID";
    public static final String BIOLOGICAL_PARENT_A_NAME_TAG = "BiologicalParentAName";
    public static final String BIOLOGICAL_PARENT_B_UUID_TAG = "BiologicalParentBUUID";
    public static final String BIOLOGICAL_PARENT_B_NAME_TAG = "BiologicalParentBName";
    public static final String OWNER_A_UUID_TAG = "OwnerAUUID";
    public static final String OWNER_A_NAME_TAG = "OwnerAName";
    public static final String OWNER_B_UUID_TAG = "OwnerBUUID";
    public static final String OWNER_B_NAME_TAG = "OwnerBName";

    private BabyType babyType = BabyType.HARDCORE;
    private long birthGameTime = -1L;

    /** Player Baby growth state. Growth is time-based and quality-of-care based. */
    public static final String GROWTH_STATE_TAG = "GrowthState";
    public static final String GROWTH_PROGRESS_TAG = "GrowthProgress";
    public static final String HEARTY_TAG = "Hearty";
    public static final String HEARTY_LAST_HEALTH_TAG = "HeartyLastHealth";
    public static final String HEARTY_SAFETY_TICKS_TAG = "HeartySafetyTicks";
    public static final String HEARTY_FEAR_TICKS_TAG = "HeartyFearTicks";
    public static final String DISTRUST_TAG = "Distrust";
    public static final int PLAYER_GROWTH_MIN_TICKS = 72_000;   // 3 Minecraft days
    public static final int PLAYER_GROWTH_MAX_TICKS = 168_000;  // 7 Minecraft days
    public static final int PLAYER_GROWTH_DAY_TICKS = 24_000;
    private String growthState = "growing";
    private double growthProgress;
    private double hearty = 50.0D;
    private float heartyLastHealth = 20.0F;
    private int heartySafetyTicks;
    private int heartyFearTicks;
    /** Player Baby emotional distrust, 0 = fully trusting, 100 = deeply distrustful. */
    private double distrust;

    private UUID biologicalParentAUUID;
    private UUID biologicalParentBUUID;
    private String biologicalParentAName = "";
    private String biologicalParentBName = "";

    private UUID ownerAUUID;
    private UUID ownerBUUID;
    private String ownerAName = "";
    private String ownerBName = "";

    /**
     * Initial spawn ride state.
     *
     * While active:
     * - Baby stays on the owner's head.
     * - Baby cannot take damage.
     * - Baby has no physics/collision.
     * - Baby waits until the owner moves.
     */
    private boolean initialRideActive;
    private double initialRideStartX;
    private double initialRideStartY;
    private double initialRideStartZ;
    /** Fire transfer state while the Baby is carried on the owner's head. */
    private int ownerFireTicksWhileCarried;
    private boolean ownerFireTransferred;
    private boolean lavaFireTransferred;
    /**
     * Vertical clearance above the vehicle's actual top bounding-box face.
     * 0.0D = Baby feet exactly touch the Player's top face.
     */
    public static final double HEAD_RIDE_OFFSET = 0.08D;

    /** Local-space offset relative to the Player's facing direction. */
    public static final double HEAD_RIDE_FORWARD_OFFSET = 0.0D;
    public static final double HEAD_RIDE_SIDE_OFFSET = 0.0D;

    /**
     * Prevents Baby -> Owner -> Baby recursive death.
     */
    private boolean lifeBondDeathTriggered;
    private String textureId = "baby1";
    private boolean itemRenderMode;

    /**
     * Temporary physics state used only while this Baby was thrown from its
     * carried ItemStack. Fall damage is suppressed during this state, while
     * impact damage remains fully capable of killing the Baby.
     */
    private boolean thrownPhysics;
    private boolean thrownPreviousNoAi;
    private UUID thrownByUUID;
    private int thrownTicks;
    private float thrownCharge;
    private float thrownSprintFactor;
    private double thrownLastSpeed;
    private double thrownStartY;
    private double thrownMaxY;

    /**
     * Delayed post-throw recovery scream. The delay is randomized between
     * 1:30 and 2:30 and is only scheduled when the Baby survives the throw.
     */
    private boolean thrownRecoveryScreamPending;
    private int thrownRecoveryScreamTicks;
    private int thrownRecoveryScreamElapsedTicks;
    private int thrownRecoveryScreamBaseTicks;

    /** Server-side GUI state. While the owner has the Baby GUI open, the Baby
     * stands still and faces that owner. This is deliberately separate from
     * the head-riding state. */
    private boolean inventoryOpen;
    private UUID inventoryViewerUUID;

    /** Player-like survival state. Serialized so pickup/place never resets it. */
    public static final String FOOD_LEVEL_TAG = "BabyFoodLevel";
    public static final String SATURATION_LEVEL_TAG = "BabySaturation";
    public static final String EXHAUSTION_LEVEL_TAG = "BabyExhaustion";
    public static final int MAX_HUNGER = 20;
    public static final float MAX_SATURATION = 20.0F;
    public static final int MAX_AIR = 300;
    public static final String TOTAL_XP_TAG = "BabyTotalExperience";
    public static final String EXPERIENCE_LEVEL_TAG = "BabyExperienceLevel";
    private float foodLevel = 20.0F;
    private float saturationLevel = 5.0F;
    private float exhaustionLevel = 0.0F;
    private int totalExperience = 0;
    private int experienceLevel = 0;

    /** Independent Baby game mode. Default is Survival. */
    public static final String BABY_GAME_MODE_TAG = "BabyGameMode";
    private String babyGameMode = "survival";

    /** Actual-position based activity tracking for hunger. */
    private double lastFoodX;
    private double lastFoodY;
    private double lastFoodZ;
    private boolean foodMovementInitialized;
    private boolean foodWasOnGround = true;
    private float lastFoodHealth = 20.0F;
    private boolean sleepingOnBed;

    /**
     * Baby vocalization state. A single cooldown is shared by idle/hungry
     * voices so the two systems can never schedule on top of one another.
     */
    private int voiceCooldown = 0;
    private int nextIdleVoiceTick = 100;
    private static final int VOICE_COOLDOWN_TICKS = 60;
    private static final int IDLE_VOICE_MIN_DELAY = 180;
    private static final int IDLE_VOICE_MAX_DELAY = 360;
    private static final float HUNGRY_VOICE_CHANCE = 0.25F;

    /** Nine-slot Baby inventory, independent from the player inventory. */
    private final SimpleContainer babyInventory = new SimpleContainer(9);

    public Container getBabyInventory() {
        return babyInventory;
    }

    public BabyNPCPlayerEntity(
            EntityType<? extends PathfinderMob> entityType,
            Level level
    ) {
        super(entityType, level);

        /*
         * Real Baby creation gets a fresh random variant.
         * Item placement restores the saved value immediately after
         * EntityType.create(), so variants remain unique/persistent.
         */
        int initialTextureIndex =
                this.random.nextInt(
                        MAX_TEXTURE_INDEX
                ) + MIN_TEXTURE_INDEX;

        this.textureId =
                textureIdFromIndex(
                        initialTextureIndex
                );

        this.entityData.set(
                SYNCED_TEXTURE_INDEX,
                initialTextureIndex
        );
        this.lastFoodX = getX();
        this.lastFoodY = getY();
        this.lastFoodZ = getZ();
        this.lastFoodHealth = getHealth();
    }


    /**
     * Attributes
     */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D);
    }

    /**
     * AI Goals
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));

        // Threat response has higher priority than normal following/wandering.
        // The Baby always tries to get back to its owner while a hostile mob
        // is close, and remains in the escape state until the threat has
        // cleared from its safety radius.
        goalSelector.addGoal(1, new EscapeHostileGoal(this, 1.55D, 12.0D, 16.0D));

        /*
         * Normal behavior:
         *
         *  - Baby may wander in an 8-12 block area around the owner.
         *  - Once the owner is more than 13 blocks away, follow starts.
         *  - Follow stops at 1 block.
         *  - >20 blocks is an emergency teleport only.
         */
        goalSelector.addGoal(
                2,
                new FollowOwnerGoal(this, 1.25D, 13.0D, 1.0D)
        );

        goalSelector.addGoal(3, new OrphanFollowPlayerGoal(this, 0.95D));
        goalSelector.addGoal(4, new BabyFoodTemptGoal(this, 1.15D));

        goalSelector.addGoal(5, new BabyFoodPickupGoal(this));

        goalSelector.addGoal(
                6,
                new OwnerAreaWanderGoal(this, 1.0D, 8.0D, 13.0D)
        );

        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();

        entityData.define(
                SYNCED_TEXTURE_INDEX,
                1
        );
        entityData.define(
                SYNCED_TEXTURE_ID,
                "baby1"
        );
        entityData.define(
                SYNCED_SPECIAL_VARIANT_ID,
                ""
        );
        entityData.define(
                SYNCED_FOOD_LEVEL,
                20.0F
        );
    }

    /**
     * The client renderer reads the same synchronized value that the server
     * writes. This prevents the constructor's random variant from surviving
     * on the client after an ItemStack is placed.
     */
    @Override
    public void onSyncedDataUpdated(
            EntityDataAccessor<?> key
    ) {
        super.onSyncedDataUpdated(key);

        if (SYNCED_TEXTURE_INDEX.equals(key)) {
            if (specialVariantId == null || specialVariantId.isBlank()) {
                this.textureId =
                        textureIdFromIndex(
                                entityData.get(
                                        SYNCED_TEXTURE_INDEX
                                )
                        );
            }
        } else if (SYNCED_TEXTURE_ID.equals(key)) {
            this.textureId = entityData.get(SYNCED_TEXTURE_ID);
        }
    }

    @Override
    public void tick() {
        double previousThrownSpeed = thrownPhysics
                ? getDeltaMovement().length()
                : 0.0D;

        // Keep the Baby as a normal Mob during a throw. Only the owner
        // emergency-teleport rule is suppressed below while thrownPhysics is true.
        super.tick();

        if (thrownPhysics) {
            tickThrownPhysics(previousThrownSpeed);
        }

        tickThrownSuffer();

        // Baby has a player-like XP system: nearby XP orbs are magnetized
        // toward the Baby and absorbed when they reach it.
        if (!level().isClientSide) {
            tickExperienceOrbs();
            BabyEquipmentLogic.tickEntity(this);
            net.devatnoter.normalnpcplayer.growth.PlayerBabyGrowthManager.tick(this);
        }
        BabyEquipmentLogic.tickEntityPassiveEffects(this);

        if (inventoryOpen && !level().isClientSide) {
            ServerPlayer viewer = null;
            if (level() instanceof ServerLevel serverLevel && inventoryViewerUUID != null) {
                viewer = serverLevel.getServer().getPlayerList().getPlayer(inventoryViewerUUID);
            }

            if (viewer == null || !viewer.isAlive() || viewer.distanceToSqr(this) > 64.0D) {
                inventoryOpen = false;
                inventoryViewerUUID = null;
            } else {
                getNavigation().stop();
                setDeltaMovement(Vec3.ZERO);
                fallDistance = 0.0F;

                double dx = viewer.getX() - getX();
                double dz = viewer.getZ() - getZ();
                if (dx * dx + dz * dz > 0.0001D) {
                    float facing = (float)(Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
                    setYRot(facing);
                    yRotO = facing;
                    setYBodyRot(facing);
                    yBodyRotO = facing;
                    setYHeadRot(facing);
                    yHeadRotO = facing;
                }
            }
        }

        if (level().isClientSide) {
            foodLevel = entityData.get(SYNCED_FOOD_LEVEL);
        }

        if (!level().isClientSide && !initialRideActive) {
            tickSurvivalNeeds();
        }

        if (!level().isClientSide) {
            tickVocalization();
            if (orphaned && tickCount % 100 == 0 && random.nextFloat() < 0.45F && level() instanceof ServerLevel serverLevel) {
                playOrphanParticles(serverLevel, this);
            }
        }

        if (initialRideActive) {
            if (!isPassenger() || !(getVehicle() instanceof ServerPlayer owner) || owner.level() != level()) {
                clearInitialRideState();
            } else {
                // The initial protection lasts only until the player actually
                // moves from the position at which the Baby was born. Rotation
                // alone does not count as movement; walking, strafing, jumping,
                // falling, knockback, or any other positional displacement does.
                double dx = owner.getX() - initialRideStartX;
                double dy = owner.getY() - initialRideStartY;
                double dz = owner.getZ() - initialRideStartZ;
                if (dx * dx + dy * dy + dz * dz > 0.0004D) {
                    clearInitialRideState();
                } else {
                    // Keep only the gameplay protections that existed for the
                    // initial head ride. Positioning is owned entirely by the
                    // real Entity passenger pipeline (rideTick ->
                    // vehicle.positionRider), so there is no teleport/follow loop.
                    tickCarriedFireTransfer(owner);
                    setNoGravity(true);
                    setInvulnerable(true);
                    setDeltaMovement(Vec3.ZERO);
                    fallDistance = 0.0F;
                    return;
                }
            }
        }

        if (!level().isClientSide && !thrownPhysics) {
            // Mirror vanilla player-target semantics instead of forcing every
            // hostile mob in range to attack the Baby. The Baby is only eligible
            // when that mob would naturally consider a player a valid target.
            if (tickCount % 5 == 0 && !initialRideActive) {
                updatePlayerLikeHostility();
            }

            ServerPlayer owner = getBehaviorOwner();

            if (owner != null
                    && owner.level() == level()
                    && distanceToSqr(owner)
                    > 20.0D * 20.0D) {
                teleportNearOwner(owner);
            }
        }
    }

    /**
     * Gives the Baby player-like mob hostility without turning every hostile
     * creature in the area into a forced target. Vanilla special cases are
     * preserved: Endermen require the Baby to stare at them, Piglins respect
     * gold armor, and neutral mobs do not become hostile merely because the
     * Baby exists nearby.
     */
    private void updatePlayerLikeHostility() {
        var nearby = level().getEntitiesOfClass(
                Mob.class,
                getBoundingBox().inflate(32.0D),
                mob -> mob.isAlive()
                        && mob != this
                        && isPlayerLikeThreat(mob)
                        && mob.hasLineOfSight(this)
                        && mob.canAttack(this)
        );

        for (Mob mob : nearby) {
            double followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
            double maxRange = Math.min(32.0D, Math.max(16.0D, followRange));
            if (mob.distanceToSqr(this) > maxRange * maxRange) continue;

            LivingEntity current = mob.getTarget();
            if (current == this) continue;

            // Preserve vanilla-style nearest-target behavior. Do not overwrite
            // a mob's closer valid target merely because the Baby is nearby.
            if (current != null
                    && current.isAlive()
                    && mob.distanceToSqr(current) <= mob.distanceToSqr(this)) {
                continue;
            }

            mob.setTarget(this);
        }
    }

    private boolean isPlayerLikeThreat(Mob mob) {
        if (mob instanceof EnderMan enderman) {
            return isLookingAtEnderman(enderman);
        }

        if (mob instanceof Piglin piglin) {
            return !PiglinAi.isWearingGold(this);
        }

        if (mob instanceof PiglinBrute) {
            return true;
        }

        // Zombified piglins, endermen, and other neutral mobs keep their
        // vanilla retaliation/provocation rules rather than becoming hostile
        // just because a Baby exists.
        if (mob instanceof NeutralMob) {
            return false;
        }

        return mob instanceof Enemy;
    }

    private boolean isLookingAtEnderman(EnderMan enderman) {
        // Match vanilla's pumpkin protection for player-like entities.
        ItemStack helmet = getItemBySlot(EquipmentSlot.HEAD);
        if (!helmet.isEmpty() && helmet.is(Items.CARVED_PUMPKIN)) {
            return false;
        }

        Vec3 look = getViewVector(1.0F).normalize();
        Vec3 toEnderman = new Vec3(
                enderman.getX() - getX(),
                (enderman.getBoundingBox().minY + enderman.getBbHeight() * 0.5D)
                        - getEyeY(),
                enderman.getZ() - getZ()
        );

        double distance = toEnderman.length();
        if (distance <= 0.0001D || distance > 64.0D) return false;

        toEnderman = toEnderman.normalize();
        double dot = look.dot(toEnderman);
        return dot > 1.0D - 0.025D / distance
                && hasLineOfSight(enderman);
    }

    private boolean hasNearbyGoldenMilkFood(double radius) {
        // Golden milk must never make the Baby abandon danger merely because a
        // second bottle is nearby. It is only a valid target when the previous
        // absorption protection has been consumed/expired or the Baby has
        // actually lost HP.
        if (!canConsumeGoldenMilk()) {
            return false;
        }

        return !level().getEntitiesOfClass(
                ItemEntity.class,
                getBoundingBox().inflate(radius),
                item -> item.isAlive()
                        && item.getItem().getItem() instanceof BabyFoodItem food
                        && (food.kind() == BabyFoodItem.Kind.GOLDEN_MILK_BOTTLE
                        || food.kind() == BabyFoodItem.Kind.ENCHANTED_GOLDEN_MILK_BOTTLE)
        ).isEmpty();
    }

    /**
     * Autonomous golden-milk decision gate. After drinking a golden milk
     * bottle, the Baby does not immediately consume another one while its
     * absorption shield is still active and its HP is still full. A new bottle
     * becomes valid once the Baby has actually lost HP or the absorption shield
     * has reached zero.
     */
    public boolean canConsumeGoldenMilk() {
        return getHealth() < getMaxHealth()
                || getAbsorptionAmount() <= 0.0F;
    }

    private boolean hasNearbyHostile(double radius) {
        return !level().getEntitiesOfClass(
                Mob.class,
                getBoundingBox().inflate(radius),
                mob -> mob.isAlive()
                        && mob != this
                        && isPlayerLikeThreat(mob)
                        && mob.canAttack(this)
                        && mob.hasLineOfSight(this)
        ).isEmpty();
    }


    /**
     * Feed the Baby only with a Milk Bucket. Empty-hand interactions are left
     * to the normal pickup/care interaction instead of feeding implicitly.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player == null) {
            return InteractionResult.PASS;
        }

        var stack = player.getItemInHand(hand);

        // Shift + right-click with an empty hand is reserved for putting the
        // Baby on the owner's head. Handle it before every other interaction
        // so it cannot fall through to the normal pickup/care interaction.
        if (stack.isEmpty() && player.isShiftKeyDown()) {
            // Shift + empty-hand right-click is exclusively the head-ride action.
            // Only the Baby owner may mount it; return SUCCESS so the interaction
            // cannot fall through into the normal pickup/care path.
            boolean canMount = player.getUUID().equals(ownerUUID);
            if (babyType == BabyType.PLAYER) {
                canMount = canMount
                        || player.getUUID().equals(biologicalParentAUUID)
                        || player.getUUID().equals(biologicalParentBUUID);
            }

            if (canMount) {
                if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                    mountOnHead(serverPlayer);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }

        if (!BabyFoodItem.isBabyFood(stack) && !stack.is(Items.MILK_BUCKET)) {
            return super.mobInteract(player, hand);
        }

        if (!level().isClientSide) {
            if (BabyFoodItem.isBabyFood(stack)) {
                if (!consumeBabyFoodStack(stack, player, hand)) {
                    return InteractionResult.PASS;
                }
            } else {
                if (!feedMilk()) {
                    return InteractionResult.PASS;
                }
                recordFedByPlayer(player);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        player.setItemInHand(hand, new ItemStack(Items.BUCKET));
                    } else {
                        player.getInventory().placeItemBackInInventory(new ItemStack(Items.BUCKET));
                    }
                }
            }
        }

        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    private boolean consumeBabyFoodStack(ItemStack stack, Player player, InteractionHand hand) {
        if (!BabyFoodItem.isBabyFood(stack)) {
            return false;
        }
        BabyFoodItem.FoodData food = BabyFoodItem.getFoodData(stack);
        if (!foodFeed(food)) {
            return false;
        }
        if (!level().isClientSide) {
            recordFedByPlayer(player);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            ItemStack empty = food.emptyReturn().copy();
            if (!empty.isEmpty()) {
                player.getInventory().placeItemBackInInventory(empty);
            }
        }
        return true;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean accepted = super.hurt(source, amount);
        if (accepted && babyType == BabyType.PLAYER) {
            net.minecraft.world.entity.Entity attacker = source.getEntity();
            if (attacker instanceof Player) {
                // Being intentionally/accidentally hurt by a player creates a
                // lasting distrust response while Hearty handles the immediate
                // emotional/safety penalty separately.
                addDistrust(Math.min(20.0D, Math.max(5.0D, amount * 4.0D)));
            }
        }
        return accepted;
    }

    /**
     * Records only deliberate player feeding. Autonomous Baby food pickup does
     * not call this method, so the history represents actual player care.
     */
    private void recordFedByPlayer(Player player) {
        if (player == null || level().isClientSide) return;

        if (babyType == BabyType.PLAYER) {
            // Deliberate feeding is a strong trust-building interaction.
            addDistrust(-2.0D);
        }

        ListTag history = getOrCreateFedByPlayers();
        String uuid = player.getUUID().toString();
        String name = player.getGameProfile().getName();

        for (int i = 0; i < history.size(); i++) {
            CompoundTag entry = history.getCompound(i);
            if (uuid.equals(entry.getString("UUID"))) {
                entry.putString("Name", name);
                entry.putInt("Times", entry.getInt("Times") + 1);
                return;
            }
        }

        CompoundTag entry = new CompoundTag();
        entry.putString("UUID", uuid);
        entry.putString("Name", name);
        entry.putInt("Times", 1);
        history.add(entry);
    }

    private ListTag getOrCreateFedByPlayers() {
        if (fedByPlayers == null) {
            fedByPlayers = new ListTag();
        }
        return fedByPlayers;
    }

    /** Persisted history of deliberate player feeding. */
    private ListTag fedByPlayers = new ListTag();

    public ListTag getFedByPlayers() {
        return (ListTag) getOrCreateFedByPlayers().copy();
    }

    /**
     * Records a player who actually killed a hostile mob that was visible to
     * this Baby. The event-side visibility/fairness checks are performed by
     * BabyProtectorEvents before this method is called.
     */
    public void recordProtector(Player player) {
        if (player == null || level().isClientSide) return;

        ListTag history = getOrCreateProtectors();
        String uuid = player.getUUID().toString();
        String name = player.getGameProfile().getName();

        for (int i = 0; i < history.size(); i++) {
            CompoundTag entry = history.getCompound(i);
            if (uuid.equals(entry.getString("UUID"))) {
                entry.putString("Name", name);
                entry.putInt("Times", entry.getInt("Times") + 1);
                return;
            }
        }

        CompoundTag entry = new CompoundTag();
        entry.putString("UUID", uuid);
        entry.putString("Name", name);
        entry.putInt("Times", 1);
        history.add(entry);
    }

    private ListTag getOrCreateProtectors() {
        if (protectors == null) {
            protectors = new ListTag();
        }
        return protectors;
    }

    /** Persisted history of fair protector credit. */
    private ListTag protectors = new ListTag();

    public ListTag getProtectors() {
        return (ListTag) getOrCreateProtectors().copy();
    }

    /**
     * A mob is protectable only when it follows the same player-like threat
     * rules used by the Baby's hostility system and can actually attack it.
     */
    public boolean isProtectableThreat(Mob mob) {
        if (mob == null || mob == this || !mob.isAlive()) return false;
        if (!isPlayerLikeThreat(mob)) return false;
        return mob.canAttack(this);
    }

    /**
     * The Baby must be able to directly see the entity at the moment of the
     * kill. The same 32-block gameplay horizon used by the Baby's hostile
     * detection is used here; this is deliberately not an arbitrary global
     * server radius.
     */
    public boolean canSeeForProtection(LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity.level() != level()) return false;
        double maxDistance = 32.0D;
        return distanceToSqr(entity) <= maxDistance * maxDistance
                && hasLineOfSight(entity);
    }

    /**
     * Persist the protector history with the normal LivingEntity save path.
     */

    private boolean foodFeed(BabyFoodItem.FoodData food) {
        float before = getFoodLevelExact();
        float nutrition = Math.min(food.food(), 20.0F - before);

        // Golden milk remains consumable even when Hunger is already full,
        // because its Baby-only effects/absorption are separate benefits.
        // Ordinary Milk Bottle still requires room for food.
        boolean hasExtraBenefit = food.absorption() > 0.0F || !food.effects().isEmpty();
        if (nutrition <= 0.0F && !hasExtraBenefit) return false;

        if (nutrition > 0.0F) {
            float saturation = Math.min(food.saturation(), nutrition);
            addFood(nutrition, saturation / Math.max(0.0001F, nutrition * 2.0F));
        }

        boolean changed = nutrition > 0.0F;
        if (food.absorption() > 0.0F) {
            float oldAbsorption = getAbsorptionAmount();
            float newAbsorption = Math.max(oldAbsorption, food.absorption());
            setAbsorptionAmount(newAbsorption);
            changed |= newAbsorption > oldAbsorption;
        }
        if (!food.effects().isEmpty()) {
            food.effects().forEach(effect -> addEffect(new net.minecraft.world.effect.MobEffectInstance(effect)));
            changed = true;
        }
        if (!changed) return false;
        playMilkFeedEffects();
        return true;
    }


    /**
     * Start the initial "sat on head" state.
     */
    /**
     * First-child Hardcore head ride.
     *
     * Called only by the first-child creation path.
     *
     * Player is not made generally rideable. The mod invokes Entity's
     * addPassenger directly for this one Baby relationship.
     */
    /**
     * Start the automatic first-child head state.
     * Used only by the first-child Hardcore creation path.
     */
    /**
     * Initial Hardcore first-child head state.
     *
     * This uses a mod-owned attachment rather than making Player a vanilla
     * network vehicle. It therefore cannot produce Invalid player data.
     */
    /**
     * Transfers environmental fire from the owner to a Baby that is currently
     * being carried on the owner's head. This is intentionally isolated from
     * the normal damage/AI systems: the Baby is merely marked as burning while
     * carried, and normal fire damage begins only after it is placed down.
     *
     * Rules:
     * - Any direct owner lava exposure ignites the Baby immediately.
     * - Any owner burning state ignites the Baby immediately.
     * - A continuous exposure does not reset the transferred fire every tick.
     */
    private void tickCarriedFireTransfer(ServerPlayer owner) {
        if (owner == null || !initialRideActive || level().isClientSide) {
            return;
        }

        // The Baby is physically on the owner's head, so exposure must be
        // derived from the owner's actual environment, not from the owner's
        // fire-duration or oxygen timers. Any direct lava contact or burning
        // immediately transfers the environmental hazard to the Baby.
        boolean ownerInLava = isOwnerExposedToLava(owner);
        if (ownerInLava) {
            if (!lavaFireTransferred) {
                igniteFromOwnerFire();
                lavaFireTransferred = true;
            }
        } else {
            lavaFireTransferred = false;
        }

        boolean ownerBurning = owner.isOnFire();
        if (ownerBurning) {
            if (!ownerFireTransferred) {
                igniteFromOwnerFire();
                ownerFireTransferred = true;
            }
        } else {
            ownerFireTicksWhileCarried = 0;
            ownerFireTransferred = false;
        }
    }

    private void igniteFromOwnerFire() {
        // Fire Resistance belongs to the Baby itself. The owner's resistance
        // does not shield the Baby from transferred fire/lava exposure.
        if (hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return;
        }

        // Keep an existing longer burn and only extend/ignite when needed.
        setSecondsOnFire(Math.max(5, getRemainingFireTicks() / 20));
    }

    /**
     * Checks the owner's actual bounding-box footprint and finds the highest
     * lava surface intersecting the player's body. The Baby ignites only when
     * that surface is above half of the owner's body height, avoiding a false
     * trigger from merely touching a shallow lava edge with the feet.
     */
    private boolean isOwnerExposedToLava(ServerPlayer owner) {
        if (owner == null) {
            return false;
        }

        // isInLava() covers the normal player-in-lava case. Fluid height also
        // catches partial body/limb contact at the edge of a lava source where
        // the player is not considered deeply submerged yet.
        return owner.isInLava()
                || owner.getFluidHeight(FluidTags.LAVA) > 0.0D
                || isOwnerMoreThanHalfSubmergedInLava(owner);
    }

    private boolean isOwnerMoreThanHalfSubmergedInLava(ServerPlayer owner) {
        double minX = owner.getBoundingBox().minX;
        double maxX = owner.getBoundingBox().maxX;
        double minY = owner.getBoundingBox().minY;
        double maxY = owner.getBoundingBox().maxY;
        double halfBodyY = minY + (maxY - minY) * 0.5D;

        int minBlockX = net.minecraft.core.BlockPos.containing(minX, minY, owner.getBoundingBox().minZ).getX();
        int maxBlockX = net.minecraft.core.BlockPos.containing(maxX, minY, owner.getBoundingBox().maxZ).getX();
        int minBlockZ = net.minecraft.core.BlockPos.containing(minX, minY, owner.getBoundingBox().minZ).getZ();
        int maxBlockZ = net.minecraft.core.BlockPos.containing(maxX, minY, owner.getBoundingBox().maxZ).getZ();
        int minBlockY = net.minecraft.core.BlockPos.containing(minX, minY, owner.getBoundingBox().minZ).getY();
        double maxZ = owner.getBoundingBox().maxZ;
        int maxBlockY = net.minecraft.core.BlockPos.containing(maxX, maxY, maxZ).getY();

        double highestLavaSurface = Double.NEGATIVE_INFINITY;

        for (int x = minBlockX; x <= maxBlockX; x++) {
            for (int z = minBlockZ; z <= maxBlockZ; z++) {
                for (int y = minBlockY; y <= maxBlockY; y++) {
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
                    net.minecraft.world.level.material.FluidState fluid = level().getFluidState(pos);
                    if (!fluid.is(FluidTags.LAVA)) {
                        continue;
                    }

                    double surfaceY = y + fluid.getHeight(level(), pos);
                    if (surfaceY > highestLavaSurface) {
                        highestLavaSurface = surfaceY;
                    }
                }
            }
        }

        return highestLavaSurface > halfBodyY;
    }

    /**
     * Mount this Baby on a Player using the real Entity passenger pipeline.
     * Manual mounts intentionally do not enable initial-child invulnerability.
     */
    public boolean mountOnHead(ServerPlayer player) {
        if (player == null || isRemoved()) {
            return false;
        }

        if (getVehicle() == player) {
            return true;
        }

        if (!startRiding(player, false)) {
            return false;
        }

        if (!level().isClientSide) {
            player.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(player)
            );
        }

        return getVehicle() == player;
    }

    public void beginInitialRide(ServerPlayer player) {
        if (!mountOnHead(player)) {
            return;
        }

        initialRideActive = true;
        initialRideStartX = player.getX();
        initialRideStartY = player.getY();
        initialRideStartZ = player.getZ();
        ownerFireTicksWhileCarried = 0;
        ownerFireTransferred = false;
        lavaFireTransferred = false;
        setInvulnerable(true);
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0.0F;
    }


    /**
     * Damage the Baby's equipped armor exactly through the vanilla LivingEntity
     * armor-durability path.  The Baby uses real EquipmentSlot stacks, so the
     * armor must lose durability whenever normal damage reaches hurtArmor().
     */
    @Override
    protected void hurtArmor(
            net.minecraft.world.damagesource.DamageSource source,
            float amount
    ) {
        if (amount <= 0.0F || source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }

        int durabilityDamage = (int) (amount * 4.0F);
        if (durabilityDamage <= 0) {
            return;
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) {
                continue;
            }

            ItemStack stack = getItemBySlot(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) {
                continue;
            }

            stack.hurtAndBreak(
                    durabilityDamage,
                    this,
                    entity -> entity.broadcastBreakEvent(slot)
            );

            setItemSlot(slot, stack);
        }
    }


    /**
     * Starts the temporary thrown-physics state.
     *
     * The Baby is still a normal LivingEntity: it can receive ordinary damage
     * and die. Only fall damage caused by the ballistic flight is protected.
     */
    public void beginThrownPhysics(
            UUID thrower,
            Vec3 initialVelocity,
            float charge,
            double sprintFactor
    ) {
        this.thrownPhysics = true;
        interruptBabyVoiceAndReschedule();
        // Do not disable AI for throwing. Gravity and normal Mob processing
        // must continue; only the owner emergency-teleport rule is suspended
        // while thrownPhysics is true.
        this.thrownByUUID = thrower;
        this.thrownTicks = 0;
        this.thrownCharge = Math.max(0.0F, Math.min(1.0F, charge));
        this.thrownSprintFactor = (float)Math.max(0.0D, Math.min(1.0D, sprintFactor));
        this.thrownLastSpeed = initialVelocity.length();
        this.thrownStartY = getY();
        this.thrownMaxY = getY();
        this.thrownRecoveryScreamPending = false;
        this.thrownRecoveryScreamTicks = 0;
        this.thrownRecoveryScreamElapsedTicks = 0;
        this.thrownRecoveryScreamBaseTicks = 0;

        this.initialRideActive = false;
        this.setInvulnerable(false);
        this.setNoGravity(false);
        this.noPhysics = false;
        this.fallDistance = 0.0F;
    }

    public boolean isThrownPhysics() {
        return thrownPhysics;
    }

    private void tickThrownPhysics(double previousSpeed) {
        if (level().isClientSide) {
            return;
        }

        thrownTicks++;

        Vec3 velocity = getDeltaMovement();
        double speed = velocity.length();
        this.thrownMaxY = Math.max(this.thrownMaxY, getY());

        // Fluids are treated as a landing surface. Resolve this before the
        // tumble so entering water/lava/bubble columns stops rotation at once.
        if (isInWaterOrBubble() || isInLava()) {
            // Hand the entity to normal fluid physics without cancelling the
            // incoming velocity. This prevents the Baby from "snapping" to a
            // floating state and lets gravity + water drag make it sink/slow
            // naturally after entering the fluid.
            fallDistance = 0.0F;
            endThrownPhysics();
            return;
        }

        // Tumble only while airborne. Rotation is advanced from the current
        // entity rotation and wrapped through Minecraft's Mth implementation.
        float spin = (float)(12.0D + (18.0D * Math.max(0.0F, thrownCharge)));
        float nextX = getXRot() + spin;
        float nextY = getYRot() + spin * 0.72F;
        setXRot(Mth.wrapDegrees(nextX));
        setYRot(Mth.wrapDegrees(nextY));
        setYHeadRot(getYRot());
        setYBodyRot(getYRot());

        // Any actual block/ground collision ends the throw. Impact damage is
        // applied only when there was meaningful incoming speed.
        if (horizontalCollision || verticalCollision || onGround()) {
            if (previousSpeed > 0.20D) {
                applyThrowImpactDamage(previousSpeed);
            }
            setDeltaMovement(Vec3.ZERO);
            fallDistance = 0.0F;
            endThrownPhysics();
            return;
        }

        // Also catch entity-to-entity impacts where block collision is false.
        AABB impactBox = getBoundingBox().inflate(0.12D);
        var hit = level().getEntities(
                this,
                impactBox,
                entity -> entity.isAlive()
                        && entity != getVehicle()
                        && entity instanceof LivingEntity
                        && (thrownByUUID == null
                        || !thrownByUUID.equals(entity.getUUID())
                        || thrownTicks > 5)
        );

        if (!hit.isEmpty()) {
            if (previousSpeed > 0.20D) {
                applyThrowImpactDamage(previousSpeed);
            }
            setDeltaMovement(Vec3.ZERO);
            fallDistance = 0.0F;
            endThrownPhysics();
            return;
        }

        thrownLastSpeed = speed;

        // Safety exit for an impossible/stale physics state.
        if (thrownTicks > 20 * 20) {
            setDeltaMovement(Vec3.ZERO);
            fallDistance = 0.0F;
            endThrownPhysics();
        }
    }

    /**
     * Single exit point for thrown physics. Every landing/impact/fluid/timeout
     * path restores the exact AI state that existed before the throw.
     */
    private void endThrownPhysics() {
        boolean wasThrown = thrownPhysics;
        thrownPhysics = false;
        thrownByUUID = null;
        thrownLastSpeed = 0.0D;
        thrownTicks = 0;
        thrownStartY = getY();
        thrownMaxY = getY();
        fallDistance = 0.0F;

        // AI was never disabled by the throw state, so there is nothing to
        // restore here. The normal owner teleport rule resumes automatically
        // because tick() checks !thrownPhysics.

        scheduleThrownSufferIfAlive();
    }

    private void scheduleThrownSufferIfAlive() {
        if (level().isClientSide || !isAlive()) {
            thrownRecoveryScreamPending = false;
            thrownRecoveryScreamTicks = 0;
            thrownRecoveryScreamElapsedTicks = 0;
            thrownRecoveryScreamBaseTicks = 0;
            return;
        }

        /*
         * This is NOT a recovery/healing system. The Baby's existing food
         * simulation remains the only system that restores HP. This timer only
         * decides when the post-throw suffer voice happens.
         *
         * The initial timer is random between 1:30 and 2:30. As the existing
         * food system heals the Baby, the remaining timer is shortened from
         * that already-selected duration. HP does not get changed here.
         */
        thrownRecoveryScreamPending = true;
        thrownRecoveryScreamBaseTicks =
                20 * 90 + getRandom().nextInt(20 * 61);
        thrownRecoveryScreamElapsedTicks = 0;
        thrownRecoveryScreamTicks = thrownRecoveryScreamBaseTicks;
    }

    private int getHealthAdjustedScreamDuration() {
        float maxHealth = Math.max(1.0F, getMaxHealth());
        float healthRatio = Math.max(
                0.0F,
                Math.min(1.0F, getHealth() / maxHealth)
        );

        // At <=50% HP, keep the complete originally-randomized 90-150 sec
        // suffer voice window. From 50% to 90%+, progressively shorten it.
        float nearFull = (healthRatio - 0.50F) / 0.40F;
        nearFull = Math.max(0.0F, Math.min(1.0F, nearFull));

        // Near/full HP can bring the suffer voice timer down substantially, but the
        // value is still derived from the original random 90-150 sec timer.
        // This avoids introducing a second independent random timer every tick.
        float durationMultiplier = 1.0F - (0.75F * nearFull);
        return Math.max(20, Math.round(thrownRecoveryScreamBaseTicks * durationMultiplier));
    }

    private void tickThrownSuffer() {
        if (level().isClientSide || !thrownRecoveryScreamPending) {
            return;
        }

        if (!isAlive()) {
            thrownRecoveryScreamPending = false;
            thrownRecoveryScreamTicks = 0;
            thrownRecoveryScreamElapsedTicks = 0;
            thrownRecoveryScreamBaseTicks = 0;
            return;
        }

        /*
         * Health is checked every tick. If the existing food/healing system
         * raises HP, the target suffer voice duration becomes shorter immediately.
         * If HP drops again, an already-shortened timer is never extended.
         */
        thrownRecoveryScreamElapsedTicks++;
        int targetDuration = getHealthAdjustedScreamDuration();
        thrownRecoveryScreamTicks = Math.max(0, targetDuration - thrownRecoveryScreamElapsedTicks);

        if (thrownRecoveryScreamTicks <= 0) {
            interruptBabyVoiceAndReschedule();
            playSound(
                    ModSounds.BABY_SUFFER.get(),
                    1.0F,
                    0.92F + getRandom().nextFloat() * 0.16F
            );
            thrownRecoveryScreamPending = false;
            thrownRecoveryScreamTicks = 0;
            thrownRecoveryScreamElapsedTicks = 0;
            thrownRecoveryScreamBaseTicks = 0;
        }
    }

    private void applyThrowImpactDamage(double impactSpeed) {
        /*
         * Landing damage is deliberately tied to BOTH the height actually
         * fallen and the remaining impact velocity. The old implementation
         * only used a small kinetic-energy coefficient, which made a hard
         * throw/landing feel far too soft.
         *
         * Vanilla-style fall damage starts after roughly 3 blocks of fall.
         * For a thrown Baby we additionally retain the vertical distance from
         * the highest point reached during the throw and add a velocity-based
         * impact component. This is still one damage event, so there is no
         * double-hit from fall damage + throw damage.
         */
        double heightDrop = Math.max(0.0D, thrownMaxY - getY());
        double effectiveFall = Math.max(0.0D, heightDrop - 3.0D);

        // Scale with the actual impact speed. This keeps wall/low-height hits
        // meaningful while making high-speed landings substantially stronger.
        double speedExcess = Math.max(0.0D, impactSpeed - 0.35D);
        double velocityDamage = speedExcess * speedExcess * 5.0D;

        // A 1-block fall should not be lethal by itself, but a fast throw can
        // still hurt. Height damage follows the vanilla fall-damage shape.
        double heightDamage = effectiveFall * 1.0D;
        // Keep the final damage as a whole number. Minecraft's DamageSource
        // accepts floats internally, but this mechanic intentionally deals
        // integer damage values so the result never becomes e.g. 3.47 damage.
        int damage = (int)Math.round(Math.max(0.0D, Math.min(40.0D,
                heightDamage + velocityDamage)));

        if (damage > 0) {
            hurt(level().damageSources().fall(), (float)damage);
        }
    }

    /**
     * Thrown flight itself never produces vanilla fall damage. Impact damage
     * above is separate and can kill the Baby normally.
     */
    @Override
    public boolean causeFallDamage(
            float fallDistance,
            float damageMultiplier,
            net.minecraft.world.damagesource.DamageSource source
    ) {
        if (thrownPhysics) {
            this.fallDistance = 0.0F;
            return false;
        }

        return super.causeFallDamage(
                fallDistance,
                damageMultiplier,
                source
        );
    }

    /**
     * Absolutely prevent damage while the baby is still mounted.
     */
    @Override
    public boolean isInvulnerableTo(
            net.minecraft.world.damagesource.DamageSource source
    ) {
        return initialRideActive || super.isInvulnerableTo(source);
    }

    /**
     * Find a safe position around the owner for the baby to dismount.
     */
    private Vec3 findSafeDismountPosition(ServerPlayer player) {
        int baseX = player.blockPosition().getX();
        int baseY = player.blockPosition().getY();
        int baseZ = player.blockPosition().getZ();

        int[][] offsets = {
                {0, 0},
                {1, 0},
                {-1, 0},
                {0, 1},
                {0, -1},
                {1, 1},
                {1, -1},
                {-1, 1},
                {-1, -1}
        };

        for (int[] offset : offsets) {
            int x = baseX + offset[0];
            int z = baseZ + offset[1];
            int y = baseY;

            if (isSafeLandingSpot(x, y, z)) {
                return new Vec3(
                        x + 0.5D,
                        y,
                        z + 0.5D
                );
            }

            if (isSafeLandingSpot(x, y - 1, z)) {
                return new Vec3(
                        x + 0.5D,
                        y - 1,
                        z + 0.5D
                );
            }
        }

        return null;
    }

    /**
     * Teleport the baby to a safe location near the owner.
     */
    private void teleportNearOwner(ServerPlayer owner) {
        int baseX = owner.blockPosition().getX();
        int baseY = owner.blockPosition().getY();
        int baseZ = owner.blockPosition().getZ();

        int[][] offsets = {
                {2, 0},
                {-2, 0},
                {0, 2},
                {0, -2},
                {2, 2},
                {2, -2},
                {-2, 2},
                {-2, -2},
                {3, 0},
                {-3, 0},
                {0, 3},
                {0, -3}
        };

        for (int[] offset : offsets) {
            int x = baseX + offset[0];
            int z = baseZ + offset[1];

            if (isSafeLandingSpot(x, baseY, z)) {
                moveTo(
                        x + 0.5D,
                        baseY,
                        z + 0.5D,
                        owner.getYRot(),
                        0.0F
                );

                getNavigation().stop();
                return;
            }

            if (isSafeLandingSpot(x, baseY - 1, z)) {
                moveTo(
                        x + 0.5D,
                        baseY - 1,
                        z + 0.5D,
                        owner.getYRot(),
                        0.0F
                );

                getNavigation().stop();
                return;
            }
        }
    }

    /**
     * Select a safe point behind the owner's current facing direction.
     *
     * Minecraft yaw uses a forward vector of (-sin(yaw), cos(yaw)); the
     * opposite vector is therefore the owner's rear. The primary target is
     * kept 2.5 blocks behind the owner so the Baby never pathfinds to the
     * owner's center during an escape. Small lateral fallbacks are still in
     * the rear sector and are only used when the exact rear block is blocked.
     */
    private Vec3 findSafeEscapePositionBehindOwner(ServerPlayer owner) {
        float yaw = owner.getYRot() * ((float) Math.PI / 180.0F);

        double backX = Math.sin(yaw);
        double backZ = -Math.cos(yaw);

        // Perpendicular to the rear vector, used only for tiny rear-sector
        // adjustments when the exact block is obstructed.
        double sideX = Math.cos(yaw);
        double sideZ = Math.sin(yaw);

        double[] distances = {1.10D, 1.35D, 1.60D, 2.00D};
        double[] sideOffsets = {0.0D, 0.65D, -0.65D};

        int ownerY = owner.blockPosition().getY();

        for (double distance : distances) {
            for (double side : sideOffsets) {
                double x = owner.getX() + backX * distance + sideX * side;
                double z = owner.getZ() + backZ * distance + sideZ * side;

                int blockX = (int) Math.floor(x);
                int blockZ = (int) Math.floor(z);

                for (int dy = 1; dy >= -2; dy--) {
                    int y = ownerY + dy;

                    if (isSafeLandingSpot(blockX, y, blockZ)) {
                        return new Vec3(
                                blockX + 0.5D,
                                y,
                                blockZ + 0.5D
                        );
                    }
                }
            }
        }

        return null;
    }

    /**
     * Safe landing check.
     */
    private boolean isSafeLandingSpot(int x, int y, int z) {
        var feetPos = new net.minecraft.core.BlockPos(x, y, z);
        var headPos = new net.minecraft.core.BlockPos(x, y + 1, z);
        var groundPos = new net.minecraft.core.BlockPos(x, y - 1, z);

        var feet = level().getBlockState(feetPos);
        var head = level().getBlockState(headPos);
        var ground = level().getBlockState(groundPos);

        // Baby needs two blocks of free space.
        if (!feet.getCollisionShape(level(), feetPos).isEmpty()) {
            return false;
        }

        if (!head.getCollisionShape(level(), headPos).isEmpty()) {
            return false;
        }

        // Must have solid ground.
        if (ground.getCollisionShape(level(), groundPos).isEmpty()) {
            return false;
        }

        // Dangerous blocks.
        if (feet.is(Blocks.POWDER_SNOW)
                || head.is(Blocks.POWDER_SNOW)
                || ground.is(Blocks.POWDER_SNOW)) {
            return false;
        }

        if (feet.is(Blocks.LAVA)
                || head.is(Blocks.LAVA)
                || ground.is(Blocks.LAVA)) {
            return false;
        }

        if (feet.is(Blocks.FIRE)
                || feet.is(Blocks.SOUL_FIRE)) {
            return false;
        }

        if (ground.is(Blocks.CACTUS)) {
            return false;
        }

        return !level()
                .getFluidState(feetPos)
                .isSource();
    }

    /**
     * Natural Baby vocalization. Idle and hungry share one exclusive voice
     * channel. The channel is also suspended while the Escape goal is active.
     */
    private void tickVocalization() {
        if (voiceCooldown > 0) {
            voiceCooldown--;
        }

        // EscapeHostileGoal owns the scared voice while fleeing. Never allow
        // idle/hungry to schedule or overlap with it.
        if (isEscapingHostile()) {
            return;
        }

        if (tickCount < nextIdleVoiceTick || voiceCooldown > 0) {
            return;
        }

        if (getFoodLevelExact() < 10.0F) {
            wakeFromHunger();
            playBabyVoice(ModSounds.BABY_HUNGRY.get());
            return;
        }

        if (getFoodLevelExact() < 13.0F
                && random.nextFloat() < HUNGRY_VOICE_CHANCE) {
            wakeFromHunger();
            playBabyVoice(ModSounds.BABY_HUNGRY.get());
            return;
        }

        // Idle is a natural vocalization, not a movement sound. It must be
        // able to occur while the Baby is standing still.
        playBabyVoice(ModSounds.BABY_IDLE.get());
    }

    private boolean isEscapingHostile() {
        return goalSelector.getRunningGoals().anyMatch(
                wrapped -> wrapped.getGoal() instanceof EscapeHostileGoal
        );
    }

    public boolean isSleepingOnBed() {
        return sleepingOnBed && isSleeping();
    }

    public boolean putToBed(BlockPos bedPos) {
        if (bedPos == null) {
            return false;
        }

        var state = level().getBlockState(bedPos);
        if (!(state.getBlock() instanceof BedBlock)) {
            return false;
        }

        if (state.hasProperty(BedBlock.OCCUPIED)
                && state.getValue(BedBlock.OCCUPIED)) {
            return false;
        }

        sleepingOnBed = false;
        setNoAi(false);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);

        startSleeping(bedPos);

        if (!isSleeping()) {
            sleepingOnBed = false;
            return false;
        }

        sleepingOnBed = true;
        setNoAi(true);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        return true;
    }

    private void wakeFromHunger() {
        if (sleepingOnBed || isSleeping()) {
            stopSleeping();
            sleepingOnBed = false;
            setNoAi(false);
        }
    }

    public static void playFamilyHearts(ServerLevel level, BabyNPCPlayerEntity baby) {
        if (level == null || baby == null) return;
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                baby.getX(), baby.getY() + baby.getBbHeight() + 0.15D, baby.getZ(),
                12, 0.35D, 0.45D, 0.35D, 0.02D);
    }

    public static void playOrphanParticles(ServerLevel level, BabyNPCPlayerEntity baby) {
        if (level == null || baby == null) return;
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER,
                baby.getX(), baby.getY() + baby.getBbHeight() + 0.15D, baby.getZ(),
                4, 0.25D, 0.35D, 0.25D, 0.02D);
    }

    /**
     * Stops any currently playing Baby voice for nearby listeners immediately.
     * Priority sounds (scared/hurt/throw/suffer/death) must never stack on top
     * of an unfinished Baby voice. Natural voice scheduling is reset so a new
     * idle/hungry voice is selected later when the channel is safe.
     */
    private void interruptBabyVoiceAndReschedule() {
        if (level().isClientSide || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 head = getEyePosition(1.0F);
        AABB hearingBox = new AABB(head, head).inflate(32.0D);
        ResourceLocation[] babyVoiceIds = new ResourceLocation[] {
                ModSounds.BABY_IDLE.get().getLocation(),
                ModSounds.BABY_HUNGRY.get().getLocation(),
                ModSounds.BABY_SCARED.get().getLocation(),
                ModSounds.BABY_HURT.get().getLocation(),
                ModSounds.BABY_SCREAM.get().getLocation(),
                ModSounds.BABY_SUFFER.get().getLocation(),
                ModSounds.BABY_DEAD.get().getLocation()
        };

        for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, hearingBox)) {
            for (ResourceLocation soundId : babyVoiceIds) {
                player.connection.send(
                        new ClientboundStopSoundPacket(soundId, SoundSource.NEUTRAL)
                );
            }
        }

        voiceCooldown = 0;
        nextIdleVoiceTick = tickCount
                + IDLE_VOICE_MIN_DELAY
                + random.nextInt(IDLE_VOICE_MAX_DELAY - IDLE_VOICE_MIN_DELAY + 1);
    }

    private void playBabyVoice(SoundEvent sound) {
        if (voiceCooldown > 0 || level().isClientSide || isEscapingHostile()) {
            return;
        }

        Vec3 head = getEyePosition(1.0F);
        level().playSound(
                null,
                head.x,
                head.y,
                head.z,
                sound,
                SoundSource.NEUTRAL,
                0.85F,
                0.92F + random.nextFloat() * 0.16F
        );
        voiceCooldown = VOICE_COOLDOWN_TICKS;
        nextIdleVoiceTick = tickCount
                + IDLE_VOICE_MIN_DELAY
                + random.nextInt(IDLE_VOICE_MAX_DELAY - IDLE_VOICE_MIN_DELAY + 1);
    }

    private void playScaredVoice() {
        if (level().isClientSide) {
            return;
        }

        interruptBabyVoiceAndReschedule();

        Vec3 head = getEyePosition(1.0F);

        level().playSound(
                null,
                head.x,
                head.y,
                head.z,
                ModSounds.BABY_SCARED.get(),
                SoundSource.NEUTRAL,
                0.95F,
                0.90F + random.nextFloat() * 0.20F
        );
        voiceCooldown = VOICE_COOLDOWN_TICKS;
        nextIdleVoiceTick = tickCount + VOICE_COOLDOWN_TICKS;
    }

    /**
     * Lightweight player-like hunger model. The values are intentionally
     * independent from PathfinderMob so the existing AI remains untouched.
     */
    private void tickSurvivalNeeds() {
        if (level().isClientSide || !isFoodSimulationEnabled()) {
            return;
        }

        if (!foodMovementInitialized) {
            lastFoodX = getX();
            lastFoodY = getY();
            lastFoodZ = getZ();
            foodMovementInitialized = true;
        }

        double dx = getX() - lastFoodX;
        double dy = getY() - lastFoodY;
        double dz = getZ() - lastFoodZ;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        // Hunger activity is based on actual Baby displacement, never on a
        // navigation request or the vehicle's velocity. A Baby stuck against
        // a wall or sitting in a stationary boat therefore consumes nothing.
        if (!isPassenger() && horizontalDistance > 0.0005D) {
            double speed = getDeltaMovement().horizontalDistance();
            float exhaustion = (float) Math.min(0.10D, horizontalDistance * 0.10D);
            if (speed > 0.20D) {
                exhaustion *= 1.25F;
            }
            addExhaustion(exhaustion);
        }

        // Count an actual jump only when the Baby really leaves the ground.
        if (foodWasOnGround && !onGround() && getDeltaMovement().y > 0.05D && !isPassenger()) {
            addExhaustion(0.05F);
        }
        foodWasOnGround = onGround();

        // Vanilla-style activity costs requested for Baby: taking damage and
        // recovering HP are real activity, not timers.
        float currentHealth = getHealth();
        if (currentHealth < lastFoodHealth) {
            addExhaustion(0.10F);
        } else if (currentHealth > lastFoodHealth) {
            addExhaustion((currentHealth - lastFoodHealth) * 6.0F);
        }
        lastFoodHealth = currentHealth;

        if (exhaustionLevel >= 4.0F) {
            while (exhaustionLevel >= 4.0F) {
                exhaustionLevel -= 4.0F;
                if (saturationLevel > 0.0F) {
                    saturationLevel = Math.max(0.0F, saturationLevel - 1.0F);
                } else if (foodLevel > 0.0F) {
                    foodLevel = Math.max(0.0F, foodLevel - 1.0F);
                }
            }
        }

        if (getFoodLevelExact() >= 18.0F
                && getHealth() < getMaxHealth()
                && tickCount % 10 == 0
                && level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
            heal(1.0F);
        }

        if (!level().isClientSide) {
            entityData.set(SYNCED_FOOD_LEVEL, foodLevel);
        }

        if (foodLevel <= 0.0F && getHealth() > 0.0F && tickCount % 80 == 0) {
            if (level().getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
                    && (level().getDifficulty() != net.minecraft.world.Difficulty.EASY || getHealth() > 10.0F)
                    && (level().getDifficulty() != net.minecraft.world.Difficulty.NORMAL || getHealth() > 1.0F)) {
                hurt(level().damageSources().starve(), 1.0F);
            }
        }

        lastFoodX = getX();
        lastFoodY = getY();
        lastFoodZ = getZ();
    }

    private boolean isFoodSimulationEnabled() {
        return switch (babyGameMode) {
            case "creative", "spectator" -> false;
            default -> level().getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL;
        };
    }

    private void addExhaustion(float amount) {
        if (amount > 0.0F && isFoodSimulationEnabled()) {
            exhaustionLevel = Math.min(40.0F, exhaustionLevel + amount);
        }
    }

    public String getBabyGameMode() {
        return babyGameMode;
    }

    public boolean setBabyGameMode(String mode) {
        if (mode == null) return false;
        String normalized = mode.toLowerCase(java.util.Locale.ROOT);
        if (!normalized.equals("survival") && !normalized.equals("adventure")
                && !normalized.equals("creative") && !normalized.equals("spectator")) {
            return false;
        }
        babyGameMode = normalized;
        return true;
    }

    public static float readFoodLevelTag(CompoundTag tag, float fallback) {
        if (tag == null || !tag.contains(FOOD_LEVEL_TAG)) {
            return fallback;
        }
        if (tag.contains(FOOD_LEVEL_TAG, Tag.TAG_FLOAT)) {
            return tag.getFloat(FOOD_LEVEL_TAG);
        }
        if (tag.contains(FOOD_LEVEL_TAG, Tag.TAG_INT)) {
            // Backward compatibility with the previous integer hunger format.
            return tag.getInt(FOOD_LEVEL_TAG);
        }
        return fallback;
    }

    public int getFoodLevel() {
        return Math.max(0, Math.min(20, (int) Math.floor(foodLevel)));
    }

    public float getFoodLevelExact() {
        return Math.max(0.0F, Math.min(20.0F, foodLevel));
    }

    public float getSaturationLevel() {
        return Math.max(0.0F, saturationLevel);
    }

    public float getExhaustionLevel() {
        return Math.max(0.0F, exhaustionLevel);
    }

    public void setFoodLevel(int value) {
        foodLevel = Math.max(0.0F, Math.min(20.0F, value));
    }

    public void setFoodLevel(float value) {
        foodLevel = Math.max(0.0F, Math.min(20.0F, value));
        if (!level().isClientSide) {
            entityData.set(SYNCED_FOOD_LEVEL, foodLevel);
        }
    }

    public void setSaturationLevel(float value) {
        saturationLevel = Math.max(0.0F, Math.min(20.0F, value));
    }

    public void setExhaustionLevel(float value) {
        exhaustionLevel = Math.max(0.0F, Math.min(40.0F, value));
    }

    public void addFood(float nutrition, float saturationModifier) {
        if (nutrition <= 0.0F) return;
        foodLevel = Math.min(20.0F, foodLevel + nutrition);
        saturationLevel = Math.min(foodLevel, saturationLevel + nutrition * saturationModifier * 2.0F);
        if (!level().isClientSide) {
            entityData.set(SYNCED_FOOD_LEVEL, foodLevel);
        }
    }

    public boolean feedMilk() {
        if (getFoodLevelExact() >= 20.0F) {
            return false;
        }

        float nutrition = Math.min(2.5F, 20.0F - getFoodLevelExact());
        if (nutrition <= 0.0F) return false;
        foodLevel = Math.min(20.0F, foodLevel + nutrition);
        saturationLevel = Math.min(foodLevel, saturationLevel + 3.5F);
        if (!level().isClientSide) entityData.set(SYNCED_FOOD_LEVEL, foodLevel);
        playMilkFeedEffects();
        return true;
    }

    private void playMilkFeedEffects() {
        if (level().isClientSide) {
            return;
        }

        // Honey-bottle drinking sound, pitched slightly higher to read as a
        // small Baby drinking rather than an adult eating.
        level().playSound(
                null,
                getX(),
                getY() + 0.35D,
                getZ(),
                SoundEvents.HONEY_DRINK,
                SoundSource.NEUTRAL,
                0.72F,
                1.18F + (random.nextFloat() - random.nextFloat()) * 0.04F
        );

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.ITEM_SNOWBALL,
                    getX(),
                    getY() + 0.45D,
                    getZ(),
                    8,
                    0.14D,
                    0.18D,
                    0.14D,
                    0.035D
            );
        }
    }

    @Override
    protected SoundEvent getHurtSound(
            net.minecraft.world.damagesource.DamageSource source
    ) {
        interruptBabyVoiceAndReschedule();
        return ModSounds.BABY_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        interruptBabyVoiceAndReschedule();
        return ModSounds.BABY_DEAD.get();
    }

    /**
     * Baby -> Owner Life Bond.
     *
     * This runs only for an actual entity death. Removing/unloading the
     * entity does not call die().
     */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (lifeBondDeathTriggered) {
            super.die(source);
            return;
        }

        lifeBondDeathTriggered = true;

        /*
         * Foster parents are deliberately outside Life Bond.
         * A foster parent's Baby must never kill that foster parent when it
         * dies. Life Bond remains exclusive to the biological relationship.
         */
        ServerPlayer owner = isLifeBondEnabled() && !isFoster() ? getOwner() : null;

        super.die(source);

        if (!level().isClientSide && owner != null && owner.isAlive()) {
            boolean starvation = source.is(DamageTypes.STARVE);
            net.devatnoter.normalnpcplayer.event.LifeBondEvents.killOwner(
                    owner,
                    this,
                    starvation
            );
        }
    }

    /**
     * Player -> Baby Life Bond.
     *
     * This bypasses normal damage immunity because the baby is protected
     * while riding on the player's head.
     */
    public void killFromLifeBond() {
        if (lifeBondDeathTriggered || isRemoved()) {
            return;
        }

        lifeBondDeathTriggered = true;
        super.die(level().damageSources().genericKill());
    }

    public void setTextureIndex(int textureIndex) {
        int clamped = Math.max(
                MIN_TEXTURE_INDEX,
                Math.min(MAX_TEXTURE_INDEX, textureIndex)
        );

        this.entityData.set(SYNCED_TEXTURE_INDEX, clamped);

        if (specialVariantId == null || specialVariantId.isBlank()) {
            this.textureId = textureIdFromIndex(clamped);
            this.entityData.set(SYNCED_TEXTURE_ID, this.textureId);
        }
    }

    public int getTextureIndex() {
        return this.entityData.get(SYNCED_TEXTURE_INDEX);
    }

    public int getSyncedTextureIndex() {
        return this.entityData.get(SYNCED_TEXTURE_INDEX);
    }

    public String getTextureId() {
        return this.textureId;
    }

    public void setTextureId(String textureId) {
        if (textureId == null || textureId.isBlank()) {
            return;
        }

        if (textureId.startsWith("baby")) {
            try {
                int index = Integer.parseInt(textureId.substring(4));
                int clamped = Math.max(
                        MIN_TEXTURE_INDEX,
                        Math.min(MAX_TEXTURE_INDEX, index)
                );
                this.textureId = textureId;
                this.entityData.set(SYNCED_TEXTURE_INDEX, clamped);
                this.entityData.set(SYNCED_TEXTURE_ID, textureId);
                return;
            } catch (NumberFormatException ignored) {
                // Custom texture ids beginning with "baby" remain valid.
            }
        }

        this.textureId = textureId;
        this.entityData.set(SYNCED_TEXTURE_ID, textureId);
    }

    /** Returns the immutable creator/profile identity used for SpecialVariant matching. */
    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        if (this.profileName != null && !this.profileName.isBlank()) {
            return;
        }
        this.profileName = profileName == null ? "" : profileName.trim();
    }

    public String getSpecialVariantId() {
        return specialVariantId;
    }

    public void setSpecialVariantId(String specialVariantId) {
        this.specialVariantId = specialVariantId == null
                ? ""
                : specialVariantId.trim();
        this.entityData.set(
                SYNCED_SPECIAL_VARIANT_ID,
                this.specialVariantId
        );
    }

    public void applyPersistedAppearance(
            String persistedSpecialVariantId,
            String persistedTextureId,
            int persistedTextureIndex
    ) {
        String specialId = persistedSpecialVariantId == null
                ? ""
                : persistedSpecialVariantId.trim();

        int clampedIndex = Math.max(
                MIN_TEXTURE_INDEX,
                Math.min(MAX_TEXTURE_INDEX, persistedTextureIndex)
        );

        String exactTextureId = persistedTextureId == null
                ? ""
                : persistedTextureId.trim();

        if (exactTextureId.isBlank()) {
            exactTextureId = textureIdFromIndex(clampedIndex);
        }

        this.specialVariantId = specialId;
        this.textureId = exactTextureId;

        this.entityData.set(SYNCED_SPECIAL_VARIANT_ID, specialId);
        this.entityData.set(SYNCED_TEXTURE_INDEX, clampedIndex);
        this.entityData.set(SYNCED_TEXTURE_ID, exactTextureId);
    }

    /**
     * Assign appearance exactly once for a genuinely new Baby. A registered
     * SpecialVariant for the immutable ProfileName always wins over normal
     * random variants.
     */
    public void initializeRandomAppearance() {
        BabySpecialVariantRegistry.Selection special =
                BabySpecialVariantRegistry.pickForProfile(profileName, random);

        if (special != null) {
            applyPersistedAppearance(
                    special.id(),
                    special.textureId(),
                    MIN_TEXTURE_INDEX
            );
            return;
        }

        int normalIndex = random.nextInt(
                MAX_TEXTURE_INDEX
                        - MIN_TEXTURE_INDEX
                        + 1
        ) + MIN_TEXTURE_INDEX;

        applyPersistedAppearance(
                "",
                textureIdFromIndex(normalIndex),
                normalIndex
        );
    }


    /**
     * Follow owner when the owner is genuinely far away.
     *
     * Start following at > 13 blocks.
     * Stop following once within 1 block.
     *
     * The separate teleport safety net handles > 20 blocks.
     */
    /**
     * Wander in an area around the owner rather than using RandomStrollGoal
     * across the entire world.
     *
     * The baby chooses a random safe point 8-12 blocks from its owner.
     * This goal never competes with FollowOwnerGoal when the owner is >13
     * blocks away.
     */
    private static class OwnerAreaWanderGoal extends Goal {

        private final BabyNPCPlayerEntity baby;
        private final double speedModifier;
        private final double minRadius;
        private final double maxRadius;

        private ServerPlayer owner;
        private Vec3 target;

        public OwnerAreaWanderGoal(
                BabyNPCPlayerEntity baby,
                double speedModifier,
                double minRadius,
                double maxRadius
        ) {
            this.baby = baby;
            this.speedModifier = speedModifier;
            this.minRadius = minRadius;
            this.maxRadius = maxRadius;

            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (baby.thrownPhysics || baby.level().isClientSide || baby.initialRideActive) {
                return false;
            }

            owner = baby.getBehaviorOwner();

            if (owner == null || owner.level() != baby.level()) {
                return false;
            }

            // Follow takes over when the owner is >13 blocks away.
            if (baby.distanceToSqr(owner) > 13.0D * 13.0D) {
                return false;
            }

            // Do not constantly generate a new walking target.
            if (baby.getRandom().nextInt(40) != 0) {
                return false;
            }

            for (int attempt = 0; attempt < 8; attempt++) {
                double angle = baby.getRandom().nextDouble() * Math.PI * 2.0D;
                double radius = minRadius
                        + baby.getRandom().nextDouble() * (maxRadius - minRadius);

                int x = (int) Math.floor(owner.getX() + Math.cos(angle) * radius);
                int z = (int) Math.floor(owner.getZ() + Math.sin(angle) * radius);

                int ownerY = owner.blockPosition().getY();

                for (int dy = 2; dy >= -2; dy--) {
                    int y = ownerY + dy;

                    if (baby.isSafeLandingSpot(x, y, z)) {
                        target = new Vec3(
                                x + 0.5D,
                                y,
                                z + 0.5D
                        );
                        return true;
                    }
                }
            }

            target = null;
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return !baby.thrownPhysics
                    && target != null
                    && !baby.initialRideActive
                    && owner != null
                    && !baby.isOrphaned()
                    && owner.isAlive()
                    && !owner.isSpectator()
                    && owner.level() == baby.level()
                    && baby.distanceToSqr(owner) <= 13.0D * 13.0D
                    && !baby.getNavigation().isDone();
        }

        @Override
        public void start() {
            if (target != null) {
                baby.getNavigation().moveTo(
                        target.x,
                        target.y,
                        target.z,
                        speedModifier
                );
            }
        }

        @Override
        public void stop() {
            baby.getNavigation().stop();
            target = null;
            owner = null;
        }
    }

    /**
     * Emergency escape behavior. When an ordinary hostile mob is close, the
     * Baby abandons wandering/tempting/following and runs directly toward its
     * owner. A larger continuation radius prevents the goal from flickering
     * on/off at the edge of the detection range.
     */
    private static class EscapeHostileGoal extends Goal {
        private final BabyNPCPlayerEntity baby;
        private final double speedModifier;
        private final double detectRadius;
        private final double safeRadius;
        private ServerPlayer owner;
        private Vec3 escapeTarget;
        private int repathCooldown;

        private EscapeHostileGoal(
                BabyNPCPlayerEntity baby,
                double speedModifier,
                double detectRadius,
                double safeRadius
        ) {
            this.baby = baby;
            this.speedModifier = speedModifier;
            this.detectRadius = detectRadius;
            this.safeRadius = safeRadius;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (baby.thrownPhysics || baby.level().isClientSide || baby.initialRideActive) {
                return false;
            }

            owner = baby.getBehaviorOwner();
            // If the Baby is hungry and a Golden Milk Bottle is nearby,
            // allow the food goal to take over briefly. After the Baby eats,
            // this escape goal can immediately resume.
            if (baby.getFoodLevelExact() < 20.0F
                    && baby.hasNearbyGoldenMilkFood(8.0D)) {
                return false;
            }
            return !baby.thrownPhysics
                    && owner != null
                    && !baby.isOrphaned()
                    && owner.isAlive()
                    && !owner.isSpectator()
                    && owner.level() == baby.level()
                    && baby.hasNearbyHostile(detectRadius);
        }

        @Override
        public boolean canContinueToUse() {
            if (baby.getFoodLevelExact() < 20.0F
                    && baby.hasNearbyGoldenMilkFood(8.0D)) {
                return false;
            }

            if (baby.thrownPhysics || baby.isOrphaned() || baby.initialRideActive) {
                return false;
            }

            owner = baby.getBehaviorOwner();
            return owner != null
                    && owner.isAlive()
                    && !owner.isSpectator()
                    && owner.level() == baby.level()
                    && baby.hasNearbyHostile(safeRadius);
        }

        @Override
        public void start() {
            repathCooldown = 0;
            baby.playScaredVoice();
            updateEscapeTarget();
        }

        @Override
        public void tick() {
            if (baby.thrownPhysics) {
                return;
            }

            owner = baby.getBehaviorOwner();
            if (owner == null) {
                return;
            }

            // Never run directly into the owner. The safe point is deliberately
            // computed from the owner's current facing direction so the Baby
            // takes cover behind the owner rather than stopping in front of them.
            if (repathCooldown-- <= 0) {
                updateEscapeTarget();
                repathCooldown = 5;
            }
        }

        @Override
        public void stop() {
            baby.getNavigation().stop();
            owner = null;
            escapeTarget = null;
            repathCooldown = 0;
        }

        private void updateEscapeTarget() {
            if (owner == null) {
                return;
            }

            Vec3 target = baby.findSafeEscapePositionBehindOwner(owner);
            if (target == null) {
                // If the exact rear position is temporarily blocked, keep the
                // existing target instead of falling back to the owner's center.
                // This preserves the "behind owner" rule.
                return;
            }

            escapeTarget = target;

            baby.getLookControl().setLookAt(
                    owner,
                    20.0F,
                    baby.getMaxHeadXRot()
            );

            baby.getNavigation().moveTo(
                    escapeTarget.x,
                    escapeTarget.y,
                    escapeTarget.z,
                    speedModifier
            );
        }
    }

    private static class BabyFoodTemptGoal extends Goal {
        private final BabyNPCPlayerEntity baby;
        private final double speed;
        private Player target;

        BabyFoodTemptGoal(BabyNPCPlayerEntity baby, double speed) {
            this.baby = baby; this.speed = speed;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override public boolean canUse() {
            if (baby.thrownPhysics || baby.initialRideActive || baby.getFoodLevelExact() >= 20.0F) return false;
            target = baby.findFoodPlayer();
            return target != null;
        }

        @Override public boolean canContinueToUse() {
            return !baby.thrownPhysics && target != null && target.isAlive() && !target.isSpectator()
                    && baby.getFoodLevelExact() < 20.0F
                    && (BabyFoodItem.isBabyFood(target.getMainHandItem()) || BabyFoodItem.isBabyFood(target.getOffhandItem())
                    || target.getMainHandItem().is(Items.MILK_BUCKET) || target.getOffhandItem().is(Items.MILK_BUCKET))
                    && baby.distanceToSqr(target) <= 64.0D;
        }

        @Override public void tick() {
            if (target != null) {
                baby.getLookControl().setLookAt(target, 20.0F, baby.getMaxHeadXRot());
                baby.getNavigation().moveTo(target, speed);
            }
        }

        @Override public void stop() { target = null; baby.getNavigation().stop(); }
    }

    private Player findFoodPlayer() {
        return level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(8.0D), p -> {
            if (!p.isAlive() || p.isSpectator()) return false;

            if (!isOrphaned()) {
                if (babyType == BabyType.PLAYER) {
                    if (!isPlayerBabyOwner(p)) return false;
                } else {
                    if (ownerUUID == null || !ownerUUID.equals(p.getUUID())) return false;
                }
            }

            return BabyFoodItem.isBabyFood(p.getMainHandItem()) || BabyFoodItem.isBabyFood(p.getOffhandItem())
                    || p.getMainHandItem().is(Items.MILK_BUCKET) || p.getOffhandItem().is(Items.MILK_BUCKET);
        }).stream().min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    private static class OrphanFollowPlayerGoal extends Goal {
        private final BabyNPCPlayerEntity baby;
        private final double speed;
        private Player target;
        OrphanFollowPlayerGoal(BabyNPCPlayerEntity baby, double speed) {
            this.baby = baby; this.speed = speed; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        @Override public boolean canUse() {
            if (baby.thrownPhysics || !baby.isOrphaned() || baby.initialRideActive || baby.isSleeping()) return false;
            // An orphan does not automatically attach itself to the nearest
            // visible player. It follows a player only when that player is
            // actively holding Baby food/milk, using the same food-target rule
            // as an owned Baby.
            target = baby.findFoodPlayer();
            return target != null;
        }
        @Override public boolean canContinueToUse() {
            return !baby.thrownPhysics && baby.isOrphaned() && target != null && target.isAlive() && !target.isSpectator()
                    && (BabyFoodItem.isBabyFood(target.getMainHandItem()) || BabyFoodItem.isBabyFood(target.getOffhandItem())
                    || target.getMainHandItem().is(Items.MILK_BUCKET) || target.getOffhandItem().is(Items.MILK_BUCKET))
                    && baby.distanceToSqr(target) > 2.0D && baby.distanceToSqr(target) < 256.0D;
        }
        @Override public void tick() {
            if (target != null) { baby.getLookControl().setLookAt(target, 10.0F, baby.getMaxHeadXRot()); baby.getNavigation().moveTo(target, speed); }
        }
        @Override public void stop() { target = null; baby.getNavigation().stop(); }
    }

    private static class FollowOwnerGoal extends Goal {

        private final BabyNPCPlayerEntity baby;
        private final double speedModifier;
        private final double startDistance;
        private final double stopDistance;

        private ServerPlayer owner;
        private final PathNavigation navigation;

        public FollowOwnerGoal(
                BabyNPCPlayerEntity baby,
                double speedModifier,
                double startDistance,
                double stopDistance
        ) {
            this.baby = baby;
            this.speedModifier = speedModifier;
            this.startDistance = startDistance;
            this.stopDistance = stopDistance;
            this.navigation = baby.getNavigation();

            this.setFlags(EnumSet.of(
                    Flag.MOVE,
                    Flag.LOOK
            ));
        }

        @Override
        public boolean canUse() {
            if (baby.thrownPhysics || baby.level().isClientSide) {
                return false;
            }

            if (baby.initialRideActive) {
                return false;
            }

            owner = baby.getBehaviorOwner();

            if (baby.thrownPhysics || owner == null) {
                return false;
            }

            return baby.distanceToSqr(owner)
                    > startDistance * startDistance;
        }

        @Override
        public boolean canContinueToUse() {
            if (baby.thrownPhysics || baby.initialRideActive || baby.isOrphaned()) {
                return false;
            }

            // Player Babies dynamically re-evaluate both parents so the
            // nearest parent always has follow priority. Hardcore Babies
            // simply resolve back to their legacy single owner.
            ServerPlayer nearest = baby.getBehaviorOwner();
            if (nearest == null) {
                return false;
            }
            owner = nearest;

            return owner.isAlive()
                    && !owner.isSpectator()
                    && owner.level() == baby.level()
                    && baby.distanceToSqr(owner) > stopDistance * stopDistance;
        }

        @Override
        public void start() {
            navigation.stop();
        }

        @Override
        public void stop() {
            navigation.stop();
            owner = null;
        }

        @Override
        public void tick() {
            if (baby.thrownPhysics) {
                return;
            }

            ServerPlayer nearest = baby.getBehaviorOwner();
            if (nearest == null) {
                return;
            }
            owner = nearest;

            baby.getLookControl().setLookAt(
                    owner,
                    10.0F,
                    baby.getMaxHeadXRot()
            );

            double distance = baby.distanceTo(owner);
            double speed = Math.min(
                    1.6D,
                    1.0D + Math.max(0.0D, distance - 2.0D) * 0.12D
            );

            baby.getNavigation().moveTo(owner, speed);

            // Navigation may finish slightly before the requested center
            // distance because the target is an entity. When that happens,
            // keep the Baby moving toward the actual owner position instead
            // of allowing the follow state to stall above stopDistance.
            if (distance > stopDistance && baby.getNavigation().isDone()) {
                baby.getMoveControl().setWantedPosition(
                        owner.getX(),
                        owner.getY(),
                        owner.getZ(),
                        speed
                );
            }
        }
    }

    public void setItemRenderMode(boolean value) {
        this.itemRenderMode = value;
    }

    public boolean isItemRenderMode() {
        return itemRenderMode;
    }

    private static final class BabyFoodPickupGoal extends Goal {
        private final BabyNPCPlayerEntity baby;
        private ItemEntity target;

        private BabyFoodPickupGoal(BabyNPCPlayerEntity baby) {
            this.baby = baby;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (baby.thrownPhysics || baby.isSleeping() || baby.initialRideActive) return false;
            target = findNearestFood();
            if (target == null) return false;

            ItemStack stack = target.getItem();
            if (isGoldenMilk(stack)) {
                return baby.canConsumeGoldenMilk();
            }
            return baby.getFoodLevelExact() < 20.0F;
        }

        @Override
        public boolean canContinueToUse() {
            if (baby.thrownPhysics || target == null || !target.isAlive() || baby.isSleeping()) return false;
            ItemStack stack = target.getItem();
            boolean usable = isGoldenMilk(stack)
                    ? baby.canConsumeGoldenMilk()
                    : baby.getFoodLevelExact() < 20.0F;
            return usable
                    && (BabyFoodItem.isBabyFood(stack) || stack.is(Items.MILK_BUCKET))
                    && baby.distanceToSqr(target) <= 64.0D;
        }

        @Override
        public void stop() {
            target = null;
            baby.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (target == null || !target.isAlive()) return;
            baby.getLookControl().setLookAt(target, 20.0F, baby.getMaxHeadXRot());
            baby.getNavigation().moveTo(target, 1.15D);

            if (baby.distanceToSqr(target) <= 2.25D) {
                ItemStack stack = target.getItem();
                if (stack.is(Items.MILK_BUCKET)) {
                    if (baby.feedMilk()) {
                        stack.shrink(1);
                        if (stack.isEmpty()) target.setItem(new ItemStack(Items.BUCKET));
                    }
                } else if (BabyFoodItem.isBabyFood(stack)) {
                    BabyFoodItem.FoodData food = BabyFoodItem.getFoodData(stack);
                    if (baby.foodFeed(food)) {
                        stack.shrink(1);
                        if (stack.isEmpty()) target.discard();
                        else target.setItem(stack);
                        if (!food.emptyReturn().isEmpty()) {
                            baby.level().addFreshEntity(new ItemEntity(
                                    baby.level(), baby.getX(), baby.getY() + 0.25D, baby.getZ(),
                                    food.emptyReturn().copy()));
                        }
                    }
                }
                stop();
            }
        }

        private static boolean isGoldenMilk(ItemStack stack) {
            return !stack.isEmpty()
                    && stack.getItem() instanceof BabyFoodItem food
                    && (food.kind() == BabyFoodItem.Kind.GOLDEN_MILK_BOTTLE
                    || food.kind() == BabyFoodItem.Kind.ENCHANTED_GOLDEN_MILK_BOTTLE);
        }

        private ItemEntity findNearestFood() {
            return baby.level().getEntitiesOfClass(
                    ItemEntity.class, baby.getBoundingBox().inflate(8.0D),
                    e -> e.isAlive() && isEligibleFood(e.getItem())
            ).stream().min(java.util.Comparator.comparingDouble(baby::distanceToSqr)).orElse(null);
        }

        private boolean isEligibleFood(ItemStack stack) {
            if (stack.is(Items.MILK_BUCKET)) {
                return baby.getFoodLevelExact() < 20.0F;
            }
            if (!BabyFoodItem.isBabyFood(stack)) return false;
            if (isGoldenMilk(stack)) {
                return baby.canConsumeGoldenMilk();
            }
            return baby.getFoodLevelExact() < 20.0F;
        }
    }

    /**
     * GeckoLib Animation
     */
    @Override
    public void registerControllers(
            AnimatableManager.ControllerRegistrar controllers
    ) {
        controllers.add(new AnimationController<>(
                this,
                "main",
                0,
                state -> {
                    if (isItemRenderMode()) {
                        return state.setAndContinue(
                                RawAnimation.begin()
                                        .thenLoop("idle1")
                        );
                    }

                    if (state.isMoving()) {
                        return state.setAndContinue(
                                RawAnimation.begin()
                                        .thenLoop("walk1")
                        );
                    }

                    return state.setAndContinue(
                            RawAnimation.begin()
                                    .thenLoop("idle1")
                    );
                }
        ));
    }

    /**
     * Texture
     */
    /**
     * Owner
     */
    /**
     * True only during the automatic initial head ride.
     */
    public boolean isInitialRideActive() {
        return initialRideActive;
    }

    public void clearInitialRideState() {
        if (getVehicle() instanceof ServerPlayer vehiclePlayer) {
            stopRiding();
            if (!level().isClientSide) {
                vehiclePlayer.connection.send(
                        new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(vehiclePlayer)
                );
            }
        } else if (getVehicle() != null) {
            stopRiding();
        }
        initialRideActive = false;
        initialRideStartX = 0.0D;
        initialRideStartY = 0.0D;
        initialRideStartZ = 0.0D;
        ownerFireTicksWhileCarried = 0;
        ownerFireTransferred = false;
        lavaFireTransferred = false;
        this.setNoGravity(false);
        this.setInvulnerable(false);
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setInventoryOpen(boolean open, UUID viewerUUID) {
        this.inventoryOpen = open;
        this.inventoryViewerUUID = open ? viewerUUID : null;
        if (open) {
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            fallDistance = 0.0F;
        }
    }

    public UUID getInventoryViewerUUID() {
        return inventoryViewerUUID;
    }

    public boolean isInventoryOpen() {
        return inventoryOpen;
    }

    public boolean isFoster() {
        return foster;
    }

    public BabyType getBabyType() {
        return babyType;
    }

    public String getGrowthState() {
        return growthState;
    }

    public boolean isPlayerBabyGrown() {
        return babyType == BabyType.PLAYER && "grown".equalsIgnoreCase(growthState);
    }

    public double getGrowthProgress() {
        return Math.max(0.0D, Math.min(1.0D, growthProgress));
    }

    public double getHearty() {
        return Math.max(0.0D, Math.min(100.0D, hearty));
    }

    public void addHearty(double amount) {
        hearty = Math.max(0.0D, Math.min(100.0D, hearty + amount));
    }

    public void setHearty(double value) {
        hearty = Math.max(0.0D, Math.min(100.0D, value));
    }

    public double getDistrust() {
        return Math.max(0.0D, Math.min(100.0D, distrust));
    }

    public void addDistrust(double amount) {
        distrust = Math.max(0.0D, Math.min(100.0D, distrust + amount));
    }

    public void setDistrust(double value) {
        distrust = Math.max(0.0D, Math.min(100.0D, value));
    }

    public float getHeartyLastHealth() {
        return heartyLastHealth;
    }

    public void setHeartyLastHealth(float value) {
        heartyLastHealth = value;
    }

    public int getHeartySafetyTicks() {
        return heartySafetyTicks;
    }

    public void setHeartySafetyTicks(int value) {
        heartySafetyTicks = Math.max(0, value);
    }

    public int getHeartyFearTicks() {
        return heartyFearTicks;
    }

    public void setHeartyFearTicks(int value) {
        heartyFearTicks = Math.max(0, value);
    }

    public int getPlayerGrowthTargetTicks() {
        return (int) Math.round(
                PLAYER_GROWTH_MAX_TICKS
                        - (PLAYER_GROWTH_MAX_TICKS - PLAYER_GROWTH_MIN_TICKS)
                        * (getHearty() / 100.0D)
        );
    }

    public void setGrowthState(String state) {
        growthState = state == null || state.isBlank() ? "growing" : state.toLowerCase(java.util.Locale.ROOT);
    }

    public void setGrowthProgress(double progress) {
        growthProgress = Math.max(0.0D, Math.min(1.0D, progress));
    }

    public void setBabyType(BabyType babyType) {
        this.babyType = babyType == null ? BabyType.HARDCORE : babyType;
    }

    public long getBirthGameTime() {
        return birthGameTime;
    }

    public void setBirthGameTime(long birthGameTime) {
        this.birthGameTime = Math.max(0L, birthGameTime);
    }

    public boolean isLifeBondEnabled() {
        return babyType == BabyType.HARDCORE;
    }

    public UUID getBiologicalParentAUUID() {
        return biologicalParentAUUID;
    }

    public UUID getBiologicalParentBUUID() {
        return biologicalParentBUUID;
    }

    public String getBiologicalParentAName() {
        return biologicalParentAName;
    }

    public String getBiologicalParentBName() {
        return biologicalParentBName;
    }

    public void setBiologicalParents(ServerPlayer parentA, ServerPlayer parentB) {
        if (parentA == null || parentB == null) return;
        biologicalParentAUUID = parentA.getUUID();
        biologicalParentAName = parentA.getGameProfile().getName();
        biologicalParentBUUID = parentB.getUUID();
        biologicalParentBName = parentB.getGameProfile().getName();
        if (babyType == BabyType.PLAYER) {
            setPlayerBabyOwners(parentA, parentB);
        }
    }

    public boolean isPlayerBabyOwner(Player player) {
        if (player == null || babyType != BabyType.PLAYER) {
            return false;
        }
        UUID id = player.getUUID();
        return id.equals(ownerUUID)
                || id.equals(ownerAUUID)
                || id.equals(ownerBUUID)
                || id.equals(biologicalParentAUUID)
                || id.equals(biologicalParentBUUID);
    }

    public String getPlayerBabyOwnerDisplayName() {
        if (babyType != BabyType.PLAYER) {
            return ownerName;
        }
        String a = ownerAName == null ? "" : ownerAName.trim();
        String b = ownerBName == null ? "" : ownerBName.trim();
        if (!a.isEmpty() && !b.isEmpty()) return a + " & " + b;
        if (!a.isEmpty()) return a;
        if (!b.isEmpty()) return b;
        return ownerName == null ? "" : ownerName.trim();
    }

    public void setPlayerBabyOwners(ServerPlayer ownerA, ServerPlayer ownerB) {
        if (ownerA == null || ownerB == null) return;
        ownerAUUID = ownerA.getUUID();
        ownerAName = ownerA.getGameProfile().getName();
        ownerBUUID = ownerB.getUUID();
        ownerBName = ownerB.getGameProfile().getName();
    }

    public void setFoster(boolean foster) {
        this.foster = foster;
    }

    public boolean isOrphaned() {
        return orphaned;
    }

    public void setOrphaned(boolean orphaned) {
        this.orphaned = orphaned;
    }

    public UUID getBiologicalParentUUID() {
        return biologicalParentUUID;
    }

    public String getBiologicalParentName() {
        return biologicalParentName;
    }

    /**
     * Records the original biological parent once, at real child creation.
     * This identity is never overwritten by foster ownership.
     */
    public void setBiologicalParent(ServerPlayer parent) {
        if (parent == null || biologicalParentUUID != null) {
            return;
        }

        biologicalParentUUID = parent.getUUID();
        biologicalParentName = parent.getGameProfile().getName();
    }

    /**
     * A foster relationship can only be created from an orphaned Baby.
     * The current Owner fields are deliberately overridden so all existing
     * owner-based systems now point at the foster parent.
     */
    public boolean fosterTo(ServerPlayer fosterParent) {
        if (fosterParent == null || !orphaned) {
            return false;
        }

        setOwnerUUID(fosterParent.getUUID());
        setOwnerName(fosterParent.getGameProfile().getName());
        foster = true;
        orphaned = false;
        return true;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    /**
     * Returns the Player Baby's active parent target.
     *
     * Hardcore Babies keep the legacy single-owner behavior. Player Babies
     * treat both parents equally and select the nearest valid parent.
     */
    public ServerPlayer getBehaviorOwner() {
        if (babyType != BabyType.PLAYER) {
            return getOwner();
        }

        if (orphaned || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        ServerPlayer nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        java.util.HashSet<UUID> parentIds = new java.util.HashSet<>();
        if (ownerAUUID != null) parentIds.add(ownerAUUID);
        if (ownerBUUID != null) parentIds.add(ownerBUUID);
        if (biologicalParentAUUID != null) parentIds.add(biologicalParentAUUID);
        if (biologicalParentBUUID != null) parentIds.add(biologicalParentBUUID);
        if (ownerUUID != null) parentIds.add(ownerUUID);

        for (UUID parentId : parentIds) {
            ServerPlayer candidate = serverLevel.getServer().getPlayerList().getPlayer(parentId);
            if (candidate == null || !candidate.isAlive() || candidate.isSpectator()
                    || candidate.level() != level()) {
                continue;
            }

            double distance = distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }

        return nearest;
    }

    /**
     * คืนผู้เล่นที่เป็นเจ้าของ
     */
    public ServerPlayer getOwner() {
        // Once the biological parent dies, the Baby is orphaned and must not
        // continue treating that player's spectator instance as its owner.
        if (orphaned || ownerUUID == null) {
            return null;
        }

        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        ServerPlayer owner = serverLevel.getServer()
                .getPlayerList()
                .getPlayer(ownerUUID);

        if (owner == null || !owner.isAlive() || owner.isSpectator()) {
            return null;
        }

        return owner;
    }

    /**
     * Save
     */
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        tag.putInt(TEXTURE_INDEX_TAG, getTextureIndex());
        tag.putString(TEXTURE_ID_TAG, getTextureId());
        tag.putString(PROFILE_NAME_TAG, profileName);
        tag.putString(SPECIAL_VARIANT_TAG, specialVariantId);

        if (ownerUUID != null) {
            tag.putUUID("OwnerUUID", ownerUUID);
        }

        tag.putString("OwnerName", ownerName);
        if (ownerAUUID != null) tag.putUUID(OWNER_A_UUID_TAG, ownerAUUID);
        tag.putString(OWNER_A_NAME_TAG, ownerAName);
        if (ownerBUUID != null) tag.putUUID(OWNER_B_UUID_TAG, ownerBUUID);
        tag.putString(OWNER_B_NAME_TAG, ownerBName);
        tag.putString(BABY_TYPE_TAG, babyType.name());
        if (babyType == BabyType.PLAYER) {
            tag.putLong(BIRTH_GAME_TIME_TAG, birthGameTime);
            tag.putString(GROWTH_STATE_TAG, growthState);
            tag.putDouble(GROWTH_PROGRESS_TAG, growthProgress);
            tag.putDouble(HEARTY_TAG, hearty);
            tag.putFloat(HEARTY_LAST_HEALTH_TAG, heartyLastHealth);
            tag.putInt(HEARTY_SAFETY_TICKS_TAG, heartySafetyTicks);
            tag.putInt(HEARTY_FEAR_TICKS_TAG, heartyFearTicks);
            tag.putDouble(DISTRUST_TAG, distrust);
        }

        if (biologicalParentAUUID != null) tag.putUUID(BIOLOGICAL_PARENT_A_UUID_TAG, biologicalParentAUUID);
        tag.putString(BIOLOGICAL_PARENT_A_NAME_TAG, biologicalParentAName);
        if (biologicalParentBUUID != null) tag.putUUID(BIOLOGICAL_PARENT_B_UUID_TAG, biologicalParentBUUID);
        tag.putString(BIOLOGICAL_PARENT_B_NAME_TAG, biologicalParentBName);

        tag.putBoolean(FOSTER_TAG, foster);
        tag.putBoolean(ORPHANED_TAG, orphaned);
        tag.putString(BABY_GAME_MODE_TAG, babyGameMode);
        tag.putBoolean("SleepingOnBed", sleepingOnBed);

        tag.putBoolean("ThrownPhysics", thrownPhysics);
        tag.putBoolean("ThrownPreviousNoAi", thrownPreviousNoAi);
        if (thrownByUUID != null) {
            tag.putUUID("ThrownByUUID", thrownByUUID);
        }
        tag.putInt("ThrownTicks", thrownTicks);
        tag.putFloat("ThrownCharge", thrownCharge);
        tag.putFloat("ThrownSprintFactor", thrownSprintFactor);
        tag.putDouble("ThrownLastSpeed", thrownLastSpeed);
        tag.putDouble("ThrownStartY", thrownStartY);
        tag.putDouble("ThrownMaxY", thrownMaxY);
        tag.putBoolean("ThrownRecoveryScreamPending", thrownRecoveryScreamPending);
        tag.putInt("ThrownRecoveryScreamTicks", thrownRecoveryScreamTicks);
        tag.putInt("ThrownRecoveryScreamElapsedTicks", thrownRecoveryScreamElapsedTicks);
        tag.putInt("ThrownRecoveryScreamBaseTicks", thrownRecoveryScreamBaseTicks);

        if (biologicalParentUUID != null) {
            tag.putUUID(BIOLOGICAL_PARENT_UUID_TAG, biologicalParentUUID);
        }
        tag.putString(BIOLOGICAL_PARENT_NAME_TAG, biologicalParentName);

        tag.putFloat(FOOD_LEVEL_TAG, foodLevel);
        tag.putFloat(SATURATION_LEVEL_TAG, saturationLevel);
        tag.putFloat(EXHAUSTION_LEVEL_TAG, exhaustionLevel);
        // Explicitly persist the real absorption buffer so the Baby carrier
        // tooltip and pickup/place round-trip can never fall back to zero.
        tag.putFloat("AbsorptionAmount", getAbsorptionAmount());
        tag.putInt(TOTAL_XP_TAG, totalExperience);
        tag.putInt(EXPERIENCE_LEVEL_TAG, experienceLevel);
        // Compatibility keys used by the Baby Item tooltip renderer.
        tag.putInt("XpTotal", totalExperience);
        tag.putInt("XpLevel", experienceLevel);

        ListTag babyInv = new ListTag();
        for (int i = 0; i < babyInventory.getContainerSize(); i++) {
            ItemStack stack = babyInventory.getItem(i);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putByte("Slot", (byte) i);
            entry.put("Item", stack.save(new CompoundTag()));
            babyInv.add(entry);
        }
        tag.put("BabyInventory", babyInv);

        CompoundTag babyEquipment = new CompoundTag();
        babyEquipment.put("MainHand", getItemBySlot(EquipmentSlot.MAINHAND).save(new CompoundTag()));
        babyEquipment.put("OffHand", getItemBySlot(EquipmentSlot.OFFHAND).save(new CompoundTag()));
        babyEquipment.put("Head", getItemBySlot(EquipmentSlot.HEAD).save(new CompoundTag()));
        babyEquipment.put("Chest", getItemBySlot(EquipmentSlot.CHEST).save(new CompoundTag()));
        babyEquipment.put("Legs", getItemBySlot(EquipmentSlot.LEGS).save(new CompoundTag()));
        babyEquipment.put("Feet", getItemBySlot(EquipmentSlot.FEET).save(new CompoundTag()));
        tag.put("BabyEquipment", babyEquipment);
        BabyEquipmentLogic.persistEntityEquipmentTags(this, tag);
        tag.put("FedByPlayers", getOrCreateFedByPlayers().copy());
        tag.put("Protectors", getOrCreateProtectors().copy());
    }

    /**
     * Load
     */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        fedByPlayers = tag.contains("FedByPlayers", 9)
                ? tag.getList("FedByPlayers", 10).copy()
                : new ListTag();
        protectors = tag.contains("Protectors", 9)
                ? tag.getList("Protectors", 10).copy()
                : new ListTag();

        if (tag.contains("AbsorptionAmount")) {
            setAbsorptionAmount(Math.max(0.0F, tag.getFloat("AbsorptionAmount")));
        }

        String savedSpecialVariantId = tag.contains(SPECIAL_VARIANT_TAG)
                ? tag.getString(SPECIAL_VARIANT_TAG).trim()
                : "";

        String savedTextureId = tag.contains(TEXTURE_ID_TAG)
                ? tag.getString(TEXTURE_ID_TAG).trim()
                : "";

        int savedTextureIndex = tag.contains(TEXTURE_INDEX_TAG)
                ? tag.getInt(TEXTURE_INDEX_TAG)
                : getTextureIndex();

        if (tag.contains(PROFILE_NAME_TAG)) {
            profileName = tag.getString(PROFILE_NAME_TAG).trim();
        }

        if (tag.contains(TEXTURE_INDEX_TAG)
                || tag.contains(TEXTURE_ID_TAG)
                || tag.contains(SPECIAL_VARIANT_TAG)) {
            applyPersistedAppearance(
                    savedSpecialVariantId,
                    savedTextureId,
                    savedTextureIndex
            );
        }

        // Backward compatibility: Babies created before ProfileName existed
        // inherit their original biological-parent identity when available.
        // We intentionally DO NOT reroll their existing appearance here.

        if (profileName.isBlank() && tag.contains(BIOLOGICAL_PARENT_NAME_TAG)) {
            profileName = tag.getString(BIOLOGICAL_PARENT_NAME_TAG).trim();
        }
        if (profileName.isBlank() && tag.contains("OwnerName")) {
            profileName = tag.getString("OwnerName").trim();
        }

        if (tag.getBoolean("ThrownPhysics")) {
            thrownPhysics = true;
            // Do not disable AI when restoring a thrown Baby. The throw state
            // only suppresses the owner emergency-teleport rule.
            thrownByUUID = tag.hasUUID("ThrownByUUID")
                    ? tag.getUUID("ThrownByUUID")
                    : null;
            thrownTicks = tag.getInt("ThrownTicks");
            thrownCharge = tag.getFloat("ThrownCharge");
            thrownSprintFactor = tag.getFloat("ThrownSprintFactor");
            thrownLastSpeed = tag.getDouble("ThrownLastSpeed");
            thrownStartY = tag.contains("ThrownStartY") ? tag.getDouble("ThrownStartY") : getY();
            thrownMaxY = tag.contains("ThrownMaxY") ? tag.getDouble("ThrownMaxY") : getY();
        }

        thrownRecoveryScreamPending = tag.getBoolean("ThrownRecoveryScreamPending");
        thrownRecoveryScreamTicks = Math.max(0, tag.getInt("ThrownRecoveryScreamTicks"));
        thrownRecoveryScreamElapsedTicks = Math.max(0, tag.getInt("ThrownRecoveryScreamElapsedTicks"));
        thrownRecoveryScreamBaseTicks = Math.max(0, tag.getInt("ThrownRecoveryScreamBaseTicks"));
        if (thrownRecoveryScreamPending && thrownRecoveryScreamBaseTicks <= 0) {
            thrownRecoveryScreamBaseTicks = Math.max(
                    thrownRecoveryScreamTicks + thrownRecoveryScreamElapsedTicks,
                    20 * 90
            );
        }

        if (tag.hasUUID("OwnerUUID")) {
            ownerUUID = tag.getUUID("OwnerUUID");
        }
        ownerAUUID = tag.hasUUID(OWNER_A_UUID_TAG) ? tag.getUUID(OWNER_A_UUID_TAG) : null;
        ownerAName = tag.getString(OWNER_A_NAME_TAG);
        ownerBUUID = tag.hasUUID(OWNER_B_UUID_TAG) ? tag.getUUID(OWNER_B_UUID_TAG) : null;
        ownerBName = tag.getString(OWNER_B_NAME_TAG);

        ownerName = tag.getString("OwnerName");
        babyType = BabyType.fromId(tag.getString(BABY_TYPE_TAG));
        if (babyType == BabyType.PLAYER) {
            birthGameTime = tag.contains(BIRTH_GAME_TIME_TAG)
                    ? Math.max(0L, tag.getLong(BIRTH_GAME_TIME_TAG))
                    : Math.max(0L, level().getGameTime());
            growthState = tag.contains(GROWTH_STATE_TAG) ? tag.getString(GROWTH_STATE_TAG) : "growing";
            growthProgress = Math.max(0.0D, Math.min(1.0D, tag.getDouble(GROWTH_PROGRESS_TAG)));
            hearty = Math.max(0.0D, Math.min(100.0D, tag.contains(HEARTY_TAG) ? tag.getDouble(HEARTY_TAG) : 50.0D));
            heartyLastHealth = tag.contains(HEARTY_LAST_HEALTH_TAG) ? tag.getFloat(HEARTY_LAST_HEALTH_TAG) : getHealth();
            heartySafetyTicks = Math.max(0, tag.getInt(HEARTY_SAFETY_TICKS_TAG));
            heartyFearTicks = Math.max(0, tag.getInt(HEARTY_FEAR_TICKS_TAG));
            distrust = Math.max(0.0D, Math.min(100.0D, tag.contains(DISTRUST_TAG) ? tag.getDouble(DISTRUST_TAG) : 0.0D));
        }

        biologicalParentAUUID = tag.hasUUID(BIOLOGICAL_PARENT_A_UUID_TAG)
                ? tag.getUUID(BIOLOGICAL_PARENT_A_UUID_TAG) : null;
        biologicalParentAName = tag.getString(BIOLOGICAL_PARENT_A_NAME_TAG);
        biologicalParentBUUID = tag.hasUUID(BIOLOGICAL_PARENT_B_UUID_TAG)
                ? tag.getUUID(BIOLOGICAL_PARENT_B_UUID_TAG) : null;
        biologicalParentBName = tag.getString(BIOLOGICAL_PARENT_B_NAME_TAG);

        foster = tag.contains(FOSTER_TAG) && tag.getBoolean(FOSTER_TAG);
        orphaned = tag.contains(ORPHANED_TAG) && tag.getBoolean(ORPHANED_TAG);
        babyGameMode = tag.contains(BABY_GAME_MODE_TAG) ? tag.getString(BABY_GAME_MODE_TAG).toLowerCase(java.util.Locale.ROOT) : "survival";
        if (!setBabyGameMode(babyGameMode)) babyGameMode = "survival";
        sleepingOnBed = tag.contains("SleepingOnBed") && tag.getBoolean("SleepingOnBed");

        if (tag.hasUUID(BIOLOGICAL_PARENT_UUID_TAG)) {
            biologicalParentUUID = tag.getUUID(BIOLOGICAL_PARENT_UUID_TAG);
        }
        biologicalParentName = tag.getString(BIOLOGICAL_PARENT_NAME_TAG);

        /*
         * Backward compatibility for Babies created before relationship
         * lineage NBT existed. A non-foster Baby's existing Owner is its
         * biological parent unless explicit lineage data says otherwise.
         */
        if (biologicalParentUUID == null
                && !foster
                && ownerUUID != null) {
            biologicalParentUUID = ownerUUID;
            biologicalParentName = ownerName;
        }

        foodLevel = readFoodLevelTag(tag, 20.0F);
        saturationLevel = tag.contains(SATURATION_LEVEL_TAG) ? tag.getFloat(SATURATION_LEVEL_TAG) : 5.0F;
        if (!level().isClientSide) {
            entityData.set(SYNCED_FOOD_LEVEL, foodLevel);
        }
        exhaustionLevel = tag.contains(EXHAUSTION_LEVEL_TAG) ? tag.getFloat(EXHAUSTION_LEVEL_TAG) : 0.0F;

        babyInventory.clearContent();
        if (tag.contains("BabyInventory", Tag.TAG_LIST)) {
            ListTag babyInv = tag.getList("BabyInventory", Tag.TAG_COMPOUND);
            for (int i = 0; i < babyInv.size(); i++) {
                CompoundTag entry = babyInv.getCompound(i);
                int slot = entry.getByte("Slot") & 255;
                if (slot >= 0 && slot < babyInventory.getContainerSize() && entry.contains("Item", Tag.TAG_COMPOUND)) {
                    babyInventory.setItem(slot, ItemStack.of(entry.getCompound("Item")));
                }
            }
        }

        if (tag.contains("BabyEquipment", Tag.TAG_COMPOUND)) {
            CompoundTag eq = tag.getCompound("BabyEquipment");
            loadEquipmentSlot(eq, "MainHand", EquipmentSlot.MAINHAND);
            loadEquipmentSlot(eq, "OffHand", EquipmentSlot.OFFHAND);
            loadEquipmentSlot(eq, "Head", EquipmentSlot.HEAD);
            loadEquipmentSlot(eq, "Chest", EquipmentSlot.CHEST);
            loadEquipmentSlot(eq, "Legs", EquipmentSlot.LEGS);
            loadEquipmentSlot(eq, "Feet", EquipmentSlot.FEET);
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            getPersistentData().putBoolean(BabyEquipmentLogic.manualKey(slot), tag.getBoolean(BabyEquipmentLogic.manualKey(slot)));
        }
        if (tag.contains(BabyEquipmentLogic.MAINHAND_ROLE_TAG)) {
            getPersistentData().putString(BabyEquipmentLogic.MAINHAND_ROLE_TAG, tag.getString(BabyEquipmentLogic.MAINHAND_ROLE_TAG));
        }

        totalExperience = Math.max(0, tag.contains(TOTAL_XP_TAG) ? tag.getInt(TOTAL_XP_TAG) : 0);
        experienceLevel = Math.max(0, tag.contains(EXPERIENCE_LEVEL_TAG) ? tag.getInt(EXPERIENCE_LEVEL_TAG) : 0);
        foodLevel = Math.max(0.0F, Math.min(20.0F, foodLevel));
        saturationLevel = Math.max(0.0F, Math.min(20.0F, saturationLevel));
        exhaustionLevel = Math.max(0.0F, Math.min(40.0F, exhaustionLevel));
    }

    private void loadEquipmentSlot(CompoundTag root, String key, EquipmentSlot slot) {
        if (root.contains(key, Tag.TAG_COMPOUND)) {
            setItemSlot(slot, ItemStack.of(root.getCompound(key)));
        }
    }

    public int getTotalExperience() {
        return Math.max(0, totalExperience);
    }

    public int getExperienceLevel() {
        return Math.max(0, experienceLevel);
    }

    public void setTotalExperience(int value) {
        totalExperience = Math.max(0, value);
    }

    public void setExperienceLevel(int value) {
        experienceLevel = Math.max(0, value);
    }

    /**
     * Vanilla-player XP curve for Minecraft 1.20.1.
     * Returns the XP required to advance from the supplied level.
     */
    public static int getXpNeededForNextLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        if (level >= 15) {
            return 37 + (level - 15) * 5;
        }
        return 7 + level * 2;
    }

    /** XP accumulated at the beginning of a vanilla level. */
    public static int getXpAtLevel(int level) {
        if (level <= 0) return 0;
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) (2.5D * level * level - 40.5D * level + 360.0D);
        }
        return (int) (4.5D * level * level - 162.5D * level + 2220.0D);
    }

    /** Current progress inside the Baby's current level. */
    public int getExperienceProgress() {
        return Math.max(0, totalExperience - getXpAtLevel(experienceLevel));
    }

    /**
     * Adds XP exactly like a player gaining XP: total XP increases and the
     * level advances whenever the current level's vanilla threshold is met.
     */
    public void addExperience(int amount) {
        if (amount <= 0) return;

        totalExperience = Math.max(0, totalExperience + amount);
        int level = Math.max(0, experienceLevel);

        // Keep an old/inconsistent snapshot from preventing level-up.
        while (totalExperience >= getXpAtLevel(level + 1)) {
            level++;
        }
        experienceLevel = level;
    }

    private void tickExperienceOrbs() {
        /*
         * Use the same attraction shape as vanilla ExperienceOrb: an 8-block
         * magnet, quadratic falloff, and a small per-tick acceleration. The
         * old implementation used a large 0.16 pull and a 1.35-block pickup
         * radius, which made the orb snap into the Baby and disappear too
         * early.
         *
         * When a real Player is closer to the orb, the Player wins naturally.
         * The Baby only competes for an orb when it is at least as close as the
         * nearest Player, so the Baby does not globally steal XP.
         */
        final double maxDist = 8.0D;
        final double pickupRadius = 0.55D;

        Player nearestPlayer = level().getNearestPlayer(this, maxDist);
        AABB searchBox = getBoundingBox().inflate(maxDist);

        for (ExperienceOrb orb : level().getEntitiesOfClass(
                ExperienceOrb.class, searchBox)) {
            if (orb.isRemoved() || !orb.isAlive()) {
                continue;
            }

            Vec3 babyTarget = position()
                    .add(0.0D, getBbHeight() * 0.5D, 0.0D);
            Vec3 toBaby = babyTarget.subtract(orb.position());
            double distance = toBaby.length();
            if (distance <= 0.001D || distance > maxDist) {
                continue;
            }

            double playerDistance = nearestPlayer == null
                    ? Double.POSITIVE_INFINITY
                    : nearestPlayer.position().add(
                    0.0D,
                    nearestPlayer.getBbHeight() * 0.5D,
                    0.0D
            ).distanceTo(orb.position());

            // The Baby wins only the local pickup race. A strictly closer
            // Player keeps the orb; equal-distance ties alternate by orb id.
            boolean babyWins = distance < playerDistance - 0.05D
                    || (Math.abs(distance - playerDistance) <= 0.05D
                    && (orb.getId() & 1) == (getId() & 1));

            if (distance <= pickupRadius && babyWins) {
                int value = Math.max(0, orb.getValue());
                if (value > 0) {
                    addExperience(value);
                    level().playSound(
                            null,
                            blockPosition(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP,
                            SoundSource.PLAYERS,
                            0.10F,
                            0.90F + random.nextFloat() * 0.20F
                    );
                }
                orb.discard();
                continue;
            }

            if (!babyWins) {
                continue;
            }

            // Vanilla-style XP-orb attraction: quadratic falloff toward the
            // Baby's head/upper body, with the orb's existing velocity kept so
            // the flight remains smooth instead of snapping to the target.
            double x = toBaby.x / maxDist;
            double y = toBaby.y / maxDist;
            double z = toBaby.z / maxDist;
            double normalizedDistance = Math.sqrt(x * x + y * y + z * z);
            double power = 1.0D - normalizedDistance;
            if (power > 0.0D && normalizedDistance > 0.0001D) {
                power *= power;
                Vec3 velocity = orb.getDeltaMovement().add(
                        (x / normalizedDistance) * power * 0.10D,
                        (y / normalizedDistance) * power * 0.10D,
                        (z / normalizedDistance) * power * 0.10D
                );
                orb.setDeltaMovement(velocity);
            }
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}