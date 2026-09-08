package net.devatnoter.normalnpcplayer.client.tooltip;

import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Real viewport renderer for the Baby SHIFT tooltip.
 *
 * The vanilla tooltip supplies the outer background.
 * This component owns:
 *
 * - fixed viewport height
 * - clipped content
 * - mouse-wheel scrolling
 * - keyboard scrolling
 * - scrollbar
 * - vanilla hunger icon
 * - vanilla oxygen icon
 * - animated vanilla XP orb
 */
public final class BabySurvivalTooltipClientComponent
        implements ClientTooltipComponent {

    private static final ResourceLocation GUI_ICONS =
            new ResourceLocation(
                    "minecraft",
                    "textures/gui/icons.png"
            );

    private static final ResourceLocation EXPERIENCE_ORB =
            new ResourceLocation(
                    "minecraft",
                    "textures/entity/experience_orb.png"
            );

    private static final int ICON_SIZE = 9;
    private static final int LINE_HEIGHT = 10;
    private static final int SECTION_GAP = 5;
    private static final int INNER_PAD = 6;

    // Player-like survival limits used by the Baby tooltip.
    private static final int MAX_HUNGER = 20;
    private static final float MAX_SATURATION = 20.0F;
    private static final int MAX_AIR = 300;

    /*
     * Mouse wheel is multiplied by this value.
     *
     * One normal wheel notch therefore moves roughly 24 pixels.
     */
    private static final int SCROLL_STEP = 24;

    /*
     * Vanilla icons.png coordinates.
     */
    private static final int FOOD_FULL_U = 16;
    private static final int FOOD_HALF_U = 25;
    private static final int FOOD_V = 27;

    private static final int AIR_FULL_U = 16;
    private static final int AIR_V = 18;

    /*
     * Scroll state is shared by the currently displayed Baby tooltip.
     */
    private static int scrollOffset;
    private static int maxScroll;

    /*
     * Prevent the previous Baby's scroll position from leaking into
     * another Baby item.
     */
    private static String activeIdentity = "";

    private final CompoundTag data;
    private final int viewportHeight;
    private final int contentWidth;
    private final List<String> lines;
    private final int contentHeight;

    public BabySurvivalTooltipClientComponent(
            BabyDetailTooltipComponent component
    ) {
        this.data = component.data().copy();

        this.viewportHeight = component.viewportHeight();
        this.contentWidth = component.contentWidth();

        this.lines = buildLines();

        this.contentHeight = Math.max(
                viewportHeight,
                lines.size() * LINE_HEIGHT + INNER_PAD * 2
        );

        maxScroll = Math.max(
                0,
                contentHeight - viewportHeight
        );

        String identity = identity(data);

        if (!identity.equals(activeIdentity)) {
            activeIdentity = identity;
            scrollOffset = 0;
        }

        scrollOffset = clamp(
                scrollOffset,
                0,
                maxScroll
        );
    }

    public static void resetScroll() {
        scrollOffset = 0;
        maxScroll = 0;
        activeIdentity = "";
    }

    public static boolean hasScrollableContent() {
        return maxScroll > 0;
    }

    /**
     * Scroll using the Minecraft wheel delta.
     *
     * Positive delta = wheel up.
     * Negative delta = wheel down.
     */
    public static void scroll(double delta) {
        if (maxScroll <= 0 || delta == 0.0D) {
            return;
        }

        int movement = (int) Math.round(
                delta * SCROLL_STEP
        );

        scrollOffset = clamp(
                scrollOffset - movement,
                0,
                maxScroll
        );
    }

    public static int getScrollOffset() {
        return scrollOffset;
    }

    public static int getMaxScroll() {
        return maxScroll;
    }

    public static String getScrollHint() {
        if (maxScroll <= 0) {
            return "Hold SHIFT to view details";
        }

        if (scrollOffset <= 0) {
            return "Scroll down for more";
        }

        if (scrollOffset >= maxScroll) {
            return "Scroll up for more";
        }

        return "Scroll for more";
    }

    @Override
    public int getHeight() {
        return viewportHeight;
    }

    @Override
    public int getWidth(Font font) {
        return contentWidth;
    }

    @Override
    public void renderImage(
            Font font,
            int x,
            int y,
            GuiGraphics graphics
    ) {
        final int viewportTop = y + 1;

        final int viewportBottom =
                viewportTop + viewportHeight - 2;

        /*
         * HARD CLIP.
         *
         * Nothing from the long Baby detail list can render outside
         * the allocated tooltip viewport.
         */
        graphics.enableScissor(
                x,
                viewportTop,
                x + contentWidth,
                viewportBottom
        );

        int drawY =
                viewportTop
                        + INNER_PAD
                        - scrollOffset;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            int lineY =
                    drawY
                            + i * LINE_HEIGHT;

            renderLine(
                    font,
                    graphics,
                    line,
                    x + INNER_PAD,
                    lineY
            );
        }

        graphics.disableScissor();

        /*
         * Scrollbar only exists when there is actually something
         * below the visible viewport.
         */
        if (maxScroll > 0) {
            renderScrollbar(
                    font,
                    graphics,
                    x,
                    viewportTop,
                    viewportBottom
            );
        }
    }

    private void renderScrollbar(
            Font font,
            GuiGraphics graphics,
            int x,
            int viewportTop,
            int viewportBottom
    ) {
        int trackX =
                x + contentWidth - 4;

        int trackTop =
                viewportTop + 2;

        int trackBottom =
                viewportBottom - 2;

        int trackHeight =
                Math.max(
                        8,
                        trackBottom - trackTop
                );

        int thumbHeight =
                Math.max(
                        10,
                        (int) (
                                (long) trackHeight
                                        * viewportHeight
                                        / Math.max(
                                        1,
                                        contentHeight
                                )
                        )
                );

        int thumbTravel =
                Math.max(
                        0,
                        trackHeight - thumbHeight
                );

        int thumbY =
                trackTop
                        + (int) (
                        (long) thumbTravel
                                * scrollOffset
                                / Math.max(
                                1,
                                maxScroll
                        )
                );

        graphics.fill(
                trackX,
                trackTop,
                trackX + 2,
                trackBottom,
                0x55333333
        );

        graphics.fill(
                trackX,
                thumbY,
                trackX + 2,
                thumbY + thumbHeight,
                0xFFAAAAAA
        );

        /*
         * Keep the scroll hint inside the clipped viewport.
         */
        String hint = getScrollHint();

        graphics.drawString(
                font,
                Component.literal(hint),
                x + INNER_PAD,
                viewportBottom - LINE_HEIGHT,
                0xFFAAAAAA,
                true
        );
    }

    private void renderLine(
            Font font,
            GuiGraphics graphics,
            String line,
            int x,
            int y
    ) {
        if (line.isEmpty()) {
            return;
        }

        /*
         * HP
         */
        if (line.startsWith("__HEART__")) {
            renderVanillaIcon(
                    graphics,
                    x,
                    y,
                    52,
                    0
            );

            graphics.drawString(
                    font,
                    Component.literal(
                            line.substring(9)
                    ),
                    x + ICON_SIZE + 3,
                    y + 1,
                    0xFFFFFFFF,
                    true
            );

            return;
        }

        /*
         * ABSORPTION
         *
         * Uses the real LivingEntity absorption buffer, not a derived
         * effect level. Vanilla icons.png uses U=160 for a full yellow
         * absorption heart and U=169 for a half heart.
         */
        if (line.startsWith("__ABSORB__")) {
            float absorption = Math.max(0.0F, data.getFloat("AbsorptionAmount"));
            int u = (absorption > 0.0F && ((int) Math.floor(absorption) & 1) == 1)
                    ? 169
                    : 160;

            renderVanillaIcon(
                    graphics,
                    x,
                    y,
                    u,
                    0
            );

            graphics.drawString(
                    font,
                    Component.literal(line.substring(10)),
                    x + ICON_SIZE + 3,
                    y + 1,
                    0xFFFFE76A,
                    true
            );

            return;
        }

        /*
         * HUNGER
         *
         * One icon only.
         *
         * This is intentionally NOT a 10-icon hunger bar.
         * The number beside it remains authoritative.
         */
        if (line.startsWith("__FOOD__")) {
            float hunger =
                    data.contains(BabyNPCPlayerEntity.FOOD_LEVEL_TAG)
                            ? BabyNPCPlayerEntity.readFoodLevelTag(data, 20.0F)
                            : data.contains("Hunger")
                              ? data.getFloat("Hunger")
                              : MAX_HUNGER;

            int maxHunger =
                    MAX_HUNGER;

            int u;

            if (hunger <= 0) {
                u = FOOD_HALF_U;
            } else if (hunger * 2 < maxHunger) {
                u = FOOD_HALF_U;
            } else {
                u = FOOD_FULL_U;
            }

            renderVanillaIcon(
                    graphics,
                    x,
                    y,
                    u,
                    FOOD_V
            );

            graphics.drawString(
                    font,
                    Component.literal(
                            line.substring(8)
                    ),
                    x + ICON_SIZE + 3,
                    y + 1,
                    0xFFFFFFFF,
                    true
            );

            return;
        }

        /*
         * OXYGEN
         *
         * IMPORTANT:
         *
         * The bubble does NOT become a broken/burst bubble merely because
         * the current air value is below maximum.
         *
         * Normal summary state uses the stable vanilla bubble.
         * Critical/low-air information is shown in the Survival/Diagnosis
         * section instead.
         */
        if (line.startsWith("__AIR__")) {
            renderVanillaIcon(
                    graphics,
                    x,
                    y,
                    AIR_FULL_U,
                    AIR_V
            );

            graphics.drawString(
                    font,
                    Component.literal(
                            line.substring(7)
                    ),
                    x + ICON_SIZE + 3,
                    y + 1,
                    0xFFFFFFFF,
                    true
            );

            return;
        }

        /*
         * XP
         *
         * Render one complete 16x16 frame from the vanilla 4x4 XP orb
         * animation sheet.
         *
         * The result is scaled to the same 9x9 visual size as the other
         * tooltip icons.
         */
        if (line.startsWith("__XP__")) {
            renderExperienceOrb(
                    graphics,
                    x,
                    y
            );

            graphics.drawString(
                    font,
                    Component.literal(
                            line.substring(6)
                    ),
                    x + ICON_SIZE + 3,
                    y + 1,
                    0xFFFFFFFF,
                    true
            );

            return;
        }

        int color =
                line.startsWith("[")
                        ? 0xFFFFFFFF
                        : 0xFFDDDDDD;

        graphics.drawString(
                font,
                Component.literal(line),
                x,
                y,
                color,
                true
        );
    }

    private void renderVanillaIcon(
            GuiGraphics graphics,
            int x,
            int y,
            int u,
            int v
    ) {
        graphics.blit(
                GUI_ICONS,
                x,
                y,
                ICON_SIZE,
                ICON_SIZE,
                u,
                v,
                ICON_SIZE,
                ICON_SIZE,
                256,
                256
        );
    }

    /**
     * Render a real vanilla XP orb frame.
     *
     * experience_orb.png is a 64x64 sheet containing sixteen 16x16
     * animation frames arranged as a 4x4 grid.
     *
     * The whole sheet is never rendered.
     */
    private void renderExperienceOrb(
            GuiGraphics graphics,
            int x,
            int y
    ) {
        Minecraft minecraft =
                Minecraft.getInstance();

        long gameTime =
                minecraft.level != null
                        ? minecraft.level.getGameTime()
                        : System.currentTimeMillis() / 50L;

        /*
         * Slow enough to be visually readable, while still giving the
         * familiar vanilla XP-orb shimmer.
         */
        int frame =
                (int) ((gameTime / 2L) & 15L);

        int u =
                (frame & 3) * 16;

        int v =
                ((frame >> 2) & 3) * 16;

        /*
         * Slight pulse creates the "XP received" visual language without
         * replacing the vanilla orb texture.
         */
        float pulse =
                0.86F
                        + 0.14F
                        * (float) Math.sin(
                        gameTime * 0.30D
                );

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.setShaderColor(
                pulse,
                pulse,
                pulse,
                1.0F
        );

        graphics.blit(
                EXPERIENCE_ORB,
                x,
                y,
                ICON_SIZE,
                ICON_SIZE,
                u,
                v,
                16,
                16,
                64,
                64
        );

        RenderSystem.setShaderColor(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        RenderSystem.disableBlend();
    }

    private List<String> buildLines() {
        List<String> out =
                new ArrayList<>();

        float health =
                data.contains("Health")
                        ? data.getFloat("Health")
                        : 20.0F;

        float absorption =
                data.contains("AbsorptionAmount")
                        ? Math.max(0.0F, data.getFloat("AbsorptionAmount"))
                        : 0.0F;

        float hunger =
                data.contains(BabyNPCPlayerEntity.FOOD_LEVEL_TAG)
                        ? BabyNPCPlayerEntity.readFoodLevelTag(data, 20.0F)
                        : data.contains("Hunger")
                          ? data.getFloat("Hunger")
                          : MAX_HUNGER;

        float saturation =
                data.contains("Saturation")
                        ? data.getFloat("Saturation")
                        : MAX_SATURATION;

        float exhaustion =
                data.contains("Exhaustion")
                        ? data.getFloat("Exhaustion")
                        : 0.0F;

        int air =
                data.contains("Air")
                        ? data.getInt("Air")
                        : MAX_AIR;

        int level =
                data.contains("XpLevel")
                        ? data.getInt("XpLevel")
                        : data.contains("ExperienceLevel")
                          ? data.getInt("ExperienceLevel")
                          : 0;

        int xp =
                data.contains("XpTotal")
                        ? data.getInt("XpTotal")
                        : data.contains("TotalExperience")
                          ? data.getInt("TotalExperience")
                          : 0;

        String owner =
                data.contains("OwnerName")
                        ? data.getString("OwnerName")
                        : "";

        /*
         * Header
         */
        out.add(
                owner.isEmpty()
                        ? "Baby of Dev"
                        : "Baby of " + owner
        );

        out.add(
                "A living baby NPC Player..."
        );

        if (!owner.isEmpty()) {
            out.add(
                    "Belongs to " + owner
            );
        }

        out.add("");

        /*
         * Tip
         */
        out.add("Tip:");

        addWrapped(
                out,
                "Every meal helps him grow stronger. Bring him to the End and defeat the Ender Dragon to help him grow up."
        );

        out.add("");

        /*
         * Summary stats
         */
        out.add(
                "__HEART__"
                        + format(health)
                        + " / 20.0 HP"
        );

        out.add(
                "__ABSORB__"
                        + format(absorption)
                        + " Absorption HP"
        );

        out.add(
                "__FOOD__"
                        + String.format(java.util.Locale.ROOT, "%.1f / 20.0 [FL]", hunger)
        );

        out.add(
                "__AIR__"
                        + air
                        + " / 300 [AIR]"
        );

        out.add(
                "__XP__"
                        + "Level: "
                        + level
                        + " [XP: "
                        + xp
                        + "]"
        );

        out.add("");

        /*
         * Survival
         */
        out.add("[ Survival ]");

        out.add(
                "  Saturation:   "
                        + format(saturation)
        );

        out.add(
                "  Exhaustion:   "
                        + format(exhaustion)
        );

        out.add(
                "  Air:          "
                        + air
                        + " / 300"
        );

        out.add(
                "  Fire:         "
                        + fireState()
        );

        out.add(
                "  Wet:          "
                        + (isWet()
                        ? "Yes"
                        : "No")
        );

        out.add("");

        /*
         * Active effects
         */
        out.add("[ Active Effects ]");

        addEffects(out);

        out.add("");

        /*
         * Equipment
         */
        out.add("[ Equipment ]");

        addEquipment(out);

        out.add("[ Equipment Durability ]");
        addEquipmentDurability(out);

        out.add("");

        /*
         * Player base stats
         */
        out.add("[ Player Base Stats ]");

        out.add(
                "  Attack Damage:        1.0"
        );

        out.add(
                "  Attack Speed:         4.0"
        );

        out.add(
                "  Movement Speed:       0.100"
        );

        out.add(
                "  Armor:                0"
        );

        out.add(
                "  Armor Toughness:      0"
        );

        out.add(
                "  Knockback Resistance: 0.000"
        );

        out.add(
                "  Luck:                 0.0"
        );

        out.add("");

        /*
         * Diagnosis
         */
        out.add(
                "[ Diagnosis — Body Overall ]"
        );

        out.add(
                "  Fire Resistance:      "
                        + fireDiagnosis()
        );

        out.add(
                "  Air Supply:           "
                        + airDiagnosis(air)
        );

        out.add(
                "  Health:               "
                        + healthDiagnosis(health)
        );

        out.add(
                "  Absorption:           "
                        + format(absorption)
        );

        return out;
    }

    private void addWrapped(
            List<String> out,
            String text
    ) {
        /*
         * Keep the wrapping conservative so the vanilla tooltip never
         * produces extremely long horizontal lines.
         */
        int maxChars =
                Math.max(
                        28,
                        contentWidth / 7
                );

        String[] words =
                text.split(" ");

        StringBuilder line =
                new StringBuilder();

        for (String word : words) {
            if (line.length() > 0
                    && line.length()
                    + 1
                    + word.length()
                    > maxChars) {

                out.add(
                        line.toString()
                );

                line.setLength(0);
            }

            if (line.length() > 0) {
                line.append(' ');
            }

            line.append(word);
        }

        if (line.length() > 0) {
            out.add(
                    line.toString()
            );
        }
    }

    private void addEffects(
            List<String> out
    ) {
        if (!data.contains("ActiveEffects", 9)) {
            out.add("  None");
            return;
        }

        ListTag effects =
                data.getList(
                        "ActiveEffects",
                        10
                );

        if (effects.isEmpty()) {
            out.add("  None");
            return;
        }

        for (int i = 0; i < effects.size(); i++) {
            CompoundTag tag =
                    effects.getCompound(i);

            int effectNumericId = readEffectId(tag);

            MobEffect effect =
                    effectNumericId >= 0
                            ? BuiltInRegistries.MOB_EFFECT.byId(
                            effectNumericId
                    )
                            : null;

            String id =
                    effect != null
                            ? effect.getDescriptionId()
                            : "effect.minecraft.unknown";

            int duration =
                    tag.contains("Duration")
                            ? tag.getInt("Duration")
                            : 0;

            int amplifier =
                    tag.contains("Amplifier")
                            ? tag.getInt("Amplifier")
                            : 0;

            out.add(
                    "  [Potion Icon] "
                            + prettyEffect(
                            id.replace(
                                    "effect.minecraft.",
                                    "minecraft:"
                            )
                    )
                            + " "
                            + roman(amplifier + 1)
                            + "    "
                            + formatDuration(duration)
            );
        }
    }

    private void addEquipmentDurability(List<String> out) {
        CompoundTag equipment = data.contains("BabyEquipment", 10)
                ? data.getCompound("BabyEquipment")
                : new CompoundTag();

        String[] keys = {"Head", "Chest", "Legs", "Feet", "MainHand", "OffHand"};
        for (String key : keys) {
            if (!equipment.contains(key, 10)) {
                continue;
            }
            ItemStack stack = ItemStack.of(equipment.getCompound(key));
            if (stack.isEmpty() || !stack.isDamageableItem()) {
                continue;
            }

            int max = stack.getMaxDamage();
            int remaining = Math.max(0, max - stack.getDamageValue());
            int percent = max <= 0 ? 0 : Math.round(remaining * 100.0F / max);

            out.add("  " + key + ": " + remaining + "/" + max + " (" + percent + "%)");
        }
    }

    private void addEquipment(
            List<String> out
    ) {
        addEquipmentLine(
                out,
                "Head",
                "ArmorItems",
                3
        );

        addEquipmentLine(
                out,
                "Chest",
                "ArmorItems",
                2
        );

        addEquipmentLine(
                out,
                "Legs",
                "ArmorItems",
                1
        );

        addEquipmentLine(
                out,
                "Feet",
                "ArmorItems",
                0
        );

        addEquipmentLine(
                out,
                "Main Hand",
                "HandItems",
                0
        );

        addEquipmentLine(
                out,
                "Off Hand",
                "HandItems",
                1
        );
    }

    private void addEquipmentLine(
            List<String> out,
            String label,
            String listName,
            int index
    ) {
        if (!data.contains(listName, 9)) {
            out.add(
                    "  "
                            + label
                            + ":         None"
            );
            return;
        }

        ListTag list =
                data.getList(
                        listName,
                        10
                );

        if (index >= list.size()) {
            out.add(
                    "  "
                            + label
                            + ":         None"
            );
            return;
        }

        CompoundTag item =
                list.getCompound(index);

        if (item.isEmpty()
                || !item.contains("id")) {

            out.add(
                    "  "
                            + label
                            + ":         None"
            );

            return;
        }

        out.add(
                "  "
                        + label
                        + ":         "
                        + item.getString("id")
        );
    }

    private String attribute(
            String id,
            double fallback
    ) {
        if (!data.contains("Attributes", 9)) {
            return formatAttribute(
                    id,
                    fallback
            );
        }

        ListTag attributes =
                data.getList(
                        "Attributes",
                        10
                );

        for (int i = 0;
             i < attributes.size();
             i++) {

            CompoundTag tag =
                    attributes.getCompound(i);

            if (id.equals(
                    tag.getString("Name")
            )) {

                double base =
                        tag.contains("Base")
                                ? tag.getDouble("Base")
                                : fallback;

                return formatAttribute(
                        id,
                        base
                );
            }
        }

        return formatAttribute(
                id,
                fallback
        );
    }

    private String formatAttribute(
            String id,
            double value
    ) {
        if (id.endsWith(
                "movement_speed"
        )) {
            return String.format(
                    Locale.ROOT,
                    "%.3f",
                    value
            );
        }

        if (id.endsWith(
                "knockback_resistance"
        )) {
            return String.format(
                    Locale.ROOT,
                    "%.3f",
                    value
            );
        }

        if (id.endsWith(
                "attack_speed"
        )) {
            return String.format(
                    Locale.ROOT,
                    "%.1f",
                    value
            );
        }

        if (Math.abs(
                value - Math.rint(value)
        ) < 0.0001D) {

            return String.format(
                    Locale.ROOT,
                    "%.0f",
                    value
            );
        }

        return String.format(
                Locale.ROOT,
                "%.1f",
                value
        );
    }

    private String fireState() {
        int fireTicks =
                data.contains("Fire")
                        ? data.getInt("Fire")
                        : 0;

        if (fireTicks > 0
                && !hasEffect(
                "minecraft:fire_resistance"
        )) {

            return "Burning";
        }

        return "Safe";
    }

    private String fireDiagnosis() {
        return hasEffect(
                "minecraft:fire_resistance"
        )
                ? "Active"
                : "None";
    }

    private boolean hasEffect(
            String effectId
    ) {
        if (!data.contains(
                "ActiveEffects",
                9
        )) {
            return false;
        }

        ListTag effects =
                data.getList(
                        "ActiveEffects",
                        10
                );

        ResourceLocation wanted = ResourceLocation.tryParse(effectId);
        if (wanted == null) {
            return false;
        }

        for (int i = 0;
             i < effects.size();
             i++) {
            int numericId = readEffectId(effects.getCompound(i));
            MobEffect effect = numericId >= 0
                    ? BuiltInRegistries.MOB_EFFECT.byId(numericId)
                    : null;
            if (effect != null
                    && wanted.equals(BuiltInRegistries.MOB_EFFECT.getKey(effect))) {
                return true;
            }
        }

        return false;
    }

    private static int readEffectId(CompoundTag tag) {
        if (tag.contains("Id", Tag.TAG_BYTE)) {
            return tag.getByte("Id") & 0xFF;
        }
        if (tag.contains("Id", Tag.TAG_INT)) {
            return tag.getInt("Id");
        }
        return -1;
    }

    private boolean isWet() {
        return data.getBoolean("Wet");
    }

    private String airDiagnosis(
            int air
    ) {
        if (air <= 0) {
            return "Critical";
        }

        if (air <= 60) {
            return "Low";
        }

        if (air <= 120) {
            return "Reduced";
        }

        return "Normal";
    }

    private String healthDiagnosis(
            float health
    ) {
        if (health <= 0.0F) {
            return "Critical";
        }

        if (health < 6.0F) {
            return "Unstable";
        }

        if (health < 20.0F) {
            return "Injured";
        }

        return "Stable";
    }

    private String prettyEffect(
            String id
    ) {
        String name =
                id.substring(
                        id.indexOf(':') + 1
                ).replace(
                        '_',
                        ' '
                );

        String[] parts =
                name.split(" ");

        StringBuilder result =
                new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            if (result.length() > 0) {
                result.append(' ');
            }

            result.append(
                    Character.toUpperCase(
                            part.charAt(0)
                    )
            );

            result.append(
                    part.substring(1)
            );
        }

        return result.toString();
    }

    private String formatDuration(
            int ticks
    ) {
        int seconds =
                Math.max(
                        0,
                        ticks
                ) / 20;

        return String.format(
                Locale.ROOT,
                "%d:%02d",
                seconds / 60,
                seconds % 60
        );
    }

    private String roman(
            int value
    ) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(value);
        };
    }

    private static String format(
            float value
    ) {
        return String.format(
                Locale.ROOT,
                "%.1f",
                value
        );
    }

    private static String identity(
            CompoundTag data
    ) {
        String owner =
                data.contains("OwnerName")
                        ? data.getString("OwnerName")
                        : "";

        int health =
                data.contains("Health")
                        ? Float.floatToIntBits(
                        data.getFloat("Health")
                )
                        : 20;

        float hunger =
                data.contains(BabyNPCPlayerEntity.FOOD_LEVEL_TAG)
                        ? BabyNPCPlayerEntity.readFoodLevelTag(data, 20.0F)
                        : data.contains("Hunger")
                          ? data.getFloat("Hunger")
                          : 20.0F;

        return owner
                + ':'
                + health
                + ':'
                + hunger
                + ':'
                + data.hashCode();
    }

    private static int clamp(
            int value,
            int min,
            int max
    ) {
        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }
}