package net.devatnoter.normalnpcplayer.item;

import net.devatnoter.normalnpcplayer.client.renderer.BabyNPCPlayerItemRenderer;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

public final class BabyNPCPlayerItem extends Item {

    public static final String BABY_DATA_TAG = "BabyEntityData";
    public static final String CARRIER_VERSION_TAG = "BabyCarrierVersion";
    public static final String CARRIER_TEXTURE_INDEX_TAG = "BabyTextureIndex";
    public static final String CARRIER_SKIN_ID_TAG = "BabySkinId";
    public static final String CARRIER_TEXTURE_REF_TAG = "BabyTextureRef";
    public static final String BABY_VARIANT_TAG = "BabyVariant";
    public static final String BABY_ENTITY_ID =
            "normalnpcplayer:baby_npc_player";

    /** Last server game-time processed while this Baby exists as an item. */
    public static final String CARRIER_LAST_TICK_TAG = "BabyCarrierLastTick";

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

        // Snapshot environmental state for the carrier tooltip.
        entityData.putBoolean("Wet", baby.isInWaterOrRain());

        entityData.remove("Pos");
        entityData.remove("Motion");
        entityData.remove("Rotation");
        entityData.remove("FallDistance");

        entityData.putString(
                "id",
                BABY_ENTITY_ID
        );

        // The carrier is a frozen snapshot of the real Baby, but its
        // survival/effect clocks continue while it is held in an inventory.
        entityData.putLong(
                CARRIER_LAST_TICK_TAG,
                baby.level().getGameTime()
        );
        // Keep the authoritative absorption buffer in the carrier snapshot.
        entityData.putFloat("AbsorptionAmount", baby.getAbsorptionAmount());

        int textureIndex =
                baby.getTextureIndex();

        String textureRef = baby.getTextureId();
        if (textureRef == null || textureRef.isBlank()) {
            textureRef =
                    "normalnpcplayer:textures/entity/baby/baby"
                            + textureIndex
                            + ".png";
        }

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

        stack.setTag(carrier);
        stack.setCount(1);

