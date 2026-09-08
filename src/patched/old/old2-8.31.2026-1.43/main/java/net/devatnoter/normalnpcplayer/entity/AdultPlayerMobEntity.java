package net.devatnoter.normalnpcplayer.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.devatnoter.normalnpcplayer.ai.normal.NormalBrain;
import net.devatnoter.normalnpcplayer.ai.hunter.HunterBrain;

import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import java.util.UUID;

/**
 * Adult Player Mob.
 *
 * Identity rules:
 * - No PlayerMobProfileName -> no GameProfile, no random name, no nametag.
 * - PlayerMobProfileName -> that Minecraft player profile is used for skin/cape.
 * - If no explicit CustomName exists, PlayerMobProfileName is also used as the
 *   visible nametag. A supplied CustomName always wins.
 * - Random identity is used ONLY by Baby -> Adult growth.
 *
 * Trait rules:
 * - RUNNER: faster movement.
 * - DEFAULT: normal movement.
 * The trait is randomized once and persisted in NBT.
 */
public class AdultPlayerMobEntity extends PathfinderMob implements RangedAttackMob {

    public static final String PROFILE_UUID_TAG = "PlayerMobProfileUUID";
    public static final String PROFILE_NAME_TAG = "PlayerMobProfileName";
    public static final String SLIM_TAG = "PlayerMobSlim";
    public static final String TRAIT_TAG = "PlayerMobTrait";
    public static final String HUNTER_TARGET_TAG = "HunterTarget";
    public static final String PROFILE_DISPLAY_NAME_TAG = "PlayerMobProfileDisplayName";

    private static final EntityDataAccessor<Boolean> PROFILE_PRESENT =
            SynchedEntityData.defineId(
                    AdultPlayerMobEntity.class,
                    EntityDataSerializers.BOOLEAN
            );

    private static final EntityDataAccessor<String> PROFILE_UUID =
            SynchedEntityData.defineId(
                    AdultPlayerMobEntity.class,
                    EntityDataSerializers.STRING
            );

    private static final EntityDataAccessor<String> PROFILE_NAME =
            SynchedEntityData.defineId(
                    AdultPlayerMobEntity.class,
                    EntityDataSerializers.STRING
            );

    private static final EntityDataAccessor<Boolean> SLIM =
            SynchedEntityData.defineId(
                    AdultPlayerMobEntity.class,
                    EntityDataSerializers.BOOLEAN
            );

    private static final EntityDataAccessor<String> TRAIT =
            SynchedEntityData.defineId(
                    AdultPlayerMobEntity.class,
                    EntityDataSerializers.STRING
            );

    /** Real Minecraft identities are used so the vanilla texture service can resolve them. */
    private static final String[] RANDOM_PROFILE_NAMES = {
            "Notch",
            "jeb_",
            "Dinnerbone"
    };

    private static final UUID NOTCH_UUID =
            UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private static final UUID JEB_UUID =
            UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");

    private static final UUID DINNERBONE_UUID =
            UUID.fromString("b876ec32-e396-476b-a115-8438d83c67d4");

    public enum PlayerMobTrait {
        NORMAL,
        HUNTER,
        RUNNER,
        DEFAULT
    }

    private volatile GameProfile gameProfile;
    private boolean slim;
    private PlayerMobTrait trait;
    private boolean profileNameDisplayFallback;
    private volatile boolean profileLookupStarted;
    private String hunterTargetName;

    /** Small internal inventory used by NORMAL trait for pickup/build/store actions. */
    private final SimpleContainer inventory = new SimpleContainer(27);

