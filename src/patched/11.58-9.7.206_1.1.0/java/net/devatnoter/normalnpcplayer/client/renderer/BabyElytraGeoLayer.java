package net.devatnoter.normalnpcplayer.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Renders the vanilla Elytra model on the Baby's posed chest bone.
 *
 * Vanilla ElytraLayer cannot be attached directly to GeoEntityRenderer because
 * vanilla RenderLayer requires an EntityModel parent. GeckoLib's per-bone layer
 * callback gives us the missing attachment point: the pose stack is already
 * positioned at the selected GeoBone when renderForBone() is called.
 *
 * The vanilla ElytraModel is still used for the actual wing geometry and
 * animation, so fall-flying/crouching wing poses stay vanilla-compatible.
 */
public final class BabyElytraGeoLayer extends GeoRenderLayer<BabyNPCPlayerEntity> {
    private static final ResourceLocation ELYTRA_TEXTURE =
            new ResourceLocation("minecraft", "textures/entity/elytra.png");

    private static final float BABY_ELYTRA_SCALE = 1.25F;
    private static final float BABY_ELYTRA_X_OFFSET = 0.0F;
    private static final float BABY_ELYTRA_Y_OFFSET = 1.5F;
    private static final float BABY_ELYTRA_Z_OFFSET = 0.0F;

    private final ElytraModel<BabyNPCPlayerEntity> elytraModel;

    public BabyElytraGeoLayer(
            GeoEntityRenderer<BabyNPCPlayerEntity> renderer,
            EntityModelSet modelSet
    ) {
        super(renderer);
        this.elytraModel = new ElytraModel<>(
                modelSet.bakeLayer(ModelLayers.ELYTRA)
        );
    }
    
    @Override
    public void renderForBone(
            PoseStack poseStack,
            BabyNPCPlayerEntity baby,
            GeoBone bone,
            RenderType renderType,
            MultiBufferSource bufferSource,
            VertexConsumer buffer,
            float partialTick,
            int packedLight,
            int packedOverlay
    ) {
        if (!"elytra".equals(bone.getName())) return;

        ItemStack chest = baby.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.isEmpty() || !(chest.getItem() instanceof ElytraItem)) return;

        poseStack.pushPose();

        // The `elytra` bone is a real cube-bearing attachment marker, fully buried
        // inside the Baby body. GeckoLib therefore dispatches this callback at
        // the exact Elytra attachment bone while the actual wing geometry stays
        // the vanilla ElytraModel. Keep the vanilla 0.125 back offset and scale
        // the vanilla 8-unit Elytra to the Baby chest width (6/8 = 0.75).
        // The GeoBone callback already places us at the Elytra attachment point.
        // Correct only the vanilla Elytra orientation/scale relative to that anchor.
        poseStack.translate(
                BABY_ELYTRA_X_OFFSET,
                BABY_ELYTRA_Y_OFFSET,
                BABY_ELYTRA_Z_OFFSET
        );

// Rotation
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180.0F));
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(0.0F));

        poseStack.scale(
                BABY_ELYTRA_SCALE,
                BABY_ELYTRA_SCALE,
                BABY_ELYTRA_SCALE
        );

        elytraModel.setupAnim(
                baby,
                0.0F,
                0.0F,
                baby.tickCount + partialTick,
                baby.getYRot(),
                baby.getXRot()
        );

        String texturePath = ForgeHooksClient.getArmorTexture(
                baby,
                chest,
                ELYTRA_TEXTURE.toString(),
                EquipmentSlot.CHEST,
                null
        );
        ResourceLocation texture = new ResourceLocation(texturePath);

        VertexConsumer elytraBuffer = ItemRenderer.getArmorFoilBuffer(
                bufferSource,
                RenderType.armorCutoutNoCull(texture),
                false,
                chest.hasFoil()
        );

        elytraModel.renderToBuffer(
                poseStack,
                elytraBuffer,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        poseStack.popPose();
    }
}
