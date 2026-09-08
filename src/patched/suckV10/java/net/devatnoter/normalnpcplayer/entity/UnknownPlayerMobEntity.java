package net.devatnoter.normalnpcplayer.entity;

import net.devatnoter.normalnpcplayer.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.util.RandomSource;

import java.util.EnumSet;
import java.util.UUID;

/**
 * The deliberately uncanny "Unknown Player".
 *
 * This is an adult-sized player model, but it has its own behavior and never
 * participates in AdultPlayerMobEntity's normal/hunter brain. It is designed
 * to create a believable single-player "someone joined my world" moment.
 */
public class UnknownPlayerMobEntity extends AdultPlayerMobEntity {

    private static final String TARGET_UUID_TAG = "UnknownTargetUUID";
    private static final String STATE_TAG = "UnknownState";
    private static final String STATE_TICKS_TAG = "UnknownStateTicks";
    private static final String GAZE_TICKS_TAG = "UnknownGazeTicks";


    private static final double APPROACH_STOP_DISTANCE = 4.5D;
    private static final double FLEE_DISTANCE = 18.0D;

    private enum UnknownState {
        APPROACH,
        SHORT_REACTION,
        GREETING,
        LONG_REACTION,
        ESCAPE
    }

    private UUID targetUuid;
    private UnknownState unknownState = UnknownState.APPROACH;
    private int stateTicks;
    private int gazeTicks;
    private int reactionStep = -1;
    private int reactionTimer;
    private boolean joinedMessageSent;
    private boolean initialSightSwingStarted;
    private int initialSightSwingDelay;
    private boolean hiMessageSent;
    private boolean leftMessageSent;
    private double escapeX;
    private double escapeZ;
    private int footstepTimer;
    private int escapeTicks;

    public UnknownPlayerMobEntity(
            EntityType<? extends PathfinderMob> entityType,
            Level level
    ) {
        super(entityType, level);

        // Give the Unknown Player a normal player-style nametag using the
        // same random profile name that is used for the fake join/leave chat.
        if (!level.isClientSide) {
            setRandomIdentity();
            setCustomName(net.minecraft.network.chat.Component.literal(getChatDisplayName()));
            setCustomNameVisible(true);
        }
    }