    public AdultPlayerMobEntity(
            EntityType<? extends PathfinderMob> entityType,
            Level level
    ) {
        super(entityType, level);

        // Plain /summon must be a nameless base player mob.
        this.gameProfile = null;
        this.slim = false;
        this.trait = randomTrait();
        this.profileNameDisplayFallback = false;
        this.profileLookupStarted = false;
        this.hunterTargetName = "";

        // defineSynchedData runs before this constructor body, so the random
        // trait must be written here rather than in defineSynchedData().
        this.entityData.set(TRAIT, this.trait.name());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();

        entityData.define(PROFILE_PRESENT, false);
        entityData.define(PROFILE_UUID, "");
        entityData.define(PROFILE_NAME, "");
        entityData.define(SLIM, false);
        entityData.define(TRAIT, PlayerMobTrait.DEFAULT.name());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        NormalBrain.registerGoals(this);
        HunterBrain.registerGoals(this);
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        applyTraitSpeed();
    }

    /**
     * Returns the profile used by the client renderer.
     *
     * A name-only profile is valid in Authlib. The client resolves its real
     * UUID/textures asynchronously through the same skull/player-profile
     * lookup path Minecraft uses, so /summon never blocks the server thread.
     */
    public GameProfile getGameProfile() {
        if (gameProfile == null && entityData.get(PROFILE_PRESENT)) {
            gameProfile = createProfileFromSynchedData();
        }

        if (level().isClientSide
                && gameProfile != null
                && !gameProfile.isComplete()
                && !profileLookupStarted) {
            profileLookupStarted = true;

            SkullBlockEntity.updateGameprofile(
                    gameProfile,
                    this::onClientProfileResolved
            );
        }

        return gameProfile;
    }

    private GameProfile createProfileFromSynchedData() {
        String name = entityData.get(PROFILE_NAME);
        if (name == null || name.isBlank()) {
            return null;
        }

        String uuidText = entityData.get(PROFILE_UUID);
        if (uuidText != null && !uuidText.isBlank()) {
            try {
                return new GameProfile(
                        UUID.fromString(uuidText),
                        name
                );
            } catch (IllegalArgumentException ignored) {
                // Fall back to a valid name-only profile below.
            }
        }

        return new GameProfile(null, name);
    }

    private void onClientProfileResolved(GameProfile resolved) {
        if (resolved != null
                && resolved.getName() != null
                && !resolved.getName().isBlank()) {
            this.gameProfile = resolved;
        }

        this.profileLookupStarted = false;
    }

    /**
     * Used by future systems that already have a GameProfile.
     * It never changes CustomName automatically.
     */
    public void setGameProfile(GameProfile profile) {
        if (profile == null
                || profile.getName() == null
                || profile.getName().isBlank()) {
            clearPlayerProfile();
            return;
        }

        this.gameProfile = profile;
        this.profileLookupStarted = false;
        this.profileNameDisplayFallback = true;
        syncProfile();
        applyProfileNameAsDisplayNameIfNeeded();
    }

    /**
     * Sets PlayerMobProfileName only. CustomName remains independent.
     */
    public void setProfileIdentity(
            String name,
            UUID profileUuid,
            boolean slim
    ) {
        setProfileIdentityInternal(
                name,
                profileUuid,
                slim,
                true
        );
    }

    private void setProfileIdentityInternal(
            String name,
            UUID profileUuid,
            boolean slim,
            boolean profileNameDisplayFallback
    ) {
        if (name == null || name.isBlank()) {
            clearPlayerProfile();
            return;
        }

        UUID safeUuid = profileUuid != null
                ? profileUuid
                : knownProfileUuid(name);

        // For unknown names safeUuid is null. This is intentional: it keeps
        // the requested username intact and lets the client resolve the real
        // Mojang profile asynchronously instead of freezing the server.
        this.gameProfile = new GameProfile(safeUuid, name.trim());
        this.slim = slim;
        this.profileNameDisplayFallback = profileNameDisplayFallback;
        this.profileLookupStarted = false;

        syncProfile();
        applyProfileNameAsDisplayNameIfNeeded();
    }

    /**
     * ONLY used during Baby -> Adult growth when the Baby has no CustomName.
     */
    public void setRandomIdentity() {
        String name = RANDOM_PROFILE_NAMES[
                this.random.nextInt(RANDOM_PROFILE_NAMES.length)
                ];

        setProfileIdentityInternal(
                name,
                knownProfileUuid(name),
                this.random.nextBoolean(),
                false
        );
    }

