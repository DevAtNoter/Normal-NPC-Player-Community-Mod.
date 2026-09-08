package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class BabyItemDropMixin {

    /**
     * Block Player.drop(boolean) before vanilla removes the selected stack
     * from inventory. This prevents the GUI desync/ghost-empty-slot behavior.
     */
    @Inject(
            method = "drop(Z)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void normalnpcplayer$preventBabyDrop(
            boolean dropAll,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Player player = (Player) (Object) this;

        ItemStack main = player.getItemInHand(
                InteractionHand.MAIN_HAND
        );

        ItemStack off = player.getItemInHand(
                InteractionHand.OFF_HAND
        );

        if (!BabyNPCPlayerItem.isBabyStack(main)
                && !BabyNPCPlayerItem.isBabyStack(off)) {
            return;
        }

        player.displayClientMessage(
                Component.literal(
                        "Never drop baby down like that again."
                ),
                true
        );

        cir.setReturnValue(false);
    }
}
