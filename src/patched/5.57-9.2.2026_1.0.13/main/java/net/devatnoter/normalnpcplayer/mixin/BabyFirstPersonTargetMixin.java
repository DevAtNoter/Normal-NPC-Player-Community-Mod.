package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Removes the owner's Baby passenger from the local first-person crosshair
 * target without making the Baby globally unpickable.
 *
 * If the Baby was the closest entity hit, we perform the same practical
 * camera ray query again while excluding that Baby. This allows a mob/entity
 * behind the Baby to become the actual target instead of leaving the Baby as
 * a phantom hit target that blocks attacks/interactions.
 */
@Mixin(GameRenderer.class)
public abstract class BabyFirstPersonTargetMixin {

    @Inject(method = "pick", at = @At("TAIL"))
    private void normalnpcplayer$skipOwnPassengerTarget(
            float partialTick,
            CallbackInfo ci
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
            return;
        }

        if (!minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }

        if (!(minecraft.hitResult instanceof EntityHitResult entityHit)) {
            return;
        }

        if (!(entityHit.getEntity() instanceof BabyNPCPlayerEntity baby)) {
            return;
        }

        if (baby.getVehicle() != minecraft.player || !minecraft.player.hasPassenger(baby)) {
            return;
        }

        Vec3 start = minecraft.player.getEyePosition(partialTick);
        Vec3 direction = minecraft.player.getViewVector(partialTick).normalize();
        double reach = minecraft.gameMode.getPickRange();
        Vec3 end = start.add(direction.scale(reach));

        HitResult blockHit = minecraft.level.clip(
                new net.minecraft.world.level.ClipContext(
                        start,
                        end,
                        net.minecraft.world.level.ClipContext.Block.OUTLINE,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        minecraft.player
                )
        );

        EntityHitResult nearestEntityHit = null;
        double nearestEntityDistance = Double.MAX_VALUE;

        AABB searchBox = new AABB(start, end).inflate(1.0D);

        for (Entity entity : minecraft.level.getEntities(
                minecraft.player,
                searchBox,
                entity -> entity.isPickable()
                        && entity.isAlive()
                        && entity != baby
                        && !entity.isSpectator()
        )) {
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
            Optional<Vec3> hitPoint = box.clip(start, end);

            if (hitPoint.isEmpty()) {
                continue;
            }

            double distance = start.distanceToSqr(hitPoint.get());
            if (distance < nearestEntityDistance) {
                nearestEntityDistance = distance;
                nearestEntityHit = new EntityHitResult(entity, hitPoint.get());
            }
        }

        if (nearestEntityHit == null) {
            minecraft.hitResult = blockHit;
            return;
        }

        double blockDistance = blockHit.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE
                : start.distanceToSqr(blockHit.getLocation());

        minecraft.hitResult = nearestEntityDistance <= blockDistance
                ? nearestEntityHit
                : blockHit;
    }
}
