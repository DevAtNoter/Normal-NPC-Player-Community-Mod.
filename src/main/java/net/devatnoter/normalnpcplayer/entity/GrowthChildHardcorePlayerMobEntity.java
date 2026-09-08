package net.devatnoter.normalnpcplayer.entity;

import net.devatnoter.normalnpcplayer.ai.growth.GrowthChildHardcoreBrain;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Hardcore Baby grown form.
 *
 * This entity deliberately does NOT run GrowthChildBrain. It has its own small
 * post-growth brain containing only wandering, following and holding position.
 * The global GrowthChild combat-target bridge still allows hostile mobs to
 * target this entity because it is a GrowthChildPlayerMobEntity subtype.
 */
public class GrowthChildHardcorePlayerMobEntity extends GrowthChildPlayerMobEntity {
    private static final String PARENT_UUID_TAG = "HardcoreChildParentUUID";
    private static final String PARENT_NAME_TAG = "HardcoreChildParentName";
    private static final String OWNER_UUID_TAG = "HardcoreChildOwnerUUID";
    private static final String OWNER_NAME_TAG = "HardcoreChildOwnerName";
    private static final String THANK_YOU_AT_TAG = "HardcoreChildThankYouAt";
    private static final String THANK_YOU_DONE_TAG = "HardcoreChildThankYouDone";

    private UUID parentUUID, hardcoreOwnerUUID;
    private String parentName = "", hardcoreOwnerName = "";
    private boolean sendingHardcoreThankYou;

    public GrowthChildHardcorePlayerMobEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setStyle(Style.HARDCORE);
        // Hardcore Growth Children never use the GrowthChild chunk-loader bridge.
        setChunkLoadEnabled(false);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        GrowthChildHardcoreBrain.registerGoals(this);
    }

    @Override
    public void tick() {
        // Keep the normal LivingEntity/Mob/PathfinderMob tick chain, but do not
        // invoke GrowthChildBrain, GrowthChildAIController or NPCPlayerChunkManager.
        tickAdultBase();
        setPersistenceRequired();

        if (!level().isClientSide) {
            GrowthChildHardcoreBrain.tick(this);
            tickHardcoreThankYou();
        }
    }

    /**
     * Hardcore Growth Children have exactly three right-click modes:
     * WANDERING -> FOLLOW -> HOLD POSITION -> WANDERING.
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player == null || !isAuthorized(player) || !player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) return InteractionResult.SUCCESS;

        switch (getCurrentIntent()) {
            case WANDER_HOME, IDLE, WORKING, GO_HOME, GUARD, MINING, WOOD_COLLECTION, CUSTOM -> {
                setFollowTarget(player);
                setCurrentIntent(AIIntent.FOLLOW);
                setCurrentTask("following " + player.getName().getString());
                chatToPlayer((ServerPlayer) player, "I will follow you.");
            }
            case FOLLOW -> {
                setFollowTarget(null);
                setCurrentIntent(AIIntent.HOLD_POSITION);
                setCurrentTask("holding position");
                getNavigation().stop();
                chatToPlayer((ServerPlayer) player, "I will hold my position.");
            }
            case HOLD_POSITION -> {
                setFollowTarget(null);
                setCurrentIntent(AIIntent.WANDER_HOME);
                setCurrentTask("wandering");
                chatToPlayer((ServerPlayer) player, "I will wander around.");
            }
        }

        swingInteraction();
        return InteractionResult.SUCCESS;
    }

    /**
     * Hardcore death notification uses recipient-specific identity.
     * Named child: "Alex has fallen".
     * Family/owner when unnamed: "My Child has fallen."
     * Everyone else when unnamed: "Someone's Child has fallen."
     */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (level() instanceof ServerLevel level) {
            String familyName = hasCustomName()
                    ? getCustomName().getString()
                    : familyFallbackForRecipient(null, true);

            for (ServerPlayer recipient : level.players()) {
                if (recipient == null || !recipient.isAlive()) continue;
                String display = hasCustomName()
                        ? familyName
                        : familyFallbackForRecipient(recipient, isFamilyRecipient(recipient));
                recipient.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                                display + (hasCustomName() ? " has fallen" : " has fallen.")
                        )
                );
            }
        }
        super.die(source);
    }

    private boolean isFamilyRecipient(ServerPlayer player) {
        UUID id = player.getUUID();
        return (getTrueFamilyParentAUUID() != null && getTrueFamilyParentAUUID().equals(id))
                || (getTrueFamilyParentBUUID() != null && getTrueFamilyParentBUUID().equals(id))
                || (getTrueFamilyOwnerUUID() != null && getTrueFamilyOwnerUUID().equals(id));
    }

    private String familyFallbackForRecipient(ServerPlayer recipient, boolean familyRecipient) {
        return familyRecipient ? "My Child" : "Someone's Child";
    }

    public void setHardcoreChildLineage(
            UUID parentUUID,
            String parentName,
            UUID ownerUUID,
            String ownerName
    ) {
        this.parentUUID = parentUUID;
        this.parentName = parentName == null ? "" : parentName;
        this.hardcoreOwnerUUID = ownerUUID;
        this.hardcoreOwnerName = ownerName == null ? "" : ownerName;
        setFamilyLineage(
                parentUUID,
                this.parentName,
                null,
                "",
                ownerUUID,
                this.hardcoreOwnerName
        );

        // The one and only Hardcore post-growth speech happens three seconds
        // after the transformation. It is stored as world game-time so the
        // delay remains deterministic across ticks/save boundaries.
        if (level() instanceof ServerLevel serverLevel) {
            getPersistentData().putLong(
                    THANK_YOU_AT_TAG,
                    serverLevel.getGameTime() + 60L
            );
            getPersistentData().putBoolean(THANK_YOU_DONE_TAG, false);
        }

        // Hardcore starts in the first and default cycle state: wandering.
        setFollowTarget(null);
        setCurrentIntent(AIIntent.WANDER_HOME);
        setCurrentTask("wandering");
    }

    private void tickHardcoreThankYou() {
        if (getPersistentData().getBoolean(THANK_YOU_DONE_TAG)) return;
        long at = getPersistentData().getLong(THANK_YOU_AT_TAG);
        if (at <= 0L || !(level() instanceof ServerLevel level) || level.getGameTime() < at) return;

        getPersistentData().putBoolean(THANK_YOU_DONE_TAG, true);
        if (hardcoreOwnerUUID == null) return;

        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(hardcoreOwnerUUID);
        if (owner == null || !owner.isAlive()) return;

        String name = hardcoreOwnerName == null || hardcoreOwnerName.isBlank()
                ? owner.getGameProfile().getName()
                : hardcoreOwnerName;
        String message = "Thank you, " + name + ". You are the greatest parent I could ever have.";

        // Send the one growth speech to the family. The chat sender name is
        // resolved per recipient by GrowthChildPlayerMobEntity: named children
        // keep their real name, family members see "My Child", and everyone
        // else would see "Someone's Child".
        sendingHardcoreThankYou = true;
        try {
            for (UUID id : getFamilyMemberUUIDs()) {
                ServerPlayer recipient = level.getServer().getPlayerList().getPlayer(id);
                if (recipient != null && recipient.isAlive()) {
                    super.chatTo(recipient, message);
                }
            }
        } finally {
            sendingHardcoreThankYou = false;
        }
    }

    /** Hardcore Growth Children are silent except for the one growth message. */
    @Override
    public boolean chatTo(ServerPlayer player, String message) {
        if (!sendingHardcoreThankYou) return false;
        return super.chatTo(player, message);
    }

    public UUID getHardcoreChildParentUUID() { return parentUUID; }
    public String getHardcoreChildParentName() { return parentName; }
    public UUID getHardcoreChildOwnerUUID() { return hardcoreOwnerUUID; }
    public String getHardcoreChildOwnerName() { return hardcoreOwnerName; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (parentUUID != null) tag.putUUID(PARENT_UUID_TAG, parentUUID);
        tag.putString(PARENT_NAME_TAG, parentName);
        if (hardcoreOwnerUUID != null) tag.putUUID(OWNER_UUID_TAG, hardcoreOwnerUUID);
        tag.putString(OWNER_NAME_TAG, hardcoreOwnerName);
        if (getPersistentData().contains(THANK_YOU_AT_TAG)) {
            tag.putLong(THANK_YOU_AT_TAG, getPersistentData().getLong(THANK_YOU_AT_TAG));
        }
        tag.putBoolean(THANK_YOU_DONE_TAG, getPersistentData().getBoolean(THANK_YOU_DONE_TAG));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        parentUUID = tag.hasUUID(PARENT_UUID_TAG) ? tag.getUUID(PARENT_UUID_TAG) : null;
        parentName = tag.getString(PARENT_NAME_TAG);
        hardcoreOwnerUUID = tag.hasUUID(OWNER_UUID_TAG) ? tag.getUUID(OWNER_UUID_TAG) : null;
        hardcoreOwnerName = tag.getString(OWNER_NAME_TAG);
        if (tag.contains(THANK_YOU_AT_TAG)) {
            getPersistentData().putLong(THANK_YOU_AT_TAG, tag.getLong(THANK_YOU_AT_TAG));
        }
        getPersistentData().putBoolean(THANK_YOU_DONE_TAG, tag.getBoolean(THANK_YOU_DONE_TAG));
        setStyle(Style.HARDCORE);
        setChunkLoadEnabled(false);

        // Legacy/invalid modes are normalized into the three-mode Hardcore cycle.
        AIIntent intent = getCurrentIntent();
        if (intent != AIIntent.WANDER_HOME
                && intent != AIIntent.FOLLOW
                && intent != AIIntent.HOLD_POSITION) {
            setCurrentIntent(AIIntent.WANDER_HOME);
            setCurrentTask("wandering");
        }
    }
}
