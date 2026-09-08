package net.devatnoter.normalnpcplayer.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.GameProfileCache;

import java.util.Optional;
import java.util.UUID;

/**
 * Adult Player Mob.
 *
 * Identity rules:
 * - No PlayerMobProfileName -> no GameProfile, no random name, no nametag.
 * - PlayerMobProfileName -> real player profile when the server can resolve it.
 * - CustomName is independent and always wins over the profile name for display.
 * - Random identity is used ONLY by Baby -> Adult growth when the Baby has no CustomName.
 *
 * Trait rules:
 * - RUNNER: faster movement.
 * - DEFAULT: normal movement.
 * The trait is randomized once and persisted in NBT.
 */
public class AdultPlayerMobEntity extends PathfinderMob {

    public static final String PROFILE_UUID_TAG = "PlayerMobProfileUUID";
    public static final String PROFILE_NAME_TAG = "PlayerMobProfileName";
    public static final String SLIM_TAG = "PlayerMobSlim";
    public static final String TRAIT_TAG = "PlayerMobTrait";

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

    /** Only names that are valid Minecraft player identities are used. */
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
        RUNNER,
        DEFAULT
    }

    private GameProfile gameProfile;
    private boolean slim;
    private PlayerMobTrait trait;

    public AdultPlayerMobEntity(
            EntityType<? extends PathfinderMob> entityType,
            Level level
    ) {
        super(entityType, level);

        // IMPORTANT: no default profile and no default CustomName.
        // This prevents /summon without NBT from inventing a name.
        this.gameProfile = null;
        this.slim = false;
        this.trait = randomTrait();
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
        goalSelector.addGoal(2, new RandomStrollGoal(this, 1.0D));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        applyTraitSpeed();
    }

    public GameProfile getGameProfile() {
        // Client reconstruction from SynchedEntityData.
        if (gameProfile == null
                && entityData.get(PROFILE_PRESENT)) {
            String uuidText = entityData.get(PROFILE_UUID);
            String name = entityData.get(PROFILE_NAME);

            if (!uuidText.isBlank() && !name.isBlank()) {
                try {
                    gameProfile = new GameProfile(
                            UUID.fromString(uuidText),
                            name
                    );
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }

        return gameProfile;
    }

    public void setGameProfile(GameProfile profile) {
        if (profile == null || profile.getId() == null
                || profile.getName() == null
                || profile.getName().isBlank()) {
            clearPlayerProfile();
            return;
        }

        this.gameProfile = profile;
        this.slim = this.slim;

        syncProfile();

        // Profile name is only the fallback display name.
        // An explicit CustomName always wins.
        if (!hasCustomName()) {
            setCustomName(Component.literal(profile.getName()));
            setCustomNameVisible(true);
        }
    }

    /**
     * Sets a concrete PlayerMob profile.
     */
    public void setProfileIdentity(
            String name,
            UUID profileUuid,
            boolean slim
    ) {
        if (name == null || name.isBlank()) {
            clearPlayerProfile();
            return;
        }

        UUID safeUuid = profileUuid == null
                ? knownProfileUuid(name)
                : profileUuid;

        this.gameProfile = new GameProfile(safeUuid, name);
        this.slim = slim;

        syncProfile();

        if (!hasCustomName()) {
            setCustomName(Component.literal(name));
            setCustomNameVisible(true);
        }

        resolveServerProfile();
    }

    /**
     * Chooses a real Minecraft username. This is intentionally the only
     * automatic profile randomization entry point.
     */
    public void setRandomIdentity() {
        String name = RANDOM_PROFILE_NAMES[
                this.random.nextInt(RANDOM_PROFILE_NAMES.length)
        ];

        setProfileIdentity(
                name,
                knownProfileUuid(name),
                this.random.nextBoolean()
        );
    }

    public void clearPlayerProfile() {
        this.gameProfile = null;
        this.slim = false;

        entityData.set(PROFILE_PRESENT, false);
        entityData.set(PROFILE_UUID, "");
        entityData.set(PROFILE_NAME, "");
        entityData.set(SLIM, false);

        // Deliberately do NOT create a CustomName here.
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

    /**
     * Restores a Baby Name Tag after growth.
     */
    public void setPreservedCustomName(Component customName) {
        if (customName == null) {
            return;
        }

        setCustomName(customName);
        setCustomNameVisible(true);
    }

    public PlayerMobTrait getTrait() {
        if (entityData.get(TRAIT).isBlank()) {
            return trait;
        }

        try {
            return PlayerMobTrait.valueOf(entityData.get(TRAIT));
        } catch (IllegalArgumentException ignored) {
            return PlayerMobTrait.DEFAULT;
        }
    }

    public void setTrait(PlayerMobTrait trait) {
        this.trait = trait == null
                ? PlayerMobTrait.DEFAULT
                : trait;

        entityData.set(
                TRAIT,
                this.trait.name()
        );

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

        getAttribute(Attributes.MOVEMENT_SPEED)
                .setBaseValue(
                        getTrait() == PlayerMobTrait.RUNNER
                                ? 0.34D
                                : 0.25D
                );
    }

    private void syncProfile() {
        if (gameProfile == null
                || gameProfile.getId() == null
                || gameProfile.getName() == null
                || gameProfile.getName().isBlank()) {
            clearPlayerProfile();
            return;
        }

        entityData.set(PROFILE_PRESENT, true);
        entityData.set(PROFILE_UUID, gameProfile.getId().toString());
        entityData.set(PROFILE_NAME, gameProfile.getName());
        entityData.set(SLIM, slim);
    }

    /**
     * Server-side profile resolution. If the profile cache has the real
     * Mojang UUID, use it. The client then resolves the actual skin/cape.
     */
    private void resolveServerProfile() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (gameProfile == null) {
            return;
        }

        String name = gameProfile.getName();
        if (name == null || name.isBlank()) {
            return;
        }

        try {
            GameProfileCache cache =
                    serverLevel.getServer().getProfileCache();

            if (cache == null) {
                return;
            }

            Optional<GameProfile> cached = cache.get(name);
            if (cached.isEmpty()) {
                return;
            }

            GameProfile resolved = cached.get();

            try {
                resolved = serverLevel.getServer()
                        .getMinecraftSessionService()
                        .fillProfileProperties(
                                resolved,
                                false
                        );
            } catch (Exception ignored) {
                // UUID is still useful even when texture filling is unavailable.
            }

            if (resolved.getId() != null) {
                this.gameProfile = resolved;
                syncProfile();
            }
        } catch (Exception ignored) {
            // Keep the requested profile if resolution is unavailable.
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        if (gameProfile != null && gameProfile.getId() != null) {
            tag.putUUID(
                    PROFILE_UUID_TAG,
                    gameProfile.getId()
            );

            tag.putString(
                    PROFILE_NAME_TAG,
                    gameProfile.getName()
            );
        }

        tag.putBoolean(
                SLIM_TAG,
                slim
        );

        tag.putString(
                TRAIT_TAG,
                getTrait().name()
        );
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains(PROFILE_NAME_TAG)) {
            String profileName =
                    tag.getString(PROFILE_NAME_TAG);

            if (!profileName.isBlank()) {
                UUID profileUuid =
                        tag.hasUUID(PROFILE_UUID_TAG)
                                ? tag.getUUID(PROFILE_UUID_TAG)
                                : knownProfileUuid(profileName);

                this.slim = tag.contains(SLIM_TAG)
                        && tag.getBoolean(SLIM_TAG);

                this.gameProfile =
                        new GameProfile(
                                profileUuid,
                                profileName
                        );

                syncProfile();
                resolveServerProfile();

                // Only use profile name when there was no explicit CustomName.
                if (!hasCustomName()) {
                    setCustomName(
                            Component.literal(profileName)
                    );
                    setCustomNameVisible(true);
                }
            } else {
                clearPlayerProfile();
            }
        } else {
            clearPlayerProfile();
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

        entityData.set(
                TRAIT,
                this.trait.name()
        );

        applyTraitSpeed();
    }

    @Override
    public void tick() {
        super.tick();
        applyTraitSpeed();
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
        return UUID.randomUUID();
    }

    public static String randomName(
            net.minecraft.util.RandomSource random
    ) {
        return RANDOM_PROFILE_NAMES[
                random.nextInt(RANDOM_PROFILE_NAMES.length)
        ];
    }
}
