package net.devatnoter.normalnpcplayer.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.devatnoter.normalnpcplayer.client.skin.AdultPlayerSkinManager;
import net.devatnoter.normalnpcplayer.entity.AdultPlayerMobEntity;
import net.devatnoter.normalnpcplayer.mixin.PlayerModelAccessor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
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
import net.minecraft.client.model.geom.ModelPart;

/**
 * Adult Player Mob renderer.
 *
 * Keeps the vanilla humanoid pipeline:
 * - skin
 * - held items
 * - armor
 * - Elytra
 * - custom cape
 * - CustomName / CustomNameVisible via the normal entity renderer
 */
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

        // HumanoidMobRenderer does not guarantee these layers for custom mobs.
        this.addLayer(
                new ItemInHandLayer<>(this)
        );

        this.addLayer(
                new ElytraLayer<>(
                        this,
                        context.getModelSet()
                )
        );

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

        this.addLayer(new CapeLayer(this));

        this.shadowRadius = 0.5F;
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

        this.model = slim
                ? this.slimModel
                : this.wideModel;

        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                buffer,
                packedLight
        );
    }

    @Override
    public ResourceLocation getTextureLocation(
            AdultPlayerMobEntity entity
    ) {
        AdultPlayerSkinManager.ProfileTextures textures =
                AdultPlayerSkinManager.getTextures(
                        entity.getGameProfile()
                );

        if (textures != null && textures.skin() != null) {
            return textures.skin();
        }

        // Base adult without PlayerMobProfileName uses vanilla default skin.
        return DefaultPlayerSkin.getDefaultSkin(
                entity.getUUID()
        );
    }

    /**
     * Player cape layer.
     *
     * PlayerModel.cloak is private in 1.20.1, so the layer uses the tiny
     * PlayerModelAccessor mixin instead of model.cape/model.cloak access.
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

            if (textures == null || textures.cape() == null) {
                return;
            }

            ItemStack chest =
                    entity.getItemBySlot(
                            EquipmentSlot.CHEST
                    );

            // Vanilla behavior: Elytra hides the cape.
            if (!chest.isEmpty()
                    && chest.getItem() instanceof ElytraItem) {
                return;
            }

            PlayerModel<AdultPlayerMobEntity> model =
                    this.getParentModel();

            ModelPart cloak =
                    ((PlayerModelAccessor) (Object) model)
                            .normalnpcplayer$getCloak();

            // Vanilla-style trailing motion using the mob's previous/current
            // positions. No direct access to Player-only cloak fields.
            double currentX = Mth.lerp(
                    partialTick,
                    entity.xOld,
                    entity.getX()
            );
            double currentY = Mth.lerp(
                    partialTick,
                    entity.yOld,
                    entity.getY()
            );
            double currentZ = Mth.lerp(
                    partialTick,
                    entity.zOld,
                    entity.getZ()
            );

            double dx = entity.xOld - currentX;
            double dy = entity.yOld - currentY;
            double dz = entity.zOld - currentZ;

            float bodyYaw = Mth.rotLerp(
                    partialTick,
                    entity.yBodyRotO,
                    entity.yBodyRot
            );

            float radians = bodyYaw * ((float) Math.PI / 180F);
            double sin = Mth.sin(radians);
            double cos = -Mth.cos(radians);

            float vertical = Mth.clamp(
                    (float) dy * 10.0F,
                    -6.0F,
                    32.0F
            );

            float forward = Mth.clamp(
                    (float) (dx * sin + dz * cos) * 100.0F,
                    0.0F,
                    150.0F
            );

            float sideways = Mth.clamp(
                    (float) (dx * cos - dz * sin) * 100.0F,
                    -20.0F,
                    20.0F
            );

            float movement =
                    (float) entity.getDeltaMovement()
                            .horizontalDistance();

            vertical += Mth.clamp(
                    movement * 90.0F,
                    0.0F,
                    12.0F
            );

            if (entity.isCrouching()) {
                vertical += 25.0F;
            }

            cloak.xRot = (
                    6.0F
                            + forward / 2.0F
                            + vertical
            ) * ((float) Math.PI / 180F);

            cloak.zRot =
                    sideways / 2.0F
                            * ((float) Math.PI / 180F);

            cloak.yRot =
                    -sideways / 2.0F
                            * ((float) Math.PI / 180F);

            VertexConsumer consumer =
                    buffer.getBuffer(
                            RenderType.entitySolid(
                                    textures.cape()
                            )
                    );

            model.renderCloak(
                    poseStack,
                    consumer,
                    packedLight,
                    OverlayTexture.NO_OVERLAY
            );
        }
    }
}
