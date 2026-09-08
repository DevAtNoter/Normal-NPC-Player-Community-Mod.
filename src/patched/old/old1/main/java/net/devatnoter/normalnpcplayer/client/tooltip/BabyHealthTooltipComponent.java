package net.devatnoter.normalnpcplayer.client.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Server-neutral data component used only while building the Baby Item tooltip.
 * The client renderer chooses normal or hardcore HUD hearts from the current world.
 */
public record BabyHealthTooltipComponent(float health, float maxHealth) implements TooltipComponent {
}