        @Override
    protected void registerGoals() {
        // Float only. The normal/hunter Adult brains are intentionally not
        // registered for this entity; Unknown Player has its own state machine.
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D);
    }

    public static boolean checkSpawnRules(
            EntityType<UnknownPlayerMobEntity> type,
            ServerLevelAccessor level,
            MobSpawnType reason,
            BlockPos pos,
            RandomSource random
    ) {
        if (level.getLevel().dimension() != Level.OVERWORLD) {
            return false;
        }

        return Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }

    public void bindTarget(ServerPlayer player) {
        if (player == null) return;
        this.targetUuid = player.getUUID();
        setHunterTargetName(player.getGameProfile().getName());
    }

    public ServerPlayer getBoundTarget() {
        if (!(level() instanceof ServerLevel serverLevel) || targetUuid == null) {
            return null;
        }
        return serverLevel.getServer().getPlayerList().getPlayer(targetUuid);
    }

    @Override
    public void tick() {
        // Run the vanilla LivingEntity/Mob/PathfinderMob tick chain and Adult's
        // common setup, but explicitly bypass Adult's Normal/Hunter brain.
        tickAdultBase();

        if (level().isClientSide || !isAlive()) {
            return;
        }

        ServerPlayer target = getBoundTarget();
        if (target == null || !target.isAlive() || target.level() != level()) {
            disappearWithLeaveMessage();
            return;
        }

        stateTicks++;

        switch (unknownState) {
            case APPROACH -> tickApproach(target);
            case SHORT_REACTION -> tickShortReaction(target);
            case GREETING -> tickGreeting(target);
            case LONG_REACTION -> tickLongReaction(target);
            case ESCAPE -> tickEscape(target);
        }
    }

    private void tickApproach(ServerPlayer target) {
        if (!joinedMessageSent) {
            joinedMessageSent = true;
            // Join is a system event. Keep it separate from NPC speech.
            target.sendSystemMessage(
                    Component.translatable("multiplayer.player.joined", Component.literal(getProfileNameForMessage()))
                            .withStyle(ChatFormatting.YELLOW)
            );
        }

        // First sight: two real vanilla swings, separated by four server ticks.
        // LivingEntity's normal swing() rejects a restart during the first half
        // of the current swing, so the second call is intentionally delayed.
        if (!initialSightSwingStarted && canSeeTarget(target)) {
            initialSightSwingStarted = true;
            initialSightSwingDelay = 4;
            swingMainHand();
        } else if (initialSightSwingStarted && initialSightSwingDelay > 0) {
            initialSightSwingDelay--;
            if (initialSightSwingDelay == 0) {
                swingMainHand();
            }
        }

        boolean looking = isActuallyVisibleTo(target);
        if (looking) {
            gazeTicks++;
        } else {
            gazeTicks = 0;
        }

        double distance = distanceToSqr(target);
        if (distance <= APPROACH_STOP_DISTANCE * APPROACH_STOP_DISTANCE || looking) {
            getNavigation().stop();
            setSprinting(false);
            getLookControl().setLookAt(target, 35.0F, 35.0F);
        } else {
            getNavigation().moveTo(target, 1.05D);
            setSprinting(distance > 10.0D * 10.0D);
            emitRunningEffects();
        }

        if (gazeTicks >= 20) {
            beginState(UnknownState.SHORT_REACTION);
        }
    }

    private void tickShortReaction(ServerPlayer target) {
        getNavigation().stop();
        setSprinting(false);
        getLookControl().setLookAt(target, 35.0F, 35.0F);
        setYRot(yRotForTarget(target));
        yHeadRot = getYRot();

        // Three rapid crouches, then one final crouch.
        if (reactionStep < 0) {
            reactionStep = 0;
            reactionTimer = 0;
        }

        reactionTimer++;
        if (reactionStep < 3) {
            setReactionCrouch((reactionTimer / 4) % 2 == 0);
            if (reactionTimer >= 8) {
                reactionTimer = 0;
                reactionStep++;
            }
        } else if (reactionStep == 3) {
            setReactionCrouch(reactionTimer < 8);
            if (reactionTimer >= 12) {
                setReactionCrouch(false);
                reactionStep = 4;
                reactionTimer = 0;
            }
        } else {
            setReactionCrouch(false);
            if (reactionTimer >= 20) {
                beginState(UnknownState.GREETING);
            }
        }
    }

    private void tickGreeting(ServerPlayer target) {
        getNavigation().stop();
        setSprinting(false);
        getLookControl().setLookAt(target, 35.0F, 35.0F);

        if (!hiMessageSent) {
            hiMessageSent = true;
            chatTo(target, "Hi, " + target.getGameProfile().getName());
        }

        boolean looking = isActuallyVisibleTo(target);
        if (looking) {
            gazeTicks++;
        } else {
            gazeTicks = 0;
        }

        // Three seconds of continued watching triggers the second, more
        // unnatural reaction sequence.
        if (gazeTicks >= 60) {
            beginState(UnknownState.LONG_REACTION);
        } else if (stateTicks >= 40) {
            beginEscape(target);
        }
    }

    private void tickLongReaction(ServerPlayer target) {
        getNavigation().stop();
        setSprinting(false);
        getLookControl().setLookAt(target, 35.0F, 35.0F);

        if (reactionStep < 0) {
            reactionStep = 0;
            reactionTimer = 0;
        }

        reactionTimer++;

        // Five very quick crouches with the requested irregular rhythm.
        if (reactionStep < 5) {
            setReactionCrouch((reactionTimer / 3) % 2 == 0);
            if (reactionTimer >= 6) {
                reactionTimer = 0;
                reactionStep++;
            }
        } else {
            setReactionCrouch(false);

            // 1 2 3 / 1 2 / 1 2 3: little head/body twitches plus hand swing.
            int pulse = reactionTimer % 24;
            float yaw = (float) Math.sin(pulse * 0.95D) * 18.0F;
            float pitch = (float) Math.sin(pulse * 1.35D) * 10.0F;
            yBodyRot = yRotForTarget(target) + yaw;
            yHeadRot = yBodyRot;
            setXRot(pitch);

            if (reactionTimer % 8 == 0) {
                swingMainHand();
            }
            if (reactionTimer % 12 == 0 && onGround()) {
                getJumpControl().jump();
            }

            if (reactionTimer >= 48) {
                setXRot(0.0F);
                beginEscape(target);
            }
        }
    }

    /** Forces both the networked crouch flag and entity pose so the player model
     * reliably receives the crouch animation even when the state changes quickly. */
    private void setReactionCrouch(boolean crouching) {
        // Same vanilla path used by PlayerMob: the entity owns the crouch
        // state and the PlayerModel renderer consumes it.
        setShiftKeyDown(crouching);
        setPose(crouching ? Pose.CROUCHING : Pose.STANDING);
    }

    private void beginEscape(ServerPlayer target) {
        unknownState = UnknownState.ESCAPE;
        stateTicks = 0;
        escapeTicks = 0;
        setReactionCrouch(false);
        setXRot(0.0F);
        reactionStep = -1;

        Vec3 away = position().subtract(target.position());
        if (away.lengthSqr() < 0.01D) {
            away = new Vec3(random.nextDouble() - 0.5D, 0.0D, random.nextDouble() - 0.5D);
        }
        away = away.normalize();
        escapeX = getX() + away.x * FLEE_DISTANCE;
        escapeZ = getZ() + away.z * FLEE_DISTANCE;
    }

    private void tickEscape(ServerPlayer target) {
        escapeTicks++;
        setSprinting(true);
        getNavigation().moveTo(escapeX, getY(), escapeZ, 1.35D);

        // Occasional jumps and a subtle side-to-side path keep the departure
        // looking like an actual player rather than a straight mob flee.
        if (onGround() && escapeTicks % 14 == 0) {
            getJumpControl().jump();
        }

        float targetYaw = (float) (Math.toDegrees(Math.atan2(
                escapeZ - getZ(),
                escapeX - getX()
        )) - 90.0D);
        float sway = (float) Math.sin(escapeTicks * 0.38D) * 9.0F;
        setYRot(targetYaw + sway);
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        emitRunningEffects();

        if (distanceToSqr(target) > 28.0D * 28.0D || escapeTicks >= 120) {
            disappearWithLeaveMessage();
        }
    }

    private void beginState(UnknownState state) {
        unknownState = state;
        stateTicks = 0;
        reactionStep = -1;
        reactionTimer = 0;
    }

    /**
     * "Seen on screen" is intentionally broader than crosshair targeting.
     * We use a viewing cone plus an unobstructed ray to the entity, so simply
     * turning the camera toward the Unknown Player for about one second is enough.
     */
    private boolean canSeeTarget(ServerPlayer player) {
        if (distanceToSqr(player) > 48.0D * 48.0D) return false;

        Vec3 eye = getEyePosition();
        Vec3 targetEye = player.getEyePosition();

        BlockHitResult hit = level().clip(new net.minecraft.world.level.ClipContext(
                eye,
                targetEye,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                this
        ));

        return hit.getType() == HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(eye) >= eye.distanceToSqr(targetEye) - 0.35D;
    }

    private boolean isActuallyVisibleTo(ServerPlayer player) {
        if (distanceToSqr(player) > 48.0D * 48.0D) return false;

        Vec3 eye = player.getEyePosition();
        Vec3 toEntity = getEyePosition().subtract(eye);
        double length = toEntity.length();
        if (length <= 0.001D) return false;

        Vec3 direction = toEntity.scale(1.0D / length);
        double dot = player.getViewVector(1.0F).dot(direction);
        if (dot < 0.52D) return false; // roughly a wide 62-degree cone

        BlockHitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(
                eye,
                getEyePosition(),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player
        ));

        return hit.getType() == HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(eye) >= length * length - 0.35D;
    }

    private float yRotForTarget(Player target) {
        return (float) (Math.toDegrees(Math.atan2(
                target.getZ() - getZ(),
                target.getX() - getX()
        )) - 90.0D);
    }

    private void emitRunningEffects() {
        if (++footstepTimer < 5 || !onGround()) return;
        footstepTimer = 0;

        BlockPos pos = blockPosition().below();
        BlockState state = level().getBlockState(pos);
        if (state.isAir()) return;

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, state),
                    getX(),
                    getY() + 0.08D,
                    getZ(),
                    5,
                    0.16D,
                    0.03D,
                    0.16D,
                    0.03D
            );

            level().playSound(
                    null,
                    blockPosition(),
                    state.getSoundType().getStepSound(),
                    getSoundSource(),
                    0.45F,
                    0.92F + random.nextFloat() * 0.12F
            );
        }
    }

    private String getProfileNameForMessage() {
        return getGameProfile() != null && getGameProfile().getName() != null
                ? getGameProfile().getName()
                : "Unknown Player";
    }

    private void disappearWithLeaveMessage() {
        if (!leftMessageSent && level() instanceof ServerLevel serverLevel) {
            leftMessageSent = true;
            ServerPlayer target = getBoundTarget();
            if (target != null) {
                // Leave is a system event. Keep it separate from NPC speech.
                target.sendSystemMessage(
                        Component.translatable("multiplayer.player.left", Component.literal(getProfileNameForMessage()))
                                .withStyle(ChatFormatting.YELLOW)
                );
            }
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (targetUuid != null) tag.putUUID(TARGET_UUID_TAG, targetUuid);
        tag.putString(STATE_TAG, unknownState.name());
        tag.putInt(STATE_TICKS_TAG, stateTicks);
        tag.putInt(GAZE_TICKS_TAG, gazeTicks);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID(TARGET_UUID_TAG)) {
            targetUuid = tag.getUUID(TARGET_UUID_TAG);
        }
        try {
            unknownState = UnknownState.valueOf(tag.getString(STATE_TAG));
        } catch (IllegalArgumentException ignored) {
            unknownState = UnknownState.APPROACH;
        }
        stateTicks = tag.getInt(STATE_TICKS_TAG);
        gazeTicks = tag.getInt(GAZE_TICKS_TAG);
    }
}
