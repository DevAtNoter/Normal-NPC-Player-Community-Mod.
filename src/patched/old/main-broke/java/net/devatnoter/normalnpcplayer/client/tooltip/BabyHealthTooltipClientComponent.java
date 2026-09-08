package net.devatnoter.normalnpcplayer.client.tooltip;

import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

/**
 * Complete Baby Item tooltip renderer.
 *
 * Normal mode is the compact summary. Holding SHIFT switches to a fixed
 * detail viewport. The detail body is clipped and scrollable, so its size
 * never grows beyond the tooltip frame.
 */
public final class BabyHealthTooltipClientComponent implements ClientTooltipComponent {

    private static final ResourceLocation GUI_ICONS =
            new ResourceLocation("minecraft", "textures/gui/icons.png");
    private static final ResourceLocation XP_ORB =
            new ResourceLocation("minecraft", "textures/entity/experience_orb.png");
    private static final ResourceLocation EMPTY_SWORD =
            new ResourceLocation("minecraft", "textures/item/empty_slot_sword.png");
    private static final ResourceLocation EMPTY_SHIELD =
            new ResourceLocation("minecraft", "textures/item/empty_armor_slot_shield.png");

    private static final int ICON_SIZE = 9;
    private static final int TEXT_GAP = 3;
    private static final int LINE_HEIGHT = 10;
    private static final int SUMMARY_SECTION_GAP = 3;
    private static final int EFFECT_HEIGHT = 20;
    private static final int EFFECT_BAR_HEIGHT = 2;
    private static final int EFFECT_ICON_X = 2;
    private static final int EFFECT_CONTENT_X = 24;
    private static final int MAX_HUNGER = 20;

    /*
     * The detail panel is deliberately compact.  Its height is also reduced
     * automatically on small GUI scales so vanilla tooltip positioning can
     * always keep the frame on-screen.
     */
    private static final int DETAIL_VIEWPORT_MAX_HEIGHT = 190;
    private static final int DETAIL_VIEWPORT_MIN_HEIGHT = 120;
    private static final int MAX_SUMMARY_WIDTH = 240;
    private static final int MAX_DETAIL_WIDTH = 240;
    private static final int MAX_SUMMARY_CONTENT_WIDTH = 232;
    private static final int MAX_DETAIL_CONTENT_WIDTH = 232;

    private static final int INNER_PAD = 4;
    private static final int SCROLLBAR_WIDTH = 2;
    private static final int SCROLLBAR_RIGHT = 3;
    private static final int SCROLL_STEP = 20;
    private static final int PAGE_STEP = 90;
    private static final int DETAIL_FOOTER_HEIGHT = 10;
    private static final int EQUIPMENT_ROW_HEIGHT = 18;

    /*
     * Vanilla icons.png coordinates.
     *
     * Food/air coordinates are the legacy 1.20.1 icons.png HUD atlas.
     * Food: empty/full/half = 16/52/61 at v=27.
     * Air: full/empty = 16/25 at v=18.
     */
    private static final int FOOD_EMPTY_U = 16;
    private static final int FOOD_FULL_U = 52;
    private static final int FOOD_HALF_U = 61;
    private static final int FOOD_V = 27;
    private static final int AIR_FULL_U = 16;
    private static final int AIR_V = 18;

    private static final int XP_ORB_SHEET_SIZE = 64;
    private static final int XP_ORB_FRAME_SIZE = 16;

    private static int scrollOffset;
    private static int activeMaxScroll;
    private static String scrollIdentity = "";
    private static String hoverIdentity = "";
    private static int currentTipIndex;
    private static long lastHoverRenderNanos;
    private static final long HOVER_RESTART_NANOS = 500_000_000L;
    private static final Random TIP_RANDOM = new Random();

    private static final Map<String, EffectBarState> EFFECT_BAR_STATES = new HashMap<>();
    private static boolean effectBarStatesLoaded;

    private static final class EffectBarState {
        private int maxDuration;
        private int previousDuration;

        private EffectBarState(int duration) {
            this.maxDuration = Math.max(1, duration);
            this.previousDuration = Math.max(0, duration);
        }
    }

    private static final String[] TIPS = {
            "Every meal helps him grow stronger. Bring him to the End and defeat the Ender Dragon to help him grow up.",
            "Keep him fed and safe. A healthy baby grows stronger as the adventure continues.",
            "Take good care of him. Every journey and every meal brings him closer to growing up.",
            "Bring him along on your adventures and help him reach the Ender Dragon.",
            "A well-fed baby is a happy baby. Protect him while he grows into a strong player."
    };

    private final CompoundTag data;
    private final float health;
    private final float maxHealth;
    private final float absorption;
    private final int armorPoints;
    private final float hunger;
    private final float saturation;
    private final float exhaustion;
    private final int air;
    private final int maxAir;
    private final boolean onFire;
    private final boolean wet;
    private final int level;
    private final int totalXp;
    private final List<MobEffectInstance> effects = new ArrayList<>();

