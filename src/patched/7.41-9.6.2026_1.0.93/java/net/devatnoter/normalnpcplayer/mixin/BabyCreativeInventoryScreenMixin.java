package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.devatnoter.normalnpcplayer.network.ModNetwork;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Creative Inventory Baby click lock + diagnostic logging.
 *
 * IMPORTANT: CreativeModeInventoryScreen overrides slotClicked(), so targeting
 * AbstractContainerScreen here does not reliably intercept Creative clicks.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class BabyCreativeInventoryScreenMixin {

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void normalnpcplayer$debugAndLockBabySlot(
            Slot slot, int button, int modifiers, ClickType clickType, CallbackInfo ci) {

        NormalNPCPlayer.LOGGER.info(
                "[BABY-CREATIVE-DEBUG] slotClicked FIRED | slot={} containerSlot={} item={} clickType={} button={} modifiers={} shift={} ctrl={} alt={}",
                slot == null ? "null" : slot.getClass().getSimpleName(),
                slot == null ? -1 : slot.getContainerSlot(),
                slot == null ? "null" : slot.getItem().getItem(),
                clickType,
                button,
                modifiers,
                Screen.hasShiftDown(),
                Screen.hasControlDown(),
                Screen.hasAltDown()
        );

        if (slot == null || !BabyNPCPlayerItem.isBabyStack(slot.getItem())) {
            NormalNPCPlayer.LOGGER.info("[BABY-CREATIVE-DEBUG] -> hovered slot is NOT Baby; vanilla continues");
            return;
        }

        NormalNPCPlayer.LOGGER.info(
                "[BABY-CREATIVE-DEBUG] -> BABY HIT | stack={} count={} slot={} | decision pending",
                slot.getItem().getItem(),
                slot.getItem().getCount(),
                slot.getContainerSlot()
        );

        // Shift + Right Click is the intentional Baby Inventory opener.
        if (button == 1 && Screen.hasShiftDown()) {
            NormalNPCPlayer.LOGGER.info("[BABY-CREATIVE-DEBUG] -> SHIFT+RIGHT on Baby: sendOpenBabyItemInventory({})", slot.getContainerSlot());
            ModNetwork.sendOpenBabyItemInventory(slot.getContainerSlot());
            ci.cancel();
            return;
        }

        // Preserve existing Ctrl-click equipment handling.
        if (Screen.hasControlDown() && (button == 0 || button == 1)) {
            NormalNPCPlayer.LOGGER.info("[BABY-CREATIVE-DEBUG] -> CTRL click on Baby: ALLOW existing equipment handler");
            return;
        }

        NormalNPCPlayer.LOGGER.info("[BABY-CREATIVE-DEBUG] -> BLOCK vanilla Creative operation on Baby");
        ci.cancel();
    }
}
