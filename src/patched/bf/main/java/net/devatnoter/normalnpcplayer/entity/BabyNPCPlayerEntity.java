package net.devatnoter.normalnpcplayer.entity;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;

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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
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
import net.devatnoter.normalnpcplayer.registry.ModSounds;

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

    /** Server-side GUI state. While the owner has the Baby GUI open, the Baby
     * stands still and faces that owner. This is deliberately separate from
     * the head-riding state. */
    private boolean inventoryOpen;
    private UUID inventoryViewerUUID;

    /** Player-like survival state. Serialized so pickup/place never resets it. */
    public static final String FOOD_LEVEL_TAG = "BabyFoodLevel";
    public static final String SATURATION_LEVEL_TAG = "BabySaturation";
    public static final String EXHAUSTION_LEVEL_TAG = "BabyExhaustion";
    public static final String TOTAL_XP_TAG = "BabyTotalExperience";
    public static final String EXPERIENCE_LEVEL_TAG = "BabyExperienceLevel";
    private float foodLevel = 20.0F;
    private float saturationLevel = 5.0F;
    private float exhaustionLevel = 0.0F;
    private int totalExperience = 0;
    private int experienceLevel = 0;

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
                new FollowOwnerGoal(this, 1.0D, 13.0D, 1.0D)
        );

        goalSelector.addGoal(
                3,
                new TemptGoal(this, 1.15D, Ingredient.of(Items.MILK_BUCKET), false)
        );

        goalSelector.addGoal(4, new MilkBucketPickupGoal(this));

        goalSelector.addGoal(
                5,
                new OwnerAreaWanderGoal(this, 1.0D, 8.0D, 12.0D)
        );

        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
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
        super.tick();

        // Baby has a player-like XP system: nearby XP orbs are magnetized
        // toward the Baby and absorbed when they reach it.
        if (!level().isClientSide) {
            tickExperienceOrbs();
        }

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

        if (!level().isClientSide) {
            // Mirror vanilla player-target semantics instead of forcing every
            // hostile mob in range to attack the Baby. The Baby is only eligible
            // when that mob would naturally consider a player a valid target.
            if (tickCount % 5 == 0 && !initialRideActive) {
                updatePlayerLikeHostility();
            }

            ServerPlayer owner = getOwner();

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
            if (player.getUUID().equals(ownerUUID)) {
                if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                    mountOnHead(serverPlayer);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }

        if (isBabyInventoryOpener(stack)) {
            if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                net.minecraftforge.network.NetworkHooks.openScreen(
                        serverPlayer,
                        new net.minecraft.world.MenuProvider() {
                            @Override
                            public net.minecraft.network.chat.Component getDisplayName() {
                                return net.minecraft.network.chat.Component.literal("Baby Inventory");
                            }

                            @Override
                            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                                    int id, net.minecraft.world.entity.player.Inventory inventory, Player ignored) {
                                return new net.devatnoter.normalnpcplayer.menu.BabyInventoryMenu(id, inventory, BabyNPCPlayerEntity.this);
                            }
                        },
                        buf -> buf.writeVarInt(getId())
                );
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (!stack.is(net.minecraft.world.item.Items.MILK_BUCKET)) {
            return super.mobInteract(player, hand);
        }

        if (getFoodLevelExact() >= 20.0F) {
            return InteractionResult.PASS;
        }

        if (!level().isClientSide) {
            feedMilk();

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    player.setItemInHand(
                            hand,
                            new net.minecraft.world.item.ItemStack(
                                    net.minecraft.world.item.Items.BUCKET));
                } else {
                    player.getInventory().placeItemBackInInventory(
                            new net.minecraft.world.item.ItemStack(
                                    net.minecraft.world.item.Items.BUCKET));
                }
            }

        }

        return InteractionResult.sidedSuccess(level().isClientSide);
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
            playBabyVoice(ModSounds.BABY_HUNGRY.get());
            return;
        }

        if (getFoodLevelExact() < 13.0F
                && random.nextFloat() < HUNGRY_VOICE_CHANCE) {
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

        // The previous idle/hungry sound has no server-side SoundInstance id,
        // so stop the exact sound event on listeners before starting scared.
        // The packet is sent only to players within the same audible radius.
        ResourceLocation idleId = ModSounds.BABY_IDLE.get().getLocation();
        ResourceLocation hungryId = ModSounds.BABY_HUNGRY.get().getLocation();
        Vec3 head = getEyePosition(1.0F);
        AABB hearingBox = new AABB(head, head).inflate(32.0D);
        for (ServerPlayer player : ((ServerLevel) level()).getEntitiesOfClass(
                ServerPlayer.class, hearingBox)) {
            player.connection.send(new ClientboundStopSoundPacket(idleId, SoundSource.NEUTRAL));
            player.connection.send(new ClientboundStopSoundPacket(hungryId, SoundSource.NEUTRAL));
        }

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
        // Keep this deliberately player-like:
        // - food is an exact 0.0..20.0 value so custom 1.5-point feeds are lossless.
        // - movement creates exhaustion.
        // - exhaustion drains saturation first, then food.
        // - food >= 18 allows natural regeneration.
        // - food == 0 can eventually cause starvation damage.
        double movement = getDeltaMovement().horizontalDistanceSqr();
        if (movement > 0.000001D) {
            exhaustionLevel = Math.min(40.0F, exhaustionLevel + 0.01F);
        }

        if (exhaustionLevel >= 4.0F) {
            exhaustionLevel -= 4.0F;
            if (saturationLevel > 0.0F) {
                saturationLevel = Math.max(0.0F, saturationLevel - 1.0F);
            } else if (foodLevel > 0.0F) {
                foodLevel = Math.max(0.0F, foodLevel - 1.0F);
            }
        }

        if (getFoodLevel() >= 18
                && getHealth() < getMaxHealth()
                && tickCount % 10 == 0
                && level().getGameRules().getBoolean(
                        GameRules.RULE_NATURAL_REGENERATION)) {
            heal(1.0F);
            exhaustionLevel = Math.min(40.0F, exhaustionLevel + 3.0F);
        }

        if (!level().isClientSide) {
            entityData.set(SYNCED_FOOD_LEVEL, foodLevel);
        }

        if (foodLevel <= 0.0F
                && getHealth() > 0.0F
                && tickCount % 80 == 0) {
            // Starvation damage follows the player's cadence.
            if (level().getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
                    && (level().getDifficulty() != net.minecraft.world.Difficulty.EASY
                    || getHealth() > 10.0F)
                    && (level().getDifficulty() != net.minecraft.world.Difficulty.NORMAL
                    || getHealth() > 1.0F)) {
                hurt(level().damageSources().starve(), 1.0F);
            }
        }
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
        saturationLevel = Math.min(20.0F, saturationLevel + nutrition * saturationModifier * 2.0F);
        if (!level().isClientSide) {
            entityData.set(SYNCED_FOOD_LEVEL, foodLevel);
        }
    }

    private static boolean isBabyInventoryOpener(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem
                || stack.getItem() instanceof ShieldItem
                || stack.getItem() instanceof ArrowItem
                || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof PickaxeItem
                || stack.getItem() instanceof HoeItem
                || stack.getItem() instanceof ShovelItem
                || stack.is(Items.TOTEM_OF_UNDYING);
    }

    public boolean feedMilk() {
        if (getFoodLevelExact() >= 20.0F) {
            return false;
        }

        // Milk is a Baby-specific drink: exactly +1.5 hunger, whether the
        // Baby receives the bucket from a player or finds a dropped bucket.
        addFood(1.5F, 0.0F);
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
        ServerPlayer owner = isFoster() ? null : getOwner();

        super.die(source);

        if (!level().isClientSide && owner != null && owner.isAlive()) {
            net.devatnoter.normalnpcplayer.event.LifeBondEvents.killOwner(owner);
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
                Math.min(
                        MAX_TEXTURE_INDEX,
                        textureIndex
                )
        );

        /*
         * SynchedEntityData is the single source of truth. The client gets
         * this value through normal entity synchronization.
         */
        this.entityData.set(
                SYNCED_TEXTURE_INDEX,
                clamped
        );

        if (specialVariantId == null || specialVariantId.isBlank()) {
            this.textureId = textureIdFromIndex(clamped);
            this.entityData.set(SYNCED_TEXTURE_ID, this.textureId);
        }
    }

    public int getTextureIndex() {
        return this.entityData.get(
                SYNCED_TEXTURE_INDEX
        );
    }

    public int getSyncedTextureIndex() {
        return this.entityData.get(
                SYNCED_TEXTURE_INDEX
        );
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
                setTextureIndex(
                        Integer.parseInt(
                                textureId.substring(4)
                        )
                );
                return;
            } catch (NumberFormatException ignored) {
                return;
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
        this.specialVariantId = specialVariantId == null ? "" : specialVariantId.trim();
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
            this.specialVariantId = special.id();
            setTextureId(special.textureId());
            return;
        }

        this.specialVariantId = "";
        setTextureIndex(
                random.nextInt(
                        MAX_TEXTURE_INDEX
                                - MIN_TEXTURE_INDEX
                                + 1
                ) + MIN_TEXTURE_INDEX
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
            if (baby.level().isClientSide || baby.initialRideActive) {
                return false;
            }

            owner = baby.getOwner();

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
            return target != null
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
            if (baby.level().isClientSide || baby.initialRideActive) {
                return false;
            }

            owner = baby.getOwner();
            return owner != null
                    && !baby.isOrphaned()
                    && owner.isAlive()
                    && !owner.isSpectator()
                    && owner.level() == baby.level()
                    && baby.hasNearbyHostile(detectRadius);
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null
                    && !baby.isOrphaned()
                    && owner.isAlive()
                    && !owner.isSpectator()
                    && owner.level() == baby.level()
                    && !baby.initialRideActive
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
            if (baby.level().isClientSide) {
                return false;
            }

            if (baby.initialRideActive) {
                return false;
            }

            owner = baby.getOwner();

            if (owner == null) {
                return false;
            }

            return baby.distanceToSqr(owner)
                    > startDistance * startDistance;
        }

        @Override
        public boolean canContinueToUse() {
            if (owner == null) {
                return false;
            }

            if (baby.initialRideActive || baby.isOrphaned()) {
                return false;
            }

            if (!owner.isAlive() || owner.isSpectator()) {
                return false;
            }

            return baby.distanceToSqr(owner)
                    > stopDistance * stopDistance;
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
            if (owner == null) {
                return;
            }

            baby.getLookControl().setLookAt(
                    owner,
                    10.0F,
                    baby.getMaxHeadXRot()
            );

            navigation.moveTo(
                    owner,
                    speedModifier
            );
        }
    }

    public void setItemRenderMode(boolean value) {
        this.itemRenderMode = value;
    }

    public boolean isItemRenderMode() {
        return itemRenderMode;
    }

    private static final class MilkBucketPickupGoal extends Goal {
        private final BabyNPCPlayerEntity baby;
        private ItemEntity target;

        private MilkBucketPickupGoal(BabyNPCPlayerEntity baby) {
            this.baby = baby;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (baby.getFoodLevelExact() >= 20.0F) return false;
            target = findNearestMilk();
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null
                    && target.isAlive()
                    && target.getItem().is(Items.MILK_BUCKET)
                    && baby.getFoodLevelExact() < 20.0F
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
                if (baby.feedMilk()) {
                    ItemStack bucket = target.getItem();
                    bucket.shrink(1);
                    if (bucket.isEmpty()) {
                        target.setItem(new ItemStack(Items.BUCKET));
                    } else {
                        target.setItem(bucket);
                    }
                    stop();
                }
            }
        }

        private ItemEntity findNearestMilk() {
            return baby.level().getEntitiesOfClass(
                    ItemEntity.class,
                    baby.getBoundingBox().inflate(8.0D),
                    item -> item.isAlive()
                            && item.getItem().is(Items.MILK_BUCKET)
                            && !item.getItem().isEmpty()
            ).stream()
                    .min(java.util.Comparator.comparingDouble(baby::distanceToSqr))
                    .orElse(null);
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

        tag.putBoolean(FOSTER_TAG, foster);
        tag.putBoolean(ORPHANED_TAG, orphaned);

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
    }

    /**
     * Load
     */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("AbsorptionAmount")) {
            setAbsorptionAmount(Math.max(0.0F, tag.getFloat("AbsorptionAmount")));
        }

        if (tag.contains(TEXTURE_INDEX_TAG)) {
            setTextureIndex(
                    tag.getInt(TEXTURE_INDEX_TAG)
            );
        } else if (tag.contains(TEXTURE_ID_TAG)) {
            setTextureId(
                    tag.getString(TEXTURE_ID_TAG)
            );
        }

        if (tag.contains(PROFILE_NAME_TAG)) {
            profileName = tag.getString(PROFILE_NAME_TAG).trim();
        }
        if (tag.contains(SPECIAL_VARIANT_TAG)) {
            specialVariantId = tag.getString(SPECIAL_VARIANT_TAG).trim();
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

        if (tag.hasUUID("OwnerUUID")) {
            ownerUUID = tag.getUUID("OwnerUUID");
        }

        ownerName = tag.getString("OwnerName");

        foster = tag.contains(FOSTER_TAG) && tag.getBoolean(FOSTER_TAG);
        orphaned = tag.contains(ORPHANED_TAG) && tag.getBoolean(ORPHANED_TAG);

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
        // A modest magnet radius keeps this useful without stealing XP from
        // a player standing several blocks away. The orb itself is moved, so
        // the Baby's navigation/AI is never disturbed.
        final double magnetRadius = 6.0D;
        final double pickupRadius = 1.35D;

        AABB searchBox = getBoundingBox().inflate(magnetRadius);
        for (ExperienceOrb orb : level().getEntitiesOfClass(
                ExperienceOrb.class, searchBox)) {

            if (orb.isRemoved() || !orb.isAlive()) {
                continue;
            }

            Vec3 toBaby = position().add(0.0D, getBbHeight() * 0.5D, 0.0D)
                    .subtract(orb.position());
            double distance = toBaby.length();
            if (distance <= pickupRadius) {
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

            if (distance > 0.001D && distance <= magnetRadius) {
                Vec3 pull = toBaby.normalize();
                Vec3 velocity = orb.getDeltaMovement().scale(0.82D)
                        .add(pull.scale(0.16D));
                orb.setDeltaMovement(velocity);
            }
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}