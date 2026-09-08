package net.devatnoter.normalnpcplayer.client.renderer;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.devatnoter.normalnpcplayer.client.skin.AdultPlayerSkinManager;
import net.devatnoter.normalnpcplayer.entity.AdultPlayerMobEntity;
import net.devatnoter.normalnpcplayer.entity.AdultPlayerEntityScale;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;

public final class AdultPlayerMobRenderer
        extends HumanoidMobRenderer<
        AdultPlayerMobEntity,
        PlayerModel<AdultPlayerMobEntity>> {

    private final PlayerModel<AdultPlayerMobEntity> wideModel;
    private final PlayerModel<AdultPlayerMobEntity> slimModel;

    public AdultPlayerMobRenderer(
            EntityRendererProvider.Context context
    ) {
        super(
                context,
                new PlayerModel<>(
                        context.bakeLayer(ModelLayers.PLAYER),
                        false
                ),
                0.5F
        );

        this.wideModel = this.model;

        this.slimModel = new PlayerModel<>(
                context.bakeLayer(ModelLayers.PLAYER_SLIM),
                true
        );

        /*
         * ============================================================
         * HELD ITEMS
         * ============================================================
         */
        this.addLayer(
                new ItemInHandLayer<>(
                        this,
                        context.getItemInHandRenderer()
                )
        );

        /*
         * ============================================================
         * ARMOR
         * ============================================================
         *
         * Normal entity equipment slots are rendered here.
         */
        this.addLayer(
                new HumanoidArmorLayer<>(
                        this,
                        new HumanoidModel<>(
                                context.bakeLayer(
                                        ModelLayers.PLAYER_INNER_ARMOR
                                )
                        ),
                        new HumanoidModel<>(
                                context.bakeLayer(
                                        ModelLayers.PLAYER_OUTER_ARMOR
                                )
                        ),
                        context.getModelManager()
                )
        );

        /*
         * ============================================================
         * ELYTRA
         * ============================================================
         *
         * Custom layer:
         *
         * - Normal Elytra item still works.
         * - If the GameProfile has a cape, that cape texture is used
         *   on the Elytra, matching the player cosmetic behavior.
         */
        this.addLayer(
                new PlayerMobElytraLayer(
                        this,
                        context.getModelSet()
                )
        );

        /*
         * ============================================================
         * CAPE
         * ============================================================
         */
        this.addLayer(
                new CapeLayer(this)
        );

        this.shadowRadius = 0.5F;
    }

    @Override
    protected void scale(
            AdultPlayerMobEntity entity,
            PoseStack poseStack,
            float partialTick
    ) {
        // Vanilla PlayerRenderer applies 0.9375F to PlayerModel.
        // Keep Adult custom scale on top of that vanilla baseline.
        final float vanillaPlayerScale = 0.9375F;
        poseStack.scale(
                vanillaPlayerScale * AdultPlayerEntityScale.SCALE_X,
                vanillaPlayerScale * AdultPlayerEntityScale.SCALE_Y,
                vanillaPlayerScale * AdultPlayerEntityScale.SCALE_Z
        );
    }

    @Override
    public void render(
            AdultPlayerMobEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight
    ) {
        boolean slim =
                AdultPlayerSkinManager.isSlim(
                        entity.getGameProfile(),
                        entity.isSlim()
                );

        this.model =
                slim
                        ? this.slimModel
                        : this.wideModel;

        // PlayerMob uses the vanilla PlayerRenderer pattern: PlayerModel has
        // its own crouching flag, so set it before HumanoidMobRenderer runs
        // setupAnim(). This keeps crouch entirely on the vanilla model pipe.
        this.model.crouching = entity.isCrouching();

        // PlayerModel needs the same arm-pose preparation used by vanilla
        // player rendering. HumanoidMobRenderer does not populate these
        // PlayerModel-specific pose fields for us. Without this, swing/use
        // animation can be triggered on the entity but the player arms do not
        // receive the correct pose state.
        applyArmPoses(entity, this.model);

        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                buffer,
                packedLight
        );

        // No per-frame swing diagnostics in production rendering.
        // The vanilla PlayerModel receives the swing state above.

    }

    private static void applyArmPoses(
            AdultPlayerMobEntity entity,
            PlayerModel<AdultPlayerMobEntity> model
    ) {
        HumanoidModel.ArmPose mainPose = armPose(entity, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose offPose = armPose(entity, InteractionHand.OFF_HAND);

        // Vanilla PlayerModel gives the active-hand pose priority over the
        // other hand when an item is being used.
        if (mainPose.isTwoHanded()) {
            offPose = entity.getItemInHand(InteractionHand.OFF_HAND).isEmpty()
                    ? HumanoidModel.ArmPose.EMPTY
                    : HumanoidModel.ArmPose.ITEM;
        }

        if (entity.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = mainPose;
            model.leftArmPose = offPose;
        } else {
            model.rightArmPose = offPose;
            model.leftArmPose = mainPose;
        }
    }

    private static HumanoidModel.ArmPose armPose(
            AdultPlayerMobEntity entity,
            InteractionHand hand
    ) {
        ItemStack stack = entity.getItemInHand(hand);
        if (stack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }

        if (entity.getUsedItemHand() == hand && entity.getUseItemRemainingTicks() > 0) {
            UseAnim useAnim = stack.getUseAnimation();
            return switch (useAnim) {
                case BLOCK -> HumanoidModel.ArmPose.BLOCK;
                case BOW -> HumanoidModel.ArmPose.BOW_AND_ARROW;
                case SPEAR -> HumanoidModel.ArmPose.THROW_SPEAR;
                case CROSSBOW -> HumanoidModel.ArmPose.CROSSBOW_CHARGE;
                case SPYGLASS -> HumanoidModel.ArmPose.SPYGLASS;
                case TOOT_HORN -> HumanoidModel.ArmPose.TOOT_HORN;
                case BRUSH -> HumanoidModel.ArmPose.BRUSH;
                default -> HumanoidModel.ArmPose.ITEM;
            };
        }

        // A charged crossbow is held with both arms unless the entity is
        // currently in a charging/use state. This mirrors PlayerModel's
        // player-mob renderer behavior.
        if (!entity.swinging
                && stack.getItem() instanceof CrossbowItem
                && CrossbowItem.isCharged(stack)) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }

        return HumanoidModel.ArmPose.ITEM;
    }

    @Override
    public ResourceLocation getTextureLocation(
            AdultPlayerMobEntity entity
    ) {
        AdultPlayerSkinManager.ProfileTextures textures =
                AdultPlayerSkinManager.getTextures(
                        entity.getGameProfile()
                );

        if (textures != null) {
            ResourceLocation skin =
                    textures.skin();

            if (skin != null) {
                return skin;
            }
        }

        return DefaultPlayerSkin.getDefaultSkin(
                entity.getUUID()
        );
    }

    /*
     * ================================================================
     * PLAYER MOB ELYTRA LAYER
     * ================================================================
     *
     * Vanilla ElytraLayer is retained.
     *
     * The only behavior changed is texture selection:
     *
     *     Cape available -> use cape texture on Elytra
     *     No cape        -> use normal Elytra texture
     */
    private static final class PlayerMobElytraLayer
            extends ElytraLayer<
            AdultPlayerMobEntity,
            PlayerModel<AdultPlayerMobEntity>> {

        private PlayerMobElytraLayer(
                RenderLayerParent<
                        AdultPlayerMobEntity,
                        PlayerModel<AdultPlayerMobEntity>
                        > parent,
                net.minecraft.client.model.geom.EntityModelSet modelSet
        ) {
            super(parent, modelSet);
        }

        @Override
        public ResourceLocation getElytraTexture(
                ItemStack stack,
                AdultPlayerMobEntity entity
        ) {
            AdultPlayerSkinManager.ProfileTextures textures =
                    AdultPlayerSkinManager.getTextures(
                            entity.getGameProfile()
                    );

            if (textures != null) {
                ResourceLocation capeTexture =
                        textures.cape();

                if (capeTexture != null) {
                    return capeTexture;
                }
            }

            return super.getElytraTexture(
                    stack,
                    entity
            );
        }
    }

    /*
     * ================================================================
     * CAPE LAYER
     * ================================================================
     *
     * Uses PlayerModel.renderCloak().
     *
     * We do not access PlayerModel.cloak directly.
     */
    private static final class CapeLayer
            extends RenderLayer<
            AdultPlayerMobEntity,
            PlayerModel<AdultPlayerMobEntity>> {

        private CapeLayer(
                RenderLayerParent<
                        AdultPlayerMobEntity,
                        PlayerModel<AdultPlayerMobEntity>
                        > parent
        ) {
            super(parent);
        }

        @Override
        public void render(
                PoseStack poseStack,
                MultiBufferSource buffer,
                int packedLight,
                AdultPlayerMobEntity entity,
                float limbSwing,
                float limbSwingAmount,
                float partialTick,
                float ageInTicks,
                float netHeadYaw,
                float headPitch
        ) {
            AdultPlayerSkinManager.ProfileTextures textures =
                    AdultPlayerSkinManager.getTextures(
                            entity.getGameProfile()
                    );

            if (textures == null) {
                return;
            }

            ResourceLocation capeTexture =
                    textures.cape();

            if (capeTexture == null) {
                return;
            }

            /*
             * If Elytra is equipped, do not render the cape as a
             * second layer on top of the Elytra.
             */
            ItemStack chestStack =
                    entity.getItemBySlot(
                            EquipmentSlot.CHEST
                    );

            if (!chestStack.isEmpty()
                    && chestStack.getItem() instanceof ElytraItem) {
                return;
            }

            poseStack.pushPose();

            PlayerModel<AdultPlayerMobEntity> model =
                    this.getParentModel();

            /*
             * ========================================================
             * POSITION INTERPOLATION
             * ========================================================
             */
            double currentX =
                    Mth.lerp(
                            partialTick,
                            entity.xOld,
                            entity.getX()
                    );

            double currentY =
                    Mth.lerp(
                            partialTick,
                            entity.yOld,
                            entity.getY()
                    );

            double currentZ =
                    Mth.lerp(
                            partialTick,
                            entity.zOld,
                            entity.getZ()
                    );

            double capeX =
                    entity.xOld;

            double capeY =
                    entity.yOld;

            double capeZ =
                    entity.zOld;

            double dx =
                    capeX - currentX;

            double dy =
                    capeY - currentY;

            double dz =
                    capeZ - currentZ;

            /*
             * ========================================================
             * BODY ROTATION
             * ========================================================
             */
            float bodyYaw =
                    Mth.rotLerp(
                            partialTick,
                            entity.yBodyRotO,
                            entity.yBodyRot
                    );

            double radians =
                    bodyYaw
                            * ((double) Math.PI / 180.0D);

            double sin =
                    Math.sin(radians);

            double cos =
                    -Math.cos(radians);

            /*
             * ========================================================
             * VANILLA-LIKE CAPE PHYSICS
             * ========================================================
             */
            float vertical =
                    (float) dy * 10.0F;

            vertical =
                    Mth.clamp(
                            vertical,
                            -6.0F,
                            32.0F
                    );

            float forward =
                    (float)
                            (dx * sin + dz * cos)
                            * 100.0F;

            forward =
                    Mth.clamp(
                            forward,
                            0.0F,
                            150.0F
                    );

            float sideways =
                    (float)
                            (dx * cos - dz * sin)
                            * 100.0F;

            sideways =
                    Mth.clamp(
                            sideways,
                            -20.0F,
                            20.0F
                    );

            float horizontalSpeed =
                    (float)
                            entity.getDeltaMovement()
                                    .horizontalDistance();

            vertical +=
                    Mth.clamp(
                            horizontalSpeed * 90.0F,
                            0.0F,
                            12.0F
                    );

            if (entity.isCrouching()) {
                vertical += 25.0F;
            }

            /*
             * ========================================================
             * CAPE TRANSFORM
             * ========================================================
             *
             * IMPORTANT:
             *
             * The old renderer was missing the vanilla 180-degree
             * Y rotation. That caused the cape to face backwards.
             *
             * Vanilla-style sequence:
             *
             *     X rotation
             *     Z rotation
             *     Y compensation
             *     Y 180 degrees
             */
            poseStack.translate(
                    0.0D,
                    0.0D,
                    0.125D
            );

            poseStack.mulPose(
                    Axis.XP.rotationDegrees(
                            6.0F
                                    + forward / 2.0F
                                    + vertical
                    )
            );

            poseStack.mulPose(
                    Axis.ZP.rotationDegrees(
                            sideways / 2.0F
                    )
            );

            poseStack.mulPose(
                    Axis.YP.rotationDegrees(
                            -sideways / 2.0F
                    )
            );

            /*
             * This 180 degree rotation is essential.
             *
             * Without it the PlayerModel cloak is rendered
             * facing the wrong direction.
             */
            poseStack.mulPose(
                    Axis.YP.rotationDegrees(
                            180.0F
                    )
            );

            /*
             * ========================================================
             * CAPE RENDER
             * ========================================================
             */
            VertexConsumer consumer =
                    buffer.getBuffer(
                            RenderType.entitySolid(
                                    capeTexture
                            )
                    );

            model.renderCloak(
                    poseStack,
                    consumer,
                    packedLight,
                    OverlayTexture.NO_OVERLAY
            );

            poseStack.popPose();
        }
    }
}