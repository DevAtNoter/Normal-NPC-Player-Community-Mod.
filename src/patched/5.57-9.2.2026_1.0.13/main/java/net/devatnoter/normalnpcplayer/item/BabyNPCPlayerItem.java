package net.devatnoter.normalnpcplayer.item;

import net.devatnoter.normalnpcplayer.client.renderer.BabyNPCPlayerItemRenderer;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.registry.ModItems;
import net.devatnoter.normalnpcplayer.network.BabyItemClientState;
import net.devatnoter.normalnpcplayer.network.BabyItemSyncPacket;
import net.devatnoter.normalnpcplayer.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public final class BabyNPCPlayerItem extends Item {

    public static final String BABY_DATA_TAG = "BabyEntityData";
    public static final String CARRIER_VERSION_TAG = "BabyCarrierVersion";
    public static final String CARRIER_TEXTURE_INDEX_TAG = "BabyTextureIndex";
    public static final String CARRIER_SKIN_ID_TAG = "BabySkinId";
    public static final String CARRIER_TEXTURE_REF_TAG = "BabyTextureRef";
    public static final String BABY_VARIANT_TAG = "BabyVariant";
    public static final String BABY_ENTITY_ID =
            "normalnpcplayer:baby_npc_player";

    // Item-side simulation state. The Baby Item is stack-size 1, so each
    // ItemStack represents exactly one Baby and can safely carry live state.
    private static final String ITEM_TICK_TAG = "BabyItemSimulationTicks";
    private static final String ITEM_HOLDER_TAG = "BabyItemHolder";
    private static final String ITEM_ANIMATION_TAG = "BabyItemAnimation";

    /*
     * Live Baby simulation is server-authoritative and intentionally does NOT
     * live-update the ItemStack NBT while the Baby is being carried.
     *
     * If the ItemStack itself is rewritten every tick, vanilla container sync
     * sees a different stack and may rebuild first-person item state. That is
     * exactly the reload/freeze class of bug this item must avoid.
     *
     * The runtime state below owns the moving survival values while carried.
     * It is merged back into the serialized Baby only when the Baby leaves the
     * Item state (placement, death conversion, etc.). A separate S2C packet
     * feeds the client display state.
     */
    private static final Map<UUID, RuntimeState> RUNTIME_STATES = new HashMap<>();

    private static final class RuntimeState {
        private final CompoundTag data;
        private long simulationTicks;
        private float lastSentHealth = Float.NaN;
        private float lastSentHunger = Float.NaN;
        private float lastSentSaturation = Float.NaN;
        private float lastSentExhaustion = Float.NaN;
        private int lastSentAir = Integer.MIN_VALUE;
        private float lastSentAbsorption = Float.NaN;
        private int lastSentXpTotal = Integer.MIN_VALUE;
        private int lastSentXpLevel = Integer.MIN_VALUE;
        private String lastSentEffects = "";
        private long lastSentTick = Long.MIN_VALUE;

        private RuntimeState(CompoundTag data) {
            this.data = data;
            mergeDuplicateActiveEffects(this.data);
            this.simulationTicks = data.contains(ITEM_TICK_TAG)
                    ? data.getLong(ITEM_TICK_TAG)
                    : 0L;
        }
    }

    public BabyNPCPlayerItem(Properties properties) {
        super(properties.stacksTo(1));
    }


    /**
     * Baby Item gameplay NBT is intentionally live while carried. Updating
     * HP/Air/Hunger/etc. must never restart vanilla hand equip progress in
     * first-person view. Only an actual slot change should produce the normal
     * re-equip animation.
     */
    @Override
    public boolean shouldCauseReequipAnimation(
            ItemStack oldStack,
            ItemStack newStack,
            boolean slotChanged
    ) {
        return slotChanged;
    }

    public static boolean isBabyStack(ItemStack stack) {
        return !stack.isEmpty()
                && stack.is(ModItems.BABY_NPC_PLAYER_ITEM.get())
                && stack.hasTag()
                && stack.getTag().contains(BABY_DATA_TAG, 10);
    }

    /**
     * Serialize the actual Baby entity. No texture is chosen here.
     * The entity's TextureIndex is the sole source of appearance identity.
     */
    public static ItemStack createFromEntity(
            BabyNPCPlayerEntity baby
    ) {
        ItemStack stack = new ItemStack(
                ModItems.BABY_NPC_PLAYER_ITEM.get(),
                1
        );

        CompoundTag entityData = new CompoundTag();
        baby.saveWithoutId(entityData);

        entityData.remove("Pos");
        entityData.remove("Motion");
        entityData.remove("Rotation");
        entityData.remove("FallDistance");

        entityData.putString(
                "id",
                BABY_ENTITY_ID
        );

        int textureIndex =
                baby.getTextureIndex();

        String textureRef =
                "normalnpcplayer:textures/entity/baby/baby"
                        + textureIndex
                        + ".png";

        CompoundTag carrier = new CompoundTag();

        carrier.putInt(
                CARRIER_VERSION_TAG,
                5
        );

        /*
         * Exact appearance SOT captured at pickup.
         */
        carrier.putInt(
                BABY_VARIANT_TAG,
                textureIndex
        );
        carrier.putInt(
                CARRIER_TEXTURE_INDEX_TAG,
                textureIndex
        );
        carrier.putString(
                CARRIER_SKIN_ID_TAG,
                baby.getTextureId()
        );
        carrier.putString(
                CARRIER_TEXTURE_REF_TAG,
                textureRef
        );

        carrier.put(
                BABY_DATA_TAG,
                entityData
        );

        carrier.putInt("BabyHealth", Math.round(baby.getHealth() * 10.0F));
        carrier.putInt("BabyHunger", baby.getFoodLevel());
        carrier.putFloat("BabySaturation", baby.getSaturationLevel());
        carrier.putInt("BabyAir", baby.getAirSupply());
        carrier.putString(ITEM_ANIMATION_TAG, "ITEM_IDLE");
        carrier.putLong(ITEM_TICK_TAG, 0L);

        stack.setTag(carrier);
        stack.setCount(1);

        // A newly created carrier is a fresh serialized snapshot. Never reuse
        // an old in-memory simulation state for the same Baby UUID.
        forgetRuntimeState(baby.getUUID());

        return stack;
    }

    public static CompoundTag copyEntityData(ItemStack stack) {
        if (!isBabyStack(stack)) {
            return new CompoundTag();
        }

        CompoundTag root = stack.getTag();
        CompoundTag base = root.getCompound(BABY_DATA_TAG).copy();

        // Server-side callers such as placement/death conversion must see the
        // latest carried simulation, even though the client ItemStack is never
        // rewritten every tick. Client callers intentionally use the stack
        // snapshot and receive live display values through BabyItemClientState.
        if (!root.contains(BABY_DATA_TAG, 10)) {
            return base;
        }

        CompoundTag source = root.getCompound(BABY_DATA_TAG);
        if (source.hasUUID("UUID")) {
            RuntimeState runtime = RUNTIME_STATES.get(source.getUUID("UUID"));
            if (runtime != null) {
                return runtime.data.copy();
            }
        }

        return base;
    }

    private static RuntimeState runtimeStateFor(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(BABY_DATA_TAG, 10)) {
            return null;
        }

        CompoundTag source = root.getCompound(BABY_DATA_TAG);
        if (!source.hasUUID("UUID")) {
            return null;
        }

        UUID uuid = source.getUUID("UUID");
        return RUNTIME_STATES.computeIfAbsent(uuid, ignored ->
                new RuntimeState(source.copy())
        );
    }

    private static void forgetRuntimeState(UUID uuid) {
        if (uuid != null) {
            RUNTIME_STATES.remove(uuid);
        }
    }

    public static void forgetRuntimeState(ItemStack stack) {
        if (!isBabyStack(stack)) return;
        CompoundTag data = stack.getTag().getCompound(BABY_DATA_TAG);
        if (data.hasUUID("UUID")) {
            forgetRuntimeState(data.getUUID("UUID"));
        }
    }

    /**
     * Canonical skin identity stored inside the Baby EntityData.
     */
    /**
     * The original Baby's TextureIndex is the only appearance identity.
     * Prefer nested EntityData, then the carrier mirror for compatibility.
     */
    /**
     * Exact appearance snapshot captured from the original Baby at pickup.
     *
     * BabyVariant is the ItemStack SOT. Entity NBT remains as a full
     * serialized backup/state snapshot.
     */
    public static int getTextureIndex(
            ItemStack stack
    ) {
        CompoundTag root =
                stack.getTag();

        if (root != null
                && root.contains(BABY_VARIANT_TAG)) {
            return clampTextureIndex(
                    root.getInt(
                            BABY_VARIANT_TAG
                    )
            );
        }

        if (root != null
                && root.contains(
                        CARRIER_TEXTURE_INDEX_TAG
                )) {
            return clampTextureIndex(
                    root.getInt(
                            CARRIER_TEXTURE_INDEX_TAG
                    )
            );
        }

        CompoundTag data =
                copyEntityData(stack);

        if (data.contains(
                BabyNPCPlayerEntity.TEXTURE_INDEX_TAG
        )) {
            return clampTextureIndex(
                    data.getInt(
                            BabyNPCPlayerEntity.TEXTURE_INDEX_TAG
                    )
            );
        }

        return BabyNPCPlayerEntity.MIN_TEXTURE_INDEX;
    }

    private static int clampTextureIndex(
            int textureIndex
    ) {
        return Math.max(
                BabyNPCPlayerEntity.MIN_TEXTURE_INDEX,
                Math.min(
                        BabyNPCPlayerEntity.MAX_TEXTURE_INDEX,
                        textureIndex
                )
        );
    }


    @Override
    public Component getName(ItemStack stack) {
        CompoundTag data = copyEntityData(stack);

        String ownerName = data.contains("OwnerName")
                ? data.getString("OwnerName")
                : "";

        if (ownerName.isEmpty()) {
            return Component.translatable(
                    "item.normalnpcplayer.baby_npc_player"
            );
        }

        return Component.translatable(
                "item.normalnpcplayer.baby_npc_player",
                ownerName
        );
    }


    @Override
    public void appendHoverText(
            ItemStack stack,
            Level level,
            java.util.List<Component> tooltip,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, level, tooltip, flag);

        // Keep the ordinary Item tooltip static. Live Baby values are rendered
        // by BabyHealthTooltipClientComponent from BabyItemClientState.
        // Nothing in the normal tooltip list is allowed to change every tick.
        CompoundTag data = copyEntityData(stack);
        String ownerName = data.contains("OwnerName")
                ? data.getString("OwnerName")
                : "";

        tooltip.add(
                Component.translatable(
                        "item.normalnpcplayer.baby_npc_player.description"
                ).withStyle(ChatFormatting.GRAY)
        );

        if (!ownerName.isEmpty()) {
            tooltip.add(
                    Component.translatable(
                            "item.normalnpcplayer.baby_npc_player.owner",
                            ownerName
                    ).withStyle(ChatFormatting.GRAY)
            );
        }
    }

    private static String formatStat(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001F) {
            return Integer.toString(Math.round(value));
        }

        return String.format(
                java.util.Locale.ROOT,
                "%.1f",
                value
        );
    }

    /**
     * The Baby Item is stack-size 1, therefore its serialized Baby state can
     * continue to evolve while it is carried. This is intentionally limited
     * to the Baby's Player-like survival state; no invisible LivingEntity is
     * spawned just to tick an ItemStack.
     *
     * While a Player carries the Baby:
     * - hunger/saturation/exhaustion respond to the holder's activity
     * - natural regeneration uses the Baby's own food state
     * - air follows the holder's water environment
     * - health damage/recovery is written back into BabyEntityData
     * - an animation state is written for the renderer
     */
    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int slot,
            boolean selected
    ) {
        super.inventoryTick(stack, level, entity, slot, selected);

        if (level.isClientSide || !isBabyStack(stack)) {
            return;
        }

        RuntimeState runtime = runtimeStateFor(stack);
        if (runtime == null) {
            return;
        }

        CompoundTag data = runtime.data;
        long ticks = ++runtime.simulationTicks;

        float health = data.contains("Health") ? data.getFloat("Health") : 20.0F;

        // Tick the serialized status effects exactly while the Baby is
        // carried. In particular, Regeneration must heal over time and the
        // Absorption shield must disappear when its effect expires.
        health = tickSerializedEffects(data, ticks, health);
        int hunger = data.contains(BabyNPCPlayerEntity.FOOD_LEVEL_TAG)
                ? clampInt((int) Math.floor(data.getFloat(BabyNPCPlayerEntity.FOOD_LEVEL_TAG)), 0, BabyNPCPlayerEntity.MAX_HUNGER)
                : BabyNPCPlayerEntity.MAX_HUNGER;
        float saturation = data.contains(BabyNPCPlayerEntity.SATURATION_LEVEL_TAG)
                ? clampFloat(data.getFloat(BabyNPCPlayerEntity.SATURATION_LEVEL_TAG), 0.0F, BabyNPCPlayerEntity.MAX_SATURATION)
                : BabyNPCPlayerEntity.MAX_SATURATION;
        float exhaustion = data.contains(BabyNPCPlayerEntity.EXHAUSTION_LEVEL_TAG)
                ? Math.max(0.0F, data.getFloat(BabyNPCPlayerEntity.EXHAUSTION_LEVEL_TAG))
                : 0.0F;
        int air = data.contains("Air")
                ? clampInt(data.getInt("Air"), 0, BabyNPCPlayerEntity.MAX_AIR)
                : BabyNPCPlayerEntity.MAX_AIR;

        Player holder = entity instanceof Player p ? p : null;
        boolean underwater = holder != null && holder.isEyeInFluid(FluidTags.WATER);
        boolean waterBreathing = hasSerializedEffect(data, "minecraft:water_breathing");

        if (underwater && !waterBreathing) {
            air = Math.max(0, air - 1);
            if (air <= 0 && ticks % 20L == 0L) {
                health -= 2.0F;
                setItemAnimation(data, "DROWNING");
            }
        } else {
            air = Math.min(BabyNPCPlayerEntity.MAX_AIR, air + (underwater ? 0 : 4));
        }

        // Holder activity is still the Baby's carried-world activity source.
        if (holder != null) {
            if (holder.getDeltaMovement().horizontalDistanceSqr() > 0.0004D) {
                exhaustion += 0.012F;
            }
            if (holder.isSprinting()) exhaustion += 0.020F;
            if (holder.isSwimming()) exhaustion += 0.020F;
        }

        while (exhaustion >= 4.0F) {
            exhaustion -= 4.0F;
            if (saturation > 0.0F) {
                saturation = Math.max(0.0F, saturation - 1.0F);
            } else if (hunger > 0) {
                hunger--;
            }
        }

        if (hunger >= 18 && health < 20.0F) {
            int interval = saturation > 0.0F ? 10 : 80;
            if (ticks % interval == 0L) {
                health = Math.min(20.0F, health + 1.0F);
                if (saturation > 0.0F) {
                    saturation = Math.max(0.0F, saturation - 3.0F);
                    exhaustion += 3.0F;
                } else {
                    exhaustion += 6.0F;
                }
                setItemAnimation(data, "RECOVERING");
            }
        }

        boolean starvationDeath = false;
        if (hunger <= 0 && ticks % 80L == 0L && health > 0.0F) {
            // Natural starvation can kill a Baby only on HARD difficulty, just
            // like the vanilla starvation rule. EASY/NORMAL retain their
            // vanilla health floors.
            if (level.getDifficulty() == net.minecraft.world.Difficulty.HARD) {
                health = Math.max(0.0F, health - 1.0F);
                starvationDeath = health <= 0.0F;
            } else if (level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
                    && (level.getDifficulty() != net.minecraft.world.Difficulty.EASY || health > 10.0F)
                    && (level.getDifficulty() != net.minecraft.world.Difficulty.NORMAL || health > 1.0F)) {
                health = Math.max(1.0F, health - 1.0F);
            }
            setItemAnimation(data, "HUNGRY");
        }

        health = clampFloat(health, 0.0F, 20.0F);
        data.putFloat("Health", health);

        // The tooltip is display-only. If the authoritative carried Baby has
        // actually reached 0 HP, biological Life Bond must kill the owner
        // immediately. Starvation keeps a dedicated Life Bond death cause so
        // the player's death message states that the Baby starved to death.
        if (holder instanceof net.minecraft.server.level.ServerPlayer serverHolder
                && net.devatnoter.normalnpcplayer.event.LifeBondEvents.enforceCarriedBabyHealth(
                        serverHolder, data, starvationDeath)) {
            syncClientState(serverHolder, runtime, data);
            return;
        }

        if (underwater && air <= 80) setItemAnimation(data, "DROWNING");
        else if (health <= 5.0F) setItemAnimation(data, "HURT");
        else if (hunger <= 3) setItemAnimation(data, "HUNGRY");
        else setItemAnimation(data, "ITEM_IDLE");

        data.putFloat("Health", health);
        data.putFloat(BabyNPCPlayerEntity.FOOD_LEVEL_TAG, hunger);
        data.putFloat(BabyNPCPlayerEntity.SATURATION_LEVEL_TAG, saturation);
        data.putFloat(BabyNPCPlayerEntity.EXHAUSTION_LEVEL_TAG, exhaustion);
        data.putInt("Hunger", hunger);
        data.putFloat("Saturation", saturation);
        data.putFloat("Exhaustion", exhaustion);
        data.putInt("Air", air);
        data.putLong(ITEM_TICK_TAG, ticks);

        syncClientState(holder instanceof net.minecraft.server.level.ServerPlayer serverPlayer ? serverPlayer : null, runtime, data);
    }

    /**
     * Adds XP to a Baby that is currently stored as an ItemStack. The ItemStack
     * itself is not rewritten every tick; the live RuntimeState remains the
     * authoritative carried state and is merged back when the Baby is placed.
     */
    public static boolean addExperienceToCarriedBaby(
            ItemStack babyStack,
            int amount,
            ServerPlayer player
    ) {
        if (amount <= 0 || player == null || !isBabyStack(babyStack)) {
            return false;
        }

        RuntimeState runtime = runtimeStateFor(babyStack);
        if (runtime == null) {
            return false;
        }

        CompoundTag data = runtime.data;
        int totalXp = data.contains(BabyNPCPlayerEntity.TOTAL_XP_TAG)
                ? Math.max(0, data.getInt(BabyNPCPlayerEntity.TOTAL_XP_TAG))
                : data.contains("XpTotal") ? Math.max(0, data.getInt("XpTotal")) : 0;
        int level = data.contains(BabyNPCPlayerEntity.EXPERIENCE_LEVEL_TAG)
                ? Math.max(0, data.getInt(BabyNPCPlayerEntity.EXPERIENCE_LEVEL_TAG))
                : data.contains("XpLevel") ? Math.max(0, data.getInt("XpLevel")) : 0;

        totalXp = Math.max(0, totalXp + amount);
        while (totalXp >= BabyNPCPlayerEntity.getXpAtLevel(level + 1)) {
            level++;
        }

        data.putInt(BabyNPCPlayerEntity.TOTAL_XP_TAG, totalXp);
        data.putInt(BabyNPCPlayerEntity.EXPERIENCE_LEVEL_TAG, level);
        data.putInt("XpTotal", totalXp);
        data.putInt("XpLevel", level);

        // XP pickup is an authoritative state change, so immediately refresh
        // the carried tooltip without touching the ItemStack NBT.
        runtime.lastSentXpTotal = Integer.MIN_VALUE;
        runtime.lastSentXpLevel = Integer.MIN_VALUE;
        runtime.lastSentTick = Long.MIN_VALUE;
        syncClientState(player, runtime, data);

        player.level().playSound(
                null,
                player.blockPosition(),
                net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                net.minecraft.sounds.SoundSource.PLAYERS,
                0.10F,
                0.90F + player.getRandom().nextFloat() * 0.20F
        );
        return true;
    }

    /** Finds the first carried Baby Item in the player's inventory. */
    public static ItemStack findCarriedBaby(Player player) {
        if (player == null) return ItemStack.EMPTY;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isBabyStack(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void syncClientState(
            net.minecraft.server.level.ServerPlayer player,
            RuntimeState runtime,
            CompoundTag data
    ) {
        if (player == null || !data.hasUUID("UUID")) return;

        float health = data.contains("Health") ? data.getFloat("Health") : 20.0F;
        float hunger = data.contains(BabyNPCPlayerEntity.FOOD_LEVEL_TAG) ? data.getFloat(BabyNPCPlayerEntity.FOOD_LEVEL_TAG) : 20.0F;
        float saturation = data.contains(BabyNPCPlayerEntity.SATURATION_LEVEL_TAG) ? data.getFloat(BabyNPCPlayerEntity.SATURATION_LEVEL_TAG) : 20.0F;
        float exhaustion = data.contains(BabyNPCPlayerEntity.EXHAUSTION_LEVEL_TAG) ? data.getFloat(BabyNPCPlayerEntity.EXHAUSTION_LEVEL_TAG) : 0.0F;
        int air = data.contains("Air") ? data.getInt("Air") : BabyNPCPlayerEntity.MAX_AIR;
        float absorption = data.contains("AbsorptionAmount") ? data.getFloat("AbsorptionAmount") : 0.0F;
        int totalXp = data.contains(BabyNPCPlayerEntity.TOTAL_XP_TAG) ? data.getInt(BabyNPCPlayerEntity.TOTAL_XP_TAG) : data.contains("XpTotal") ? data.getInt("XpTotal") : 0;
        int level = data.contains(BabyNPCPlayerEntity.EXPERIENCE_LEVEL_TAG) ? data.getInt(BabyNPCPlayerEntity.EXPERIENCE_LEVEL_TAG) : data.contains("XpLevel") ? data.getInt("XpLevel") : 0;
        net.minecraft.nbt.ListTag effects = data.contains("ActiveEffects", 9)
                ? (net.minecraft.nbt.ListTag) data.getList("ActiveEffects", 10).copy()
                : new net.minecraft.nbt.ListTag();
        String effectsSignature = effects.toString();

        boolean changed = Float.compare(runtime.lastSentHealth, health) != 0
                || Float.compare(runtime.lastSentHunger, hunger) != 0
                || Float.compare(runtime.lastSentSaturation, saturation) != 0
                || Float.compare(runtime.lastSentExhaustion, exhaustion) != 0
                || runtime.lastSentAir != air
                || Float.compare(runtime.lastSentAbsorption, absorption) != 0
                || runtime.lastSentXpTotal != totalXp
                || runtime.lastSentXpLevel != level
                || !runtime.lastSentEffects.equals(effectsSignature)
                || runtime.lastSentTick == Long.MIN_VALUE;

        if (!changed) return;

        runtime.lastSentHealth = health;
        runtime.lastSentHunger = hunger;
        runtime.lastSentSaturation = saturation;
        runtime.lastSentExhaustion = exhaustion;
        runtime.lastSentAir = air;
        runtime.lastSentAbsorption = absorption;
        runtime.lastSentXpTotal = totalXp;
        runtime.lastSentXpLevel = level;
        runtime.lastSentEffects = effectsSignature;
        runtime.lastSentTick = runtime.simulationTicks;

        ModNetwork.sendBabyItemState(
                player,
                new BabyItemSyncPacket(
                        data.getUUID("UUID"),
                        health,
                        20.0F,
                        hunger,
                        saturation,
                        exhaustion,
                        air,
                        absorption,
                        totalXp,
                        level,
                        effects
                )
        );
    }

    /**
     * Keep the carried Baby's ActiveEffects list in the same one-instance-per-effect
     * shape as LivingEntity. Re-feeding Golden Milk / Enchanted Golden Milk must
     * update the existing effect instead of appending a second row.
     *
     * MobEffectInstance.update() follows vanilla semantics: a stronger amplifier
     * replaces the weaker one, while an equal amplifier keeps the longer duration.
     */
    private static void mergeActiveEffect(
            net.minecraft.nbt.ListTag effects,
            net.minecraft.world.effect.MobEffectInstance incoming
    ) {
        if (incoming == null || incoming.getEffect() == null) return;

        for (int i = 0; i < effects.size(); i++) {
            CompoundTag existingTag = effects.getCompound(i);
            net.minecraft.world.effect.MobEffectInstance existing =
                    net.minecraft.world.effect.MobEffectInstance.load(existingTag);
            if (existing == null || existing.getEffect() != incoming.getEffect()) continue;

            existing.update(new net.minecraft.world.effect.MobEffectInstance(incoming));
            CompoundTag mergedTag = new CompoundTag();
            existing.save(mergedTag);
            effects.set(i, mergedTag);
            return;
        }

        CompoundTag effectTag = new CompoundTag();
        incoming.save(effectTag);
        effects.add(effectTag);
    }

    /**
     * Normalize older carried Baby data that may already contain duplicate
     * effect entries from the previous append-only implementation.
     */
    private static void mergeDuplicateActiveEffects(CompoundTag data) {
        if (!data.contains("ActiveEffects", 9)) return;

        net.minecraft.nbt.ListTag source = data.getList("ActiveEffects", 10);
        net.minecraft.nbt.ListTag merged = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < source.size(); i++) {
            net.minecraft.world.effect.MobEffectInstance effect =
                    net.minecraft.world.effect.MobEffectInstance.load(source.getCompound(i));
            if (effect != null) {
                mergeActiveEffect(merged, effect);
            }
        }
        data.put("ActiveEffects", merged);
    }

    private static float tickSerializedEffects(CompoundTag data, long ticks, float health) {
        if (!data.contains("ActiveEffects", 9)) {
            return health;
        }

        net.minecraft.nbt.ListTag effects = data.getList("ActiveEffects", 10);
        for (int i = effects.size() - 1; i >= 0; i--) {
            CompoundTag effectTag = effects.getCompound(i);
            net.minecraft.world.effect.MobEffectInstance effect =
                    net.minecraft.world.effect.MobEffectInstance.load(effectTag);

            int duration = effectTag.contains("Duration")
                    ? effectTag.getInt("Duration")
                    : 0;

            if (effect != null && duration > 0) {
                int amplifier = Math.max(0, effect.getAmplifier());

                if (effect.getEffect() == net.minecraft.world.effect.MobEffects.REGENERATION) {
                    int interval = Math.max(1, 50 >> Math.min(amplifier, 5));
                    if (ticks % interval == 0L && health < 20.0F) {
                        health = Math.min(20.0F, health + 1.0F);
                    }
                } else if (effect.getEffect() == net.minecraft.world.effect.MobEffects.POISON) {
                    // Vanilla Poison damages once every (25 >> amplifier) ticks.
                    // Poison cannot kill a LivingEntity; preserve the same 1 HP floor
                    // for the carried Baby while still applying the full effect timing.
                    int interval = Math.max(1, 25 >> Math.min(amplifier, 5));
                    if (ticks % interval == 0L && health > 1.0F) {
                        health = Math.max(1.0F, health - 1.0F);
                        setItemAnimation(data, "POISONED");
                    }
                } else if (effect.getEffect() == net.minecraft.world.effect.MobEffects.WITHER) {
                    // Vanilla Wither damages once every (40 >> amplifier) ticks.
                    // Unlike Poison, Wither is allowed to kill the carried Baby.
                    int interval = Math.max(1, 40 >> Math.min(amplifier, 5));
                    if (ticks % interval == 0L && health > 0.0F) {
                        health = Math.max(0.0F, health - 1.0F);
                        setItemAnimation(data, "WITHERED");
                    }
                }
            }

            duration--;
            if (duration <= 0) {
                if (effect != null
                        && effect.getEffect() == net.minecraft.world.effect.MobEffects.ABSORPTION) {
                    // Vanilla 1.20.1 absorption is tied to the active effect;
                    // when it ends, the yellow absorption hearts disappear.
                    data.putFloat("AbsorptionAmount", 0.0F);
                }
                effects.remove(i);
            } else {
                effectTag.putInt("Duration", duration);
            }
        }

        return health;
    }

    private static void setItemAnimation(
            CompoundTag root,
            String animation
    ) {
        root.putString(ITEM_ANIMATION_TAG, animation);
    }

    public static String getItemAnimation(ItemStack stack) {
        CompoundTag root = stack.getTag();

        if (root == null || !root.contains(ITEM_ANIMATION_TAG)) {
            return "ITEM_IDLE";
        }

        return root.getString(ITEM_ANIMATION_TAG);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Read vanilla LivingEntity's serialized ActiveEffects without requiring
     * an invisible temporary Baby entity.
     */
    private static boolean hasSerializedEffect(
            CompoundTag entityData,
            String effectId
    ) {
        if (!entityData.contains("ActiveEffects", 9)) {
            return false;
        }

        var effects = entityData.getList("ActiveEffects", 10);

        for (int i = 0; i < effects.size(); i++) {
            CompoundTag effect = effects.getCompound(i);

            // 1.20.1 LivingEntity effect NBT normally uses the lowercase
            // resource-location key "id". Keep the legacy/case-variant check
            // for compatibility with older snapshots.
            if (effect.contains("id", 8)
                    && effectId.equals(effect.getString("id"))) {
                return true;
            }

            if (effect.contains("Id", 8)
                    && effectId.equals(effect.getString("Id"))) {
                return true;
            }
        }

        return false;
    }


    @Override
    public InteractionResult useOn(
            UseOnContext context
    ) {
        Level level =
                context.getLevel();

        if (level.isClientSide) {
            var clientState = level.getBlockState(context.getClickedPos());
            if (clientState.getBlock() instanceof BedBlock
                    && context.isSecondaryUseActive()) {
                return InteractionResult.SUCCESS;
            }
            // A normal right-click on a bed must reach the BedBlock so the
            // Player can sleep normally while still holding the Baby Item.
            return InteractionResult.PASS;
        }

        ItemStack stack =
                context.getItemInHand();

        if (!isBabyStack(stack)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.FAIL;
        }

        Player player =
                context.getPlayer();

        if (player == null) {
            return InteractionResult.FAIL;
        }

        var clickedState =
                level.getBlockState(
                        context.getClickedPos()
                );

        /*
         * Bed interaction:
         * - Sneak + right-click bed = place this carried Baby onto the bed
         *   and make the Baby sleep.
         * - Normal right-click bed = PASS, so the Player sleeps normally while
         *   continuing to hold the Baby Item.
         */
        if (clickedState.getBlock() instanceof BedBlock) {
            if (!context.isSecondaryUseActive()) {
                return InteractionResult.PASS;
            }

            if (clickedState.hasProperty(BedBlock.OCCUPIED)
                    && clickedState.getValue(BedBlock.OCCUPIED)) {
                return InteractionResult.FAIL;
            }

            BlockPos bedPos = context.getClickedPos();

            var exactTextureIndex = getTextureIndex(stack);
            CompoundTag entityData = copyEntityData(stack);

            if (net.devatnoter.normalnpcplayer.event.LifeBondEvents.enforceCarriedBabyHealth(
                    (ServerPlayer) player, entityData)) {
                return InteractionResult.FAIL;
            }

            Entity restored = EntityType.create(
                    entityData,
                    serverLevel
            ).orElse(null);

            if (!(restored instanceof BabyNPCPlayerEntity baby)) {
                return InteractionResult.FAIL;
            }

            baby.setTextureIndex(exactTextureIndex);

            // Put the Baby at the bed before startSleeping() so the sleeping
            // pose/position belongs to the clicked bed, not the old Item use
            // location.
            baby.moveTo(
                    bedPos.getX() + 0.5D,
                    bedPos.getY() + 0.5625D,
                    bedPos.getZ() + 0.5D,
                    player.getYRot(),
                    0.0F
            );
            baby.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            baby.setNoGravity(true);
            baby.noPhysics = false;
            baby.fallDistance = 0.0F;
            baby.clearInitialRideState();

            if (!baby.putToBed(bedPos)) {
                baby.remove(Entity.RemovalReason.DISCARDED);
                return InteractionResult.FAIL;
            }

            if (!baby.isSleepingOnBed()) {
                baby.remove(Entity.RemovalReason.DISCARDED);
                return InteractionResult.FAIL;
            }

            if (!serverLevel.noCollision(baby, baby.getBoundingBox())
                    || !serverLevel.getWorldBorder().isWithinBounds(baby.getBoundingBox())) {
                baby.remove(Entity.RemovalReason.DISCARDED);
                return InteractionResult.FAIL;
            }

            serverLevel.addFreshEntity(baby);
            forgetRuntimeState(baby.getUUID());
            stack.shrink(1);
            return InteractionResult.CONSUME;
        }

        /*
         * Non-bed placement keeps the existing TOP-face rule.
         */
        if (context.getClickedFace()
                != net.minecraft.core.Direction.UP) {
            return InteractionResult.PASS;
        }

        if (isFunctionalBlock(clickedState)) {
            return InteractionResult.PASS;
        }

        /*
         * This integer is captured from the original Baby at pickup.
         * It is the exact variant to restore.
         */
        int exactTextureIndex =
                getTextureIndex(stack);

        CompoundTag entityData =
                copyEntityData(stack);

        // Placement is another authoritative boundary: a biological Baby
        // whose carried HP is already 0 cannot be used to bypass Life Bond.
        if (net.devatnoter.normalnpcplayer.event.LifeBondEvents.enforceCarriedBabyHealth(
                (ServerPlayer) player, entityData)) {
            return InteractionResult.FAIL;
        }

        /*
         * IMPORTANT:
         *
         * EntityType.create(CompoundTag, Level) reconstructs the saved
         * Entity data. We do NOT use MobSpawnType/finalizeSpawn().
         */
        Entity restored =
                EntityType.create(
                        entityData,
                        serverLevel
                ).orElse(null);

        if (!(restored
                instanceof BabyNPCPlayerEntity baby)) {
            return InteractionResult.FAIL;
        }

        /*
         * Constructor randomization is now irrelevant:
         * overwrite it with THIS Item's saved exact variant.
         */
        baby.setTextureIndex(
                exactTextureIndex
        );

        BlockPos target =
                context.getClickedPos().above();

        double babyX = target.getX() + 0.5D;
        double babyY = target.getY();
        double babyZ = target.getZ() + 0.5D;

        double dx = player.getX() - babyX;
        double dz = player.getZ() - babyZ;

        float facePlayerYaw =
                (float) (
                        Math.atan2(dz, dx)
                                * (180.0D / Math.PI)
                                - 90.0D
                );

        baby.moveTo(
                babyX,
                babyY,
                babyZ,
                facePlayerYaw,
                0.0F
        );

        baby.setYRot(facePlayerYaw);
        baby.setXRot(0.0F);
        baby.yBodyRot = facePlayerYaw;
        baby.yHeadRot = facePlayerYaw;

        /*
         * Clean physics state after being carried.
         */
        baby.setDeltaMovement(
                net.minecraft.world.phys.Vec3.ZERO
        );
        baby.setNoGravity(false);
        baby.noPhysics = false;
        baby.fallDistance = 0.0F;
        baby.clearInitialRideState();

        /*
         * Final invariant: restored Entity variant MUST equal Item variant.
         */
        if (baby.getTextureIndex()
                != exactTextureIndex) {

            baby.remove(
                    Entity.RemovalReason.DISCARDED
            );

            return InteractionResult.FAIL;
        }

        AABB box =
                baby.getBoundingBox();

        if (!serverLevel.noCollision(
                baby,
                box
        ) || !serverLevel.getWorldBorder()
                .isWithinBounds(box)) {

            baby.remove(
                    Entity.RemovalReason.DISCARDED
            );

            return InteractionResult.FAIL;
        }

        /*
         * Add only after the exact appearance and placement are final.
         */
        serverLevel.addFreshEntity(baby);
        forgetRuntimeState(baby.getUUID());

        stack.shrink(1);

        return InteractionResult.CONSUME;
    }


    /**
     * Feeds a Baby while it is stored as the carried Baby Item.
     * Only the current Owner may use this path; orphaned Babies use their
     * persisted owner rules and may be fed by the normal world path instead.
     *
     * The Baby remains an ItemStack: no temporary entity is spawned.
     */
    public static boolean feedCarriedBaby(
            ItemStack babyStack,
            ItemStack foodStack,
            Player player
    ) {
        if (player == null
                || !isBabyStack(babyStack)
                || foodStack == null
                || foodStack.isEmpty()) {
            return false;
        }

        CompoundTag root = babyStack.getTag();
        if (root == null) return false;

        RuntimeState runtime = runtimeStateFor(babyStack);
        if (runtime == null) return false;
        CompoundTag data = runtime.data;

        // A carried Baby may be fed by its Owner. An orphan has no active
        // parent, so it may be fed by any player.
        boolean orphaned = data.getBoolean("Orphaned");
        if (!orphaned) {
            if (!data.contains("OwnerUUID", 11)) return false;
            try {
                if (!player.getUUID().equals(data.getUUID("OwnerUUID"))) {
                    return false;
                }
            } catch (Exception ignored) {
                return false;
            }
        }

        BabyFoodItem.FoodData food;
        if (foodStack.is(Items.MILK_BUCKET)) {
            food = new BabyFoodItem.FoodData(
                    2.5F, 3.5F, 0.0F,
                    java.util.List.of(),
                    new ItemStack(Items.BUCKET)
            );
        } else if (BabyFoodItem.isBabyFood(foodStack)) {
            food = BabyFoodItem.getFoodData(foodStack);
        } else {
            return false;
        }

        float currentFood = data.contains(BabyNPCPlayerEntity.FOOD_LEVEL_TAG)
                ? BabyNPCPlayerEntity.readFoodLevelTag(
                        data, BabyNPCPlayerEntity.MAX_HUNGER)
                : BabyNPCPlayerEntity.MAX_HUNGER;

        float nutrition = Math.min(
                food.food(),
                BabyNPCPlayerEntity.MAX_HUNGER - currentFood
        );
        boolean hasExtraBenefit = food.absorption() > 0.0F || !food.effects().isEmpty();
        if (nutrition <= 0.0F && !hasExtraBenefit) return false;

        float saturation = data.contains(BabyNPCPlayerEntity.SATURATION_LEVEL_TAG)
                ? data.getFloat(BabyNPCPlayerEntity.SATURATION_LEVEL_TAG)
                : 0.0F;

        // Match BabyNPCPlayerEntity.foodFeed()/addFood() semantics.
        if (nutrition > 0.0F) {
            float addedSaturation = Math.min(food.saturation(), nutrition);
            saturation = Math.min(
                    Math.min(
                            BabyNPCPlayerEntity.MAX_SATURATION,
                            currentFood + nutrition
                    ),
                    saturation + addedSaturation
            );
        }

        float newFood = Math.min(
                BabyNPCPlayerEntity.MAX_HUNGER,
                currentFood + nutrition
        );

        data.putFloat(BabyNPCPlayerEntity.FOOD_LEVEL_TAG, newFood);
        data.putFloat(BabyNPCPlayerEntity.SATURATION_LEVEL_TAG, saturation);
        data.putFloat(BabyNPCPlayerEntity.EXHAUSTION_LEVEL_TAG,
                data.contains(BabyNPCPlayerEntity.EXHAUSTION_LEVEL_TAG)
                        ? Math.max(0.0F, data.getFloat(BabyNPCPlayerEntity.EXHAUSTION_LEVEL_TAG))
                        : 0.0F);

        // Golden-milk absorption follows the same Baby food payload.
        if (food.absorption() > 0.0F) {
            float currentAbsorption = data.contains("AbsorptionAmount")
                    ? Math.max(0.0F, data.getFloat("AbsorptionAmount"))
                    : 0.0F;
            data.putFloat(
                    "AbsorptionAmount",
                    Math.max(currentAbsorption, food.absorption())
            );
        }

        // Persist status effects exactly in LivingEntity's ActiveEffects list.
        if (!food.effects().isEmpty()) {
            net.minecraft.nbt.ListTag effects;
            if (data.contains("ActiveEffects", 9)) {
                effects = data.getList("ActiveEffects", 10);
            } else {
                effects = new net.minecraft.nbt.ListTag();
                data.put("ActiveEffects", effects);
            }

            for (net.minecraft.world.effect.MobEffectInstance effect
                    : food.effects()) {
                mergeActiveEffect(effects, effect);
            }
        }

        // Record only deliberate player feeding. Autonomous pickup never enters
        // this method. Persist just the history field on the stack; the live
        // survival values remain runtime-only, so this does not recreate the
        // old per-tick ItemStack reload problem.
        recordFedByPlayer(data, player);
        CompoundTag serialized = root.getCompound(BABY_DATA_TAG);
        serialized.put("FedByPlayers", data.contains("FedByPlayers", 9)
                ? data.getList("FedByPlayers", 10).copy()
                : new net.minecraft.nbt.ListTag());
        serialized.put("Protectors", data.contains("Protectors", 9)
                ? data.getList("Protectors", 10).copy()
                : new net.minecraft.nbt.ListTag());

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            syncClientState(serverPlayer, runtime, data);
        }

        return true;
    }

    /** Records a deliberate player feed in the Baby's persistent history. */
    private static void recordFedByPlayer(CompoundTag data, Player player) {
        if (data == null || player == null) return;

        net.minecraft.nbt.ListTag history = data.contains("FedByPlayers", 9)
                ? data.getList("FedByPlayers", 10)
                : new net.minecraft.nbt.ListTag();

        String uuid = player.getUUID().toString();
        String name = player.getGameProfile().getName();
        for (int i = 0; i < history.size(); i++) {
            CompoundTag entry = history.getCompound(i);
            if (uuid.equals(entry.getString("UUID"))) {
                entry.putString("Name", name);
                entry.putInt("Times", entry.getInt("Times") + 1);
                data.put("FedByPlayers", history);
                return;
            }
        }

        CompoundTag entry = new CompoundTag();
        entry.putString("UUID", uuid);
        entry.putString("Name", name);
        entry.putInt("Times", 1);
        history.add(entry);
        data.put("FedByPlayers", history);
    }

    private static boolean isFunctionalBlock(
            net.minecraft.world.level.block.state.BlockState state
    ) {
        var block = state.getBlock();

        return block instanceof net.minecraft.world.level.block.DoorBlock
                || block instanceof net.minecraft.world.level.block.TrapDoorBlock
                || block instanceof net.minecraft.world.level.block.ButtonBlock
                || block instanceof net.minecraft.world.level.block.LeverBlock
                || block instanceof net.minecraft.world.level.block.FenceGateBlock
                || block instanceof net.minecraft.world.level.block.ChestBlock
                || block instanceof net.minecraft.world.level.block.EnderChestBlock
                || block instanceof net.minecraft.world.level.block.BarrelBlock
                || block instanceof net.minecraft.world.level.block.ShulkerBoxBlock
                || block instanceof net.minecraft.world.level.block.HopperBlock
                || block instanceof net.minecraft.world.level.block.FurnaceBlock
                || block instanceof net.minecraft.world.level.block.BrewingStandBlock
                || block instanceof net.minecraft.world.level.block.DispenserBlock
                || block instanceof net.minecraft.world.level.block.DropperBlock
                || block instanceof net.minecraft.world.level.block.CraftingTableBlock
                || block instanceof net.minecraft.world.level.block.StonecutterBlock
                || block instanceof net.minecraft.world.level.block.LoomBlock
                || block instanceof net.minecraft.world.level.block.CartographyTableBlock
                || block instanceof net.minecraft.world.level.block.SmithingTableBlock
                || block instanceof net.minecraft.world.level.block.GrindstoneBlock
                || block instanceof net.minecraft.world.level.block.AnvilBlock
                || block instanceof net.minecraft.world.level.block.EnchantmentTableBlock
                || block instanceof net.minecraft.world.level.block.LecternBlock
                || block instanceof net.minecraft.world.level.block.BedBlock
                || block instanceof net.minecraft.world.level.block.BellBlock
                || block instanceof net.minecraft.world.level.block.NoteBlock
                || block instanceof net.minecraft.world.level.block.JukeboxBlock
                || block instanceof net.minecraft.world.level.block.BaseEntityBlock
                || block instanceof net.minecraft.world.level.block.SignBlock;
    }


    /**
     * Q-drop is forbidden.
     */
    @Override
    public boolean onDroppedByPlayer(
            ItemStack stack,
            Player player
    ) {
        player.displayClientMessage(
                Component.literal(
                        "Never drop baby down like that again."
                ),
                true
        );

        return false;
    }

    /**
     * Also refuse insertion into container-item storage such as bundles.
     */
    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            net.minecraft.world.InteractionHand hand
    ) {
        return InteractionResultHolder.pass(
                player.getItemInHand(hand)
        );
    }

    @Override
    public void initializeClient(
            Consumer<IClientItemExtensions> consumer
    ) {
        consumer.accept(new IClientItemExtensions() {

            private BabyNPCPlayerItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer
                    .BlockEntityWithoutLevelRenderer getCustomRenderer() {

                if (renderer == null) {
                    renderer = new BabyNPCPlayerItemRenderer();
                }

                return renderer;
            }
        });
    }
}
