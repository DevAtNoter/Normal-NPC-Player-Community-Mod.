package net.devatnoter.normalnpcplayer.client.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** One real XP orb frame, animated like a vanilla XP orb instead of cropping the sheet. */
public final class BabyExperienceTooltipClientComponent implements ClientTooltipComponent {
    private static final ResourceLocation XP_ORB =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/experience_orb.png");
    private static final int ICON = 9;

    private final int level;
    private final int totalXp;

    public BabyExperienceTooltipClientComponent(BabyExperienceTooltipComponent component) {
        this.level = Math.max(0, component.level());
        this.totalXp = Math.max(0, component.totalXp());
    }

    @Override
    public int getHeight() {
        return ICON;
    }

    @Override
    public int getWidth(Font font) {
        return ICON + 3 + font.width(format());
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        long tick = Minecraft.getInstance().level == null
                ? System.currentTimeMillis() / 50L
                : Minecraft.getInstance().level.getGameTime();

        // Use one complete 16x16 frame from the 4x4 orb animation sheet.
        int frame = (int) ((tick / 2L) & 15L);
        int u = (frame & 3) * 16;
        int v = ((frame >> 2) & 3) * 16;

        // Subtle vanilla-like shimmer; actual XP gain can later set a dedicated
        // glow timer without changing the tooltip data model.
        float pulse = 0.82F + 0.18F * (float) Math.sin(tick * 0.25D);
        graphics.setColor(1.0F, 1.0F, 1.0F, pulse);
        graphics.blit(XP_ORB, x, y, ICON, ICON, u, v, 16, 16, 64, 64);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        graphics.drawString(font, Component.literal(format()),
                x + ICON + 3, y + 1, 0xFFFFFFFF, true);
    }

    private String format() {
        return "Level: " + level + " [XP: " + totalXp + "]";
    }
}
