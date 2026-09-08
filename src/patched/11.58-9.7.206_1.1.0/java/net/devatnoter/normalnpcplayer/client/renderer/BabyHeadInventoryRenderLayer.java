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
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import java.util.UUID;

/**
 * Renders the real head-riding Baby as a child of the Player's head in the
 * vanilla inventory preview. This layer does not change gameplay transforms.
 *
 * The preview owns the Baby's orientation. The Baby's gameplay/viewer-facing
 * rotation is temporarily neutralized while GeckoLib renders it, so the Baby
 * cannot snap left/right when the inventory preview follows the mouse.
 */
public final class BabyHeadInventoryRenderLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    // Position: independent X/Y/Z controls relative to the Player head pivot.
    private static final float PREVIEW_BABY_POS_X = 0.0F;
    private static final float PREVIEW_BABY_POS_Y = -0.5F;
    private static final float PREVIEW_BABY_POS_Z = 0.0F;

    // Rotation: independent X/Y/Z controls owned by the preview.
    private static final float PREVIEW_BABY_ROT_X = 180.0F;
    private static final float PREVIEW_BABY_ROT_Y = 0.0F;
    private static final float PREVIEW_BABY_ROT_Z = 0.0F;

    // Scale: independent X/Y/Z controls.
    private static final float PREVIEW_BABY_SCALE_X = 1.0F;
    private static final float PREVIEW_BABY_SCALE_Y = 1.0F;
    private static final float PREVIEW_BABY_SCALE_Z = 1.0F;

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

        if (!(minecraft.screen instanceof InventoryScreen)
                && !(minecraft.screen instanceof CreativeModeInventoryScreen)) {
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

        // Anchor to the Player head pivot, but deliberately do not inherit the
        // Player's look pitch/yaw from the head bone.
        final var head = this.getParentModel().head;
        final float savedHeadXRot = head.xRot;
        final float savedHeadYRot = head.yRot;
        final float savedHeadZRot = head.zRot;
        head.xRot = 0.0F;
        head.yRot = 0.0F;
        head.zRot = 0.0F;
        try {
            head.translateAndRotate(poseStack);
        } finally {
            head.xRot = savedHeadXRot;
            head.yRot = savedHeadYRot;
            head.zRot = savedHeadZRot;
        }

        poseStack.translate(
                PREVIEW_BABY_POS_X,
                PREVIEW_BABY_POS_Y,
                PREVIEW_BABY_POS_Z
        );

        poseStack.scale(
                PREVIEW_BABY_SCALE_X,
                PREVIEW_BABY_SCALE_Y,
                PREVIEW_BABY_SCALE_Z
        );

        poseStack.mulPose(Axis.XP.rotationDegrees(PREVIEW_BABY_ROT_X));
        poseStack.mulPose(Axis.YP.rotationDegrees(PREVIEW_BABY_ROT_Y));
        poseStack.mulPose(Axis.ZP.rotationDegrees(PREVIEW_BABY_ROT_Z));

        // Save the complete gameplay-facing orientation/state that can affect
        // the Baby's renderer. Preview rendering must never modify gameplay.
        final float savedXRot = baby.getXRot();
        final float savedYRot = baby.getYRot();
        final float savedYRotO = baby.yRotO;
        final float savedXRotO = baby.xRotO;
        final float savedYHeadRot = baby.yHeadRot;
        final float savedYHeadRotO = baby.yHeadRotO;
        final float savedYBodyRot = baby.yBodyRot;
        final float savedYBodyRotO = baby.yBodyRotO;
        final boolean savedInventoryOpen = baby.isInventoryOpen();
        final UUID savedInventoryViewerUUID = baby.getInventoryViewerUUID();

        // Important: BabyNPCPlayerEntity has inventory-open logic that makes
        // the real Baby face its inventory viewer. That is correct for gameplay,
        // but wrong for this preview because the Player preview already owns
        // the mouse rotation. Disable that viewer-facing state for this single
        // render pass and restore it immediately afterwards.
        baby.setInventoryOpen(false, null);

        // Hard-lock all entity rotations for this render pass. The preview's
        // POS/ROT/scale values above are now the only Baby orientation source.
        baby.setXRot(0.0F);
        baby.setYRot(0.0F);
        baby.xRotO = 0.0F;
        baby.yRotO = 0.0F;
        baby.yHeadRot = 0.0F;
        baby.yHeadRotO = 0.0F;
        baby.yBodyRot = 0.0F;
        baby.yBodyRotO = 0.0F;

        try {
            EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
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
        } finally {
            // Restore every gameplay value exactly as it was before preview.
            baby.setXRot(savedXRot);
            baby.setYRot(savedYRot);
            baby.yRotO = savedYRotO;
            baby.xRotO = savedXRotO;
            baby.yHeadRot = savedYHeadRot;
            baby.yHeadRotO = savedYHeadRotO;
            baby.yBodyRot = savedYBodyRot;
            baby.yBodyRotO = savedYBodyRotO;

            if (savedInventoryOpen) {
                baby.setInventoryOpen(true, savedInventoryViewerUUID);
            } else {
                baby.setInventoryOpen(false, null);
            }

            poseStack.popPose();
        }
    }
}
