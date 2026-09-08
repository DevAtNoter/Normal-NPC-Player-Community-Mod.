package net.devatnoter.normalnpcplayer.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.UUID;

public class BabyNPCPlayerEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Integer> SYNCED_TEXTURE_INDEX =
            SynchedEntityData.defineId(
                    BabyNPCPlayerEntity.class,
                    EntityDataSerializers.INT
            );

    /**
     * Single Source of Truth for this Baby's appearance.
     * The first randomized value is persisted and reused by the carrier Item.
     */
    public static final String TEXTURE_INDEX_TAG = "TextureIndex";
    public static final String TEXTURE_ID_TAG = "TextureId";
    public static final int MIN_TEXTURE_INDEX = 1;
    public static final int MAX_TEXTURE_INDEX = 4;
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
     * Initial spawn ride state.
     *
     * While active:
     * - Baby stays on the owner's head.
     * - Baby cannot take damage.
     * - Baby has no physics/collision.
     * - Baby waits until the owner moves.
     */
    private boolean initialRideActive;
    private double lastRideX;
    private double lastRideY;
    private double lastRideZ;

    /**
     * Prevents Baby -> Owner -> Baby recursive death.
     */
    private boolean lifeBondDeathTriggered;
    private String textureId = "baby1";

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

        /*
         * Normal behavior:
         *
         *  - Baby may wander in an 8-12 block area around the owner.
         *  - Once the owner is more than 13 blocks away, follow starts.
         *  - Follow stops at 1 block.
         *  - >20 blocks is an emergency teleport only.
         */
        goalSelector.addGoal(
                1,
                new FollowOwnerGoal(this, 1.0D, 13.0D, 1.0D)
        );

        goalSelector.addGoal(
                2,
                new OwnerAreaWanderGoal(this, 1.0D, 8.0D, 12.0D)
        );

        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();

        entityData.define(
                SYNCED_TEXTURE_INDEX,
                1
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
            this.textureId =
                    textureIdFromIndex(
                            entityData.get(
                                    SYNCED_TEXTURE_INDEX
                            )
                    );
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (initialRideActive) {
            ServerPlayer owner = getOwner();

            if (owner != null && owner.level() == level()) {
                this.noPhysics = true;
                this.setNoGravity(true);
                this.setInvulnerable(true);
                this.setDeltaMovement(Vec3.ZERO);
                this.fallDistance = 0.0F;

                setPos(
                        owner.getX(),
                        owner.getY() + owner.getBbHeight() + 0.08D,
                        owner.getZ()
                );

                setYRot(owner.getYRot());
                setXRot(0.0F);

                if (!level().isClientSide) {
                    double moved = owner.distanceToSqr(
                            lastRideX,
                            lastRideY,
                            lastRideZ
                    );

                    if (moved > 0.0004D) {
                        Vec3 safePos =
                                findSafeDismountPosition(owner);

                        if (safePos != null) {
                            clearInitialRideState();

                            moveTo(
                                    safePos.x,
                                    safePos.y,
                                    safePos.z,
                                    owner.getYRot(),
                                    0.0F
                            );

                            getNavigation().stop();
                        }
                    }

                    lastRideX = owner.getX();
                    lastRideY = owner.getY();
                    lastRideZ = owner.getZ();
                }

                return;
            }

            clearInitialRideState();
        }

        if (!level().isClientSide) {
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
    public void beginInitialRide(ServerPlayer player) {
        if (player == null || isRemoved()) {
            return;
        }

        initialRideActive = true;
        setInvulnerable(true);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;

        lastRideX = player.getX();
        lastRideY = player.getY();
        lastRideZ = player.getZ();

        setPos(
                player.getX(),
                player.getY() + player.getBbHeight() + 0.08D,
                player.getZ()
        );

        setYRot(player.getYRot());
        setXRot(0.0F);
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

        ServerPlayer owner = getOwner();

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

        this.textureId =
                textureIdFromIndex(clamped);
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
    }

    /**
     * Used only when a genuinely new Baby is created.
     * Never call during ItemStack reconstruction.
     */
    public void initializeRandomAppearance() {
        setTextureIndex(
                this.random.nextInt(
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

            if (baby.initialRideActive) {
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
        initialRideActive = false;
        this.noPhysics = false;
        this.setNoGravity(false);
        this.setInvulnerable(false);
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
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
        if (ownerUUID == null) {
            return null;
        }

        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        return serverLevel.getServer()
                .getPlayerList()
                .getPlayer(ownerUUID);
    }

    /**
     * Save
     */
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        tag.putInt(TEXTURE_INDEX_TAG, getTextureIndex());
        tag.putString(TEXTURE_ID_TAG, getTextureId());

        if (ownerUUID != null) {
            tag.putUUID("OwnerUUID", ownerUUID);
        }

        tag.putString("OwnerName", ownerName);
    }

    /**
     * Load
     */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains(TEXTURE_INDEX_TAG)) {
            setTextureIndex(
                    tag.getInt(TEXTURE_INDEX_TAG)
            );
        } else if (tag.contains(TEXTURE_ID_TAG)) {
            setTextureId(
                    tag.getString(TEXTURE_ID_TAG)
            );
        }

        if (tag.hasUUID("OwnerUUID")) {
            ownerUUID = tag.getUUID("OwnerUUID");
        }

        ownerName = tag.getString("OwnerName");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}