package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diagnostic only: confirms which Creative screen method is actually called. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class BabyCreativeClickDebugMixin {

    @Inject(method = "slotClicked", at = @At("TAIL"), require = 0)
    private void normalnpcplayer$debugSlotClickedTail(
            Slot slot, int button, int modifiers,
            net.minecraft.world.inventory.ClickType clickType,
            CallbackInfo ci) {
        NormalNPCPlayer.LOGGER.info(
                "[BABY-CREATIVE-DEBUG] slotClicked TAIL | slot={} containerSlot={} item={} clickType={} button={} modifiers={}",
                slot == null ? "null" : slot.getClass().getSimpleName(),
                slot == null ? -1 : slot.getContainerSlot(),
                slot == null ? "null" : slot.getItem().getItem(),
                clickType,
                button,
                modifiers
        );
    }
}
