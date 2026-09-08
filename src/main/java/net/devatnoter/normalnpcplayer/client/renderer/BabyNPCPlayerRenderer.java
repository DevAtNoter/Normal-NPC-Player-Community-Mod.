package net.devatnoter.normalnpcplayer.client.renderer;

import net.devatnoter.normalnpcplayer.client.model.BabyNPCPlayerModel;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;

public class BabyNPCPlayerRenderer extends GeoEntityRenderer<BabyNPCPlayerEntity> {


    /**
     * The passenger Entity position includes the model-origin correction.
     * Do not apply a second visual-only Y offset here: the hitbox and model
     * must move together when the Baby rides on a Player's head.
     */
    public BabyNPCPlayerRenderer(EntityRendererProvider.Context context) {
        super(context, new BabyNPCPlayerModel());
        /*
         * ============================================================
         * HELD ITEMS
         * ============================================================
         * GeckoLib 4 API for Minecraft 1.20.1.
         *
         * The actual Baby arm bones are used directly as the hand
         * attachment points. There are no extra RightHandItem /
         * LeftHandItem bones in the model. Minecraft's ItemRenderer
         * remains responsible for the vanilla item display transform.
         */
        this.addRenderLayer(new BlockAndItemGeoLayer<BabyNPCPlayerEntity>(this) {
            @Override
            protected ItemStack getStackForBone(
                    GeoBone bone,
                    BabyNPCPlayerEntity baby
            ) {
                return switch (bone.getName()) {
                    case "right_arm" -> baby.getMainArm() == HumanoidArm.RIGHT
                            ? baby.getItemBySlot(EquipmentSlot.MAINHAND)
                            : baby.getItemBySlot(EquipmentSlot.OFFHAND);
                    case "left_arm" -> baby.getMainArm() == HumanoidArm.LEFT
                            ? baby.getItemBySlot(EquipmentSlot.MAINHAND)
                            : baby.getItemBySlot(EquipmentSlot.OFFHAND);
                    default -> super.getStackForBone(bone, baby);
                };
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(
                    GeoBone bone,
                    ItemStack stack,
                    BabyNPCPlayerEntity baby
            ) {
                if ("right_arm".equals(bone.getName())) {
                    return baby.getMainArm() == HumanoidArm.RIGHT
                            ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                            : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                }

                if ("left_arm".equals(bone.getName())) {
                    // IMPORTANT: keep the real vanilla LEFT-HAND display context.
                    // Item models can define separate third-person left/right
                    // transforms, and Forge passes the left-hand flag into the
                    // baked-model transform pipeline. Do not force the right-hand
                    // context here, otherwise items such as tools, tridents and
                    // shields can use the wrong hand transform.
                    return ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                }

                return super.getTransformTypeForStack(bone, stack, baby);
            }

            @Override
            protected void renderStackForBone(
                    PoseStack poseStack,
                    GeoBone bone,
                    ItemStack stack,
                    BabyNPCPlayerEntity baby,
                    MultiBufferSource bufferSource,
                    float partialTick,
                    int packedLight,
                    int packedOverlay
            ) {
                final boolean leftHand = "left_arm".equals(bone.getName());
                final ItemDisplayContext displayContext = getTransformTypeForStack(bone, stack, baby);

                // Move both held items from the arm pivot down to the
                // fingertips (4 model units = 0.25 block), then keep the
                // existing forward-falling X rotation.
                poseStack.translate(0.0D, -0.25D, 0.0D);
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));

                Minecraft.getInstance().getItemRenderer().renderStatic(
                        baby, stack, displayContext, leftHand, poseStack, bufferSource,
                        baby.level(), packedLight, packedOverlay, baby.getId()
                );
            }
        });

        /*
         * ============================================================
         * VANILLA ARMOR
         * ============================================================
         * GeckoLib 4 ItemArmorGeoLayer renders vanilla ArmorItem models
         * and textures on GeoBones. It uses the first cube of each mapped
         * GeoBone to derive the armor position/scale.
         *
         * Skin UV is completely independent from armor UV.
         */
        this.addRenderLayer(new ItemArmorGeoLayer<BabyNPCPlayerEntity>(this) {
            @Override
            protected EquipmentSlot getEquipmentSlotForBone(
                    GeoBone bone,
                    ItemStack stack,
                    BabyNPCPlayerEntity baby
            ) {
                return switch (bone.getName()) {
                    case "armor_head" -> EquipmentSlot.HEAD;
                    case "armor_body", "armor_left_arm", "armor_right_arm" -> EquipmentSlot.CHEST;
                    case "armor_left_leg", "armor_right_leg" -> EquipmentSlot.LEGS;
                    default -> super.getEquipmentSlotForBone(bone, stack, baby);
                };
            }

            @Override
            protected ModelPart getModelPartForBone(
                    GeoBone bone,
                    EquipmentSlot slot,
                    ItemStack stack,
                    BabyNPCPlayerEntity baby,
                    HumanoidModel<?> baseModel
            ) {
                return switch (bone.getName()) {
                    case "armor_head" -> baseModel.head;
                    case "armor_body" -> baseModel.body;
                    case "armor_left_arm" -> baseModel.leftArm;
                    case "armor_right_arm" -> baseModel.rightArm;
                    case "armor_left_leg" -> baseModel.leftLeg;
                    case "armor_right_leg" -> baseModel.rightLeg;
                    default -> super.getModelPartForBone(
                            bone, slot, stack, baby, baseModel
                    );
                };
            }

            @Override
            protected @Nullable ItemStack getArmorItemForBone(
                    GeoBone bone,
                    BabyNPCPlayerEntity baby
            ) {
                return switch (bone.getName()) {
                    case "armor_head" -> baby.getItemBySlot(EquipmentSlot.HEAD);
                    case "armor_body", "armor_left_arm", "armor_right_arm" ->
                            baby.getItemBySlot(EquipmentSlot.CHEST);
                    case "armor_left_leg", "armor_right_leg" ->
                            baby.getItemBySlot(EquipmentSlot.LEGS);
                    default -> super.getArmorItemForBone(bone, baby);
                };
            }
        });

