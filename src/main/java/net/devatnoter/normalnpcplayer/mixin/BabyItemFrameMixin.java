package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hard guard: a Baby NPC Player carrier can never be inserted into an
 * ItemFrame or Glow ItemFrame (GlowItemFrame inherits ItemFrame).
 */
@Mixin(ItemFrame.class)
public abstract class BabyItemFrameMixin {

    @Inject(
            method = "interact",
            at = @At("HEAD"),
            cancellable = true
    )
    private void normalnpcplayer$blockBabyInteraction(
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        ItemStack stack = player.getItemInHand(hand);

        if (stack.getItem() instanceof BabyNPCPlayerItem) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
