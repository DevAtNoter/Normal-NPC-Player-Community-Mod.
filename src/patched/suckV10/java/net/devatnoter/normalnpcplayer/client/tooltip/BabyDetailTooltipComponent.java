package net.devatnoter.normalnpcplayer.client.tooltip;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Full Baby detail snapshot used only by the client tooltip renderer.
 * The NBT is copied so the renderer never mutates the Baby Item state.
 */
public record BabyDetailTooltipComponent(CompoundTag data, int viewportHeight, int contentWidth)
        implements TooltipComponent {

    public BabyDetailTooltipComponent {
        data = data.copy();
        viewportHeight = Math.max(160, viewportHeight);
        contentWidth = Math.max(220, contentWidth);
    }
}