    public void clearPlayerProfile() {
        this.gameProfile = null;
        this.slim = false;
        this.profileNameDisplayFallback = false;
        this.profileLookupStarted = false;

        entityData.set(PROFILE_PRESENT, false);
        entityData.set(PROFILE_UUID, "");
        entityData.set(PROFILE_NAME, "");
        entityData.set(SLIM, false);

        // Deliberately do NOT create/remove CustomName here.
    }

    public boolean hasPlayerProfile() {
        return gameProfile != null
                || entityData.get(PROFILE_PRESENT);
    }

    public boolean isSlim() {
        if (gameProfile == null && entityData.get(PROFILE_PRESENT)) {
            return entityData.get(SLIM);
        }
        return slim;
    }

    public void setSlim(boolean slim) {
        this.slim = slim;
        entityData.set(SLIM, slim);
    }

    /** Restores the Baby's explicit Name Tag during growth. */
    public void setPreservedCustomName(Component customName) {
        if (customName == null) {
            return;
        }

        setCustomName(customName);
        setCustomNameVisible(true);
    }

    private void applyProfileNameAsDisplayNameIfNeeded() {
        if (gameProfile == null
                || gameProfile.getName() == null
                || gameProfile.getName().isBlank()
                || !profileNameDisplayFallback
                || hasCustomName()) {
            return;
        }

        setCustomName(Component.literal(gameProfile.getName()));
        setCustomNameVisible(true);
    }

    public PlayerMobTrait getTrait() {
        String value = entityData.get(TRAIT);
        if (value == null || value.isBlank()) {
            return trait == null ? PlayerMobTrait.DEFAULT : trait;
        }

        try {
            return PlayerMobTrait.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return PlayerMobTrait.DEFAULT;
        }
    }

    public void setTrait(PlayerMobTrait trait) {
        this.trait = trait == null
                ? PlayerMobTrait.DEFAULT
                : trait;

        entityData.set(TRAIT, this.trait.name());
        applyTraitSpeed();
    }

    public void randomizeTrait() {
        setTrait(randomTrait());
    }

    private PlayerMobTrait randomTrait() {
        return this.random.nextBoolean()
                ? PlayerMobTrait.RUNNER
                : PlayerMobTrait.DEFAULT;
    }

    private void applyTraitSpeed() {
        if (getAttribute(Attributes.MOVEMENT_SPEED) == null) {
            return;
        }

        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(
                getTrait() == PlayerMobTrait.RUNNER
                        ? 0.34D
                        : getTrait() == PlayerMobTrait.NORMAL
                          ? 0.28D
                          : 0.25D
        );
    }

