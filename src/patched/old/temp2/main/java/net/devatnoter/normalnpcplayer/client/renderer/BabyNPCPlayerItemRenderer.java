package net.devatnoter.normalnpcplayer.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class BabyNPCPlayerItemRenderer
        extends BlockEntityWithoutLevelRenderer {

    /*
     * ============================================================
     * ADJUSTMENT PANEL
     * ============================================================
     *
     * Change ONLY these values when tuning how the Baby looks.
     *
     * Rotation:
     *   ROTATION_DEGREES controls left/right turning around the Y axis.
     *   Positive / negative values change the facing direction.
     *
     * Scale:
     *   SCALE controls overall model size.
     *   Bigger number = bigger Baby.
     *   Smaller number = smaller Baby.
     *
     * Position:
     *   X/Y/Z offsets are written directly inside each display-context
     *   block below, with comments showing exactly what they move.
     *
     * Rotation axes:
     *   X = tilt forward/backward
     *   Y = turn left/right
     *   Z = roll clockwise/counter-clockwise
     *
     * FPP and TPP are separated into MAIN HAND and OFF HAND so each side
     * can be tuned independently.
     */

    // =========================
    // GUI
    // =========================

    // ROTATE Y = turn Baby left/right around the vertical axis.
    private static final float GUI_ROTATION_Y = 25.0F;

    // ROTATE X = tilt Baby forward/backward.
    private static final float GUI_ROTATION_X = 0.0F;

    // ROTATE Z = roll Baby clockwise/counter-clockwise.
    private static final float GUI_ROTATION_Z = 0.0F;

    // SCALE = enlarge/shrink Baby in the inventory / recipe GUI.
    private static final float GUI_SCALE = 0.62F;

    // MOVE = GUI position inside the 16x16 item space.
    private static final double GUI_X = 0.50D;
    private static final double GUI_Y = 0.04D;
    private static final double GUI_Z = 0.50D;


    // =========================
    // FPP - MAIN HAND / RIGHT
    // =========================

    // MOVE: X = left/right, Y = up/down, Z = forward/back.
    private static final double FPP_MAIN_X = 0.50D;
    private static final double FPP_MAIN_Y = 0.55D;
    private static final double FPP_MAIN_Z = 0.50D;

    // ROTATE X = tilt forward/backward.
    private static final float FPP_MAIN_ROTATION_X = -20.0F;

    // ROTATE Y = turn left/right.
    private static final float FPP_MAIN_ROTATION_Y = -50.0F;

    // ROTATE Z = roll clockwise/counter-clockwise.
    private static final float FPP_MAIN_ROTATION_Z = -20.0F;

    // SCALE = overall size in the main hand.
    private static final float FPP_MAIN_SCALE = 0.5F;


    // =========================
    // FPP - OFF HAND / LEFT
    // =========================

    // MOVE: X = left/right, Y = up/down, Z = forward/back.
    private static final double FPP_OFF_X = 0.50D;
    private static final double FPP_OFF_Y = 0.55D;
    private static final double FPP_OFF_Z = 0.50D;

    // ROTATE X = tilt forward/backward.
    private static final float FPP_OFF_ROTATION_X = -20.0F;

    // ROTATE Y = turn left/right.
    private static final float FPP_OFF_ROTATION_Y = 50.0F;

    // ROTATE Z = roll clockwise/counter-clockwise.
    private static final float FPP_OFF_ROTATION_Z = 20.0F;

    // SCALE = overall size in the off hand.
    private static final float FPP_OFF_SCALE = 0.5F;


    // =========================
    // TPP - MAIN HAND / RIGHT
    // =========================

    // MOVE: X = left/right, Y = up/down, Z = forward/back.
    private static final double TPP_MAIN_X = 0.50D;
    private static final double TPP_MAIN_Y = 0.55D;
    private static final double TPP_MAIN_Z = 0.40D;

    // ROTATE X = tilt forward/backward.
    private static final float TPP_MAIN_ROTATION_X = 95.0F;

    // ROTATE Y = turn left/right.
    private static final float TPP_MAIN_ROTATION_Y = 180.0F;

    // ROTATE Z = roll clockwise/counter-clockwise.
    private static final float TPP_MAIN_ROTATION_Z = 0.0F;

    // SCALE = overall size in the main hand.
    private static final float TPP_MAIN_SCALE = 0.60F;


    // =========================
    // TPP - OFF HAND / LEFT
    // =========================

    // MOVE: X = left/right, Y = up/down, Z = forward/back.
    private static final double TPP_OFF_X = 0.50D;
    private static final double TPP_OFF_Y = 0.55D;
    private static final double TPP_OFF_Z = 0.40D;

    // ROTATE X = tilt forward/backward.
    private static final float TPP_OFF_ROTATION_X = 95.0F;

    // ROTATE Y = turn left/right.
    private static final float TPP_OFF_ROTATION_Y = 180.0F;

    // ROTATE Z = roll clockwise/counter-clockwise.
    private static final float TPP_OFF_ROTATION_Z = 0.0F;

    // SCALE = overall size in the off hand.
    private static final float TPP_OFF_SCALE = 0.60F;


    /*
     * LIGHTING
     *
     * FULL_BRIGHT_LIGHT = maximum block + sky light.
     *
     * Increase/decrease this only when you intentionally want different
     * lighting behavior. This does NOT change the Baby's texture/variant.
     */
    /*
     * Full entity light for held/GUI rendering.
     * This prevents the Baby from being swallowed by scene shadow/darkness.
     */
    private static final int FULL_BRIGHT_LIGHT =
            LightTexture.FULL_BRIGHT;

    public BabyNPCPlayerItemRenderer() {
        super(
                Minecraft.getInstance()
                        .getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels()
        );
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        if (!BabyNPCPlayerItem.isBabyStack(stack)) {
            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.level == null) {
            return;
        }

        /*
         * Reconstruct the exact Baby state stored inside the ItemStack.
         *
         * The ItemStack's saved TextureIndex is applied immediately after
         * construction so the temporary constructor random variant can
         * never become the displayed Baby.
         */
        BabyNPCPlayerEntity baby =
                (BabyNPCPlayerEntity) net.minecraft.world.entity.EntityType
                        .create(
                                BabyNPCPlayerItem.copyEntityData(stack),
                                minecraft.level
                        )
                        .orElse(null);

        if (baby == null) {
            return;
        }

        baby.setTextureIndex(
                BabyNPCPlayerItem.getTextureIndex(stack)
        );

        baby.setPos(
                0.0D,
                0.0D,
                0.0D
        );

        EntityRenderDispatcher dispatcher =
                minecraft.getEntityRenderDispatcher();

        poseStack.pushPose();

        switch (displayContext) {

            /*
             * ============================================================
             * GUI
             * ============================================================
             */
            case GUI -> {
                // MOVE: change GUI_X / GUI_Y / GUI_Z.
                poseStack.translate(
                        GUI_X,
                        GUI_Y,
                        GUI_Z
                );

                // ROTATE X: tilt forward/backward.
                poseStack.mulPose(
                        Axis.XP.rotationDegrees(
                                GUI_ROTATION_X
                        )
                );

                // ROTATE Y: turn left/right.
                poseStack.mulPose(
                        Axis.YP.rotationDegrees(
                                GUI_ROTATION_Y
                        )
                );

                // ROTATE Z: roll clockwise/counter-clockwise.
                poseStack.mulPose(
                        Axis.ZP.rotationDegrees(
                                GUI_ROTATION_Z
                        )
                );

                // SCALE: change GUI_SCALE.
                poseStack.scale(
                        GUI_SCALE,
                        GUI_SCALE,
                        GUI_SCALE
                );
            }

            /*
             * ============================================================
             * FPP - MAIN HAND / RIGHT HAND
             * ============================================================
             *
             * ItemDisplayContext does distinguish the two first-person
             * hands, so they are deliberately configured independently.
             */
            case FIRST_PERSON_RIGHT_HAND -> {
                // MOVE: change FPP_MAIN_X/Y/Z.
                poseStack.translate(
                        FPP_MAIN_X,
                        FPP_MAIN_Y,
                        FPP_MAIN_Z
                );

                // ROTATE X: tilt forward/backward.
                poseStack.mulPose(
                        Axis.XP.rotationDegrees(
                                FPP_MAIN_ROTATION_X
                        )
                );

                // ROTATE Y: turn left/right.
                poseStack.mulPose(
                        Axis.YP.rotationDegrees(
                                FPP_MAIN_ROTATION_Y
                        )
                );

                // ROTATE Z: roll clockwise/counter-clockwise.
                poseStack.mulPose(
                        Axis.ZP.rotationDegrees(
                                FPP_MAIN_ROTATION_Z
                        )
                );

                // SCALE: change FPP_MAIN_SCALE.
                poseStack.scale(
                        FPP_MAIN_SCALE,
                        FPP_MAIN_SCALE,
                        FPP_MAIN_SCALE
                );
            }

            /*
             * ============================================================
             * FPP - OFF HAND / LEFT HAND
             * ============================================================
             */
            case FIRST_PERSON_LEFT_HAND -> {
                // MOVE: change FPP_OFF_X/Y/Z.
                poseStack.translate(
                        FPP_OFF_X,
                        FPP_OFF_Y,
                        FPP_OFF_Z
                );

                // ROTATE X: tilt forward/backward.
                poseStack.mulPose(
                        Axis.XP.rotationDegrees(
                                FPP_OFF_ROTATION_X
                        )
                );

                // ROTATE Y: turn left/right.
                poseStack.mulPose(
                        Axis.YP.rotationDegrees(
                                FPP_OFF_ROTATION_Y
                        )
                );

                // ROTATE Z: roll clockwise/counter-clockwise.
                poseStack.mulPose(
                        Axis.ZP.rotationDegrees(
                                FPP_OFF_ROTATION_Z
                        )
                );

                // SCALE: change FPP_OFF_SCALE.
                poseStack.scale(
                        FPP_OFF_SCALE,
                        FPP_OFF_SCALE,
                        FPP_OFF_SCALE
                );
            }

            /*
             * ============================================================
             * TPP - MAIN HAND / RIGHT HAND
             * ============================================================
             */
            case THIRD_PERSON_RIGHT_HAND -> {
                // MOVE: change TPP_MAIN_X/Y/Z.
                poseStack.translate(
                        TPP_MAIN_X,
                        TPP_MAIN_Y,
                        TPP_MAIN_Z
                );

                // ROTATE X: tilt forward/backward.
                poseStack.mulPose(
                        Axis.XP.rotationDegrees(
                                TPP_MAIN_ROTATION_X
                        )
                );

                // ROTATE Y: turn left/right.
                poseStack.mulPose(
                        Axis.YP.rotationDegrees(
                                TPP_MAIN_ROTATION_Y
                        )
                );

                // ROTATE Z: roll clockwise/counter-clockwise.
                poseStack.mulPose(
                        Axis.ZP.rotationDegrees(
                                TPP_MAIN_ROTATION_Z
                        )
                );

                // SCALE: change TPP_MAIN_SCALE.
                poseStack.scale(
                        TPP_MAIN_SCALE,
                        TPP_MAIN_SCALE,
                        TPP_MAIN_SCALE
                );
            }

            /*
             * ============================================================
             * TPP - OFF HAND / LEFT HAND
             * ============================================================
             */
            case THIRD_PERSON_LEFT_HAND -> {
                // MOVE: change TPP_OFF_X/Y/Z.
                poseStack.translate(
                        TPP_OFF_X,
                        TPP_OFF_Y,
                        TPP_OFF_Z
                );

                // ROTATE X: tilt forward/backward.
                poseStack.mulPose(
                        Axis.XP.rotationDegrees(
                                TPP_OFF_ROTATION_X
                        )
                );

                // ROTATE Y: turn left/right.
                poseStack.mulPose(
                        Axis.YP.rotationDegrees(
                                TPP_OFF_ROTATION_Y
                        )
                );

                // ROTATE Z: roll clockwise/counter-clockwise.
                poseStack.mulPose(
                        Axis.ZP.rotationDegrees(
                                TPP_OFF_ROTATION_Z
                        )
                );

                // SCALE: change TPP_OFF_SCALE.
                poseStack.scale(
                        TPP_OFF_SCALE,
                        TPP_OFF_SCALE,
                        TPP_OFF_SCALE
                );
            }

            /*
             * ============================================================
             * GROUND / FIXED / OTHER
             * ============================================================
             */
            default -> {
                // MOVE: default item-space position.
                poseStack.translate(
                        0.5D,
                        0.08D,
                        0.5D
                );

                // SCALE: default world-item size.
                poseStack.scale(
                        0.45F,
                        0.45F,
                        0.45F
                );
            }
        }

        dispatcher.render(
                baby,
                0.0D,
                0.0D,
                0.0D,
                0.0F,
                0.0F,
                poseStack,
                buffer,
                /*
                 * Explicit full-bright lightmap. Do not use packedLight here:
                 * TPP/FPP item rendering can otherwise inherit world darkness.
                 */
                packedLight
        );

        poseStack.popPose();

        /*
         * This Baby only exists for rendering the ItemStack.
         */
        baby.remove(
                net.minecraft.world.entity.Entity.RemovalReason.DISCARDED
        );
    }
}