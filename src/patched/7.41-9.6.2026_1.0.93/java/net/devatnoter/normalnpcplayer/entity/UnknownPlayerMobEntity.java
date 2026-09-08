package net.devatnoter.normalnpcplayer.entity;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.registry.ModEntities;
import net.devatnoter.normalnpcplayer.event.UnknownPlayerSpawnEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

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
    private static final double SECOND_LOST_DISTANCE = 24.0D;
    // Formation is a live relative-position state, not a fixed destination.
    private static final int SECOND_FORMATION_ENTER_TICKS = 8;
    private static final int SECOND_FORMATION_EXIT_TICKS = 5;
    private static final int SECOND_FORMATION_STABLE_REQUIRED = 16;
    private static final double SECOND_FORMATION_ENTER_MIN_DISTANCE = 2.75D;
    private static final double SECOND_FORMATION_ENTER_MAX_DISTANCE = 8.5D;
    private static final double SECOND_FORMATION_EXIT_MIN_DISTANCE = 2.25D;
    private static final double SECOND_FORMATION_EXIT_MAX_DISTANCE = 10.0D;

    private enum UnknownState {
        APPROACH,
        SHORT_REACTION,
        GREETING,
        LONG_REACTION,
        ESCAPE,
        SECOND_ATTACKER_APPROACH,
        SECOND_ATTACKER_TO_OBSERVER,
        SECOND_OBSERVER_APPROACH,
        SECOND_OBSERVER_WATCH,
        THIRD_RETURN_APPROACH,
        THIRD_ESCAPE
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
    private int initialSightSwingCount;
    private boolean hiMessageSent;
    private boolean leftMessageSent;
    private double escapeX;
    private double escapeZ;
    private int footstepTimer;
    private int escapeTicks;
    private UUID escapeFollowUuid;

    // Player-like movement planner. State logic chooses intent; this planner
    // holds a short-lived waypoint so navigation is not rewritten every tick.
    private int movementReplanTicks;
    private int movementJumpTicks;
    private double movementWaypointX;
    private double movementWaypointY;
    private double movementWaypointZ;
    private boolean movementWaypointValid;

    // State 2 encounter fields.
    private boolean secondEncounter;
    private boolean secondObserver;
    private UUID secondPartnerUuid;
    private boolean secondAttackDone;
    private boolean secondAttackStarted;
    private boolean secondObserverReady;
    private boolean secondFormationActive;
    private int secondFormationEnterTicks;
    private int secondFormationExitTicks;
    private int secondSurroundReplanTicks;
    private double secondSurroundRadius = 4.5D;
    private int secondSurroundSide = 1;
    private int secondTimer;
    private boolean secondChatSent;
    private boolean secondFinishChatSent;
    private boolean secondGuideBookThrown;
    private int secondGuideThrowTicks;
    private int secondExitDelayTicks;
    private boolean secondPlayerLost;
    private boolean secondStageFinished;
    private boolean secondDontScheduled;
    private int secondDontDelayTicks;
    private int secondPearlCatchupTicks;
    private boolean secondPearlCatchupActive;
    private int secondPearlPressureTicks;
    private double secondPearlLastDistanceSqr = -1.0D;
    private int secondPearlCooldownTicks;
    private int secondPearlDecisionThreshold;
    private double secondFormationX;
    private double secondFormationY;
    private double secondFormationZ;
    private boolean secondFormationPointValid;
    private int secondFormationStableTicks;
    private int secondFormationAttemptTicks;
    private Vec3 secondFormationLastPlayerPosition = Vec3.ZERO;
    private int secondChaseReplanTicks;
    private double secondChaseWaypointX;
    private double secondChaseWaypointY;
    private double secondChaseWaypointZ;
    private boolean secondChaseWaypointValid;
    // State 3 fields.
    private boolean thirdEncounter;
    private boolean thirdAttackDone;
    private double thirdReturnX;
    private double thirdReturnY;
    private double thirdReturnZ;

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
    public void onAddedToWorld() {
        super.onAddedToWorld();

        if (!level().isClientSide) {
            sendTabListAdd();
        }
    }

    /**
     * Adds this NPC to the target player's tab list as a fake player entry.
     * The entry is sent only to the bound player, so other players do not see it.
     */
    public void sendTabListAdd() {
        ServerPlayer target = getBoundTarget();
        GameProfile sourceProfile = getGameProfile();

        if (target == null || !target.isAlive() || sourceProfile == null) {
            return;
        }

        String name = sourceProfile.getName();
        if (name == null || name.isBlank()) {
            return;
        }

        // The entity itself may use a name-only GameProfile while the client
        // resolves its real Mojang profile asynchronously. The tab packet still
        // needs a concrete UUID, so use this entity's stable UUID for the fake
        // tab entry while retaining the actual profile name.
        GameProfile tabProfile = new GameProfile(getUUID(), name);
        tabProfile.getProperties().putAll(sourceProfile.getProperties());

        ClientboundPlayerInfoUpdatePacket.Entry entry =
                new ClientboundPlayerInfoUpdatePacket.Entry(
                        getUUID(),
                        tabProfile,
                        true,
                        0,
                        GameType.SURVIVAL,
                        null,
                        null
                );

        // Forge 1.20.1 only exposes the player-based constructor. The NPC is
        // not a ServerPlayer, so build an empty packet and replace its entry
        // list before sending it to the target connection.
        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(
                        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY
                ),
                List.of()
        );

        try {
            Field entriesField = ClientboundPlayerInfoUpdatePacket.class.getDeclaredField("entries");
            entriesField.setAccessible(true);
            entriesField.set(packet, List.of(entry));
        } catch (ReflectiveOperationException e) {
            NormalNPCPlayer.LOGGER.error(
                    "Failed to build fake tab-list entry for Unknown Player {}",
                    getUUID(),
                    e
            );
            return;
        }

        target.connection.send(packet);
    }

    /**
     * Always remove the fake tab entry when this NPC is removed for any reason.
     * This prevents a stale name from remaining in the target's tab list.
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            sendTabListRemove();
        }

        super.remove(reason);
    }

    private void sendTabListRemove() {
        ServerPlayer target = getBoundTarget();
        if (target == null) {
            return;
        }

        target.connection.send(
                new ClientboundPlayerInfoRemovePacket(List.of(getUUID()))
        );
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

        // Unknown Player is a scripted encounter only. Never allow vanilla
        // natural spawning to create another instance.
        return false;
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
    public void swingMainHand() {
        super.swingMainHand();
    }

    /**
     * UnknownPlayerMobEntity bypasses AdultPlayerMobEntity's normal brain,
     * so explicitly advance the vanilla LivingEntity swing timer here.
     * Without this, swing() sets swinging=true but swingTime remains stuck.
     */
    @Override
    public void aiStep() {
        updateSwingTime();
        super.aiStep();
    }

    @Override
    public void tick() {
        // Run the vanilla LivingEntity/Mob/PathfinderMob tick chain and Adult's
        // common setup, but explicitly bypass Adult's Normal/Hunter brain.
        tickAdultBase();

        // AdultPlayerMobEntity reapplies its trait speed every tick. Unknown has
        // its own movement contract, so restore the player-like walk/sprint
        // baseline after the parent tick and let navigation drive actual motion.
        applyUnknownMovementSpeed(false);

        if (level().isClientSide || !isAlive()) {
            return;
        }

        ServerPlayer target = getBoundTarget();
        if (target == null || !target.isAlive() || target.level() != level()) {
            disappearWithLeaveMessage();
            return;
        }

        tickSecondPearlCatchup(target);
        stateTicks++;

        switch (unknownState) {
            case APPROACH -> tickApproach(target);
            case SHORT_REACTION -> tickShortReaction(target);
            case GREETING -> tickGreeting(target);
            case LONG_REACTION -> tickLongReaction(target);
            case ESCAPE -> tickEscape(target);
            case SECOND_ATTACKER_APPROACH -> tickSecondAttacker(target);
            case SECOND_ATTACKER_TO_OBSERVER -> tickSecondAttackerToObserver(target);
            case SECOND_OBSERVER_APPROACH -> tickSecondObserverApproach(target);
            case SECOND_OBSERVER_WATCH -> tickSecondObserver(target);
            case THIRD_RETURN_APPROACH -> tickThirdReturnApproach(target);
            case THIRD_ESCAPE -> tickThirdEscape(target);
        }

    }

    private void tickApproach(ServerPlayer target) {
        if (!joinedMessageSent) {
            joinedMessageSent = true;
            target.sendSystemMessage(
                    Component.translatable("multiplayer.player.joined", Component.literal(getProfileNameForMessage()))
                            .withStyle(ChatFormatting.YELLOW)
            );
        }

        if (!initialSightSwingStarted && canSeeTarget(target)) {
            initialSightSwingStarted = true;
            initialSightSwingDelay = 6;
            initialSightSwingCount = 0;
        } else if (initialSightSwingStarted && initialSightSwingDelay > 0) {
            initialSightSwingDelay--;
            if (initialSightSwingDelay == 0) {
                swingMainHand();
                initialSightSwingCount++;
                if (initialSightSwingCount < 2) initialSightSwingDelay = 4;
            }
        }

        boolean looking = isActuallyVisibleTo(target);
        gazeTicks = looking ? gazeTicks + 1 : 0;

        double distance = distanceToSqr(target);
        if (distance <= APPROACH_STOP_DISTANCE * APPROACH_STOP_DISTANCE || looking) {
            getNavigation().stop();
            setSprinting(false);
        applyUnknownMovementSpeed(false);
            movementWaypointValid = false;
        } else {
            // Stage 1 is deliberately Player-like: sprint continuously, keep
            // forward momentum, and occasionally bias the path left/right.
            setSprinting(true);
            applyUnknownMovementSpeed(true);
            updateErraticSprintWaypoint(target);
            if (onGround() && --movementJumpTicks <= 0 && distance > 4.0D * 4.0D) {
                getJumpControl().jump();
                movementJumpTicks = 18 + random.nextInt(16);
            }
        }

        if (gazeTicks >= 20) beginState(UnknownState.SHORT_REACTION);
    }

    private void tickShortReaction(ServerPlayer target) {
        getNavigation().stop();
        setSprinting(false);
        applyUnknownMovementSpeed(false);
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
        applyUnknownMovementSpeed(false);
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
        applyUnknownMovementSpeed(false);
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

    /**
     * If the player outruns the two actors during the approach, abort the
     * entire second encounter. Actor 2 delivers one natural-looking line,
     * then both actors leave immediately without starting the choreography.
     */
    private boolean checkSecondPlayerLost(ServerPlayer target) {
        if (secondPlayerLost || secondTimer < 40 || target == null) {
            return secondPlayerLost;
        }

        UnknownPlayerMobEntity partner = getSecondPartner();
        boolean thisTooFar = distanceToSqr(target) > SECOND_LOST_DISTANCE * SECOND_LOST_DISTANCE;
        boolean partnerTooFar = partner != null
                && partner.distanceToSqr(target) > SECOND_LOST_DISTANCE * SECOND_LOST_DISTANCE;

        if (!thisTooFar && !partnerTooFar) {
            return false;
        }

        secondPlayerLost = true;

        // Actor 2 is always the one who calls out to the player, even if
        // Actor 1 notices the separation first.
        UnknownPlayerMobEntity actor2 = secondObserver ? partner : this;
        if (actor2 != null && actor2.isAlive()) {
            actor2.secondPlayerLost = true;
            actor2.chatTo(target, "Hey " + target.getGameProfile().getName() + " wait for us!");
        }

        if (partner != null && partner.isAlive()) {
            partner.secondPlayerLost = true;
            partner.beginEscape(target);
        }
        beginEscape(target);
        return true;
    }

    /**
     * All scripted movement goes through Mojang's normal Mob navigation pipeline:
     * MoveTo -> PathNavigation -> MoveControl -> movement attribute. There is no
     * post-tick velocity clamp and no direct horizontal velocity injection.
     * Bunny-hop is requested through the entity's normal JumpControl.
     */
    private void updateEscapeErraticWaypoint(ServerPlayer target) {
        if (movementReplanTicks > 0 && movementWaypointValid) {
            movementReplanTicks--;
            getNavigation().moveTo(movementWaypointX, movementWaypointY, movementWaypointZ, 1.0D);
            return;
        }

        Vec3 away = new Vec3(escapeX - getX(), 0.0D, escapeZ - getZ());
        if (away.lengthSqr() < 0.001D) {
            away = position().subtract(target.position());
            away = new Vec3(away.x, 0.0D, away.z);
        }
        if (away.lengthSqr() < 0.001D) away = new Vec3(0.0D, 0.0D, 1.0D);
        away = away.normalize();
        Vec3 right = new Vec3(-away.z, 0.0D, away.x);

        double lateral = (random.nextBoolean() ? 1.0D : -1.0D) * (0.9D + random.nextDouble() * 1.1D);
        double forwardDistance = 4.0D + random.nextDouble() * 2.5D;
        Vec3 waypoint = position().add(away.scale(forwardDistance)).add(right.scale(lateral));

        // Do not let the lateral bias send the actor backwards past the
        // intended logout point. The waypoint remains an exit steering hint.
        if (position().distanceToSqr(new Vec3(escapeX, getY(), escapeZ)) < 36.0D) {
            waypoint = new Vec3(escapeX, getY(), escapeZ);
        }

        movementWaypointX = waypoint.x;
        movementWaypointY = waypoint.y;
        movementWaypointZ = waypoint.z;
        movementWaypointValid = true;
        movementReplanTicks = 7 + random.nextInt(6);
        getNavigation().moveTo(movementWaypointX, movementWaypointY, movementWaypointZ, 1.0D);
    }

    private void chasePlayerLikeVanilla(ServerPlayer target, double navigationSpeed) {
        setSprinting(true);
        applyUnknownMovementSpeed(true);

        if (onGround() && !isPassenger()
                && distanceToSqr(target) > 3.0D * 3.0D
                && --movementJumpTicks <= 0) {
            getJumpControl().jump();
            movementJumpTicks = 12 + random.nextInt(12);
        }

        getNavigation().moveTo(target, navigationSpeed);
    }

    /** State 2 chase with bounded human-like side steps. */
    private void chaseSecondPlayerNaturally(ServerPlayer target, double navigationSpeed) {
        setSprinting(true);
        applyUnknownMovementSpeed(true);

        if (onGround() && !isPassenger()
                && distanceToSqr(target) > 3.0D * 3.0D
                && --movementJumpTicks <= 0) {
            getJumpControl().jump();
            movementJumpTicks = 10 + random.nextInt(13);
        }

        if (secondChaseReplanTicks > 0 && secondChaseWaypointValid) {
            secondChaseReplanTicks--;
            getNavigation().moveTo(secondChaseWaypointX, secondChaseWaypointY, secondChaseWaypointZ, navigationSpeed);
            return;
        }

        Vec3 toPlayer = target.position().subtract(position());
        Vec3 forward = new Vec3(toPlayer.x, 0.0D, toPlayer.z);
        double distance = forward.length();
        if (distance < 0.001D) {
            forward = getForward();
            forward = new Vec3(forward.x, 0.0D, forward.z);
        }
        if (forward.lengthSqr() < 0.001D) forward = new Vec3(0.0D, 0.0D, 1.0D);
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);

        Vec3 playerVelocity = target.getDeltaMovement();
        Vec3 velocityFlat = new Vec3(playerVelocity.x, 0.0D, playerVelocity.z);
        Vec3 movementForward = velocityFlat.lengthSqr() > 0.0004D
                ? velocityFlat.normalize()
                : forward;

        double lead = Math.min(2.25D, Math.max(0.75D, distance * 0.10D));
        double lateral = (random.nextBoolean() ? 1.0D : -1.0D)
                * (distance > 10.0D ? 1.1D : 1.7D + random.nextDouble() * 1.1D);
        double radius = Math.min(7.0D, Math.max(2.0D, distance * 0.55D));

        Vec3 desired = target.position()
                .add(movementForward.scale(lead))
                .add(right.scale(lateral));
        Vec3 offset = desired.subtract(target.position());
        if (offset.horizontalDistance() > radius) {
            Vec3 clamped = new Vec3(offset.x, 0.0D, offset.z).normalize().scale(radius);
            desired = target.position().add(clamped);
        }

        int bx = Mth.floor(desired.x);
        int bz = Mth.floor(desired.z);
        int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        Vec3 waypoint = new Vec3(desired.x, y, desired.z);

        secondChaseWaypointX = waypoint.x;
        secondChaseWaypointY = waypoint.y;
        secondChaseWaypointZ = waypoint.z;
        secondChaseWaypointValid = true;
        secondChaseReplanTicks = 5 + random.nextInt(7);
        getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, navigationSpeed);
    }

    private void updateErraticSprintWaypoint(ServerPlayer target) {
        if (movementJumpTicks <= 0) movementJumpTicks = 18 + random.nextInt(16);
        if (movementReplanTicks > 0 && movementWaypointValid) {
            movementReplanTicks--;
            getNavigation().moveTo(movementWaypointX, movementWaypointY, movementWaypointZ, 1.0D);
            return;
        }

        Vec3 toTarget = target.position().subtract(position());
        Vec3 forward = new Vec3(toTarget.x, 0.0D, toTarget.z);
        if (forward.lengthSqr() < 0.001D) {
            forward = getForward();
            forward = new Vec3(forward.x, 0.0D, forward.z);
        }
        if (forward.lengthSqr() < 0.001D) forward = new Vec3(0.0D, 0.0D, 1.0D);
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);

        // Short-lived lateral bias gives the old natural left/right run without
        // teleporting or steering the body directly toward the target.
        double lateral = (random.nextBoolean() ? 1.0D : -1.0D) * (0.8D + random.nextDouble() * 1.5D);
        double forwardDistance = Math.min(7.0D, Math.max(3.5D, Math.sqrt(distanceToSqr(target)) - 1.0D));
        Vec3 waypoint = position().add(forward.scale(forwardDistance)).add(right.scale(lateral));

        movementWaypointX = waypoint.x;
        movementWaypointY = waypoint.y;
        movementWaypointZ = waypoint.z;
        movementWaypointValid = true;
        movementReplanTicks = 8 + random.nextInt(6);
        getNavigation().moveTo(movementWaypointX, movementWaypointY, movementWaypointZ, 1.0D);
    }

    private void applyUnknownMovementSpeed(boolean sprinting) {
        var attribute = getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        // AdultPlayerMobEntity overwrites this value from its trait each tick.
        // Explicitly separating walk/sprint speeds prevents setSprinting(true)
        // from becoming a visual flag with no actual movement-speed effect.
        attribute.setBaseValue(sprinting ? 0.34D : 0.28D);
    }

    /**
     * Returns a stable formation point on the requested side of the player.
     * Actor 1 and Actor 2 therefore approach two different sides instead of
     * converging on the player's feet.
     */
    private Vec3 getSecondFormationPoint(ServerPlayer target, boolean observerSide) {
        if (secondFormationPointValid) {
            return new Vec3(secondFormationX, secondFormationY, secondFormationZ);
        }

        Vec3 targetPos = target.position();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle;
            UnknownPlayerMobEntity partner = getSecondPartner();
            if (!observerSide && partner != null) {
                Vec3 partnerOffset = partner.position().subtract(targetPos);
                double base = Math.atan2(partnerOffset.z, partnerOffset.x);
                angle = base + Math.PI + (random.nextDouble() - 0.5D) * 0.75D;
            } else {
                angle = random.nextDouble() * Math.PI * 2.0D;
            }

            double radius = 3.5D + random.nextDouble() * 2.5D;
            double x = targetPos.x + Math.cos(angle) * radius;
            double z = targetPos.z + Math.sin(angle) * radius;
            int bx = Mth.floor(x);
            int bz = Mth.floor(z);
            int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            BlockPos floor = new BlockPos(bx, y - 1, bz);

            if (!level().getBlockState(floor).isSolid()) continue;
            Vec3 candidate = new Vec3(x, y, z);
            double dy = Math.abs(candidate.y - targetPos.y);
            if (dy > 3.0D) continue;

            secondFormationX = candidate.x;
            secondFormationY = candidate.y;
            secondFormationZ = candidate.z;
            secondFormationPointValid = true;
            return candidate;
        }

        // Fallback is still randomized and opposite when a valid terrain point
        // could not be found after several attempts.
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = 4.0D + random.nextDouble() * 1.5D;
        double x = targetPos.x + Math.cos(angle) * radius;
        double z = targetPos.z + Math.sin(angle) * radius;
        int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        secondFormationX = x;
        secondFormationY = y;
        secondFormationZ = z;
        secondFormationPointValid = true;
        return new Vec3(x, y, z);
    }

    private boolean isAtSecondFormationPoint(Vec3 point) {
        double dx = getX() - point.x;
        double dz = getZ() - point.z;
        return dx * dx + dz * dz <= 0.85D * 0.85D;
    }

    private void tickSecondAttacker(ServerPlayer target) {
        secondTimer++;
        setSprinting(true);
        applyUnknownMovementSpeed(true);

        UnknownPlayerMobEntity observer = getSecondPartner();
        if (observer == null || !observer.isAlive()) {
            chaseSecondPlayerNaturally(target, 1.00D);
            return;
        }

        // Formation is deliberately loose. There is no cached left/right
        // parking coordinate and no requirement to stand on an exact block.
        // Actor 1 keeps chasing until both actors are naturally around the
        // player. Actor 2/observer decides when the loose formation is stable.
        if (!secondAttackStarted) {
            // Both actors participate in the same live surround. They do not
            // wait at parking coordinates and they do not attack merely because
            // they reached one old position.
            if (!observer.secondObserverReady || !observer.secondFormationActive) {
                moveTowardDynamicSurround(target, observer, 1.00D);
                return;
            }

            secondAttackStarted = true;
            getNavigation().stop();
            setSprinting(false);
            applyUnknownMovementSpeed(false);
            if (onGround()) {
                getJumpControl().jump();
            }
            return;
        }

        double distance = distanceToSqr(target);
        if (!secondAttackDone) {
            getLookControl().setLookAt(target, 35.0F, 35.0F);

            if (distance > 2.75D * 2.75D) {
                chaseSecondPlayerNaturally(target, 1.0D);
            } else {
                getNavigation().stop();
                setSprinting(false);
                applyUnknownMovementSpeed(false);
                if (onGround()) {
                    getJumpControl().jump();
                }
                if (distance <= 2.95D * 2.95D) {
                    performSecondCriticalAttack(target);
                    secondAttackDone = true;
                    secondTimer = 0;
                    UnknownPlayerMobEntity observerAfterHit = getSecondPartner();
                    if (observerAfterHit != null) {
                        observerAfterHit.secondTimer = 0;
                        observerAfterHit.secondDontScheduled = false;
                        observerAfterHit.secondStageFinished = false;
                    }
                    beginState(UnknownState.SECOND_ATTACKER_TO_OBSERVER);
                }
            }
        }
    }

    private void performSecondCriticalAttack(ServerPlayer target) {
        swingMainHand();

        var attack = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (attack == null) return;

        var criticalModifier = new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                java.util.UUID.randomUUID(),
                "Unknown Player critical hit",
                Math.max(0.5D, attack.getValue() * 0.5D),
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION
        );

        boolean hit;
        attack.addTransientModifier(criticalModifier);
        try {
            hit = doHurtTarget(target);
        } finally {
            attack.removeModifier(criticalModifier);
        }

        if (hit && level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.CRIT,
                    target.getX(),
                    target.getY() + target.getBbHeight() * 0.55D,
                    target.getZ(),
                    8,
                    0.18D, 0.18D, 0.18D, 0.08D
            );
            level().playSound(
                    null,
                    target.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_CRIT,
                    getSoundSource(),
                    0.8F,
                    1.05F
            );
        }
    }

    private void tickSecondAttackerToObserver(ServerPlayer target) {
        secondTimer++;
        setSprinting(false);
        applyUnknownMovementSpeed(false);

        UnknownPlayerMobEntity observer = getSecondPartner();
        if (observer == null || !observer.isAlive()) {
            disappearWithLeaveMessage();
            return;
        }

        // Preserve the original State 2 script: Actor 2 returns to Actor 1,
        // says "So?", Actor 1 leaves first, then Actor 2 gives one randomly
        // selected book and leaves after it.
        if (!secondFinishChatSent) {
            getLookControl().setLookAt(observer, 35.0F, 35.0F);
            getNavigation().moveTo(observer, 0.68D);

            if (distanceToSqr(observer) <= 2.2D * 2.2D) {
                getNavigation().stop();
                secondFinishChatSent = true;
                secondTimer = 0;
                chatTo(target, "So?");
                observer.finishSecondEncounterAndLeave();
                secondExitDelayTicks = 0;
                secondGuideThrowTicks = 0;
            }
            return;
        }

        // Actor 2 waits two seconds after Actor 1 leaves, then naturally turns
        // its body toward the player. No neck snap: the body is rotated only by
        // a small amount per tick and the book uses that actual facing.
        if (!secondGuideBookThrown) {
            getNavigation().stop();
            secondExitDelayTicks++;
            graduallyFaceTargetBody(target, 8.0F);

            if (secondExitDelayTicks < 40) {
                return;
            }

            // Exactly ONE book is selected from the available Stage 2 books.
            if (secondGuideThrowTicks == 0) {
                ItemStack selectedBook = random.nextBoolean()
                        ? createGuideBook()
                        : createTotemGuideBook();
                setItemInHand(InteractionHand.MAIN_HAND, selectedBook.copy());
                swingMainHand();
                throwBookForward(selectedBook);
                setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                secondGuideBookThrown = true;
                secondGuideThrowTicks = 0;
                secondExitDelayTicks = 0;
            }
            return;
        }

        // Actor 2 leaves after delivering both books.
        getNavigation().stop();
        secondExitDelayTicks++;
        if (secondExitDelayTicks >= 40) {
            beginEscapeFollow(observer);
        }
    }

    private void graduallyFaceTargetBody(ServerPlayer target, float maxTurn) {
        if (target == null) return;
        Vec3 delta = target.position().subtract(position());
        if (delta.horizontalDistanceSqr() < 0.001D) return;
        float desiredYaw = (float) (Mth.atan2(delta.z, delta.x) * (180.0D / Math.PI)) - 90.0F;
        float nextYaw = Mth.approachDegrees(getYRot(), desiredYaw, maxTurn);
        setYRot(nextYaw);
        yBodyRot = nextYaw;
    }

    /** State 3: Actor 2 returns to the exact position where it left State 2. */
    private void tickThirdReturnApproach(ServerPlayer target) {
        stateTicks++;
        setSprinting(false);
        applyUnknownMovementSpeed(false);

        if (thirdAttackDone) return;

        // The return point is the exact point saved when Actor 2 left State 2.
        // The scheduler validates it before spawning, so this actor does not
        // arrive in a dangerous position and fall to its death.
        if (stateTicks < 20) {
            getNavigation().stop();
            return;
        }

        if (stateTicks == 20) {
            setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(
                    net.devatnoter.normalnpcplayer.registry.ModItems.GOLDEN_MILK_BOTTLE.get()));
            swingMainHand();
            throwItemAtPlayer(target, new ItemStack(
                    net.devatnoter.normalnpcplayer.registry.ModItems.GOLDEN_MILK_BOTTLE.get()));
            setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return;
        }

        // Wait a full two seconds after the milk has been delivered.
        if (stateTicks < 60) {
            getNavigation().stop();
            graduallyFaceTargetBody(target, 6.0F);
            return;
        }

        // One real melee hit only. If the player moved away, use vanilla
        // navigation to close the gap rather than dealing damage from range.
        if (distanceToSqr(target) > 3.0D * 3.0D) {
            chasePlayerLikeVanilla(target, 0.9D);
            return;
        }

        getNavigation().stop();
        graduallyFaceTargetBody(target, 12.0F);
        if (distanceToSqr(target) <= 3.0D * 3.0D) {
            swingMainHand();
            doHurtTarget(target);
            thirdAttackDone = true;
            beginEscape(target);
            unknownState = UnknownState.THIRD_ESCAPE;
            stateTicks = 0;
        }
    }

    private void throwItemAtPlayer(ServerPlayer target, ItemStack stack) {
        if (!(level() instanceof ServerLevel serverLevel) || target == null || stack.isEmpty()) return;
        Vec3 origin = getEyePosition();
        Vec3 targetPos = target.getEyePosition().subtract(origin);
        if (targetPos.lengthSqr() < 0.001D) return;
        Vec3 velocity = targetPos.normalize().scale(0.38D);
        ItemEntity item = new ItemEntity(serverLevel, origin.x, origin.y, origin.z, stack);
        item.setPickUpDelay(10);
        item.setDeltaMovement(velocity);
        serverLevel.addFreshEntity(item);
    }

    /**
     * Throws a real Ender Pearl projectile. The pearl itself is never an item
     * entity and therefore can never be picked up or dropped as loot. Each
     * actor owns its own projectile and waits for the projectile's real impact.
     */
    private void throwEnderPearlCatchup(ServerPlayer target) {
        if (!(level() instanceof ServerLevel serverLevel) || target == null) return;
        if (secondPearlCatchupActive || secondPearlCatchupTicks > 0) return;

        Vec3 origin = getEyePosition();
        Vec3 toPlayer = target.position().subtract(position());
        Vec3 horizontalToPlayer = new Vec3(toPlayer.x, 0.0D, toPlayer.z);
        double horizontalDistance = horizontalToPlayer.length();
        if (horizontalDistance < 0.001D) return;

        // Aim ahead of the player's current movement, not at their current feet.
        // The lead is deliberately modest and is clamped so the destination stays
        // inside a practical vanilla Ender Pearl throw rather than becoming a
        // magic long-range teleport.
        Vec3 playerVelocity = target.getDeltaMovement();
        Vec3 movement = new Vec3(playerVelocity.x, 0.0D, playerVelocity.z);
        Vec3 leadDirection = movement.lengthSqr() > 0.0004D
                ? movement.normalize()
                : new Vec3(-Mth.sin(target.getYRot() * ((float) Math.PI / 180.0F)), 0.0D,
                        Mth.cos(target.getYRot() * ((float) Math.PI / 180.0F))).normalize();
        double leadDistance = movement.lengthSqr() > 0.0004D
                ? Mth.clamp(movement.length() * 20.0D, 2.5D, 7.0D)
                : 2.5D;

        Vec3 predictedLanding = target.position().add(leadDirection.scale(leadDistance));
        Vec3 horizontalAim = new Vec3(
                predictedLanding.x - origin.x,
                0.0D,
                predictedLanding.z - origin.z
        );
        double throwDistance = horizontalAim.length();

        // Keep the actual intended landing inside a conservative 40-block
        // horizontal envelope. Vanilla pearls can travel substantially farther
        // in ideal conditions, but this keeps the NPC from making implausible
        // long throws and leaves room for the projectile's arc.
        if (throwDistance > 32.0D) {
            Vec3 dir = horizontalAim.normalize();
            predictedLanding = new Vec3(
                    origin.x + dir.x * 32.0D,
                    predictedLanding.y,
                    origin.z + dir.z * 32.0D
            );
            horizontalAim = new Vec3(predictedLanding.x - origin.x, 0.0D, predictedLanding.z - origin.z);
            throwDistance = horizontalAim.length();
        }

        // Give the pearl a natural upward launch so it travels in an arc and
        // lands around the point ahead of the player instead of flying directly
        // at their current eye position.
        double verticalAim = (target.getY() + 0.8D) - origin.y + Math.min(2.0D, throwDistance * 0.035D);
        Vec3 aim = new Vec3(horizontalAim.x, verticalAim, horizontalAim.z);
        if (aim.lengthSqr() < 0.001D) return;

        setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_PEARL));
        swingMainHand();

        NpcEnderPearl pearl = new NpcEnderPearl(serverLevel, this);
        pearl.setPos(origin.x, origin.y - 0.1D, origin.z);
        pearl.shoot(aim.x, aim.y, aim.z, 1.5F, 0.75F);
        serverLevel.addFreshEntity(pearl);

        setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        secondPearlCatchupActive = true;
        secondPearlCatchupTicks = 0;
        secondPearlPressureTicks = 0;
        secondPearlLastDistanceSqr = distanceToSqr(target);
        secondPearlCooldownTicks = 100;

        serverLevel.playSound(null, blockPosition(), SoundEvents.ENDER_PEARL_THROW,
                getSoundSource(), 0.55F, 0.95F + random.nextFloat() * 0.1F);
    }

    private void tickSecondPearlCatchup(ServerPlayer target) {
        if (!secondEncounter || target == null) return;
        if (secondPearlCooldownTicks > 0) secondPearlCooldownTicks--;

        if (secondPearlCatchupActive) {
            // The projectile decides when the real impact happened. If it is
            // somehow gone without an impact callback, release the lock after
            // a long enough flight window so the NPC can throw again.
            secondPearlCatchupTicks++;
            if (secondPearlCatchupTicks >= 80) {
                secondPearlCatchupActive = false;
                secondPearlCatchupTicks = 0;
                secondPearlPressureTicks = 0;
                secondPearlCooldownTicks = 20;
            }
            return;
        }

        if (secondPearlCooldownTicks > 0) return;

        double distanceSqr = distanceToSqr(target);
        if (distanceSqr < 12.0D * 12.0D) {
            secondPearlPressureTicks = Math.max(0, secondPearlPressureTicks - 2);
            secondPearlLastDistanceSqr = distanceSqr;
            return;
        }

        Vec3 away = position().subtract(target.position());
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 0.001D) return;
        away = away.normalize();

        Vec3 targetVelocity = target.getDeltaMovement();
        double targetMovingAway = targetVelocity.x * (-away.x) + targetVelocity.z * (-away.z);
        boolean gapGrowing = secondPearlLastDistanceSqr > 0.0D
                && distanceSqr > secondPearlLastDistanceSqr + 0.20D;
        boolean targetOutrunsPath = targetMovingAway > 0.045D;

        if (gapGrowing || targetOutrunsPath) {
            secondPearlPressureTicks++;
        } else {
            secondPearlPressureTicks = Math.max(0, secondPearlPressureTicks - 1);
        }
        secondPearlLastDistanceSqr = distanceSqr;

        // This is intentionally a sustained catch-up decision, not a fixed
        // distance teleport trigger: the actor first tries to run and only
        // uses its pearl after it has clearly failed to close the gap.
        if (secondPearlPressureTicks >= secondPearlDecisionThreshold) {
            throwEnderPearlCatchup(target);
        }
    }

    private void onNpcEnderPearlImpact(Vec3 impact) {
        secondPearlCatchupActive = false;
        secondPearlCatchupTicks = 0;
        secondPearlPressureTicks = 0;

        if (!(level() instanceof ServerLevel serverLevel)) return;

        Vec3 safe = findSafePearlLanding(serverLevel, impact);
        if (safe == null) {
            secondPearlCooldownTicks = 20;
            return;
        }

        // Only the projectile impact grants the teleport. No timer, no direct
        // player-position snap, and no simultaneous Actor 1/Actor 2 teleport.
        teleportTo(safe.x, safe.y, safe.z);
        fallDistance = 0.0F;
        // Non-player entities do not receive the vanilla pearl teleport damage
        // automatically in this version, so reproduce the player's 5 damage.
        hurt(serverLevel.damageSources().fall(), 5.0F);
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
        secondPearlCooldownTicks = 80;

        serverLevel.sendParticles(ParticleTypes.PORTAL,
                safe.x, safe.y + 0.6D, safe.z, 24,
                0.25D, 0.45D, 0.25D, 0.08D);
        serverLevel.playSound(null, BlockPos.containing(safe),
                SoundEvents.ENDERMAN_TELEPORT, getSoundSource(), 0.65F, 1.0F);
    }

    private Vec3 findSafePearlLanding(ServerLevel serverLevel, Vec3 impact) {
        // Prefer the actual impact point, then search a very small radius. This
        // preserves the pearl's destination while preventing fatal landings.
        for (int radius = 0; radius <= 2; radius++) {
            for (int i = 0; i < (radius == 0 ? 1 : 16); i++) {
                double angle = radius == 0 ? 0.0D : (Math.PI * 2.0D * i / 16.0D);
                double x = impact.x + Math.cos(angle) * radius * 0.75D;
                double z = impact.z + Math.sin(angle) * radius * 0.75D;
                int bx = Mth.floor(x);
                int bz = Mth.floor(z);
                int groundY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                Vec3 candidate = new Vec3(x, groundY, z);
                if (groundY <= serverLevel.getMinBuildHeight() + 1) continue;
                BlockPos floor = BlockPos.containing(x, groundY - 1.0D, z);
                BlockState floorState = serverLevel.getBlockState(floor);
                if (!floorState.isSolid()) continue;
                if (!serverLevel.getFluidState(floor.above()).isEmpty()) continue;

                var box = getBoundingBox().move(
                        candidate.x - getX(), candidate.y - getY(), candidate.z - getZ());
                if (!serverLevel.noCollision(this, box)) continue;
                if (serverLevel.getBlockState(BlockPos.containing(candidate.x, candidate.y, candidate.z)).isSuffocating(serverLevel, BlockPos.containing(candidate))) continue;
                return candidate;
            }
        }
        return null;
    }

    private static final class NpcEnderPearl extends ThrownEnderpearl {
        NpcEnderPearl(ServerLevel level, UnknownPlayerMobEntity owner) {
            super(level, owner);
            setOwner(owner);
        }

        @Override
        protected void onHit(HitResult result) {
            Entity owner = getOwner();
            Vec3 impact = result.getLocation();
            if (owner instanceof UnknownPlayerMobEntity npc && npc.isAlive()
                    && npc.level() == level()) {
                npc.onNpcEnderPearlImpact(impact);
            }
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        impact.x, impact.y, impact.z, 20,
                        0.22D, 0.35D, 0.22D, 0.06D);
                serverLevel.playSound(null, BlockPos.containing(impact),
                        SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.NEUTRAL, 0.45F, 1.05F);
            }
            discard();
        }
    }

    private void tickThirdEscape(ServerPlayer target) {
        escapeTicks++;
        setSprinting(true);
        applyUnknownMovementSpeed(true);
        getNavigation().moveTo(escapeX, getY(), escapeZ, 0.68D);

        emitRunningEffects();

        if (distanceToSqr(target) > 28.0D * 28.0D || escapeTicks >= 120) {
            disappearWithLeaveMessage();
        }
    }

    /** Configures the one-shot final State 3 encounter. */
    public void configureThirdEncounter(
            ServerPlayer target,
            String profileName,
            UUID profileUuid,
            boolean slim,
            double returnX,
            double returnY,
            double returnZ
    ) {
        this.thirdEncounter = true;
        this.secondEncounter = false;
        this.secondObserver = false;
        this.targetUuid = target == null ? null : target.getUUID();
        setProfileIdentity(profileName, profileUuid, slim);
        setCustomName(Component.literal(profileName));
        setCustomNameVisible(true);
        this.thirdReturnX = returnX;
        this.thirdReturnY = returnY;
        this.thirdReturnZ = returnZ;
        this.thirdAttackDone = false;
        this.escapeTicks = 0;
        moveTo(returnX, returnY, returnZ, getYRot(), 0.0F);
        beginState(UnknownState.THIRD_RETURN_APPROACH);
    }

    private ItemStack createGuideBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        // Minecraft 1.20.1 validates the written-book title; keep it within
        // the vanilla title limit so the client never shows "Invalid book tag".
        tag.putString("title", "Child Care Guide");
        tag.putString("author", getProfileNameForMessage());

        ListTag pages = new ListTag();
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(
                "Child Care Guide\n\nKeep the child safe, fed, and cared for.\n\nA child depends on its parents."
        ))));
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(
                "Milk\n\nMilk Bottle: use a Glass Bottle on a Cow.\n\nMilk Bucket: use a Bucket on a Cow.\n\nA Glass Bottle can also collect milk from another Player."
        ))));
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(
                "Golden Milk Bottle\n\nCraft with 8 Gold Ingots around 1 Milk Bottle.\n\nGGG / GMG / GGG\nG = Gold Ingot\nM = Milk Bottle"
        ))));
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(
                "Golden Apple\n\nCraft with 8 Gold Ingots around 1 Apple.\n\nGGG / GAG / GGG\nG = Gold Ingot\nA = Apple"
        ))));
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(
                "A Rare Story\n\nThere is no recipe written here for the Enchanted Golden Milk Bottle.\n\nThe old story says it will be found together with an Enchanted Golden Apple, without fail."
        ))));
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(
                "Growth\n\nA child needs time, care, and the right conditions to grow.\n\nProtect it while it is still small."
        ))));
        tag.put("pages", pages);
        return book;
    }

    private ItemStack createTotemGuideBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", "Baby Combat");
        tag.putString("author", getProfileNameForMessage());

        ListTag pages = new ListTag();
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(
                "Totem of Baby Combat\n\nThis is not an ordinary totem.\n\nIt belongs to the combat tradition of the babies."
        ))));
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(
                "Where to find it\n\nSeek the places where Baby Combat encounters are fought.\n\nThe totem is tied to those encounters, not ordinary treasure."
        ))));
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(
                "A warning\n\nIf you find one, do not treat it like decoration.\n\nIt was made for baby combat and is meant to be carried into danger."
        ))));
        tag.put("pages", pages);
        return book;
    }

    private void throwBookForward(ItemStack stack) {
        if (!(level() instanceof ServerLevel serverLevel) || stack == null || stack.isEmpty()) return;

        // Throw strictly along the NPC's current body-facing direction. No
        // forced look-at and no neck snap is performed by this method.
        float yaw = getYRot() * ((float) Math.PI / 180.0F);
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw)).normalize();
        Vec3 origin = getEyePosition().add(forward.scale(0.38D));
        ItemEntity item = new ItemEntity(serverLevel, origin.x, origin.y, origin.z, stack);
        item.setPickUpDelay(30);
        item.setDeltaMovement(forward.scale(0.32D).add(0.0D, 0.10D, 0.0D));
        serverLevel.addFreshEntity(item);
    }

    private void tickSecondObserverApproach(ServerPlayer target) {
        secondTimer++;
        setSprinting(true);
        applyUnknownMovementSpeed(true);
        setReactionCrouch(false);

        UnknownPlayerMobEntity attacker = getSecondPartner();
        if (attacker == null || !attacker.isAlive()) {
            secondFormationActive = false;
            secondObserverReady = false;
            moveTowardDynamicSurround(target, null, 1.00D);
            return;
        }

        boolean candidate = isSecondFormationCandidate(target, attacker, secondFormationActive);

        if (!secondFormationActive) {
            // ENTER: both NPCs must naturally arrive around the player's CURRENT
            // position. Player movement is allowed; only the relative geometry
            // matters.
            secondObserverReady = false;
            secondFormationExitTicks = 0;
            if (candidate) {
                secondFormationEnterTicks++;
            } else {
                secondFormationEnterTicks = 0;
            }

            moveTowardDynamicSurround(target, attacker, 1.00D);

            if (secondFormationEnterTicks >= SECOND_FORMATION_ENTER_TICKS) {
                secondFormationActive = true;
                secondFormationEnterTicks = 0;
                secondFormationExitTicks = 0;
                secondFormationStableTicks = 0;
                secondSurroundReplanTicks = 0;
            }
            return;
        }

        // ACTIVE FORMATION: keep moving relative to the player. A moving player
        // does NOT break formation by itself.
        if (!candidate) {
            secondFormationExitTicks++;
            secondObserverReady = false;
            secondFormationStableTicks = 0;
            moveTowardDynamicSurround(target, attacker, 1.00D);

            // EXIT only after the relative formation is genuinely gone for a
            // few ticks. Then immediately return to dynamic chase.
            if (secondFormationExitTicks >= SECOND_FORMATION_EXIT_TICKS) {
                secondFormationActive = false;
                secondFormationExitTicks = 0;
                secondFormationEnterTicks = 0;
                secondSurroundReplanTicks = 0;
            }
            return;
        }

        secondFormationExitTicks = 0;
        moveTowardDynamicSurround(target, attacker, 0.92D);

        // STABLE means the RELATIVE formation remains valid, not that the player
        // stands still. This works while the player is stopped, walking, or
        // sprinting as long as both actors keep their relative positions.
        secondFormationStableTicks++;
        if (secondFormationStableTicks >= SECOND_FORMATION_STABLE_REQUIRED) {
            secondObserverReady = true;
        }
    }

    private boolean isSecondFormationCandidate(
            ServerPlayer target,
            UnknownPlayerMobEntity attacker,
            boolean active
    ) {
        double min = active ? SECOND_FORMATION_EXIT_MIN_DISTANCE : SECOND_FORMATION_ENTER_MIN_DISTANCE;
        double max = active ? SECOND_FORMATION_EXIT_MAX_DISTANCE : SECOND_FORMATION_ENTER_MAX_DISTANCE;

        double thisDistance = Math.sqrt(distanceToSqr(target));
        double attackerDistance = Math.sqrt(attacker.distanceToSqr(target));
        if (thisDistance < min || thisDistance > max) return false;
        if (attackerDistance < min || attackerDistance > max) return false;

        Vec3 a = position().subtract(target.position());
        Vec3 b = attacker.position().subtract(target.position());
        a = new Vec3(a.x, 0.0D, a.z);
        b = new Vec3(b.x, 0.0D, b.z);
        if (a.lengthSqr() < 0.01D || b.lengthSqr() < 0.01D) return false;

        double cosine = a.normalize().dot(b.normalize());
        // They need to occupy meaningfully different sectors around the player,
        // but there is no prescribed left/right angle.
        return cosine < 0.25D;
    }

    private void moveTowardDynamicSurround(
            ServerPlayer target,
            UnknownPlayerMobEntity partner,
            double speed
    ) {
        setSprinting(true);
        applyUnknownMovementSpeed(true);

        if (onGround() && distanceToSqr(target) > 3.2D * 3.2D && --movementJumpTicks <= 0) {
            getJumpControl().jump();
            movementJumpTicks = 9 + random.nextInt(13);
        }

        if (secondSurroundReplanTicks > 0) {
            secondSurroundReplanTicks--;
            return;
        }

        Vec3 playerPos = target.position();
        Vec3 playerMotion = new Vec3(target.getDeltaMovement().x, 0.0D, target.getDeltaMovement().z);
        Vec3 playerForward = playerMotion.lengthSqr() > 0.0004D
                ? playerMotion.normalize()
                : new Vec3(0.0D, 0.0D, 1.0D);

        double angle;
        if (partner != null && partner.isAlive()) {
            Vec3 partnerOffset = partner.position().subtract(playerPos);
            partnerOffset = new Vec3(partnerOffset.x, 0.0D, partnerOffset.z);
            if (partnerOffset.lengthSqr() > 0.04D) {
                double partnerAngle = Math.atan2(partnerOffset.z, partnerOffset.x);
                // Aim generally opposite the partner, with a small changing bias.
                angle = partnerAngle + Math.PI + (random.nextDouble() - 0.5D) * 0.55D;
            } else {
                angle = random.nextDouble() * Math.PI * 2.0D;
            }
        } else {
            angle = Math.atan2(playerForward.z, playerForward.x)
                    + secondSurroundSide * (Math.PI * 0.55D);
        }

        secondSurroundSide = random.nextBoolean() ? 1 : -1;
        secondSurroundRadius = 3.4D + random.nextDouble() * 2.6D;

        Vec3 desired = playerPos
                .add(Math.cos(angle) * secondSurroundRadius, 0.0D, Math.sin(angle) * secondSurroundRadius);

        // Lead the live player position slightly when they are moving so the NPC
        // surrounds the player's path instead of chasing a stale point.
        if (playerMotion.lengthSqr() > 0.0004D) {
            desired = desired.add(playerForward.scale(Math.min(1.6D, playerMotion.length() * 7.0D)));
        }

        int bx = Mth.floor(desired.x);
        int bz = Mth.floor(desired.z);
        int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        Vec3 waypoint = new Vec3(desired.x, y, desired.z);

        secondChaseWaypointX = waypoint.x;
        secondChaseWaypointY = waypoint.y;
        secondChaseWaypointZ = waypoint.z;
        secondChaseWaypointValid = true;
        secondChaseReplanTicks = 4 + random.nextInt(5);
        secondSurroundReplanTicks = 5 + random.nextInt(8);
        getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, speed);
    }

    private void tickSecondObserver(ServerPlayer target) {
        secondTimer++;
        setSprinting(false);
        applyUnknownMovementSpeed(false);

        UnknownPlayerMobEntity attacker = getSecondPartner();
        if (attacker == null || !attacker.isAlive()) {
            getNavigation().stop();
            getLookControl().setLookAt(target, 35.0F, 35.0F);
            return;
        }

        getNavigation().stop();
        getLookControl().setLookAt(target, 35.0F, 35.0F);
        setReactionCrouch(false);

        // Actor 2 has landed its hit. Wait exactly two seconds, then Actor 1
        // delivers the original warning line. Keep the scene alive for another
        // two seconds before ending State 2.
        if (attacker.secondAttackDone && !secondDontScheduled) {
            if (secondTimer >= 40) {
                secondDontScheduled = true;
                secondTimer = 0;
                chatTo(target, "Dont do that he has a kid.");
            }
            return;
        }

        if (secondDontScheduled && !secondStageFinished) {
            if (secondTimer >= 40) {
                secondStageFinished = true;
                secondTimer = 0;
                attacker.secondStageFinished = true;
                attacker.beginEscape(target);
                beginEscape(target);
            }
        }
    }

    private void abortSecondEncounter(ServerPlayer target) {
        if (secondPlayerLost) return;
        secondPlayerLost = true;
        secondStageFinished = false;
        secondObserverReady = false;

        if (level() instanceof ServerLevel serverLevel && target != null) {
            UnknownPlayerSpawnEvents.cancelState3(serverLevel, target);
            UnknownPlayerSpawnEvents.markEncounterCompleted(target);
        }

        UnknownPlayerMobEntity partner = getSecondPartner();
        if (partner != null && partner.isAlive() && !partner.secondPlayerLost) {
            partner.secondPlayerLost = true;
            partner.secondStageFinished = false;
            if (target != null) partner.beginEscape(target);
            else partner.disappearWithLeaveMessage();
        }

        if (target != null) beginEscape(target);
        else disappearWithLeaveMessage();
    }

    private UnknownPlayerMobEntity getSecondPartner() {
        if (secondPartnerUuid == null) return null;
        if (!(level() instanceof ServerLevel serverLevel)) return null;
        if (serverLevel.getEntity(secondPartnerUuid) instanceof UnknownPlayerMobEntity mob) {
            return mob;
        }
        return null;
    }

    public void configureSecondEncounter(
            ServerPlayer target,
            boolean observer,
            UUID partnerUuid
    ) {
        bindTarget(target);
        this.secondEncounter = true;
        this.secondObserver = observer;
        this.secondPartnerUuid = partnerUuid;
        this.secondAttackDone = false;
        this.secondAttackStarted = false;
        this.secondObserverReady = false;
        this.secondFormationActive = false;
        this.secondFormationEnterTicks = 0;
        this.secondFormationExitTicks = 0;
        this.secondSurroundReplanTicks = 0;
        this.secondSurroundRadius = 4.5D;
        this.secondSurroundSide = random.nextBoolean() ? 1 : -1;
        this.secondTimer = 0;
        this.secondChatSent = false;
        this.secondFinishChatSent = false;
        this.secondGuideBookThrown = false;
        this.secondGuideThrowTicks = 0;
        this.secondExitDelayTicks = 0;
        this.secondPlayerLost = false;
        this.secondStageFinished = false;
        this.secondDontScheduled = false;
        this.secondDontDelayTicks = 0;
        this.secondPearlCatchupTicks = 0;
        this.secondPearlCatchupActive = false;
        this.secondPearlPressureTicks = 0;
        this.secondPearlLastDistanceSqr = -1.0D;
        this.secondPearlCooldownTicks = 0;
        this.secondPearlDecisionThreshold = 26 + random.nextInt(13);
        this.secondFormationPointValid = false;
        this.secondFormationStableTicks = 0;
        this.secondFormationAttemptTicks = 0;
        this.secondFormationLastPlayerPosition = target != null ? target.position() : Vec3.ZERO;
        this.secondChaseReplanTicks = 0;
        this.secondChaseWaypointValid = false;
        this.stateTicks = 0;
        this.reactionStep = -1;
        this.reactionTimer = 0;
        this.unknownState = observer
                ? UnknownState.SECOND_OBSERVER_APPROACH
                : UnknownState.SECOND_ATTACKER_APPROACH;
    }

    public void setSecondEncounterPartner(UUID partnerUuid) {
        this.secondPartnerUuid = partnerUuid;
    }

    public boolean isSecondAttackDone() {
        return secondAttackDone;
    }

    private void finishSecondEncounterAndLeave() {
        // Actor 1 must leave first, but remain visible long enough for Actor 2
        // to perform the final slow look and throw. Reuse the normal escape
        // movement so the exit is an actual walk-away rather than a pop.
        setReactionCrouch(false);
        ServerPlayer target = getBoundTarget();
        if (target != null) {
            beginEscape(target);
        } else {
            disappearWithLeaveMessage();
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
        escapeFollowUuid = null;
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

    private void beginEscapeFollow(UnknownPlayerMobEntity leader) {
        if (leader == null || !leader.isAlive()) return;
        escapeFollowUuid = leader.getUUID();
        unknownState = UnknownState.ESCAPE;
        stateTicks = 0;
        escapeTicks = 0;
        setReactionCrouch(false);
        setXRot(0.0F);
        reactionStep = -1;
        escapeX = leader.getX();
        escapeZ = leader.getZ();
        thirdReturnX = getX();
        thirdReturnY = getY();
        thirdReturnZ = getZ();
    }

    private void tickEscape(ServerPlayer target) {
        escapeTicks++;
        setSprinting(true);
        applyUnknownMovementSpeed(true);

        if (escapeFollowUuid != null && level() instanceof ServerLevel serverLevel) {
            Entity leaderEntity = serverLevel.getEntity(escapeFollowUuid);
            if (leaderEntity instanceof UnknownPlayerMobEntity leader && leader.isAlive()) {
                applyUnknownMovementSpeed(true);
                getNavigation().moveTo(leader, 1.0D);
                if (distanceToSqr(leader) <= 2.4D * 2.4D) {
                    disappearWithLeaveMessage();
                }
                return;
            }
            escapeFollowUuid = null;
        }

        applyUnknownMovementSpeed(true);

        // Stage 1 exit: keep the original fast Player-like escape instead of
        // simply sprinting in a straight line. The actor repeatedly chooses
        // a short waypoint ahead of the logout point, alternating a natural
        // left/right bias while preserving forward momentum.
        updateEscapeErraticWaypoint(target);

        // Jump repeatedly throughout the escape until the logout condition is
        // reached. This is an actual vanilla jump, never a position change.
        if (onGround() && --movementJumpTicks <= 0) {
            getJumpControl().jump();
            movementJumpTicks = 12 + random.nextInt(11);
        }

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
        // Do not manufacture dust/particles. Vanilla movement should provide
        // the visual result; keep only a light cadence of ordinary step sounds.
        if (++footstepTimer < 7 || !onGround()) return;
        footstepTimer = 0;

        BlockPos pos = blockPosition().below();
        BlockState state = level().getBlockState(pos);
        if (state.isAir()) return;

        level().playSound(
                null,
                blockPosition(),
                state.getSoundType().getStepSound(),
                getSoundSource(),
                0.35F,
                0.96F + random.nextFloat() * 0.08F
        );
    }

    @Override
    public void die(DamageSource source) {
        // Stage 1 can be killed normally. A dead Stage 1 actor must not
        // transition into State 2.
        if (!secondEncounter && !thirdEncounter) {
            ServerPlayer target = getBoundTarget();
            if (target != null && level() instanceof ServerLevel serverLevel) {
                UnknownPlayerSpawnEvents.cancelSecondEncounter(serverLevel, target);
            }
        }
        super.die(source);
    }

    public String getProfileNameForMessage() {
        return getGameProfile() != null && getGameProfile().getName() != null
                ? getGameProfile().getName()
                : "Unknown Player";
    }

    private void disappearWithLeaveMessage() {
        ServerPlayer target = getBoundTarget();

        if (!leftMessageSent && level() instanceof ServerLevel serverLevel) {
            leftMessageSent = true;
            if (target != null) {
                // Leave is a system event. Keep it separate from NPC speech.
                target.sendSystemMessage(
                        Component.translatable("multiplayer.player.left", Component.literal(getProfileNameForMessage()))
                                .withStyle(ChatFormatting.YELLOW)
                );
            }

            // State 1 -> State 2.
            if (!secondEncounter && !thirdEncounter && target != null) {
                GameProfile profile = getGameProfile();
                if (profile != null) {
                    UnknownPlayerSpawnEvents.scheduleSecondEncounter(
                            serverLevel, target, profile.getName(), profile.getId(), isSlim()
                    );
                }
            }

            // Only the fully completed Actor 2 branch of State 2 gets the
            // 50% State 3 roll. Aborted/lost-target State 2 never continues.
            if (secondEncounter && !secondObserver && secondStageFinished
                    && !secondPlayerLost && target != null) {
                GameProfile profile = getGameProfile();
                if (profile != null) {
                    UnknownPlayerSpawnEvents.scheduleState3(
                            serverLevel, target, profile.getName(), profile.getId(), isSlim(),
                            thirdReturnX, thirdReturnY, thirdReturnZ
                    );
                }
            }

            if (thirdEncounter && target != null && target.server.isHardcore()) {
                UnknownPlayerSpawnEvents.markEncounterCompleted(target);
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
