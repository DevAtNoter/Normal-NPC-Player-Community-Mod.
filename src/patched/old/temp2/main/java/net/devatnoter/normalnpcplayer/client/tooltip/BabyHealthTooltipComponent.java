package net.devatnoter.normalnpcplayer.client.tooltip;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** Immutable Baby Item data snapshot used by the client tooltip renderer. */
public record BabyHealthTooltipComponent(CompoundTag data) implements TooltipComponent {
    public BabyHealthTooltipComponent {
        data = data.copy();
    }
}
