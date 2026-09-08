package net.devatnoter.normalnpcplayer.entity;

import net.devatnoter.normalnpcplayer.ai.growth.GrowthChildBrain;
import net.devatnoter.normalnpcplayer.ai.growth.NPCPlayerChunkManager;
import net.devatnoter.normalnpcplayer.ai.growth.GrowthChildAIStats;
import net.devatnoter.normalnpcplayer.ai.growth.GrowthChildCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ChatType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Items;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.level.block.Blocks;
import java.util.Optional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Dedicated Player-like adult form produced by Player Baby growth. */
public class GrowthChildPlayerMobEntity extends AdultPlayerMobEntity {
    private static final String FAMILY_TAG = "GrowthChildFamily";
    private static final String PARENT_A_UUID_TAG = "TrueFamilyParentAUUID";
    private static final String PARENT_A_NAME_TAG = "TrueFamilyParentAName";
    private static final String PARENT_B_UUID_TAG = "TrueFamilyParentBUUID";
    private static final String PARENT_B_NAME_TAG = "TrueFamilyParentBName";
    private static final String OWNER_UUID_TAG = "TrueFamilyOwnerUUID";
    private static final String OWNER_NAME_TAG = "TrueFamilyOwnerName";
    private static final String HOME_SET_TAG = "HomeSet";
    private static final String HOME_X_TAG = "HomeX";
    private static final String HOME_Y_TAG = "HomeY";
    private static final String HOME_Z_TAG = "HomeZ";
    private static final String HOME_DIM_TAG = "HomeDimension";
    private static final String FOLLOW_TAG = "FollowEnabled";
    private static final String FOLLOW_UUID_TAG = "FollowTargetUUID";
    private static final String GUARD_TAG = "GuardEnabled";
    private static final String GUARD_X_TAG = "GuardX";
    private static final String GUARD_Y_TAG = "GuardY";
    private static final String GUARD_Z_TAG = "GuardZ";
    private static final String HOLD_X_TAG = "HoldX";
    private static final String HOLD_Y_TAG = "HoldY";
    private static final String HOLD_Z_TAG = "HoldZ";
    private static final String BIRTH_X_TAG = "BirthX";
    private static final String BIRTH_Y_TAG = "BirthY";
    private static final String BIRTH_Z_TAG = "BirthZ";
    private static final String BIRTH_DIM_TAG = "BirthDimension";
    private static final String BED_SET_TAG = "OwnBedSet";
    private static final String BED_X_TAG = "OwnBedX";
    private static final String BED_Y_TAG = "OwnBedY";
    private static final String BED_Z_TAG = "OwnBedZ";
    private static final String BED_DIM_TAG = "OwnBedDimension";
    private static final String CHUNK_ENABLED_TAG = "ChunkLoadEnabled";
    private static final String CHUNK_RADIUS_TAG = "ChunkLoadRadius";
    private static final String STEALTH_TAG = "StealthThreat";
    private static final String DAILY_TAG = "DailyScheduleState";
    private static final String TASK_TAG = "GrowthCurrentTask";
    private static final String STORAGE_X = "GrowthChildStorageX";
    private static final String STORAGE_Y = "GrowthChildStorageY";
    private static final String STORAGE_Z = "GrowthChildStorageZ";
    private static final String STORAGE_TICKS = "GrowthChildStorageTicks";
    /** Vanilla Java Player movement baseline: Player createAttributes uses 0.1. */
    public static final double PLAYER_MOVEMENT_SPEED = 0.1D;
    /** Vanilla Java Player base jump power. */
    public static final float PLAYER_JUMP_POWER = 0.42F;

    public enum DailyState { MORNING, WORK_MORNING, LUNCH_BREAK, WORK_AFTERNOON, RETURN_HOME, NIGHT }
    public enum CombatState { IDLE, NEUTRAL, ALERT, FIGHT, FLEE, GUARD }
    public enum AIIntent { IDLE, FOLLOW, HOLD_POSITION, WANDER_HOME, WORKING, GO_HOME, GUARD, MINING, WOOD_COLLECTION, CUSTOM }

    private UUID parentAUUID, parentBUUID, ownerUUID, followTargetUUID;
    /** Stable sibling number used only for the family-facing chat identity. */
    private int familyChildNumber = 0;
    private String parentAName = "", parentBName = "", ownerName = "";
    private boolean homeSet, following, guarding, stealthThreat, chunkLoadEnabled = true;
    private BlockPos homePos, guardAnchor, birthPos, ownBedPos;
    private String homeDimension = "", birthDimension = "", ownBedDimension = "";
    private int chunkLoadRadius = 2;
    private DailyState dailyState = DailyState.MORNING;
    private String currentTask = "idle";
    private CombatState combatState = CombatState.IDLE;
    private AIIntent currentIntent = AIIntent.IDLE;
    private float foodLevel = 20.0F;
    private float foodSaturation = 5.0F;
    /** AI-readable factual counters; owned by the entity, not the AI brain. */
    private final GrowthChildAIStats aiStats = new GrowthChildAIStats();
    /** Last external communication input. The brain consumes it as input. */
    private GrowthChildCommand aiCommand;

    public GrowthChildPlayerMobEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        // Growth Children are persistent world entities. They must never be
        // removed by the normal Mob distance-despawn system.
        setPersistenceRequired();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        // AdultPlayerMobEntity already supplies MOVEMENT_SPEED. Do not add the
        // same attribute a second time; the GrowthChild tick pins its base
        // value to the vanilla Player value (0.1) after the adult base tick.
        return AdultPlayerMobEntity.createAttributes();
    }

    @Override
    protected float getJumpPower() {
        return PLAYER_JUMP_POWER;
    }

    /** Bridge used by GrowthChildBrain for vanilla Elytra fall-flying physics. */
    public void setCombatFallFlying(boolean flying) {
        setSharedFlag(7, flying);
    }

    @Override protected void registerGoals() {
        GrowthChildBrain.registerGoals(this);
    }

    @Override public void tick() {
        // Re-assert persistence after NBT reconstruction as well.
        setPersistenceRequired();
        tickAdultBase();
        if (!level().isClientSide) {
            net.devatnoter.normalnpcplayer.ai.growth.GrowthChildAIController.tick(this);
            GrowthChildBrain.tick(this);
            if (level() instanceof ServerLevel sl) NPCPlayerChunkManager.tick(sl,getUUID(),new net.minecraft.world.level.ChunkPos(blockPosition()),chunkLoadRadius,chunkLoadEnabled);
        }
    }

    @Override public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (level() instanceof ServerLevel sl) {
            NPCPlayerChunkManager.releaseAll(sl,getUUID());
            // Hardcore Growth Child has its own recipient-aware death
            // notification in GrowthChildHardcorePlayerMobEntity.die().
            // Do not emit the normal Growth Child notification here or the
            // family will receive the death message twice.
            if (!(this instanceof GrowthChildHardcorePlayerMobEntity)) {
                notifyFamilyDeath(sl);
            }
        }
        super.die(source);
    }

    private void notifyFamilyDeath(ServerLevel level) {
        String display = hasCustomName() ? getCustomName().getString() : familyFallbackName();
        Component message = Component.literal(display + " has fallen.");
        for (UUID id : getFamilyMemberUUIDs()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.isAlive()) {
                player.sendSystemMessage(message);
            }
        }
    }

    /** Player-like armor durability for the four actual armor slots. */
    @Override
    protected void hurtArmor(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (amount <= 0.0F || source.is(DamageTypeTags.BYPASSES_ARMOR)) return;
        int durabilityDamage = (int)(amount * 4.0F);
        if (durabilityDamage <= 0) return;

        EquipmentSlot[] slots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET
        };
        for (EquipmentSlot slot : slots) {
            ItemStack stack = getItemBySlot(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) continue;
            stack.hurtAndBreak(durabilityDamage, this, entity -> entity.broadcastBreakEvent(slot));
            setItemSlot(slot, stack);
        }
    }

    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty() || !isAuthorized(player)) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            setHome(blockPosition());
            swingInteraction();
            chatToIfServer(player, "I will remember this as home.");
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (level().isClientSide) return InteractionResult.SUCCESS;

        // A pending "May I follow you?" request is accepted by one normal right-click.
        if (getPersistentData().hasUUID("GrowthChildFollowRequesterUUID")
                && player.getUUID().equals(getPersistentData().getUUID("GrowthChildFollowRequesterUUID"))) {
            getPersistentData().remove("GrowthChildFollowRequesterUUID");
            getPersistentData().remove("GrowthChildFollowRequestTicks");
            getPersistentData().putInt("GrowthChildFollowRequestCooldown", 1200);
            setGuarding(false);
            setFollowTarget(player);
            setCurrentIntent(AIIntent.FOLLOW);
            setCurrentTask("following " + player.getName().getString());
            getNavigation().stop();
            swingInteraction();
            chatToIfServer(player, "Yes. I will follow you.");
            return InteractionResult.SUCCESS;
        }

        // Visible cycle: DEFAULT -> FOLLOW -> HOLD -> WANDER HOME -> WORKING -> DEFAULT.
        // A normal authorized right-click now directly enables following. The
        // autonomous "May I follow you?" request remains available separately.
        switch (currentIntent) {
            case IDLE -> {
                setGuarding(false); setFollowTarget(player); setCurrentIntent(AIIntent.FOLLOW);
                setCurrentTask("following " + player.getName().getString()); getNavigation().stop();
                chatToIfServer(player, "I will follow you.");
            }
            case FOLLOW -> {
                setFollowTarget(null); setGuarding(false); setHoldPosition(position());
                setCurrentIntent(AIIntent.HOLD_POSITION);
                setCurrentTask("holding position"); getNavigation().stop();
                chatToIfServer(player, "I will hold my position.");
            }
            case HOLD_POSITION -> {
                setFollowTarget(null); setGuarding(false); setCurrentIntent(AIIntent.WANDER_HOME);
                setCurrentTask("wandering around home"); chatToIfServer(player, "I will wander around home.");
            }
            case WANDER_HOME -> {
                setFollowTarget(null); setGuarding(false); setCurrentIntent(AIIntent.WORKING);
                setCurrentTask("working"); chatToIfServer(player, "I will work.");
            }
            case WORKING -> {
                setFollowTarget(null); setGuarding(false); setCurrentIntent(AIIntent.IDLE);
                setCurrentTask("default routine"); chatToIfServer(player, "I will return to my default routine.");
            }
            default -> {
                // Any special mode (guard/go-home/etc.) returns to DEFAULT on a normal click.
                setFollowTarget(null); setGuarding(false); setCurrentIntent(AIIntent.IDLE);
                setCurrentTask("default routine"); chatToIfServer(player, "I will return to my default routine.");
            }
        }
        swingInteraction();
        return InteractionResult.SUCCESS;
    }

    public void chatToPlayer(ServerPlayer player, String msg) { if (player != null) chatTo(player, msg); }

    /** Player-like hand animation for a successful autonomous main-hand interaction. */
    public void swingInteraction() {
        swing(InteractionHand.MAIN_HAND);
    }

    private void chatToIfServer(Player p, String msg) { if (p instanceof ServerPlayer sp) chatTo(sp, msg); }

    public void toggleGuard(BlockPos anchor) { guarding = !guarding; if (guarding) { guardAnchor = anchor.immutable(); combatState = CombatState.GUARD; currentTask = "guarding"; } else { combatState = CombatState.IDLE; currentTask = "returning to routine"; } }
    public boolean isGuarding(){return guarding;}
    public BlockPos getGuardAnchor(){return guardAnchor;}
    public void setHome(BlockPos p){homeSet=true;homePos=p.immutable();homeDimension=level().dimension().location().toString();}
    public boolean hasHome(){return homeSet&&homePos!=null;}
    public BlockPos getHomePos(){return homePos;}
    public String getHomeDimension(){return homeDimension;}
    public void setHoldPosition(net.minecraft.world.phys.Vec3 p){
        if(p==null) return;
        getPersistentData().putDouble(HOLD_X_TAG,p.x);
        getPersistentData().putDouble(HOLD_Y_TAG,p.y);
        getPersistentData().putDouble(HOLD_Z_TAG,p.z);
    }
    public net.minecraft.world.phys.Vec3 getHoldPosition(){
        var tag=getPersistentData();
        if(!tag.contains(HOLD_X_TAG)||!tag.contains(HOLD_Y_TAG)||!tag.contains(HOLD_Z_TAG)) return null;
        return new net.minecraft.world.phys.Vec3(tag.getDouble(HOLD_X_TAG),tag.getDouble(HOLD_Y_TAG),tag.getDouble(HOLD_Z_TAG));
    }
    public void setFollowTarget(LivingEntity target){ if(target==null){following=false;followTargetUUID=null;} else {following=true;followTargetUUID=target.getUUID();} }
    public boolean isFollowing(){return following;}
    public Player getFollowTarget(){if(followTargetUUID==null||!(level() instanceof ServerLevel sl))return null;return sl.getPlayerByUUID(followTargetUUID);}
    public boolean isStealthThreat(){return stealthThreat;}
    public void setStealthThreat(boolean value){stealthThreat=value;}
    public DailyState getDailyState(){return dailyState;}
    public void setDailyState(DailyState s){dailyState=s;}
    public void setCurrentTask(String s){currentTask=s==null?"idle":s;}
    public String getCurrentTask(){return currentTask;}
    public AIIntent getCurrentIntent(){return currentIntent;}
    public void setCurrentIntent(AIIntent intent){currentIntent=intent==null?AIIntent.IDLE:intent;}
    public void setGuarding(boolean value){guarding=value; if(value) combatState=CombatState.GUARD;}
    public int getFoodLevel(){return Math.max(0, Math.min(20, (int)Math.floor(foodLevel)));}
    public float getFoodLevelExact(){return Math.max(0.0F, Math.min(20.0F, foodLevel));}
    public float getFoodSaturation(){return Math.max(0.0F, Math.min(20.0F, foodSaturation));}
    public void setFoodLevel(float value){foodLevel=Math.max(0.0F, Math.min(20.0F,value));}
    public void setFoodSaturation(float value){foodSaturation=Math.max(0.0F, Math.min(20.0F,value));}
    public GrowthChildAIStats getAIStats(){return aiStats;}
    public GrowthChildCommand getAICommand(){return aiCommand;}
    public void setAICommand(GrowthChildCommand command){aiCommand=command;}
    public void clearAICommand(){aiCommand=null;}
    public CombatState getCombatState(){return combatState;}
    public void setCombatState(CombatState state){combatState=state==null?CombatState.IDLE:state;}

    public boolean isFamilyMember(Entity entity) {
        if (entity == null) return false;
        UUID id = entity.getUUID();
        return id.equals(parentAUUID) || id.equals(parentBUUID) || id.equals(ownerUUID);
    }

    public void sayFamily(String message, double radius) {
        if (!(level() instanceof ServerLevel level) || message == null || message.isBlank()) return;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(radius), p -> p.isAlive() && isAuthorized(p))) {
            chatTo(player, message);
        }
    }
    public void setChunkLoadEnabled(boolean b){chunkLoadEnabled=b;}
    public boolean isChunkLoadEnabled(){return chunkLoadEnabled;}
    public int getChunkLoadRadius(){return chunkLoadRadius;}
    public void setChunkLoadRadius(int r){chunkLoadRadius=Math.max(0,Math.min(8,r));}
    public void setBirthLocation(BlockPos p, ResourceLocation dim){birthPos=p.immutable();birthDimension=dim==null?"":dim.toString();}
    public void setBirthLocation(BlockPos p, String dim){birthPos=p.immutable();birthDimension=dim==null?"":dim;}
    public BlockPos getBirthLocation(){return birthPos;}
    public String getBirthDimension(){return birthDimension;}
    public void setOwnBed(BlockPos p){ownBedPos=p.immutable();ownBedDimension=level().dimension().location().toString();}
    public BlockPos getOwnBed(){return ownBedPos;}
    public boolean hasOwnBed(){return ownBedPos!=null;}
    public String getOwnBedDimensionForLifecycle(){return ownBedDimension;}

    public void setFamilyLineage(UUID a,String an,UUID b,String bn,UUID owner,String ownerName){parentAUUID=a;parentAName=an==null?"":an;parentBUUID=b;parentBName=bn==null?"":bn;ownerUUID=owner;this.ownerName=ownerName==null?"":ownerName;}
    public List<UUID> getFamilyMemberUUIDs(){List<UUID> r=new ArrayList<>();if(parentAUUID!=null)r.add(parentAUUID);if(parentBUUID!=null)r.add(parentBUUID);if(ownerUUID!=null&&!r.contains(ownerUUID))r.add(ownerUUID);return r;}
    public UUID getTrueFamilyParentAUUID(){return parentAUUID;}
    public UUID getTrueFamilyParentBUUID(){return parentBUUID;}
    public UUID getTrueFamilyOwnerUUID(){return ownerUUID;}
    public String getTrueFamilyParentAName(){return parentAName;}
    public String getTrueFamilyParentBName(){return parentBName;}
    public String getTrueFamilyOwnerName(){return ownerName;}
    /** Display name used by NPC chat when no explicit name was assigned. */
    @Override
    public String getChatDisplayName() {
        if (hasCustomName()) return getCustomName().getString();
        return familyFallbackName();
    }

    private String getChatDisplayNameFor(ServerPlayer viewer) {
        if (hasCustomName()) return getCustomName().getString();
        if (viewer != null && ownerUUID != null && isAuthorized(viewer)) return familyFallbackName();
        return "Someone's Child";
    }

    /**
     * Family-facing fallback identity:
     * one child remains "My Child"; as soon as the family has more than one
     * child, every child gets a stable number (My Child 1, My Child 2, ...).
     * Explicit custom names always take precedence.
     */
    private String familyFallbackName() {
        if (ownerUUID == null) return "My Child";
        ensureFamilyChildNumber();
        int siblingCount = countFamilyChildren();
        return siblingCount > 1 && familyChildNumber > 0
                ? "My Child " + familyChildNumber
                : "My Child";
    }

    private int countFamilyChildren() {
        if (!(level() instanceof ServerLevel level)) return 1;
        return level.getEntitiesOfClass(
                GrowthChildPlayerMobEntity.class,
                getBoundingBox().inflate(256.0D),
                child -> child.isAlive() && sameFamilyOwner(child)
        ).size();
    }

    private boolean sameFamilyOwner(GrowthChildPlayerMobEntity child) {
        return child != this && ownerUUID != null && ownerUUID.equals(child.ownerUUID);
    }

    private void ensureFamilyChildNumber() {
        if (familyChildNumber > 0 || ownerUUID == null || !(level() instanceof ServerLevel level)) return;
        int max = 0;
        for (GrowthChildPlayerMobEntity child : level.getEntitiesOfClass(
                GrowthChildPlayerMobEntity.class,
                getBoundingBox().inflate(256.0D),
                x -> x != this && x.isAlive() && sameFamilyOwner(x))) {
            max = Math.max(max, child.familyChildNumber);
        }
        familyChildNumber = max + 1;
    }

    public int getFamilyChildNumber() {
        ensureFamilyChildNumber();
        return familyChildNumber;
    }

    /** Called once after lineage is attached to a newly grown child. */
    public void assignFamilyChildNumber() {
        if (familyChildNumber > 0 || ownerUUID == null || !(level() instanceof ServerLevel level)) return;
        int max = 0;
        for (GrowthChildPlayerMobEntity child : level.getEntitiesOfClass(
                GrowthChildPlayerMobEntity.class,
                getBoundingBox().inflate(256.0D),
                x -> x != this && x.isAlive() && sameFamilyOwner(x))) {
            max = Math.max(max, child.familyChildNumber);
        }
        familyChildNumber = max + 1;
    }

    /**
     * Growth Child chat must resolve the fallback name per recipient. A child
     * owned by another player must not appear as "Your Child" to everyone.
     */
    @Override
    public boolean chatTo(ServerPlayer player, String message) {
        if (!net.devatnoter.normalnpcplayer.config.NNPConfig.isGrowthChildSpeechEnabled()) return false;
        if (player == null || !player.isAlive()) return false;
        net.devatnoter.normalnpcplayer.event.NPCPlayerChatEvent event =
                new net.devatnoter.normalnpcplayer.event.NPCPlayerChatEvent(this, message);
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event)) return false;
        String output = event.getMessage();
        if (output == null || output.isBlank()) return false;
        player.connection.sendDisguisedChatMessage(
                Component.literal(output),
                ChatType.bind(
                        ChatType.CHAT,
                        player.serverLevel().registryAccess(),
                        Component.literal(getChatDisplayNameFor(player))
                )
        );
        return true;
    }

    public boolean isAuthorized(Player p) {
        return ownerUUID == null
                || p.getUUID().equals(ownerUUID)
                || p.getUUID().equals(parentAUUID)
                || p.getUUID().equals(parentBUUID);
    }

    public boolean isTruePlayerFamilyAdult(){return true;}

    public void tickGrowthState() {
        if (hasOwnBed() && level().dimension().location().toString().equals(ownBedDimension)) {
            if (!level().getBlockState(ownBedPos).is(net.minecraft.tags.BlockTags.BEDS)) ownBedPos = null;
        }
    }

    public void tryClaimAndSleep() {
        if (!(level() instanceof ServerLevel level)) return;
        if (isSleeping()) return;

        // Always canonicalize a bed to its HEAD half before navigating or
        // calling startSleeping(). Vanilla's sleeping position is based on the
        // head half; using the foot half can leave the NPC offset from the bed.
        if (ownBedPos != null
                && level().dimension().location().toString().equals(ownBedDimension)
                && level().getBlockState(ownBedPos).is(net.minecraft.tags.BlockTags.BEDS)) {
            BlockPos headBed = headBedPos(level, ownBedPos);
            if (headBed != null && isBedAllowedForChild(level, headBed)) {
                moveToAndSleep(level, headBed);
                return;
            }
        }

        BlockPos replacement = findAvailableFamilyBed(level);
        if (replacement != null) {
            setOwnBed(replacement);
            moveToAndSleep(level, replacement);
        }
    }

    private BlockPos headBedPos(ServerLevel level, BlockPos bed) {
        var state = level.getBlockState(bed);
        if (!state.is(net.minecraft.tags.BlockTags.BEDS)) return null;
        if (state.getValue(net.minecraft.world.level.block.BedBlock.PART)
                == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
            return bed.immutable();
        }
        net.minecraft.core.Direction facing = state.getValue(net.minecraft.world.level.block.BedBlock.FACING);
        BlockPos head = bed.relative(facing);
        var headState = level.getBlockState(head);
        if (!headState.is(net.minecraft.tags.BlockTags.BEDS)) return null;
        if (headState.getValue(net.minecraft.world.level.block.BedBlock.PART)
                != net.minecraft.world.level.block.state.properties.BedPart.HEAD) return null;
        return head.immutable();
    }

    private void moveToAndSleep(ServerLevel level, BlockPos bed) {
        BlockPos headBed = headBedPos(level, bed);
        if (headBed == null || !isBedAllowedForChild(level, headBed)) return;

        // Approach the HEAD half, not the foot half. This makes the autonomous
        // interaction equivalent to using the correct/top side of the bed.
        double targetX = headBed.getX() + 0.5D;
        double targetY = headBed.getY() + 0.5D;
        double targetZ = headBed.getZ() + 0.5D;
        if (distanceToSqr(targetX, targetY, targetZ) > 3.0D) {
            getNavigation().moveTo(targetX, targetY, targetZ, .85D);
            return;
        }

        // Re-check immediately before claiming: another entity may have taken
        // the bed since the previous tick.
        if (!isBedAllowedForChild(level, headBed)) return;

        swingInteraction();
        setOwnBed(headBed);
        startSleeping(headBed);
        chatNearby("I found my bed. I will sleep here and use it as my respawn point.", 18.0D);
    }

    private BlockPos findAvailableFamilyBed(ServerLevel level) {
        BlockPos base = hasHome() ? homePos : blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-12,-2,-12), base.offset(12,3,12))) {
            if (!level.getBlockState(p).is(net.minecraft.tags.BlockTags.BEDS)) continue;
            BlockPos headBed = headBedPos(level, p);
            if (headBed == null || !isBedAllowedForChild(level, headBed)) continue;
            double d = distanceToSqr(headBed.getX()+.5D, headBed.getY()+.5D, headBed.getZ()+.5D);
            if (d < bestDistance) {
                bestDistance = d;
                best = headBed;
            }
        }
        return best;
    }

    private boolean isBedAllowedForChild(ServerLevel level, BlockPos bed) {
        var state = level.getBlockState(bed);
        if (!state.is(net.minecraft.tags.BlockTags.BEDS)) return false;

        // Never share an occupied vanilla bed. This covers Players as well as
        // any other entity that has already put the bed into its occupied state.
        if (state.hasProperty(net.minecraft.world.level.block.BedBlock.OCCUPIED)
                && state.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED)) {
            return false;
        }

        // Never claim a bed that another living entity is currently sleeping on.
        var bedBox = new net.minecraft.world.phys.AABB(bed).inflate(0.25D, 0.75D, 0.25D);
        for (LivingEntity sleeper : level.getEntitiesOfClass(
                LivingEntity.class,
                bedBox,
                x -> x != this && x.isAlive() && x.isSleeping()
        )) {
            if (sleeper.getSleepingPos().isPresent() && sleeper.getSleepingPos().get().equals(bed)) {
                return false;
            }
        }

        // A GrowthChild's remembered bed is also a reservation, even if the
        // child is temporarily standing elsewhere.
        for (GrowthChildPlayerMobEntity child : level.getEntitiesOfClass(
                GrowthChildPlayerMobEntity.class,
                new net.minecraft.world.phys.AABB(bed).inflate(0.5D),
                x -> x != this && x.isAlive() && x.hasOwnBed()
        )) {
            if (child.getOwnBed().equals(bed)) return false;
        }

        // Keep the existing family/owner reservation rules.
        if (isFamilyPlayerUsingBed(level, parentAUUID, bed)) return false;
        if (isFamilyPlayerUsingBed(level, parentBUUID, bed)) return false;
        if (isFamilyPlayerUsingBed(level, ownerUUID, bed)) return false;
        return true;
    }

    private boolean isFamilyPlayerUsingBed(ServerLevel level, UUID uuid, BlockPos bed) {
        if (uuid == null) return false;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
        if (player == null) return false;
        Optional<BlockPos> sleeping = player.getSleepingPos();
        if (sleeping.isPresent() && sleeping.get().equals(bed)) return true;
        return player.getRespawnPosition() != null
                && player.getRespawnPosition().equals(bed)
                && player.getRespawnDimension() == level.dimension();
    }

    public void depositNearbyItems() {
        SimpleContainer inv=getTraitInventory();
        BlockPos base=hasHome()?homePos:blockPosition();
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-3, -1, -3),base.offset(3,2,3))) {
            BlockEntity be=level().getBlockEntity(p);
            if(!(be instanceof Container c)) continue;
            for(int i=0;i<inv.getContainerSize();i++){
                ItemStack src=inv.getItem(i); if(src.isEmpty())continue;
                for(int j=0;j<c.getContainerSize()&&!src.isEmpty();j++){
                    ItemStack dst=c.getItem(j);
                    if(dst.isEmpty()){c.setItem(j,src.copy());src=ItemStack.EMPTY;}
                    else if(ItemStack.isSameItemSameTags(dst,src)&&dst.getCount()<dst.getMaxStackSize()){
                        int n=Math.min(src.getCount(),dst.getMaxStackSize()-dst.getCount());dst.grow(n);src.shrink(n);
                    }
                }
                inv.setItem(i,src);
            }
            c.setChanged();
        }
    }

    public void tickOpenedChestVisual() {
        int ticks = getPersistentData().getInt("GrowthChildChestOpenTicks");
        if (ticks <= 0) return;
        ticks--;
        getPersistentData().putInt("GrowthChildChestOpenTicks", ticks);
        if (ticks == 0 && getPersistentData().contains("GrowthChildChestOpenX")) {
            BlockPos p = new BlockPos(getPersistentData().getInt("GrowthChildChestOpenX"), getPersistentData().getInt("GrowthChildChestOpenY"), getPersistentData().getInt("GrowthChildChestOpenZ"));
            if (level() instanceof ServerLevel level) {
                var state=level.getBlockState(p);
                if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) level.blockEvent(p,state.getBlock(),1,0);
            }
            getPersistentData().remove("GrowthChildChestOpenX");
            getPersistentData().remove("GrowthChildChestOpenY");
            getPersistentData().remove("GrowthChildChestOpenZ");
        }
    }

    /**
     * Player-like storage interaction: approach the container, face it, swing
     * once to use it, keep the chest visibly open for a short interaction
     * window, then transfer one stack at a time. This is intentionally not an
     * instant "dump inventory" operation.
     */
    public void openAndDepositNearbyContainers() {
        if (!(level() instanceof ServerLevel level)) return;
        SimpleContainer inv = getTraitInventory();
        BlockPos target = readStorageTarget();
        if (target == null || !(level.getBlockEntity(target) instanceof Container)) {
            target = findNearestStorage(level);
            if (target == null) return;
            writeStorageTarget(target);
        }

        double d = distanceToSqr(target.getX() + .5D, target.getY(), target.getZ() + .5D);
        if (d > 3.0D) {
            setCurrentTask("walking to storage");
            getNavigation().moveTo(target.getX() + .5D, target.getY(), target.getZ() + .5D, 1.0D);
            return;
        }

        getLookControl().setLookAt(target.getX() + .5D, target.getY() + .5D, target.getZ() + .5D, 30.0F, 30.0F);
        var state = level.getBlockState(target);
        int ticks = getPersistentData().getInt(STORAGE_TICKS);

        if (ticks <= 0) {
            if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
                level.blockEvent(target, state.getBlock(), 1, 1);
            }
            swingInteraction();
            setCurrentTask("opening storage");
            getPersistentData().putInt(STORAGE_TICKS, 8);
            return;
        }

        ticks--;
        getPersistentData().putInt(STORAGE_TICKS, ticks);
        if (ticks > 0) {
            setCurrentTask("storing supplies");
            return;
        }

        boolean moved = depositOneStackIntoContainer(level, target, inv);
        if (moved) {
            setCurrentTask("stored supplies");
            sayFamily("I put some supplies into the storage chest.", 20.0D);
            getPersistentData().putInt(STORAGE_TICKS, 4);
            return;
        }

        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
            level.blockEvent(target, state.getBlock(), 1, 0);
        }
        clearStorageTarget();
        setCurrentTask("finished storing supplies");
    }

    private BlockPos findNearestStorage(ServerLevel level) {
        BlockPos base = hasHome() ? homePos : blockPosition();
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-5, -1, -5), base.offset(5, 2, 5))) {
            if (!(level.getBlockEntity(p) instanceof Container)) continue;
            double d = distanceToSqr(p.getX() + .5D, p.getY(), p.getZ() + .5D);
            if (d < best) { best = d; nearest = p.immutable(); }
        }
        return nearest;
    }

    private boolean depositOneStackIntoContainer(ServerLevel level, BlockPos pos, SimpleContainer inv) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container c)) return false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack src = inv.getItem(i);
            if (src.isEmpty()) continue;
            if (src.getItem() instanceof ArmorItem || src.getItem() instanceof ElytraItem
                    || src.getItem() == Items.SHIELD || src.getItem() == Items.TOTEM_OF_UNDYING) continue;
            ItemStack moving = src.copy();
            ItemStack remaining = moving.copy();
            for (int j = 0; j < c.getContainerSize() && !remaining.isEmpty(); j++) {
                ItemStack dst = c.getItem(j);
                if (dst.isEmpty()) {
                    int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                    ItemStack placed = remaining.copyWithCount(moved);
                    c.setItem(j, placed);
                    remaining.shrink(moved);
                } else if (ItemStack.isSameItemSameTags(dst, remaining) && dst.getCount() < dst.getMaxStackSize()) {
                    int moved = Math.min(remaining.getCount(), dst.getMaxStackSize() - dst.getCount());
                    dst.grow(moved);
                    remaining.shrink(moved);
                }
            }
            if (remaining.getCount() < moving.getCount()) {
                inv.setItem(i, remaining);
                c.setChanged();
                return true;
            }
        }
        return false;
    }

    private BlockPos readStorageTarget() {
        if (!getPersistentData().contains(STORAGE_X) || !getPersistentData().contains(STORAGE_Y) || !getPersistentData().contains(STORAGE_Z)) return null;
        return new BlockPos(getPersistentData().getInt(STORAGE_X), getPersistentData().getInt(STORAGE_Y), getPersistentData().getInt(STORAGE_Z));
    }

    private void writeStorageTarget(BlockPos pos) {
        getPersistentData().putInt(STORAGE_X, pos.getX());
        getPersistentData().putInt(STORAGE_Y, pos.getY());
        getPersistentData().putInt(STORAGE_Z, pos.getZ());
    }

    private void clearStorageTarget() {
        getPersistentData().remove(STORAGE_X);
        getPersistentData().remove(STORAGE_Y);
        getPersistentData().remove(STORAGE_Z);
        getPersistentData().remove(STORAGE_TICKS);
    }

    public void setGuardFromAuthorizedPlayer(Player player) {
        if (!isAuthorized(player)) return;
        toggleGuard(blockPosition());
        if (player instanceof ServerPlayer sp) {
            chatTo(sp, guarding ? "yes I will hold on." : "I will return to my daily routine.");
        }
    }

    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);
        // Growth Child does not persist the experimental Normal Adult brain model.
        tag.remove("Expression"); tag.remove("BrainState"); tag.remove("BrainAction");
        tag.remove("BrainActionTicks"); tag.remove("BrainMineTarget"); tag.remove("BrainMineProgress");
        tag.remove("HunterTarget"); tag.remove("ChatTopic"); tag.remove("ChatMood"); tag.remove("ChatLastPlayer"); tag.remove("ChatAffinity"); tag.remove("ChatTurn");
        tag.putBoolean(FAMILY_TAG,true);
        if(parentAUUID!=null)tag.putUUID(PARENT_A_UUID_TAG,parentAUUID); tag.putString(PARENT_A_NAME_TAG,parentAName);
        if(parentBUUID!=null)tag.putUUID(PARENT_B_UUID_TAG,parentBUUID); tag.putString(PARENT_B_NAME_TAG,parentBName);
        if(ownerUUID!=null)tag.putUUID(OWNER_UUID_TAG,ownerUUID); tag.putString(OWNER_NAME_TAG,ownerName);
        tag.putInt("FamilyChildNumber", familyChildNumber);
        tag.putBoolean(HOME_SET_TAG,homeSet); putPos(tag,HOME_X_TAG,HOME_Y_TAG,HOME_Z_TAG,homePos);tag.putString(HOME_DIM_TAG,homeDimension);
        tag.putBoolean(FOLLOW_TAG,following);if(followTargetUUID!=null)tag.putUUID(FOLLOW_UUID_TAG,followTargetUUID);
        tag.putBoolean(GUARD_TAG,guarding);putPos(tag,GUARD_X_TAG,GUARD_Y_TAG,GUARD_Z_TAG,guardAnchor);
        putPos(tag,BIRTH_X_TAG,BIRTH_Y_TAG,BIRTH_Z_TAG,birthPos);tag.putString(BIRTH_DIM_TAG,birthDimension);
        tag.putBoolean(BED_SET_TAG,ownBedPos!=null);putPos(tag,BED_X_TAG,BED_Y_TAG,BED_Z_TAG,ownBedPos);tag.putString(BED_DIM_TAG,ownBedDimension);
        tag.putFloat("GrowthFoodLevel",foodLevel); tag.putFloat("GrowthFoodSaturation",foodSaturation);
        if(getHoldPosition()!=null){
            var hp=getHoldPosition();
            tag.putDouble(HOLD_X_TAG,hp.x); tag.putDouble(HOLD_Y_TAG,hp.y); tag.putDouble(HOLD_Z_TAG,hp.z);
        }
        tag.putString("GrowthAIIntent",currentIntent.name());
        tag.putBoolean(CHUNK_ENABLED_TAG,chunkLoadEnabled);tag.putInt(CHUNK_RADIUS_TAG,chunkLoadRadius);tag.putBoolean(STEALTH_TAG,stealthThreat);tag.putString(DAILY_TAG,dailyState.name());tag.putString(TASK_TAG,currentTask);tag.putString("GrowthCombatState",combatState.name());
        aiStats.save(tag);
        if(aiCommand!=null){ CompoundTag c=new CompoundTag(); c.putUUID("Sender",aiCommand.sender()); c.putString("Channel",aiCommand.channel().name()); c.putString("Type",aiCommand.type().name()); c.putString("Raw",aiCommand.rawText()); c.putString("Normalized",aiCommand.normalizedText()); tag.put("GrowthAICommand",c); }
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);
        setTrait(PlayerMobTrait.DEFAULT); setExpression(Expression.NORMAL); setBrainState(BrainState.IDLE); setBrainAction(BrainAction.NONE); clearBrainMineSession(); setHunterTargetName("");
        parentAUUID=tag.hasUUID(PARENT_A_UUID_TAG)?tag.getUUID(PARENT_A_UUID_TAG):null;parentAName=tag.getString(PARENT_A_NAME_TAG);parentBUUID=tag.hasUUID(PARENT_B_UUID_TAG)?tag.getUUID(PARENT_B_UUID_TAG):null;parentBName=tag.getString(PARENT_B_NAME_TAG);ownerUUID=tag.hasUUID(OWNER_UUID_TAG)?tag.getUUID(OWNER_UUID_TAG):null;ownerName=tag.getString(OWNER_NAME_TAG); familyChildNumber=tag.contains("FamilyChildNumber")?Math.max(0,tag.getInt("FamilyChildNumber")):0;
        foodLevel=tag.contains("GrowthFoodLevel")?tag.getFloat("GrowthFoodLevel"):20.0F; try{currentIntent=AIIntent.valueOf(tag.getString("GrowthAIIntent"));}catch(Exception ignored){currentIntent=AIIntent.IDLE;} foodSaturation=tag.contains("GrowthFoodSaturation")?tag.getFloat("GrowthFoodSaturation"):5.0F;
        homeSet=tag.getBoolean(HOME_SET_TAG);homePos=readPos(tag,HOME_X_TAG,HOME_Y_TAG,HOME_Z_TAG);homeDimension=tag.getString(HOME_DIM_TAG);
        if(tag.contains(HOLD_X_TAG)) getPersistentData().putDouble(HOLD_X_TAG,tag.getDouble(HOLD_X_TAG));
        if(tag.contains(HOLD_Y_TAG)) getPersistentData().putDouble(HOLD_Y_TAG,tag.getDouble(HOLD_Y_TAG));
        if(tag.contains(HOLD_Z_TAG)) getPersistentData().putDouble(HOLD_Z_TAG,tag.getDouble(HOLD_Z_TAG));
        following=tag.getBoolean(FOLLOW_TAG);followTargetUUID=tag.hasUUID(FOLLOW_UUID_TAG)?tag.getUUID(FOLLOW_UUID_TAG):null;guarding=tag.getBoolean(GUARD_TAG);guardAnchor=readPos(tag,GUARD_X_TAG,GUARD_Y_TAG,GUARD_Z_TAG);birthPos=readPos(tag,BIRTH_X_TAG,BIRTH_Y_TAG,BIRTH_Z_TAG);birthDimension=tag.getString(BIRTH_DIM_TAG);ownBedPos=readPos(tag,BED_X_TAG,BED_Y_TAG,BED_Z_TAG);ownBedDimension=tag.getString(BED_DIM_TAG);chunkLoadEnabled=!tag.contains(CHUNK_ENABLED_TAG)||tag.getBoolean(CHUNK_ENABLED_TAG);chunkLoadRadius=tag.contains(CHUNK_RADIUS_TAG)?tag.getInt(CHUNK_RADIUS_TAG):2;stealthThreat=tag.getBoolean(STEALTH_TAG);try{dailyState=DailyState.valueOf(tag.getString(DAILY_TAG));}catch(Exception ignored){dailyState=DailyState.MORNING;}currentTask=tag.getString(TASK_TAG);if(currentTask.isEmpty())currentTask="idle";try{combatState=CombatState.valueOf(tag.getString("GrowthCombatState"));}catch(Exception ignored){combatState=CombatState.IDLE;}
        aiStats.load(tag);
        aiCommand=null; if(tag.contains("GrowthAICommand",10)){CompoundTag c=tag.getCompound("GrowthAICommand"); try{aiCommand=new GrowthChildCommand(c.getUUID("Sender"),GrowthChildCommand.Channel.valueOf(c.getString("Channel")),GrowthChildCommand.Type.valueOf(c.getString("Type")),c.getString("Raw"),c.getString("Normalized"));}catch(Exception ignored){}}
    }
    private static void putPos(CompoundTag t,String x,String y,String z,BlockPos p){if(p!=null){t.putInt(x,p.getX());t.putInt(y,p.getY());t.putInt(z,p.getZ());}}
    private static BlockPos readPos(CompoundTag t,String x,String y,String z){return t.contains(x)&&t.contains(y)&&t.contains(z)?new BlockPos(t.getInt(x),t.getInt(y),t.getInt(z)):null;}
}
