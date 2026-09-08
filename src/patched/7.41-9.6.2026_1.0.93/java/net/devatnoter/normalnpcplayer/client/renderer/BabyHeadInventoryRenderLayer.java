package net.devatnoter.normalnpcplayer.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Renders the real head-riding Baby as a child of the Player's HEAD model
 * part while the vanilla survival inventory preview renders the Player.
 *
 * This follows the same attachment path used by Peekaboo's MobItemSpecialRenderer:
 * the entity is rendered from the HEAD display context after the head transform,
 * with the HEAD context's scale/vertical offset/180-degree Y rotation.
 *
 * The important difference is that NNP keeps the Baby as the real passenger;
 * this layer is only a client-side inventory-preview representation.
 */
public final class BabyHeadInventoryRenderLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final float PEEKABOO_HEAD_TARGET_SIZE = 1.5F;
    private static final float HEAD_TRANSLATION_Y = 0.375F;

    public BabyHeadInventoryRenderLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent
    ) {
        super(parent);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            AbstractClientPlayer player,
            float limbAngle,
            float limbDistance,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        // This layer is deliberately inventory-preview-only. In world rendering
        // the real Baby passenger is already rendered by its own renderer.
        if (!(minecraft.screen instanceof InventoryScreen)) {
            return;
        }

        if (player != minecraft.player || minecraft.level == null) {
            return;
        }

        BabyNPCPlayerEntity baby = null;
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof BabyNPCPlayerEntity candidate
                    && candidate.getVehicle() == player) {
                baby = candidate;
                break;
            }
        }

        if (baby == null || baby.isRemoved()) {
            return;
        }

        poseStack.pushPose();

        /*
         * Vanilla's PlayerModel has already received setupAnim() before its
         * render layers run. Translating through the actual HEAD ModelPart is
         * therefore the same attachment point that a HEAD equipment layer uses.
         */
        this.getParentModel().head.translateAndRotate(poseStack);

        /*
         * Peekaboo MobItemSpecialRenderer, ItemDisplayContext.HEAD: 
         *   scale = 1.5 / max(1, approximateSize)
         *   translate(0, 0.375 / scale, 0)
         *   rotate Y = 180 degrees
         *
         * Baby's registered dimensions are 0.6 x 0.9, so the exact formula
         * resolves to the target HEAD scale of 1.5.
         */
        final float approximateSize = Math.max(1.0F, baby.getBbHeight());
        final float scale =
                PEEKABOO_HEAD_TARGET_SIZE / approximateSize;

        poseStack.scale(scale, scale, scale);
        poseStack.translate(
                0.0D,
                HEAD_TRANSLATION_Y / scale,
                0.0D
        );
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        EntityRenderDispatcher dispatcher =
                minecraft.getEntityRenderDispatcher();

        /*
         * The Player HEAD transform already supplies the inventory-preview
         * orientation. Render the Baby with a neutral dispatcher yaw so its
         * own world-space passenger yaw cannot double-rotate the preview.
         */
        dispatcher.render(
                baby,
                0.0D,
                0.0D,
                0.0D,
                0.0F,
                partialTick,
                poseStack,
                bufferSource,
                packedLight
        );

        poseStack.popPose();
    }
}
