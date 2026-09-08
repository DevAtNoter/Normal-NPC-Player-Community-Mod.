package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides only the local player's Baby passenger while the camera is in first
 * person. The Baby is still rendered normally in third person and to other
 * clients.
 *
 * This is intentionally a render-layer solution, not a transparency trick:
 * an alpha-transparent entity can still participate in depth/raycast-related
 * behaviour. We simply do not submit this one entity to the renderer when it
 * would sit inside the local first-person camera.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class BabyFirstPersonRenderMixin {

    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;DDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void normalnpcplayer$hideLocalHeadPassenger(
            Entity entity,
            double x,
            double y,
            double z,
            float rotation,
            float partialTick,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci
    ) {
        if (!(entity instanceof BabyNPCPlayerEntity baby)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        CameraType cameraType = minecraft.options.getCameraType();
        if (cameraType.isFirstPerson() && baby.getVehicle() == minecraft.player) {
            ci.cancel();
        }
    }
}
