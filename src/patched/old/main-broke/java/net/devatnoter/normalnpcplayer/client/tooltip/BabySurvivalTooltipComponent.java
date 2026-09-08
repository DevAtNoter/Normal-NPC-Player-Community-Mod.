package net.devatnoter.normalnpcplayer.client.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** Compact survival snapshot retained for compatibility with the tooltip component system. */
public record BabySurvivalTooltipComponent(
        int hunger,
        int maxHunger,
        int air,
        int maxAir
) implements TooltipComponent {
}
