package net.devatnoter.normalnpcplayer.client.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the Baby's health using a single Minecraft HUD heart icon.
 *
 * Minecraft 1.20.1 uses:
 * minecraft:textures/gui/icons.png
 *
 * The heart icon changes depending on whether the current world is Hardcore.
 */
public final class BabyHealthTooltipClientComponent implements ClientTooltipComponent {

    private static final ResourceLocation GUI_ICONS =
            new ResourceLocation("minecraft", "textures/gui/icons.png");

    private static final int ICON_SIZE = 9;
    private static final int TEXT_GAP = 3;

    /*
     * Minecraft 1.20.1 icons.png
     *
     * Normal hearts:
     *   Full = U52, V0
     *   Half = U61, V0
     *
     * Hardcore hearts:
     *   Full = U52, V45
     *   Half = U61, V45
     *
     * Empty/container is intentionally not rendered.
     */
    private static final int NORMAL_FULL_U = 52;
    private static final int NORMAL_HALF_U = 61;
    private static final int NORMAL_V = 0;

    private static final int HARDCORE_FULL_U = 52;
    private static final int HARDCORE_HALF_U = 61;
    private static final int HARDCORE_V = 45;

    private final float health;
    private final float maxHealth;

    public BabyHealthTooltipClientComponent(
            BabyHealthTooltipComponent component
    ) {
        this.health = Math.max(0.0F, component.health());
        this.maxHealth = Math.max(1.0F, component.maxHealth());
    }

    @Override
    public int getHeight() {
        return ICON_SIZE;
    }

    @Override
    public int getWidth(Font font) {
        return ICON_SIZE
                + TEXT_GAP
                + font.width(formatHealth());
    }

    @Override
    public void renderImage(
            Font font,
            int x,
            int y,
            GuiGraphics graphics
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        boolean hardcore =
                minecraft.level != null
                        && minecraft.level.getLevelData().isHardcore();

        /*
         * One heart = 2 HP.
         *
         * We only render ONE heart icon.
         * The actual HP value is shown as text beside it.
         */
        boolean halfHeart = health > 0.0F && health < 2.0F;

        int heartU;
        int heartV;

        if (hardcore) {
            heartU = halfHeart
                    ? HARDCORE_HALF_U
                    : HARDCORE_FULL_U;

            heartV = HARDCORE_V;
        } else {
            heartU = halfHeart
                    ? NORMAL_HALF_U
                    : NORMAL_FULL_U;

            heartV = NORMAL_V;
        }

        /*
         * Render exactly ONE vanilla Minecraft heart.
         */
        graphics.blit(
                GUI_ICONS,
                x,
                y,
                ICON_SIZE,
                ICON_SIZE,
                heartU,
                heartV,
                ICON_SIZE,
                ICON_SIZE,
                256,
                256
        );

        /*
         * Render:
         *
         * 18.0 / 20.0 HP
         */
        graphics.drawString(
                font,
                Component.literal(formatHealth()),
                x + ICON_SIZE + TEXT_GAP,
                y + 1,
                0xFFFFFFFF,
                true
        );
    }

    private String formatHealth() {
        return String.format(
                java.util.Locale.ROOT,
                "%.1f / %.1f HP",
                Math.min(health, maxHealth),
                maxHealth
        );
    }
}