package net.devatnoter.normalnpcplayer.growth;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class BabyGrowthAdvancementEvents {

    private static final ResourceLocation FREE_THE_END =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft",
                    "end/kill_dragon"
            );

    private BabyGrowthAdvancementEvents() {
    }

    @SubscribeEvent
    public static void onAdvancementProgress(
            AdvancementEvent.AdvancementProgressEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // ONLY grant can trigger growth. Revoke can never trigger growth.
        if (event.getProgressType()
                != AdvancementEvent.AdvancementProgressEvent.ProgressType.GRANT) {
            return;
        }

        Advancement advancement = event.getAdvancement();
        if (advancement == null
                || !FREE_THE_END.equals(advancement.getId())) {
            return;
        }

        // The Ender Dragon advancement must actually be complete.
        if (!event.getAdvancementProgress().isDone()) {
            return;
        }

        // One growth per player.
        if (BabyGrowthManager.hasGrowthAlreadyTriggered(player)) {
            return;
        }

        BabyGrowthManager.growOwnedBaby(player);
    }
}