    public BabyHealthTooltipClientComponent(BabyHealthTooltipComponent component) {
        this.data = component.data().copy();
        this.health = Math.max(0.0F, getFloat("Health", 20.0F));
        this.maxHealth = 20.0F;
        this.absorption = Math.max(0.0F, getFloat("AbsorptionAmount", 0.0F));
        this.armorPoints = readArmorPoints();
        this.hunger = Math.max(0.0F, Math.min(20.0F, getFloat("BabyFoodLevel", getFloat("Hunger", 20.0F))));
        this.saturation = Math.max(0.0F, getFloat("Saturation", 5.0F));
        this.exhaustion = Math.max(0.0F, getFloat("Exhaustion", 0.0F));
        this.air = Math.max(0, getInt("Air", 300));
        this.maxAir = Math.max(1, getInt("MaxAir", 300));
        this.onFire = getInt("Fire", 0) > 0;
        this.wet = getBoolean("Wet", false);
        this.level = Math.max(0, getInt("XpLevel", getInt("ExperienceLevel", 0)));
        this.totalXp = Math.max(0, getInt("XpTotal", getInt("TotalExperience", 0)));

        loadEffectBarStates();
        beginHoverSession();

        ListTag activeEffects = data.getList("ActiveEffects", 10);
        for (int i = 0; i < activeEffects.size(); i++) {
            MobEffectInstance effect = MobEffectInstance.load(activeEffects.getCompound(i));
            if (effect != null) {
                effects.add(effect);
            }
        }

        String effectOwner = effectOwnerIdentity();
        Set<String> currentEffectKeys = new HashSet<>();
        for (MobEffectInstance effect : effects) {
            currentEffectKeys.add(effectOwner + "|"
                    + effect.getEffect() + "|" + effect.getAmplifier());
        }
        EFFECT_BAR_STATES.keySet().removeIf(key ->
                key.startsWith(effectOwner + "|") && !currentEffectKeys.contains(key));

        activeMaxScroll = maxScroll(detailLines());
        String identity = data.toString();
        if (!identity.equals(scrollIdentity)) {
            scrollIdentity = identity;
            scrollOffset = 0;
        }
        scrollOffset = clamp(scrollOffset, 0, activeMaxScroll);
    }

    public static void resetScroll() {
        scrollOffset = 0;
        activeMaxScroll = 0;
        scrollIdentity = "";
    }

    public static boolean hasScrollableContent() {
        return activeMaxScroll > 0;
    }

    /** Mouse wheel: positive delta = up, negative delta = down. */
    public static void scroll(double delta) {
        if (activeMaxScroll <= 0) {
            return;
        }
        scrollOffset = clamp(
                scrollOffset - (int) Math.round(delta * SCROLL_STEP),
                0,
                activeMaxScroll
        );
    }

    public static void scrollPage(int direction) {
        if (activeMaxScroll <= 0) {
            return;
        }
        scrollOffset = clamp(
                scrollOffset + direction * PAGE_STEP,
                0,
                activeMaxScroll
        );
    }

    @Override
    public int getHeight() {
        return Screen.hasShiftDown()
                ? detailViewportHeight()
                : summaryHeight();
    }

    @Override
    public int getWidth(Font font) {
        if (!Screen.hasShiftDown()) {
            return Math.min(MAX_SUMMARY_WIDTH, summaryWidth(font));
        }

        int width = 0;
        for (String line : detailLines()) {
            width = Math.max(width, font.width(line));
        }
        return Math.min(MAX_DETAIL_WIDTH, Math.max(220, Math.min(MAX_DETAIL_CONTENT_WIDTH, width + 8) + 8));
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        if (!Screen.hasShiftDown()) {
            renderSummary(font, x, y, graphics);
        } else {
            renderDetails(font, x, y, graphics);
        }
    }