    private void syncProfile() {
        if (gameProfile == null
                || gameProfile.getName() == null
                || gameProfile.getName().isBlank()) {
            clearPlayerProfile();
            return;
        }

        UUID id = gameProfile.getId();

        entityData.set(PROFILE_PRESENT, true);
        entityData.set(
                PROFILE_UUID,
                id == null ? "" : id.toString()
        );
        entityData.set(PROFILE_NAME, gameProfile.getName());
        entityData.set(SLIM, slim);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        if (gameProfile != null
                && gameProfile.getName() != null
                && !gameProfile.getName().isBlank()) {
            tag.putString(
                    PROFILE_NAME_TAG,
                    gameProfile.getName()
            );

            if (gameProfile.getId() != null) {
                tag.putUUID(
                        PROFILE_UUID_TAG,
                        gameProfile.getId()
                );
            }
        }

        tag.putBoolean(SLIM_TAG, slim);
        tag.put("Inventory", inventory.createTag());
        tag.putBoolean(PROFILE_DISPLAY_NAME_TAG, profileNameDisplayFallback);
        tag.putString(TRAIT_TAG, getTrait().name());
        if (!getHunterTargetName().isBlank()) {
            tag.putString(HUNTER_TARGET_TAG, getHunterTargetName());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        String profileName = tag.contains(PROFILE_NAME_TAG)
                ? tag.getString(PROFILE_NAME_TAG).trim()
                : "";

        if (!profileName.isBlank()) {
            this.profileNameDisplayFallback = tag.contains(PROFILE_DISPLAY_NAME_TAG)
                    ? tag.getBoolean(PROFILE_DISPLAY_NAME_TAG)
                    : true;

            UUID profileUuid = tag.hasUUID(PROFILE_UUID_TAG)
                    ? tag.getUUID(PROFILE_UUID_TAG)
                    : knownProfileUuid(profileName);

            this.slim = tag.contains(SLIM_TAG)
                    && tag.getBoolean(SLIM_TAG);

            this.gameProfile = new GameProfile(
                    profileUuid,
                    profileName
            );

            this.profileLookupStarted = false;
            syncProfile();

            // super.readAdditionalSaveData() has already restored CustomName.
            // Only an explicitly declared profile name may become a fallback
            // nametag; it must never overwrite a CustomName.
            applyProfileNameAsDisplayNameIfNeeded();
        } else {
            clearPlayerProfile();
        }

        if (tag.contains("Inventory", 9)) {
            inventory.fromTag(tag.getList("Inventory", 10));
        }

        if (tag.contains(TRAIT_TAG)) {
            try {
                this.trait = PlayerMobTrait.valueOf(
                        tag.getString(TRAIT_TAG)
                );
            } catch (IllegalArgumentException ignored) {
                this.trait = PlayerMobTrait.DEFAULT;
            }
        }

        this.hunterTargetName = tag.contains(HUNTER_TARGET_TAG)
                ? tag.getString(HUNTER_TARGET_TAG).trim()
                : "";

        entityData.set(TRAIT, this.trait.name());
        applyTraitSpeed();
    }


    public String getHunterTargetName() {
        return hunterTargetName == null ? "" : hunterTargetName;
    }

    public void setHunterTargetName(String name) {
        this.hunterTargetName = name == null ? "" : name.trim();
    }

    public SimpleContainer getTraitInventory() {
        return inventory;
    }

    @Override
    public void performRangedAttack(net.minecraft.world.entity.LivingEntity target, float velocity) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ItemStack bow = getItemBySlot(EquipmentSlot.MAINHAND);
        if (!(bow.getItem() instanceof BowItem)) {
            return;
        }

        ItemStack arrows = findArrowStack();
        if (arrows.isEmpty()) {
            return;
        }

        var arrow = net.minecraft.world.entity.projectile.ProjectileUtil.getMobArrow(
                this,
                new ItemStack(Items.ARROW),
                1.0F
        );
        double dx = target.getX() - getX();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.2D, dz, velocity, 14.0F);
        serverLevel.addFreshEntity(arrow);
        arrows.shrink(1);
    }

    private ItemStack findArrowStack() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof ArrowItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void tick() {
        super.tick();
        applyTraitSpeed();

        if (level().isClientSide) {
            return;
        }

        switch (getTrait()) {
            case NORMAL -> NormalBrain.tick(this);
            case HUNTER -> HunterBrain.tick(this);
            default -> { }
        }
    }

    @Override
    protected void dropAllDeathLoot(net.minecraft.world.damagesource.DamageSource source) {
        super.dropAllDeathLoot(source);
        for (ItemStack stack : inventory.removeAllItems()) {
            if (!stack.isEmpty()) {
                spawnAtLocation(stack);
            }
        }
    }

    private static UUID knownProfileUuid(String name) {
        if ("Notch".equalsIgnoreCase(name)) {
            return NOTCH_UUID;
        }
        if ("jeb_".equalsIgnoreCase(name)) {
            return JEB_UUID;
        }
        if ("Dinnerbone".equalsIgnoreCase(name)) {
            return DINNERBONE_UUID;
        }
        return null;
    }
}