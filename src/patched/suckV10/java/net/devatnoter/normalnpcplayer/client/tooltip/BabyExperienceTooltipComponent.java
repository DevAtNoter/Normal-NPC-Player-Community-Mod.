package net.devatnoter.normalnpcplayer.client.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

public record BabyExperienceTooltipComponent(int level, int totalXp) implements TooltipComponent {
}
