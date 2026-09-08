package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Love is a gameplay effect, not a vanilla-style potion visual effect.
 * Force its MobEffectInstance particle visibility off, including instances
 * created by /effect, so the pink potion swirl cannot be synchronized/rendered.
 */
@Mixin(MobEffectInstance.class)
public abstract class LoveEffectInstanceMixin {
    @Shadow @Mutable private boolean visible;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void normalnpcplayer$disableLoveParticles(CallbackInfo ci) {
        MobEffectInstance self = (MobEffectInstance) (Object) this;
        if (self.getEffect() == ModEffects.LOVE.get()) {
            this.visible = false;
        }
    }

    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void normalnpcplayer$hideLovePotionParticles(CallbackInfoReturnable<Boolean> cir) {
        MobEffectInstance self = (MobEffectInstance) (Object) this;
        if (self.getEffect() == ModEffects.LOVE.get()) {
            cir.setReturnValue(false);
        }
    }
}