        return stack;
    }

    public static CompoundTag copyEntityData(ItemStack stack) {
        if (!isBabyStack(stack)) {
            return new CompoundTag();
        }

        return stack.getTag()
                .getCompound(BABY_DATA_TAG)
                .copy();
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


    /**
     * A Baby item is not a dead snapshot. While carried, its survival state
     * keeps advancing server-side so active effects, health, hunger and air do
     * not freeze/reset between pickup and placement.
     */
    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int slotId,
            boolean isSelected
    ) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        if (level.isClientSide || !isBabyStack(stack)) {
            return;
        }

        // The held Baby is still a live survival object. Its NBT is allowed to
        // refresh while selected so the main-hand tooltip never lags behind
        // the offhand. FPP equip progress is stabilized separately by the
        // client ItemInHandRenderer mixin, so state sync does not make the
        // Baby visually re-equip.

        CompoundTag data = copyEntityData(stack);
        long now = level.getGameTime();
        long last = data.contains(CARRIER_LAST_TICK_TAG)
                ? data.getLong(CARRIER_LAST_TICK_TAG)
                : now - 1L;

        long elapsed = Math.max(0L, now - last);
        // Do not mutate the ItemStack every render/tick. The survival clock
        // remains based on absolute server game time; this only controls how
        // often the resulting state is pushed into the inventory stack.
        if (elapsed < 2L) {
            return;
        }

        // Avoid an accidental multi-million tick catch-up after a long
        // disconnect while still preserving ordinary lag exactly.
        long steps = Math.min(elapsed, 1200L);
        if (entity instanceof Player holder) {
            advanceCarrierState(stack, data, level, holder, steps);
        } else {
            advanceCarrierState(stack, data, level, null, steps);
        }

        data.putLong(CARRIER_LAST_TICK_TAG, now);
        writeEntityData(stack, data);
    }

    private static void advanceCarrierState(
            ItemStack stack,
            CompoundTag data,
            Level level,
            Player holder,
            long ticks
    ) {
        if (!(level instanceof ServerLevel serverLevel) || ticks <= 0L) {
            return;
        }

        Entity restored = EntityType.create(data.copy(), serverLevel).orElse(null);
        if (!(restored instanceof BabyNPCPlayerEntity baby)) {
            return;
        }

        baby.setHealth(Math.max(0.0F, Math.min(baby.getMaxHealth(),
                data.contains("Health") ? data.getFloat("Health") : baby.getMaxHealth())));
        baby.setFoodLevel(BabyNPCPlayerEntity.readFoodLevelTag(data, 20.0F));
        baby.setSaturationLevel(data.contains(BabyNPCPlayerEntity.SATURATION_LEVEL_TAG)
                ? data.getFloat(BabyNPCPlayerEntity.SATURATION_LEVEL_TAG) : 5.0F);
        baby.setExhaustionLevel(data.contains(BabyNPCPlayerEntity.EXHAUSTION_LEVEL_TAG)
                ? data.getFloat(BabyNPCPlayerEntity.EXHAUSTION_LEVEL_TAG) : 0.0F);
        baby.setAbsorptionAmount(Math.max(0.0F,
                data.contains("AbsorptionAmount") ? data.getFloat("AbsorptionAmount") : 0.0F));
        baby.setTotalExperience(Math.max(0, data.contains(BabyNPCPlayerEntity.TOTAL_XP_TAG)
                ? data.getInt(BabyNPCPlayerEntity.TOTAL_XP_TAG)
                : data.getInt("XpTotal")));
        baby.setExperienceLevel(Math.max(0, data.contains(BabyNPCPlayerEntity.EXPERIENCE_LEVEL_TAG)
                ? data.getInt(BabyNPCPlayerEntity.EXPERIENCE_LEVEL_TAG)
                : data.getInt("XpLevel")));

        ListTag activeEffects = data.getList("ActiveEffects", 10);
        for (int i = 0; i < activeEffects.size(); i++) {
            MobEffectInstance effect = MobEffectInstance.load(activeEffects.getCompound(i));
            if (effect != null) {
                baby.addEffect(effect);
            }
        }

        for (long i = 0L; i < ticks && baby.isAlive(); i++) {
            // Carrying is still physical activity for the Baby. Mirror a
            // small movement exhaustion pulse from the carrier so hunger
            // continues naturally instead of freezing at pickup time.
            if (holder != null && holder.getDeltaMovement().horizontalDistanceSqr() > 0.000001D) {
                baby.setExhaustionLevel(baby.getExhaustionLevel() + 0.01F);
                if (baby.getExhaustionLevel() >= 4.0F) {
                    baby.setExhaustionLevel(baby.getExhaustionLevel() - 4.0F);
                    if (baby.getSaturationLevel() > 0.0F) {
                        baby.setSaturationLevel(baby.getSaturationLevel() - 1.0F);
                    } else if (baby.getFoodLevelExact() > 0.0F) {
                        baby.setFoodLevel(baby.getFoodLevelExact() - 1.0F);
                    }
                }
            }

            // The Baby has its own oxygen clock. Do NOT copy the player's air
            // supply: the Baby is a separate LivingEntity with its own Air NBT.
            // While carried, its virtual position is the same head position it
            // uses when rendered as an entity, and air is advanced from that
            // position only.
            tickCarriedAir(baby, level, holder);

            // Tick each effect exactly as LivingEntity does. This makes
            // poison/wither/regen and their durations continue in the item.
            ListTag effectsNow = baby.getActiveEffects().isEmpty()
                    ? new ListTag()
                    : new ListTag();
            for (MobEffectInstance effect : baby.getActiveEffects()) {
                effect.tick(baby, () -> {});
                if (effect.getDuration() > 0) {
                    CompoundTag effectTag = new CompoundTag();
                    effect.save(effectTag);
                    effectsNow.add(effectTag);
                }
            }
            baby.removeAllEffects();
            for (int e = 0; e < effectsNow.size(); e++) {
                MobEffectInstance effect = MobEffectInstance.load(effectsNow.getCompound(e));
                if (effect != null) {
                    baby.addEffect(effect);
                }
            }

            if (!baby.isAlive()) {
                break;
            }

            // No artificial movement is introduced while the item is held.
            // Hunger/regen still use the exact same survival rules as the
            // placed Baby.
            if (baby.getFoodLevelExact() >= 18.0F
                    && baby.getHealth() < baby.getMaxHealth()
                    && level.getGameRules().getBoolean(
                            net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION)
                    && (i + 1L) % 10L == 0L) {
                baby.heal(1.0F);
                baby.setExhaustionLevel(baby.getExhaustionLevel() + 3.0F);
            }

            // Match player-style starvation cadence while the Baby is carried.
            if (baby.getFoodLevelExact() <= 0.0F
                    && baby.getHealth() > 0.0F
                    && (i + 1L) % 80L == 0L
                    && level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
                    && (level.getDifficulty() != net.minecraft.world.Difficulty.EASY
                    || baby.getHealth() > 10.0F)
                    && (level.getDifficulty() != net.minecraft.world.Difficulty.NORMAL
                    || baby.getHealth() > 1.0F)) {
                baby.hurt(level.damageSources().starve(), 1.0F);
            }

        }

        data.putFloat("Health", Math.max(0.0F, baby.getHealth()));
        data.putFloat("AbsorptionAmount", Math.max(0.0F, baby.getAbsorptionAmount()));
        data.putInt(BabyNPCPlayerEntity.TOTAL_XP_TAG, baby.getTotalExperience());
        data.putInt(BabyNPCPlayerEntity.EXPERIENCE_LEVEL_TAG, baby.getExperienceLevel());
        data.putInt("XpTotal", baby.getTotalExperience());
        data.putInt("XpLevel", baby.getExperienceLevel());
        data.putFloat(BabyNPCPlayerEntity.FOOD_LEVEL_TAG, baby.getFoodLevelExact());
        data.putFloat(BabyNPCPlayerEntity.SATURATION_LEVEL_TAG, baby.getSaturationLevel());
        data.putFloat(BabyNPCPlayerEntity.EXHAUSTION_LEVEL_TAG, baby.getExhaustionLevel());
        data.putInt("Air", Math.max(0, baby.getAirSupply()));
        data.putInt("MaxAir", baby.getMaxAirSupply());

        ListTag effectsOut = new ListTag();
        for (MobEffectInstance effect : baby.getActiveEffects()) {
            CompoundTag effectTag = new CompoundTag();
            effect.save(effectTag);
            effectsOut.add(effectTag);
        }
        data.put("ActiveEffects", effectsOut);
        data.putBoolean("Wet", holder != null && holder.isUnderWater());
    }

    /**
     * Advance the Baby's own vanilla-style oxygen clock while it exists as an
     * inventory item. The holder's Air value is never copied into the Baby.
     * Instead we temporarily place the restored Baby at its real head-riding
     * position and evaluate the Baby's own eye against the world's water.
     */
    private static void tickCarriedAir(
            BabyNPCPlayerEntity baby,
            Level level,
            Player holder
    ) {
        if (baby == null || level == null) {
            return;
        }

        if (holder != null) {
            baby.moveTo(
                    holder.getX(),
                    holder.getY() + holder.getBbHeight() + 0.08D,
                    holder.getZ(),
                    holder.getYRot(),
                    0.0F
            );
        }

        if (baby.isEyeInFluid(FluidTags.WATER)
                && !baby.hasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING)) {
            int air = baby.getAirSupply();
            int nextAir = Math.max(-20, air - 1);
            baby.setAirSupply(nextAir);

            if (nextAir <= -20) {
                baby.setAirSupply(0);
                baby.hurt(level.damageSources().drown(), 2.0F);
            }
        } else {
            baby.setAirSupply(
                    Math.min(
                            baby.getMaxAirSupply(),
                            baby.getAirSupply() + 4
                    )
            );
        }
    }

    private static void writeEntityData(ItemStack stack, CompoundTag data) {
        CompoundTag root = stack.getOrCreateTag();
        root.put(BABY_DATA_TAG, data.copy());
        stack.setTag(root);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Level level,
            java.util.List<Component> tooltip,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, level, tooltip, flag);

        CompoundTag data = copyEntityData(stack);

        tooltip.add(
                Component.translatable(
                        "item.normalnpcplayer.baby_npc_player.description"
                ).withStyle(ChatFormatting.GRAY)
        );

        String ownerName = data.contains("OwnerName")
                ? data.getString("OwnerName")
                : "";

        if (!ownerName.isEmpty()) {
            tooltip.add(
                    Component.translatable(
                            "item.normalnpcplayer.baby_npc_player.owner",
                            ownerName
                    ).withStyle(ChatFormatting.GRAY)
            );
        }

        // BabyNPCPlayerEntity has a fixed MAX_HEALTH of 20.0.
        // The saved "Health" value is the exact health at pickup time.
        float health = data.contains("Health")
                ? data.getFloat("Health")
                : 20.0F;

        float maxHealth = 20.0F;

        health = Math.max(0.0F, health);
        maxHealth = Math.max(0.0F, maxHealth);

        // Health is rendered as a real Minecraft HUD-heart tooltip component
        // on the client. Do not add a Unicode heart here.

        CompoundTag equipment = data.contains("BabyEquipment", net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? data.getCompound("BabyEquipment") : new CompoundTag();
        appendEquipmentTooltip(tooltip, equipment, "Head");
        appendEquipmentTooltip(tooltip, equipment, "Chest");
        appendEquipmentTooltip(tooltip, equipment, "Legs");
        appendEquipmentTooltip(tooltip, equipment, "Feet");
        appendEquipmentTooltip(tooltip, equipment, "MainHand");
        appendEquipmentTooltip(tooltip, equipment, "OffHand");
    }

    private static void appendEquipmentTooltip(
            java.util.List<Component> tooltip, CompoundTag equipment, String key) {
        if (!equipment.contains(key, net.minecraft.nbt.Tag.TAG_COMPOUND)) return;
        ItemStack item = ItemStack.of(equipment.getCompound(key));
        if (item.isEmpty()) return;

        Component line = Component.literal(key + ": ")
                .append(item.getHoverName())
                .withStyle(ChatFormatting.GRAY);

        tooltip.add(line);
    }

    @Override
    public InteractionResult useOn(
            UseOnContext context
    ) {
        Level level =
                context.getLevel();

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
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

        /*
         * Placement is valid only when the player actually clicked the
         * TOP face of the clicked block.
         */
        if (context.getClickedFace()
                != net.minecraft.core.Direction.UP) {
            return InteractionResult.PASS;
        }

        var clickedState =
                level.getBlockState(
                        context.getClickedPos()
                );

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

        stack.shrink(1);

        return InteractionResult.CONSUME;
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
