package net.devatnoter.normalnpcplayer.client.tooltip;

import com.mojang.datafixers.util.Either;
import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Adds the Baby health HUD-heart line to the Baby Item tooltip. */
@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        value = net.minecraftforge.api.distmarker.Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class BabyHealthTooltipEvents {

    private BabyHealthTooltipEvents() {
    }

    /**
     * Forge 1.20.1 requires an explicit client factory registration.
     * Without this, Forge reaches ClientTooltipComponent.create() and throws
     * "Unknown TooltipComponent" when the Baby Item is hovered.
     */
    @SubscribeEvent
    public static void registerClientTooltipFactory(
            RegisterClientTooltipComponentFactoriesEvent event
    ) {
        event.register(
                BabyHealthTooltipComponent.class,
                BabyHealthTooltipClientComponent::new
        );
    }

    /** Adds the data component to Baby Item tooltips. */
    @Mod.EventBusSubscriber(
            modid = NormalNPCPlayer.MOD_ID,
            value = net.minecraftforge.api.distmarker.Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.FORGE
    )
    public static final class GatherEvents {

        private GatherEvents() {
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void gather(RenderTooltipEvent.GatherComponents event) {
            ItemStack stack = event.getItemStack();

            if (!BabyNPCPlayerItem.isBabyStack(stack)) {
                return;
            }

            var data = BabyNPCPlayerItem.copyEntityData(stack);
            float health = data.contains("Health")
                    ? data.getFloat("Health")
                    : 20.0F;

            float maxHealth = 20.0F;

            event.getTooltipElements().add(
                    Either.right(
                            (TooltipComponent) new BabyHealthTooltipComponent(
                                    health,
                                    maxHealth
                            )
                    )
            );
        }
    }
}
