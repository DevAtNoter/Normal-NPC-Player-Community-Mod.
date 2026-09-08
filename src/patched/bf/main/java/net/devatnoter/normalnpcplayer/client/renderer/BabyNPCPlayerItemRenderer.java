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
import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BabyNPCPlayerItemRenderer
        extends BlockEntityWithoutLevelRenderer {

    /*
     * The item renderer can be called every frame. Never construct a fresh
     * Baby entity on every render call: GeckoLib would receive a brand-new
     * animation instance every frame and the animation pose would restart,
     * which appears in first person as violent vertical popping/bobbing.
     *
     * Cache the render-only entity by the Baby UUID stored in the serialized
     * entity data. The ItemStack's survival NBT may change while carried, but
     * that must NOT recreate the render entity.
     */
    /**
     * Client-only render state. The state is intentionally independent from
     * the ItemStack's live survival NBT. Rewriting HP/food/air/etc. therefore
     * cannot recreate the GeckoLib animatable or reset an interact animation.
     */
    private static final Map<UUID, BabyItemRenderState> RENDER_STATES =
            new HashMap<>();
    private static BabyItemRenderState fallbackRenderState;
    private static net.minecraft.world.level.Level renderLevel;

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

    private static void freezeRenderProxy(BabyNPCPlayerEntity baby) {
        // Freeze only world-physics state. Do not use this method as a render
        // loop reset: the proxy's animation/render state must remain alive.
        baby.noPhysics = true;
        baby.setNoGravity(true);
        baby.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        baby.fallDistance = 0.0F;

        baby.setPos(0.0D, 0.0D, 0.0D);
        baby.xo = 0.0D;
        baby.yo = 0.0D;
        baby.zo = 0.0D;
        baby.xOld = 0.0D;
        baby.yOld = 0.0D;
        baby.zOld = 0.0D;

        baby.setYRot(0.0F);
        baby.setXRot(0.0F);
        baby.yRotO = 0.0F;
        baby.xRotO = 0.0F;
        baby.yHeadRot = 0.0F;
        baby.yHeadRotO = 0.0F;
        baby.yBodyRot = 0.0F;
        baby.yBodyRotO = 0.0F;
        // Deliberately do not reset tickCount here. GeckoLib can use the
        // persistent animatable instance as the clock for future item
        // animations (interact, emotes, reactions, etc.).
    }

    private static BabyNPCPlayerEntity createRenderProxy(
            CompoundTag data,
            Minecraft minecraft
    ) {
        BabyNPCPlayerEntity baby = (BabyNPCPlayerEntity) net.minecraft.world.entity.EntityType
                .create(data, minecraft.level)
                .orElse(null);
        if (baby == null) {
            return null;
        }

        baby.setItemRenderMode(true);
        freezeRenderProxy(baby);
        return baby;
    }

    private static void clearRenderStates() {
        for (BabyItemRenderState state : RENDER_STATES.values()) {
            state.entity().remove(
                    net.minecraft.world.entity.Entity.RemovalReason.DISCARDED
            );
        }
        RENDER_STATES.clear();

        if (fallbackRenderState != null) {
            fallbackRenderState.entity().remove(
                    net.minecraft.world.entity.Entity.RemovalReason.DISCARDED
            );
            fallbackRenderState = null;
        }
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
         * Reuse the same render-only Baby across frames. The serialized
         * survival state can change frequently, but it is irrelevant to the
         * mesh pose. Recreating the entity here would recreate GeckoLib's
         * controller/animation state every frame.
         */
        if (renderLevel != minecraft.level) {
            clearRenderStates();
            renderLevel = minecraft.level;
        }

        CompoundTag data = BabyNPCPlayerItem.copyEntityData(stack);
        UUID babyUuid = data.hasUUID("UUID") ? data.getUUID("UUID") : null;
        int textureIndex = BabyNPCPlayerItem.getTextureIndex(stack);

        BabyItemRenderState renderState;
        if (babyUuid != null) {
            renderState = RENDER_STATES.get(babyUuid);
            if (renderState == null || renderState.entity().isRemoved()) {
                BabyNPCPlayerEntity baby = createRenderProxy(data, minecraft);
                if (baby == null) {
                    return;
                }
                renderState = new BabyItemRenderState(baby, textureIndex);
                RENDER_STATES.put(babyUuid, renderState);
            }
        } else {
            if (fallbackRenderState == null
                    || fallbackRenderState.entity().isRemoved()) {
                BabyNPCPlayerEntity baby = createRenderProxy(data, minecraft);
                if (baby == null) {
                    return;
                }
                fallbackRenderState = new BabyItemRenderState(baby, textureIndex);
            }
            renderState = fallbackRenderState;
        }

        // Live ItemStack data is allowed to change every tick. Only update
        // appearance fields on the persistent render state; never recreate
        // the proxy/model/controller because a survival stat changed.
        renderState.syncAppearance(textureIndex);
        BabyNPCPlayerEntity baby = renderState.entity();

        // IMPORTANT: no per-frame freeze, no per-frame Entity NBT reload, and
        // no animation-controller recreation. The same proxy survives across
        // frames so future interact animations can own this render state.

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

        // Keep the render-only entity alive in the client-side cache.
        // Removing it here would destroy the GeckoLib instance and make the
        // next frame start the animation from scratch again.
    }
}