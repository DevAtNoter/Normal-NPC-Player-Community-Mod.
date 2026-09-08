package net.devatnoter.normalnpcplayer.item;

import net.devatnoter.normalnpcplayer.client.renderer.BabyNPCPlayerItemRenderer;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
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

    public BabyNPCPlayerItem(Properties properties) {
        super(properties.stacksTo(1));
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
