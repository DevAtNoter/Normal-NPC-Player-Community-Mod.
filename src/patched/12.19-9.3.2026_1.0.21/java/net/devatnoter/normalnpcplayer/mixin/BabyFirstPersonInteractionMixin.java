package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the local player right-click blocks/items behind their own Baby
 * passenger in first person.
 *
 * The Baby remains a real passenger/entity. We only replace the local
 * first-person hit result when vanilla's pick selected that passenger,
 * then perform the normal block ray trace from the camera to the same reach.
 */
@Mixin(GameRenderer.class)
public abstract class BabyFirstPersonInteractionMixin {


    private void normalnpcplayer$passRightClickThroughOwnBaby(
            float partialTick,
            CallbackInfo ci
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.level == null) {
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

        if (baby.getVehicle() != minecraft.player
                || !minecraft.player.hasPassenger(baby)) {
            return;
        }

        double reach = minecraft.gameMode.getPickRange();
        Vec3 start = minecraft.player.getEyePosition(partialTick);
        Vec3 direction = minecraft.player.getViewVector(partialTick);
        Vec3 end = start.add(direction.scale(reach));

        BlockHitResult blockHit = minecraft.level.clip(
                new ClipContext(
                        start,
                        end,
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        minecraft.player
                )
        );

        minecraft.hitResult = blockHit;
    }
}
