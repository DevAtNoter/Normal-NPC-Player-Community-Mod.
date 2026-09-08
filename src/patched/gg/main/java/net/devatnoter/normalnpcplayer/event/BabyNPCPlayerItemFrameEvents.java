package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class BabyNPCPlayerItemFrameEvents {

    private BabyNPCPlayerItemFrameEvents() {
    }

    /**
     * Extra Forge-side protection for ItemFrame and Glow ItemFrame.
     *
     * Check the item type itself. Do not use isBabyStack() here because that
     * method requires BabyEntityData NBT and is intended to validate a
     * serialized baby carrier, not to identify the item for interaction rules.
     */
    @SubscribeEvent
    public static void onItemFrameInteract(
            PlayerInteractEvent.EntityInteract event
    ) {
        if (!(event.getTarget() instanceof ItemFrame)) {
            return;
        }

        ItemStack stack =
                event.getEntity().getItemInHand(event.getHand());

        if (!(stack.getItem() instanceof BabyNPCPlayerItem)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }
}