    private void renderSummary(Font font, int x, int y, GuiGraphics graphics) {
        int lineY = y;

        // Keep the ordinary item-tooltip hierarchy: title, description, owner,
        // optional Creative-only entity tag, then the custom status summary.
        String ownerName = data.contains("OwnerName") ? data.getString("OwnerName") : "";
        String title = ownerName.isEmpty() ? "Baby NPC Player" : "Baby of " + ownerName;

        graphics.drawString(font, Component.literal(title), x, lineY, 0xFFFFFFFF, true);
        lineY += LINE_HEIGHT;
        for (String descriptionLine : wrapText(
                font,
                "A living baby NPC Player. Handle with care.",
                MAX_SUMMARY_CONTENT_WIDTH)) {
            graphics.drawString(font, Component.literal(descriptionLine),
                    x, lineY, 0xFFAAAAAA, true);
            lineY += LINE_HEIGHT;
        }

        if (!ownerName.isEmpty()) {
            graphics.drawString(font, Component.literal("Belongs to " + ownerName),
                    x, lineY, 0xFFAAAAAA, true);
            lineY += LINE_HEIGHT;
        }

        // Keep the same section gap before Tip in Survival/normal mode.
        // Creative already gets its gap after the Entity tag below.
        if (!isCreative()) {
            lineY += SUMMARY_SECTION_GAP;
        }

        if (isCreative()) {
            graphics.drawString(font, Component.literal("Entity"),
                    x, lineY, 0xFF5555FF, true);
            lineY += LINE_HEIGHT + SUMMARY_SECTION_GAP;
        }

        graphics.drawString(font, Component.literal("Tip:"), x, lineY, 0xFFFFFFFF, true);
        lineY += LINE_HEIGHT;

        for (String tipLine : tipLines(font, MAX_SUMMARY_CONTENT_WIDTH)) {
            graphics.drawString(font, Component.literal(tipLine), x, lineY, 0xFFAAAAAA, true);
            lineY += LINE_HEIGHT;
        }

        lineY += SUMMARY_SECTION_GAP;
        renderHeartLine(font, x, lineY, graphics);
        lineY += LINE_HEIGHT;

        if (absorption > 0.0F) {
            renderAbsorptionLine(font, x, lineY, graphics);
            lineY += LINE_HEIGHT;
        }

        if (armorPoints > 0) {
            renderArmorLine(font, x, lineY, graphics);
            lineY += LINE_HEIGHT;
        }

        renderHungerLine(font, x, lineY, graphics);
        lineY += LINE_HEIGHT;

        if (air < maxAir) {
            renderOxygenLine(font, x, lineY, graphics);
            lineY += LINE_HEIGHT;
        }

        renderXpLine(font, x, lineY, graphics);
        lineY += LINE_HEIGHT;

        graphics.drawString(font, Component.literal("Hold SHIFT to view details"),
                x, lineY, 0xFFAAAAAA, true);
    }

    private void renderHeartLine(Font font, int x, int y, GuiGraphics graphics) {
        boolean hardcore = Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getLevelData().isHardcore();
        boolean halfHeart = health > 0.0F && health < 2.0F;
        int u = halfHeart ? 61 : 52;
        int v = hardcore ? 45 : 0;

        graphics.blit(
                GUI_ICONS,
                x,
                y,
                0,
                u,
                v,
                ICON_SIZE,
                ICON_SIZE,
                256,
                256
        );
        graphics.drawString(
                font,
                Component.literal(formatHealth()),
                x + ICON_SIZE + TEXT_GAP,
                y + 1,
                0xFFFFFFFF,
                true
        );
    }

    private void renderAbsorptionLine(Font font, int x, int y, GuiGraphics graphics) {
        // One vanilla absorption-heart icon. The value is the temporary
        // absorption health stored by the Baby entity.
        graphics.blit(
                GUI_ICONS, x, y, 0, 88, 0,
                ICON_SIZE, ICON_SIZE, 256, 256
        );
        graphics.drawString(
                font,
                Component.literal(String.format(Locale.ROOT, "%.1f [AB]", absorption)),
                x + ICON_SIZE + TEXT_GAP, y + 1,
                0xFFFFFF55, true
        );
    }

    private void renderArmorLine(Font font, int x, int y, GuiGraphics graphics) {
        // One vanilla armor icon, matching the player's armor HUD.
        graphics.blit(
                GUI_ICONS, x, y, 0, 34, 9,
                ICON_SIZE, ICON_SIZE, 256, 256
        );
        graphics.drawString(
                font,
                Component.literal(armorPoints + " [PT]"),
                x + ICON_SIZE + TEXT_GAP, y + 1,
                0xFFFFFFFF, true
        );
    }

