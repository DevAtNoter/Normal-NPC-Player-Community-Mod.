package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class BabyFirstPersonUseMixin {

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void normalnpcplayer$passRightClickThroughBaby(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        if (!minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }

        HitResult hit = minecraft.hitResult;

        if (!(hit instanceof EntityHitResult entityHit)) {
            return;
        }

        if (!(entityHit.getEntity() instanceof BabyNPCPlayerEntity baby)) {
            return;
        }

        if (baby.getVehicle() != minecraft.player) {
            return;
        }

        Vec3 start = minecraft.player.getEyePosition(1.0F);
        Vec3 direction = minecraft.player.getViewVector(1.0F);
        double reach = minecraft.gameMode.getPickRange();
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