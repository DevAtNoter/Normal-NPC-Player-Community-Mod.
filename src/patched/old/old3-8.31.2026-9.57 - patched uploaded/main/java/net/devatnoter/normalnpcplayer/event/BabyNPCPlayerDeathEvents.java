package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class BabyNPCPlayerDeathEvents {

    private BabyNPCPlayerDeathEvents() {
    }

    @SubscribeEvent
    public static void onPlayerDeathDrops(
            LivingDropsEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }

        event.getDrops().removeIf(
                drop -> BabyNPCPlayerItem.isBabyStack(
                        drop.getItem()
                )
        );
    }
}