    private int readArmorPoints() {
        int total = 0;
        ListTag armorItems = data.getList("ArmorItems", 10);
        for (int i = 0; i < armorItems.size(); i++) {
            ItemStack stack = ItemStack.of(armorItems.getCompound(i));
            if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armorItem)) {
                continue;
            }
            total += armorItem.getDefense();
        }
        return Math.max(0, total);
    }

    private void renderHungerLine(Font font, int x, int y, GuiGraphics graphics) {
        /*
         * Summary uses ONE vanilla food icon. The numeric value remains the
         * authoritative bar state, while the icon changes between empty,
         * half and full exactly like the corresponding HUD sprite state.
         */
        boolean hungerEffect = hasEffect("minecraft:hunger");
        int emptyU = hungerEffect ? 133 : FOOD_EMPTY_U;
        int fullU = hungerEffect ? 88 : FOOD_FULL_U;
        int halfU = hungerEffect ? 97 : FOOD_HALF_U;

        int u;
        if (hunger <= 0) {
            u = emptyU;
        } else if (hunger * 2 < MAX_HUNGER) {
            u = halfU;
        } else {
            u = fullU;
        }

        graphics.blit(
                GUI_ICONS, x, y, 0, u, FOOD_V,
                ICON_SIZE, ICON_SIZE, 256, 256
        );
        graphics.drawString(
                font,
                Component.literal(String.format(Locale.ROOT,
                        "Hunger: %.1f / 20.0 [FL]", hunger)),
                x + ICON_SIZE + TEXT_GAP, y + 1,
                0xFFFFFFFF, true
        );
    }

    private void renderOxygenLine(Font font, int x, int y, GuiGraphics graphics) {
        /* One vanilla air-bubble icon; the number carries the current value. */
        int u = air <= 0 ? 25 : AIR_FULL_U;
        graphics.blit(
                GUI_ICONS, x, y, 0, u, AIR_V,
                ICON_SIZE, ICON_SIZE, 256, 256
        );
        graphics.drawString(
                font,
                Component.literal("Oxygen: " + air + " / " + maxAir + " [AIR]"),
                x + ICON_SIZE + TEXT_GAP, y + 1,
                0xFFFFFFFF, true
        );
    }

    private void renderXpLine(Font font, int x, int y, GuiGraphics graphics) {
        long tick = Minecraft.getInstance().level == null
                ? System.currentTimeMillis() / 50L
                : Minecraft.getInstance().level.getGameTime();

        /*
         * experience_orb.png is a real 4x4 animation sheet.  Pick exactly one
         * 16x16 frame so the icon remains a straight, complete orb.
         */
        int frame = (int) ((tick / 2L) & 15L);
        int u = (frame & 3) * 16;
        int v = ((frame >> 2) & 3) * 16;

        /*
         * Vanilla's orb texture is effectively a light texture which is
         * tinted by the entity renderer.  Reproduce that visual language here
         * instead of leaving the raw white texture.
         *
         * The tint slowly cycles between XP-green and yellow-green, while
         * brightness pulses to give the small "XP absorbed" shimmer.
         */
        float cycle = 0.5F + 0.5F * (float) Math.sin(tick * 0.12D);
        float pulse = 0.84F + 0.16F * (float) Math.sin(tick * 0.30D);

        float red = (0.22F + 0.42F * cycle) * pulse;
        float green = 1.00F * pulse;
        float blue = (0.18F - 0.08F * cycle) * pulse;

        /*
         * Small translucent halo first, then the real 9x9 orb.  This is kept
         * subtle so it reads as shimmer rather than a glowing UI button.
         */
        graphics.setColor(red, green, blue, 0.16F);
        graphics.blit(
                XP_ORB,
                x - 1,
                y - 1,
                ICON_SIZE + 2,
                ICON_SIZE + 2,
                u,
                v,
                XP_ORB_FRAME_SIZE,
                XP_ORB_FRAME_SIZE,
                XP_ORB_SHEET_SIZE,
                XP_ORB_SHEET_SIZE
        );

        graphics.setColor(red, green, blue, 1.0F);
        graphics.blit(
                XP_ORB,
                x,
                y,
                ICON_SIZE,
                ICON_SIZE,
                u,
                v,
                XP_ORB_FRAME_SIZE,
                XP_ORB_FRAME_SIZE,
                XP_ORB_SHEET_SIZE,
                XP_ORB_SHEET_SIZE
        );

        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        graphics.drawString(
                font,
                Component.literal("Level: " + level + " [XP: " + totalXp + "]"),
                x + ICON_SIZE + TEXT_GAP,
                y + 1,
                0xFFFFFFFF,
                true
        );
    }

    private int detailViewportHeight() {
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        /*
         * Keep the custom viewport small enough that the vanilla tooltip
         * positioner can place the whole frame around the hovered item/cursor
         * instead of being forced against a screen corner.
         */
        return clamp(
                screenHeight - 90,
                DETAIL_VIEWPORT_MIN_HEIGHT,
                Math.min(DETAIL_VIEWPORT_MAX_HEIGHT, Math.max(DETAIL_VIEWPORT_MIN_HEIGHT, screenHeight - 70))
        );
    }

    private void renderDetails(Font font, int x, int y, GuiGraphics graphics) {
        List<String> lines = detailLines();
        activeMaxScroll = maxScroll(lines);
        scrollOffset = clamp(scrollOffset, 0, activeMaxScroll);

        int viewportWidth = getWidth(font);
        int contentLeft = x + INNER_PAD;
        int contentRight = x + viewportWidth
                - SCROLLBAR_WIDTH - SCROLLBAR_RIGHT - INNER_PAD;
        int viewportTop = y + 1;
        int viewportBottom = y + detailViewportHeight() - DETAIL_FOOTER_HEIGHT;

        graphics.enableScissor(
                contentLeft,
                viewportTop,
                Math.max(contentLeft + 1, contentRight),
                viewportBottom
        );

        int lineY = viewportTop + INNER_PAD - scrollOffset;
        int effectHeader = lines.indexOf("[ Active Effects ]");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean effectRow = !effects.isEmpty()
                    && effectHeader >= 0
                    && i > effectHeader
                    && i <= effectHeader + effects.size();
            boolean equipmentRow = line.startsWith("__EQUIPMENT__");
            int rowHeight = effectRow ? EFFECT_HEIGHT
                    : equipmentRow ? EQUIPMENT_ROW_HEIGHT
                      : LINE_HEIGHT;

            if (lineY + rowHeight >= viewportTop && lineY <= viewportBottom) {
                if (effectRow) {
                    int effectIndex = i - effectHeader - 1;
                    MobEffectInstance effect = effects.get(effectIndex);
                    TextureAtlasSprite sprite = Minecraft.getInstance()
                            .getMobEffectTextures()
                            .get(effect.getEffect());
                    if (sprite != null) {
                        // Keep the 18x18 vanilla effect icon centered in the row,
                        // with enough left padding so it does not touch the frame.
                        graphics.blit(contentLeft + 4, lineY + 1, 0, 18, 18, sprite);
                    }

                    String name = Component.translatable(effect.getDescriptionId()).getString();
                    String roman = switch (effect.getAmplifier()) {
                        case 0 -> "I";
                        case 1 -> "II";
                        case 2 -> "III";
                        case 3 -> "IV";
                        case 4 -> "V";
                        default -> String.valueOf(effect.getAmplifier() + 1);
                    };
                    String duration = formatDuration(effect.getDuration());

// Text starts after the icon.
// Keep the name bright like vanilla and the remaining time gray.
                    int textX = x + 28;

                    graphics.drawString(
                            font,
                            Component.literal(name + " " + roman),
                            textX,
                            lineY,
                            0xFFFFFFFF,
                            true
                    );

                    graphics.drawString(
                            font,
                            Component.literal(duration),
                            textX,
                            lineY + 9,
                            0xFFAAAAAA,
                            true
                    );

// Remaining-duration bar.
// Its right edge moves left as the effect expires.
// Its color follows the potion effect.
                    String barKey = effectBarKey(effect);
                    EffectBarState state = EFFECT_BAR_STATES.get(barKey);
                    boolean stateChanged = false;

                    if (state == null) {
                        state = new EffectBarState(effect.getDuration());
                        EFFECT_BAR_STATES.put(barKey, state);
                        stateChanged = true;
                    }

                    int currentDuration = Math.max(0, effect.getDuration());

// The frozen effect duration is authoritative.
// Never advance the timer inside the tooltip itself.
                    if (currentDuration > state.previousDuration) {
                        state.maxDuration = Math.max(1, currentDuration);
                        stateChanged = true;
                    }

                    if (currentDuration != state.previousDuration) {
                        state.previousDuration = currentDuration;
                        stateChanged = true;
                    }

                    if (stateChanged) {
                        saveEffectBarStates();
                    }

// Leave a little space on both sides of the timing bar.
                    int barLeft = textX;
                    int barRight = contentRight - 8;
                    int barWidth = Math.max(0, barRight - barLeft);

                    int remainingWidth = (int) Math.round(
                            barWidth * (
                                    currentDuration
                                            / (double) Math.max(1, state.maxDuration)
                            )
                    );

// Small dark track behind the colored timing bar.
                    graphics.fill(
                            barLeft,
                            lineY + EFFECT_HEIGHT - EFFECT_BAR_HEIGHT,
                            barRight,
                            lineY + EFFECT_HEIGHT,
                            0x55333333
                    );

// Colored portion.
// The right edge moves left as the remaining duration decreases.
                    if (remainingWidth > 0) {
                        int effectColor = effect.getEffect().getColor();

                        graphics.fill(
                                barLeft,
                                lineY + EFFECT_HEIGHT - EFFECT_BAR_HEIGHT,
                                barLeft + remainingWidth,
                                lineY + EFFECT_HEIGHT,
                                0xFF000000 | (effectColor & 0xFFFFFF)
                        );
                    }
                } else if (line.startsWith("__EQUIPMENT__")) {
                    renderEquipmentRow(font, graphics, contentLeft, lineY, line);
                } else {
                    int lineColor;
                    if (line.startsWith("  Your blood Line:")) {
                        // Blood-line status is intentionally red.
                        lineColor = 0xFFFF5555;
                    } else if (line.startsWith("  Adopted:")) {
                        // Adoption status is intentionally orange.
                        lineColor = 0xFFFFAA00;
                    } else {
                        lineColor = line.startsWith("[") ? 0xFFFFFFFF : 0xFFCCCCCC;
                    }

                    graphics.drawString(
                            font,
                            Component.literal(line),
                            contentLeft,
                            lineY,
                            lineColor,
                            true
                    );
                }
            }

            lineY += rowHeight;
        }

        graphics.disableScissor();
        renderScrollbar(font, graphics, x, viewportTop, viewportBottom, viewportWidth);
    }

    private void renderScrollbar(
            Font font,
            GuiGraphics graphics,
            int x,
            int top,
            int bottom,
            int viewportWidth
    ) {
        if (activeMaxScroll <= 0) {
            return;
        }

        int trackX = x + viewportWidth - SCROLLBAR_WIDTH - SCROLLBAR_RIGHT;
        int trackTop = top + 1;
        int trackBottom = bottom - 1;
        int trackHeight = Math.max(8, trackBottom - trackTop);

        int contentHeight = trackHeight + activeMaxScroll;
        int thumbHeight = Math.max(
                16,
                (int) ((long) trackHeight * trackHeight / Math.max(1, contentHeight))
        );
        int travel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = trackTop + (int) ((long) travel * scrollOffset
                / Math.max(1, activeMaxScroll));

        graphics.fill(
                trackX,
                trackTop,
                trackX + SCROLLBAR_WIDTH,
                trackBottom,
                0x55333333
        );
        graphics.fill(
                trackX,
                thumbY,
                trackX + SCROLLBAR_WIDTH,
                thumbY + thumbHeight,
                0xFFAAAAAA
        );

        String hint = scrollOffset <= 0
                ? "Scroll down"
                : scrollOffset >= activeMaxScroll
                  ? "Scroll up"
                  : "Scroll";
        graphics.drawString(
                font,
                Component.literal(hint),
                x + INNER_PAD,
                bottom - 1,
                0xFFAAAAAA,
                true
        );
    }

    private List<String> detailLines() {
        List<String> lines = new ArrayList<>();

        lines.add("[ Relationship ]");
        lines.add("  Hearty:           0-100%");
        lines.add("  Distrust:         0-100%");

        boolean foster = getBoolean("Foster", false);
        boolean orphaned = getBoolean("Orphaned", false);

        /*
         * Owner is the current caregiver. Foster=1 means that owner is a
         * foster parent and overrides the original biological Owner fields.
         * When the biological parent dies, Orphaned=1 is authoritative until
         * a deliberate foster transfer clears it.
         */
        if (foster) {
            lines.add("  Parent:           NO");
            lines.add("  Foster parents:   YES");
        } else if (orphaned) {
            lines.add("  Parent:           YES (Deceased)");
            lines.add("  Foster parents:   NO");
        } else {
            lines.add("  Parent:           YES");
            lines.add("  Foster parents:   NO");
        }

        lines.add("  Orphaned:         " + (orphaned ? "YES" : "NO"));
        lines.add("  Your blood Line:  " + (!foster ? "TRUE" : "FALSE"));
        lines.add("  Adopted:          " + (foster ? "YES" : "NO"));
        lines.add("");
        lines.add("[ Survival ]");
        lines.add(String.format(Locale.ROOT, "  Saturation:   %.1f", saturation));
        lines.add(String.format(Locale.ROOT, "  Exhaustion:   %.1f", exhaustion));
        lines.add("  Air:          " + air + " / " + maxAir);
        lines.add("  Fire:         " + (onFire ? "Burning" : "Safe"));
        lines.add("  Wet:          " + (wet ? "Yes" : "No"));
        lines.add("  Sickness:     0");
        lines.add("");
        lines.add("[ Active Effects ]");
        if (effects.isEmpty()) {
            lines.add("  None");
        } else {
            for (MobEffectInstance effect : effects) {
                lines.add("  " + formatEffect(effect));
            }
        }
        lines.add("");
        lines.add("[ Equipment ]");
        lines.add("__EQUIPMENT__HEAD");
        lines.add("__EQUIPMENT__CHEST");
        lines.add("__EQUIPMENT__LEGS");
        lines.add("__EQUIPMENT__FEET");
        lines.add("__EQUIPMENT__MAIN_HAND");
        lines.add("__EQUIPMENT__OFF_HAND");
        lines.add("");
        lines.add("[ Player Base Stats ]");
        lines.add("  Attack Damage:        1.0");
        lines.add("  Attack Speed:         4.0");
        lines.add("  Movement Speed:       0.100");
        lines.add("  Armor:                0");
        lines.add("  Armor Toughness:      0");
        lines.add("  Knockback Resistance: 0.000");
        lines.add("  Luck:                 0.0");
        lines.add("");
        lines.add("[ Diagnosis Report: Body Overall ]");
        lines.add("  Fire Resistance:      "
                + (hasEffect("minecraft:fire_resistance") ? "Active" : "None"));
        lines.add("  Air Supply:           " + airDiagnosis());
        lines.add("  Health:               " + diagnosisHealth());
        return lines;
    }

    private void renderEquipmentRow(
            Font font,
            GuiGraphics graphics,
            int x,
            int y,
            String token
    ) {
        String slot = token.substring("__EQUIPMENT__".length());
        String label;
        ResourceLocation emptyTexture;
        int itemIndex;
        String listName;

        switch (slot) {
            case "HEAD" -> {
                label = "Head";
                emptyTexture = new ResourceLocation("minecraft", "textures/item/empty_armor_slot_helmet.png");
                itemIndex = 3;
                listName = "ArmorItems";
            }
            case "CHEST" -> {
                label = "Chest";
                emptyTexture = new ResourceLocation("minecraft", "textures/item/empty_armor_slot_chestplate.png");
                itemIndex = 2;
                listName = "ArmorItems";
            }
            case "LEGS" -> {
                label = "Legs";
                emptyTexture = new ResourceLocation("minecraft", "textures/item/empty_armor_slot_leggings.png");
                itemIndex = 1;
                listName = "ArmorItems";
            }
            case "FEET" -> {
                label = "Feet";
                emptyTexture = new ResourceLocation("minecraft", "textures/item/empty_armor_slot_boots.png");
                itemIndex = 0;
                listName = "ArmorItems";
            }
            case "MAIN_HAND" -> {
                label = "Main Hand";
                emptyTexture = EMPTY_SWORD;
                itemIndex = 0;
                listName = "HandItems";
            }
            default -> {
                label = "Off Hand";
                emptyTexture = EMPTY_SHIELD;
                itemIndex = 1;
                listName = "HandItems";
            }
        }

        ItemStack stack = getEquipmentStack(listName, itemIndex);
        if (stack.isEmpty()) {
            graphics.blit(emptyTexture, x, y, 0, 0, 16, 16, 16, 16);
        } else {
            graphics.renderItem(stack, x, y);
        }

        graphics.drawString(
                font,
                Component.literal(label + ":"),
                x + 22,
                y + 3,
                0xFFCCCCCC,
                true
        );

        String value = stack.isEmpty() ? "None" : stack.getHoverName().getString();
        graphics.drawString(
                font,
                Component.literal(value),
                x + 100,
                y + 3,
                0xFFCCCCCC,
                true
        );
    }

    private ItemStack getEquipmentStack(String listName, int index) {
        if (!data.contains(listName, 9)) {
            return ItemStack.EMPTY;
        }
        ListTag list = data.getList(listName, 10);
        if (index < 0 || index >= list.size()) {
            return ItemStack.EMPTY;
        }
        CompoundTag itemTag = list.getCompound(index);
        if (itemTag.isEmpty() || !itemTag.contains("id")) {
            return ItemStack.EMPTY;
        }
        return ItemStack.of(itemTag);
    }

    private int maxScroll(List<String> lines) {
        int contentHeight = 0;
        int effectHeader = lines.indexOf("[ Active Effects ]");
        for (int i = 0; i < lines.size(); i++) {
            boolean effectRow = !effects.isEmpty()
                    && effectHeader >= 0
                    && i > effectHeader
                    && i <= effectHeader + effects.size();
            boolean equipmentRow = lines.get(i).startsWith("__EQUIPMENT__");
            contentHeight += effectRow ? EFFECT_HEIGHT
                    : equipmentRow ? EQUIPMENT_ROW_HEIGHT
                      : LINE_HEIGHT;
        }
        int usableHeight = detailViewportHeight() - DETAIL_FOOTER_HEIGHT;
        return Math.max(0, contentHeight + INNER_PAD * 2 - usableHeight);
    }

    private int summaryWidth(Font font) {
        int width = font.width("Tip:");
        width = Math.max(width, font.width("Hold SHIFT to view details"));
        width = Math.max(width, ICON_SIZE + TEXT_GAP + font.width(formatHealth()));
        width = Math.max(width, ICON_SIZE + TEXT_GAP + font.width("Hunger: 20.0 / 20.0 [FL]"));
        width = Math.max(width, ICON_SIZE + TEXT_GAP + font.width("Level: 0 [XP: 0]"));
        if (absorption > 0.0F) {
            width = Math.max(width, ICON_SIZE + TEXT_GAP
                    + font.width(String.format(Locale.ROOT, "%.1f [AB]", absorption)));
        }
        if (armorPoints > 0) {
            width = Math.max(width, ICON_SIZE + TEXT_GAP + font.width(armorPoints + " [PT]"));
        }
        if (air < maxAir) {
            width = Math.max(width, ICON_SIZE + TEXT_GAP
                    + font.width("Oxygen: " + air + " / " + maxAir + " [AIR]"));
        }
        for (String line : tipLines(font, MAX_SUMMARY_CONTENT_WIDTH)) {
            width = Math.max(width, font.width(line));
        }
        return width + 2;
    }

    private int summaryHeight() {
        int height = summaryLineCount() * LINE_HEIGHT;

        if (isCreative()) {
            height += SUMMARY_SECTION_GAP;
        }

        height += SUMMARY_SECTION_GAP;
        height += 4; // bottom padding only

        return height;
    }

    private int summaryLineCount() {
        Font font = Minecraft.getInstance().font;
        int lines = 1; // title
        lines += wrapText(font, "A living baby NPC Player. Handle with care.", MAX_SUMMARY_CONTENT_WIDTH).length;
        if (data.contains("OwnerName") && !data.getString("OwnerName").isEmpty()) {
            lines++;
        }
        if (isCreative()) {
            lines++;
        }
        lines++; // Tip header
        lines += tipLines(font, MAX_SUMMARY_CONTENT_WIDTH).length;
        lines += 2; // heart + hunger
        if (absorption > 0.0F) {
            lines++;
        }
        if (armorPoints > 0) {
            lines++;
        }
        if (air < maxAir) {
            lines++;
        }
        lines++; // XP
        lines++; // final SHIFT hint
        return lines;
    }

    private String[] tipLines(Font font, int maxWidth) {
        return wrapText(font, currentTip(), maxWidth);
    }

    private String currentTip() {
        return TIPS[currentTipIndex % TIPS.length];
    }

    private void beginHoverSession() {
        String identity = data.toString();
        long now = System.nanoTime();

        // GatherComponents is called while the tooltip is alive. If no Baby
        // tooltip has been gathered for a short interval, the next gather is
        // a new hover session even when the same item is hovered again.
        boolean newHover = !identity.equals(hoverIdentity)
                || lastHoverRenderNanos == 0L
                || now - lastHoverRenderNanos > HOVER_RESTART_NANOS;

        if (newHover) {
            int next = TIP_RANDOM.nextInt(TIPS.length);
            if (TIPS.length > 1 && next == currentTipIndex) {
                next = (next + 1) % TIPS.length;
            }
            currentTipIndex = next;
            hoverIdentity = identity;
        }

        lastHoverRenderNanos = now;
    }

    /** Called when the pointer leaves the Baby item tooltip. */
    public static void resetHoverSession() {
        hoverIdentity = "";
        lastHoverRenderNanos = 0L;
    }

    private boolean isCreative() {
        return Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.isCreative();
    }

    private String[] wrapText(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.width(candidate) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines.toArray(new String[0]);
    }

    private String formatHealth() {
        return String.format(
                Locale.ROOT,
                "Health: %.1f / %.1f [HP]",
                Math.min(health, maxHealth),
                maxHealth
        );
    }

    private static void loadEffectBarStates() {
        if (effectBarStatesLoaded) {
            return;
        }
        effectBarStatesLoaded = true;

        Path file = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("normalnpcplayer_effect_bars.properties");
        if (!Files.isRegularFile(file)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            for (String key : properties.stringPropertyNames()) {
                String[] parts = properties.getProperty(key).split(",", 2);
                if (parts.length != 2) {
                    continue;
                }
                int max = Integer.parseInt(parts[0]);
                int previous = Integer.parseInt(parts[1]);
                EffectBarState state = new EffectBarState(max);
                state.previousDuration = Math.max(0, previous);
                EFFECT_BAR_STATES.put(key, state);
            }
        } catch (IOException | NumberFormatException ignored) {
            // A corrupt cache must never break tooltip rendering.
        }
    }

    private static void saveEffectBarStates() {
        Path file = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("normalnpcplayer_effect_bars.properties");
        try {
            Files.createDirectories(file.getParent());

            Properties properties = new Properties();
            for (Map.Entry<String, EffectBarState> entry : EFFECT_BAR_STATES.entrySet()) {
                EffectBarState state = entry.getValue();
                properties.setProperty(
                        entry.getKey(),
                        state.maxDuration + "," + state.previousDuration
                );
            }

            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "Normal NPC Player effect bar state");
            }
        } catch (IOException ignored) {
            // Persistence is best-effort; rendering must continue normally.
        }
    }

    private String effectBarKey(MobEffectInstance effect) {
        return effectOwnerIdentity() + "|"
                + effect.getEffect() + "|" + effect.getAmplifier();
    }

    private String effectOwnerIdentity() {
        CompoundTag identity = data.copy();
        identity.remove("ActiveEffects");
        identity.remove("Health");
        identity.remove("Hunger");
        identity.remove("BabyFoodLevel");
        identity.remove("BabyCarrierLastTick");
        identity.remove("Saturation");
        identity.remove("Exhaustion");
        identity.remove("Air");
        identity.remove("MaxAir");
        identity.remove("Fire");
        identity.remove("Wet");
        identity.remove("XpLevel");
        identity.remove("ExperienceLevel");
        identity.remove("XpTotal");
        identity.remove("TotalExperience");
        return identity.toString();
    }

    private String formatEffect(MobEffectInstance effect) {
        String name = Component.translatable(effect.getDescriptionId()).getString();
        String roman = switch (effect.getAmplifier()) {
            case 0 -> "I";
            case 1 -> "II";
            case 2 -> "III";
            case 3 -> "IV";
            case 4 -> "V";
            default -> String.valueOf(effect.getAmplifier() + 1);
        };
        return name + " " + roman + "    " + formatDuration(effect.getDuration());
    }

    private String formatDuration(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private String diagnosisHealth() {
        if (health <= 0.0F) return "Critical";
        if (health < maxHealth * 0.35F) return "Unstable";
        if (health < maxHealth) return "Injured";
        return hasEffect("minecraft:regeneration") ? "Regenerating" : "Stable";
    }

    private String airDiagnosis() {
        if (air <= 0) return "Critical";
        if (air <= 60) return "Low";
        if (air < maxAir) return "Reduced";
        return "Normal";
    }

    private boolean hasEffect(String id) {
        for (MobEffectInstance effect : effects) {
            MobEffect mobEffect = effect.getEffect();
            if (mobEffect != null
                    && mobEffect.getDescriptionId().equals("effect." + id.replace(':', '.'))) {
                return true;
            }
        }
        return false;
    }

    private float getFloat(String key, float fallback) {
        return data.contains(key) ? data.getFloat(key) : fallback;
    }

    private int getInt(String key, int fallback) {
        return data.contains(key) ? data.getInt(key) : fallback;
    }

    private boolean getBoolean(String key, boolean fallback) {
        return data.contains(key) && data.getBoolean(key);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}