package net.devatnoter.normalnpcplayer.client;

import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Prevents a carried Baby in the off-hand from being placed by the same
 * physical RMB hold that is being used to place blocks with the main hand.
 *
 * <p>The important distinction is a <em>physical click</em>, not an individual
 * InteractionKeyMappingTriggered event. Minecraft can generate several use
 * interactions while RMB remains held. Therefore the guard is armed once on
 * the RMB press and stays active until that physical press is released.</p>
 *
 * <p>After RMB is released, the next RMB press is a completely new interaction
 * and the Baby may be used normally. This also avoids relying on MAIN_HAND
 * event ordering, which could otherwise make the off-hand Baby fire on a held
 * block-placement input.</p>
 */
@Mod.EventBusSubscriber(
        modid = "normalnpcplayer",
        value = net.minecraftforge.api.distmarker.Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class BabyBlockHoldGuardEvents {

    /** True while the current physical RMB press is using the main-hand block interaction. */
    private static boolean blockPlacementPress;

    private BabyBlockHoldGuardEvents() {
    }

    @SubscribeEvent
    public static void onInteractionKeyMapping(
            InputEvent.InteractionKeyMappingTriggered event
    ) {
        if (!event.isUseItem()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }

        // Arm immediately from the interaction event when the main hand is a
        // block. This fixes the first interaction of a physical RMB hold: the
        // old implementation armed only at ClientTick.END, which was one
        // phase too late for the first off-hand interaction.
        boolean mainHandBlockPlacement =
                minecraft.player.getMainHandItem().getItem() instanceof BlockItem;

        // A HoeItem or ShovelItem is a block-use tool but is not a BlockItem. When
        // the player holds the Baby carrier in OFF_HAND, vanilla/Forge can
        // continue the same held RMB interaction into OFF_HAND after the
        // tool uses the block. That second interaction would call
        // BabyNPCPlayerItem.useOn() and place/remove the carried Baby.
        boolean mainHandHoeUse =
                minecraft.player.getMainHandItem().getItem() instanceof HoeItem;

        boolean mainHandShovelUse =
                minecraft.player.getMainHandItem().getItem() instanceof ShovelItem;

        boolean mainHandBlockInteraction =
                mainHandBlockPlacement || mainHandHoeUse || mainHandShovelUse;

        boolean babyInOffHand = BabyNPCPlayerItem.isBabyStack(
                minecraft.player.getOffhandItem());

        if (event.getHand() == InteractionHand.MAIN_HAND
                && mainHandBlockInteraction
                && babyInOffHand
                && minecraft.options.keyUse.isDown()) {
            blockPlacementPress = true;
            return;
        }

        // If Forge delivers OFF_HAND before MAIN_HAND, the latch does not yet
        // exist. Check the physical RMB state directly so the very first Baby
        // interaction is still suppressed.
        if (event.getHand() == InteractionHand.OFF_HAND
                && babyInOffHand
                && minecraft.options.keyUse.isDown()
                && mainHandBlockInteraction) {
            blockPlacementPress = true;
        }

        if (event.getHand() == InteractionHand.OFF_HAND
                && blockPlacementPress
                && babyInOffHand) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            blockPlacementPress = false;
            return;
        }

        // The tick is now only responsible for ending the physical hold.
        // Arming happens in the interaction event itself, so the first block
        // placement cannot race ahead of the guard.
        if (!minecraft.options.keyUse.isDown()) {
            blockPlacementPress = false;
        }
    }

}