        /*
         * Boots are a separate pass because the Baby model has leg bones
         * but no dedicated foot bones. The same leg bones can therefore
         * carry both the leggings and feet armor passes.
         */
        this.addRenderLayer(new ItemArmorGeoLayer<BabyNPCPlayerEntity>(this) {
            @Override
            protected EquipmentSlot getEquipmentSlotForBone(
                    GeoBone bone,
                    ItemStack stack,
                    BabyNPCPlayerEntity baby
            ) {
                return switch (bone.getName()) {
                    case "armor_left_leg", "armor_right_leg" -> EquipmentSlot.FEET;
                    default -> super.getEquipmentSlotForBone(bone, stack, baby);
                };
            }

            @Override
            protected ModelPart getModelPartForBone(
                    GeoBone bone,
                    EquipmentSlot slot,
                    ItemStack stack,
                    BabyNPCPlayerEntity baby,
                    HumanoidModel<?> baseModel
            ) {
                return switch (bone.getName()) {
                    case "armor_left_leg" -> baseModel.leftLeg;
                    case "armor_right_leg" -> baseModel.rightLeg;
                    default -> super.getModelPartForBone(
                            bone, slot, stack, baby, baseModel
                    );
                };
            }

            @Override
            protected @Nullable ItemStack getArmorItemForBone(
                    GeoBone bone,
                    BabyNPCPlayerEntity baby
            ) {
                return switch (bone.getName()) {
                    case "armor_left_leg", "armor_right_leg" ->
                            baby.getItemBySlot(EquipmentSlot.FEET);
                    default -> super.getArmorItemForBone(bone, baby);
                };
            }
        });

        /*
         * ============================================================
         * ELYTRA
         * ============================================================
         * Vanilla ElytraLayer cannot use a GeckoLib renderer directly
         * because it requires a vanilla EntityModel parent. The custom
         * GeckoLib layer below attaches the real vanilla ElytraModel to the
         * dedicated empty `elytra` attachment bone in the Baby model.
         */
        this.addRenderLayer(new BabyElytraGeoLayer(this, context.getModelSet()));

        this.shadowRadius = 0.35F;
    }

    /**
     * Vanilla Elytra model attached from the Baby body bone. ElytraModel.setupAnim() is the
     * vanilla animation source, so fall-flying opens the wings exactly from
     * the entity's vanilla Elytra pose state.
     *
     * The Baby chest bone is 6 model units wide while the vanilla humanoid
     * chest reference is 8 units wide, matching the 0.75 width ratio used
     * here. Keep the Elytra uniformly scaled so the vanilla wing geometry is
     * not distorted like a per-axis armor fit would be.
     */
    @Override
    public void render(
            BabyNPCPlayerEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        // Do not add a renderer-only vertical offset here. The authoritative
        // head position is the Baby Entity/AABB itself, set by
        // PlayerHeadPassengerMixin. This keeps walking and head-riding
        // geometry consistent.
        //
        // EXPERIMENT: GeckoLib treats a LivingEntity passenger specially when
        // calculating the render yaw. It can derive that yaw from the vehicle's
        // body rotation, which is exactly the Player preview rotation driven by
        // the inventory mouse. For the inventory preview only, temporarily
        // detach the Baby from its vehicle for this render call. This leaves
        // BabyNPCPlayerEntity and the real gameplay riding system untouched,
        // while making GeckoLib calculate the Baby's yaw from the Baby itself.
        final Minecraft minecraft = Minecraft.getInstance();
        final boolean previewScreen = minecraft.screen instanceof InventoryScreen
                || minecraft.screen instanceof CreativeModeInventoryScreen;
        final Entity savedVehicle = previewScreen ? entity.getVehicle() : null;

        if (savedVehicle != null) {
            entity.stopRiding();
        }

        try {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        } finally {
            if (savedVehicle != null && entity.getVehicle() == null && !entity.isRemoved()) {
                entity.startRiding(savedVehicle, true);
            }
        }

    }


}
