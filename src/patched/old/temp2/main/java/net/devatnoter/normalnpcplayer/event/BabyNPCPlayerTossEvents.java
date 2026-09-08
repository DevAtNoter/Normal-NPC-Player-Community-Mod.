package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class BabyNPCPlayerTossEvents {

    private BabyNPCPlayerTossEvents() {
    }

    /**
     * Server-side fallback only. The Player.drop mixin is the primary guard.
     */
    @SubscribeEvent
    public static void onBabyToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        ItemEntity entity = event.getEntity();
        ItemStack stack = entity.getItem();

        if (!BabyNPCPlayerItem.isBabyStack(stack)) {
            return;
        }

        event.setCanceled(true);
        entity.discard();

        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        } else if (player.getOffhandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.OFF_HAND, stack.copy());
        }

        player.containerMenu.broadcastChanges();

        player.displayClientMessage(
                Component.literal(
                        "Never drop baby down like that again."
                ),
                true
        );
    }
}
