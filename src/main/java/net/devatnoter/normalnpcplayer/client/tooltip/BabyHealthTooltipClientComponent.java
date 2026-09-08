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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Complete Baby Item tooltip renderer.
 *
 * Normal mode is the compact summary. Holding SHIFT switches to a fixed
 * detail viewport. The detail body is clipped and scrollable, so its size
 * never grows beyond the tooltip frame.
 */
public final class BabyHealthTooltipClientComponent implements ClientTooltipComponent {

    private static final ResourceLocation GUI_ICONS =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/icons.png");
    private static final ResourceLocation XP_ORB =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/experience_orb.png");
    private static final ResourceLocation EMPTY_SWORD =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/empty_slot_sword.png");
    private static final ResourceLocation EMPTY_SHIELD =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/empty_armor_slot_shield.png");

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
    private static final int STATUS_BAR_ROW_HEIGHT = 12;
    private static final int STATUS_ICON_COUNT = 10;
    private static final int STATUS_ICON_STEP = 8;
    private static final int STATUS_TEXT_GAP = 6;
    private static final int XP_ORB_SHEET_SIZE = 64;
    private static final int XP_ORB_FRAME_SIZE = 16;

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
    private static final int AIR_EMPTY_U = 25;
    private static final int AIR_V = 18;


    private static int scrollOffset;
    private static int activeMaxScroll;
    private static String scrollIdentity = "";
    private static String hoverIdentity = "";
    private static int currentTipIndex;

    private static final Map<String, EffectBarState> EFFECT_BAR_STATES = new HashMap<>();

    /* Vanilla-style HUD animation state. This is client presentation state only. */
    private static final Map<String, HudAnimationState> HUD_ANIMATION_STATES = new HashMap<>();
    private static final int HEALTH_DAMAGE_FLASH_TICKS = 20;
    private static final int HEALTH_RECOVERY_FLASH_TICKS = 10;

    private static final class HudAnimationState {
        private float previousHealth;
        private float previousHunger;
        private int previousAir;
        private int displayHealth;
        private long lastHealthTime;
        private long healthFlashUntil;
        private boolean healthRecoveryFlash;

        private HudAnimationState(float health, float hunger, int air, int maxAir) {
            this.previousHealth = health;
            this.previousHunger = hunger;
            this.previousAir = air;
            this.displayHealth = (int) Math.ceil(Math.max(0.0F, health));
            this.lastHealthTime = System.currentTimeMillis();
        }
    }

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
        this.maxHealth = Math.max(2.0F, getFloat("MaxHealth", 20.0F));
        this.absorption = Math.max(0.0F, getFloat("AbsorptionAmount", 0.0F));
        this.armorPoints = readArmorPoints();
        this.hunger = Math.max(0.0F, Math.min(20.0F, getFloat("BabyFoodLevel", getFloat("Hunger", 20.0F))));
        this.saturation = Math.max(0.0F, getFloat("Saturation", 5.0F));
        this.exhaustion = Math.max(0.0F, getFloat("Exhaustion", 0.0F));
        this.air = Math.max(0, getInt("Air", 300));
        this.maxAir = Math.max(1, getInt("MaxAir", 300));
        this.onFire = getInt("Fire", 0) > 0;
        this.wet = getBoolean("Wet", false);
        this.level = Math.max(0, getInt("XpLevel", getInt("ExperienceLevel", getInt("BabyExperienceLevel", 0))));
        this.totalXp = Math.max(0, getInt("XpTotal", getInt("TotalExperience", getInt("BabyTotalExperience", 0))));
        updateHudAnimationState(stableIdentity(data));

        // The tooltip is rebuilt by Minecraft while live Baby values change.
        // Never treat that rebuild as a new hover session: doing so resets the
        // presentation state and makes the detail scrollbar fight the mouse.
        // Tip selection is handled only when the hovered slot actually changes.

        ListTag activeEffects = data.getList("ActiveEffects", 10);
        for (int i = 0; i < activeEffects.size(); i++) {
            MobEffectInstance effect = MobEffectInstance.load(activeEffects.getCompound(i));
            if (effect != null) {
                effects.add(effect);
            }
        }

        String effectOwner = stableIdentity(data);
        Set<String> currentEffectKeys = new HashSet<>();
        for (MobEffectInstance effect : effects) {
            currentEffectKeys.add(effectOwner + "|"
                    + effect.getEffect() + "|" + effect.getAmplifier());
        }
        // Remove only stale effect lifecycles for this Baby. Never clear the
        // whole map when the tooltip component is reconstructed: Minecraft
        // rebuilds tooltip components while an effect is ticking, and clearing
        // here would make every active-effect bar jump back to 100% repeatedly.
        EFFECT_BAR_STATES.keySet().removeIf(key ->
                key.startsWith(effectOwner + "|") && !currentEffectKeys.contains(key));

        activeMaxScroll = maxScroll(detailLines());
        String identity = stableIdentity(data);
        if (!identity.equals(scrollIdentity)) {
            scrollIdentity = identity;
            scrollOffset = 0;
        }
        scrollOffset = clamp(scrollOffset, 0, activeMaxScroll);
    }

    private static String stableIdentity(CompoundTag source) {
        /*
         * Tooltip components are reconstructed by vanilla whenever the
         * tooltip is rendered/refreshed. Scroll position is presentation
         * state, so its identity must be the Baby itself, not the mutable
         * serialized snapshot. The carried Baby snapshot contains fields
         * that legitimately change while the tooltip is open (health,
         * hunger, absorption, effects, animation state, XP, etc.).
         */
        if (source.hasUUID("UUID")) {
            return "baby:" + source.getUUID("UUID");
        }

        // Legacy/corrupt stacks without a UUID still get a deterministic
        // fallback. Normal Baby Items always have a UUID.
        CompoundTag stable = source.copy();
        stable.remove("Health");
        stable.remove(BabyNPCPlayerEntity.FOOD_LEVEL_TAG);
        stable.remove("Hunger");
        stable.remove(BabyNPCPlayerEntity.SATURATION_LEVEL_TAG);
        stable.remove("Saturation");
        stable.remove(BabyNPCPlayerEntity.EXHAUSTION_LEVEL_TAG);
        stable.remove("Exhaustion");
        stable.remove("Air");
        stable.remove("MaxAir");
        stable.remove("AbsorptionAmount");
        stable.remove("ActiveEffects");
        stable.remove("Effects");
        stable.remove("Fire");
        stable.remove("Wet");
        stable.remove("BabyCarrierLastTick");
        stable.remove("BabyItemSimulationTicks");
        stable.remove("BabyItemAnimation");
        stable.remove("GrowthProgress");
        stable.remove("Hearty");
        stable.remove("HeartyLastHealth");
        stable.remove("HeartySafetyTicks");
        stable.remove("HeartyFearTicks");
        stable.remove("Distrust");
        stable.remove("Pos");
        stable.remove("Motion");
        stable.remove("Rotation");
        stable.remove("FallDistance");
        stable.remove("XpLevel");
        stable.remove("ExperienceLevel");
        stable.remove("BabyExperienceLevel");
        stable.remove("XpTotal");
        stable.remove("TotalExperience");
        stable.remove("BabyTotalExperience");
        return stable.toString();
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
        String ownerName = displayRelationshipName();
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
            graphics.drawString(font, Component.literal("GENDER: Minecraft breeding."),
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

        for (String hintLine : wrapText(font, "Hold SHIFT to view details", MAX_SUMMARY_CONTENT_WIDTH)) {
            graphics.drawString(font, Component.literal(hintLine),
                    x, lineY, 0xFFAAAAAA, true);
            lineY += LINE_HEIGHT;
        }
        for (String hintLine : wrapText(font, "Or hold SHIFT + Right Click to view baby inventory", MAX_SUMMARY_CONTENT_WIDTH)) {
            graphics.drawString(font, Component.literal(hintLine),
                    x, lineY, 0xFFAAAAAA, true);
            lineY += LINE_HEIGHT;
        }
    }

    private void renderHeartLine(Font font, int x, int y, GuiGraphics graphics) {
        boolean hardcore = Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getLevelData().isHardcore();
        boolean halfHeart = health > 0.0F && health < 2.0F;

        // Keep the summary page to ONE heart, but use the same vanilla
        // HeartType atlas family as the full health row. This makes the
        // single summary heart react to Poison / Wither / Frozen as well.
        int heartType = healthHeartTypeIndex();
        int u = (halfHeart ? 61 : 52) + heartType * 36;
        int v = hardcore ? 45 : 0;
        int iconY = y;

        // Vanilla regeneration gives the affected heart a small upward
        // presentation motion. Keep that behavior limited to this one
        // summary heart; the SHIFT detail renderer remains unchanged.
        if (hasEffect("minecraft:regeneration")) {
            iconY -= 2;
        }

        graphics.blit(
                GUI_ICONS,
                x,
                iconY,
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
        // Absorption uses the same normal-vs-hardcore atlas row selection
        // as the vanilla heart HUD.
        boolean hardcore = isHardcoreHud();
        int v = hardcore ? 45 : 0;

        graphics.blit(
                GUI_ICONS, x, y, 0, 160, v,
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

        // Baby Items keep the authoritative equipment in BabyEquipment.
        // ArmorItems is a legacy/entity mirror and is not updated by the
        // item-inventory menu, so reading it here makes the tooltip stay stale
        // until the Baby is placed as an entity and serialized again.
        if (data.contains("BabyEquipment", 10)) {
            CompoundTag equipment = data.getCompound("BabyEquipment");
            String[] keys = {"Head", "Chest", "Legs", "Feet"};
            for (String key : keys) {
                if (!equipment.contains(key, 10)) continue;
                CompoundTag itemTag = equipment.getCompound(key);
                if (!itemTag.contains("id")) continue;
                ItemStack stack = ItemStack.of(itemTag);
                if (stack.getItem() instanceof ArmorItem armorItem) {
                    total += armorItem.getDefense();
                }
            }
            return Math.max(0, total);
        }

        // Entity/legacy Baby data fallback.
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

        int xpAtLevel = BabyNPCPlayerEntity.getXpAtLevel(level);
        int nextLevelXp = BabyNPCPlayerEntity.getXpNeededForNextLevel(level);
        int progress = Math.max(0, totalXp - xpAtLevel);
        progress = Math.min(progress, nextLevelXp);

        graphics.drawString(
                font,
                Component.literal("Level: " + level + " [XP: " + progress + " / " + nextLevelXp + "]"),
                x + ICON_SIZE + TEXT_GAP,
                y + 1,
                0xFFFFFFFF,
                true
        );
    }

    /**
     * Vanilla player-style XP progress bar. The Baby uses the same level
     * curve as a Player; only the visual progress bar is shown here.
     */
    private void renderXpBar(GuiGraphics graphics, int x, int y, int barWidth) {
        int levelXp = BabyNPCPlayerEntity.getXpAtLevel(level);
        int nextLevelXp = Math.max(1, BabyNPCPlayerEntity.getXpNeededForNextLevel(level));
        int progress = Math.max(0, totalXp - levelXp);
        progress = Math.min(progress, nextLevelXp);
        int filled = Math.min(barWidth, (int) Math.floor(
                barWidth * (progress / (double) nextLevelXp)
        ));

        // The detail XP bar spans exactly from the left edge of the Health
        // icons to the right edge of the Hunger icons. The vanilla 1.20.1
        // source art is 182x5, so scale that atlas strip to the full status
        // width instead of centering a shorter 182px bar.
        graphics.blit(
                GUI_ICONS,
                x, y + 2,
                barWidth, 5,
                0, 64,
                182, 5,
                256, 256
        );

        if (filled > 0) {
            int sourceFilled = Math.max(1, Math.min(182, Math.round(
                    182.0F * filled / (float) barWidth
            )));
            graphics.blit(
                    GUI_ICONS,
                    x, y + 2,
                    filled, 5,
                    0, 69,
                    sourceFilled, 5,
                    256, 256
            );
        }
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
            boolean statusBarRow = line.startsWith("__STATUS_");
            int rowHeight = effectRow ? EFFECT_HEIGHT
                    : equipmentRow ? EQUIPMENT_ROW_HEIGHT
                      : statusBarRow ? STATUS_BAR_ROW_HEIGHT
                        : LINE_HEIGHT;

            if (lineY + rowHeight >= viewportTop && lineY <= viewportBottom) {
                if (statusBarRow) {
                    renderStatusBar(graphics, contentLeft, lineY, contentRight, line);
                } else if (effectRow) {
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

                    // Keep this state in memory only. Persisting duration
                    // baselines to disk makes a newly applied effect inherit
                    // an old lifecycle after reloads.
                    // The authoritative remaining duration always comes from
                    // the current MobEffectInstance.

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

    private static final class FedByPlayerEntry {
        private final String name;
        private final int times;

        private FedByPlayerEntry(String name, int times) {
            this.name = name;
            this.times = times;
        }
    }

    private static final class ProtectorEntry {
        private final String name;
        private final int times;

        private ProtectorEntry(String name, int times) {
            this.name = name;
            this.times = times;
        }
    }

    private List<FedByPlayerEntry> fedByPlayers() {
        List<FedByPlayerEntry> result = new ArrayList<>();
        if (!data.contains("FedByPlayers", 9)) {
            return result;
        }

        ListTag history = data.getList("FedByPlayers", 10);
        for (int i = 0; i < history.size(); i++) {
            CompoundTag entry = history.getCompound(i);
            int times = Math.max(0, entry.getInt("Times"));
            if (times <= 0) continue;

            String name = entry.getString("Name").trim();
            if (name.isEmpty()) {
                name = entry.getString("UUID").trim();
            }
            if (name.isEmpty()) continue;

            result.add(new FedByPlayerEntry(name, times));
        }

        result.sort((a, b) -> {
            int count = Integer.compare(b.times, a.times);
            return count != 0 ? count : a.name.compareToIgnoreCase(b.name);
        });
        return result;
    }

    private List<ProtectorEntry> protectors() {
        List<ProtectorEntry> result = new ArrayList<>();
        if (!data.contains("Protectors", 9)) {
            return result;
        }

        ListTag history = data.getList("Protectors", 10);
        for (int i = 0; i < history.size(); i++) {
            CompoundTag entry = history.getCompound(i);
            int times = Math.max(0, entry.getInt("Times"));
            if (times <= 0) continue;

            String name = entry.getString("Name").trim();
            if (name.isEmpty()) {
                name = entry.getString("UUID").trim();
            }
            if (name.isEmpty()) continue;

            result.add(new ProtectorEntry(name, times));
        }

        result.sort((a, b) -> {
            int count = Integer.compare(b.times, a.times);
            return count != 0 ? count : a.name.compareToIgnoreCase(b.name);
        });
        return result;
    }

    private List<String> detailLines() {
        List<String> lines = new ArrayList<>();

        lines.add("[ Relationship ]");
        if (isPlayerBaby()) {
            lines.add(String.format(Locale.ROOT, "  Hearty:           %.1f%%", getDouble("Hearty", 50.0D)));
            lines.add(String.format(Locale.ROOT, "  Distrust:         %.1f%%", getDouble("Distrust", 0.0D)));
        } else {
            lines.add("  Hearty:           N/A");
            lines.add("  Distrust:         N/A");
        }

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

        // Player Babies have a separate family section directly below Adopted.
        // Hardcore Babies intentionally do not expose this section.
        if (isPlayerBaby()) {
            lines.add("");
            lines.add("[ Family ]");
            lines.add("  Age:              " + formatPlayerBabyAge());
            lines.add("  Owner:            " + formatPlayerBabyOwners());
        }
        lines.add("");

        // Detail-only status layout mirrors the requested vanilla-style HUD
        // grouping:
        //   row 1: Armor aligned with the Health column
        //   row 2: Absorption (left) + Oxygen (right, only when active)
        //   row 3: Health (left) + Hunger (right)
        //   row 4: centered XP level + XP bar
        if (armorPoints > 0) {
            lines.add("__STATUS_ARMOR__");
        }
        if (absorption > 0.0F || air < maxAir) {
            lines.add("__STATUS_ABSORPTION_OXYGEN__");
        }
        lines.add("__STATUS_HEALTH_HUNGER__");
        lines.add("__STATUS_XP__");
        lines.add("");

        lines.add("[ Fed by Players ]");
        List<FedByPlayerEntry> fedByPlayers = fedByPlayers();
        if (fedByPlayers.isEmpty()) {
            lines.add("  None");
        } else {
            for (FedByPlayerEntry entry : fedByPlayers) {
                String timesLabel = entry.times == 1 ? "Time" : "Times";
                lines.add("  " + entry.name + " (" + entry.times + " " + timesLabel + ")");
            }
        }
        lines.add("");

        lines.add("[ The Protectors ]");
        List<ProtectorEntry> protectorEntries = protectors();
        if (protectorEntries.isEmpty()) {
            lines.add("  None");
        } else {
            for (ProtectorEntry entry : protectorEntries) {
                String timesLabel = entry.times == 1 ? "Kill" : "Kills";
                lines.add("  " + entry.name + " (" + entry.times + " " + timesLabel + ")");
            }
        }
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

    private double getDouble(String key, double fallback) {
        return data.contains(key) ? data.getDouble(key) : fallback;
    }

    private boolean isPlayerBaby() {
        return "PLAYER".equalsIgnoreCase(data.getString("BabyType"));
    }

    private String formatPlayerBabyAge() {
        long birthGameTime = data.contains("BirthGameTime")
                ? Math.max(0L, data.getLong("BirthGameTime"))
                : 0L;

        if (Minecraft.getInstance().level == null) {
            return "0d 0h";
        }

        long ageTicks = Math.max(0L, Minecraft.getInstance().level.getGameTime() - birthGameTime);
        long totalHours = ageTicks / 1000L;
        long days = totalHours / 24L;
        long hours = totalHours % 24L;
        return days + "d " + hours + "h";
    }

    private String formatPlayerBabyOwners() {
        String value = displayRelationshipName();
        return value.isEmpty() ? "Unknown" : value;
    }

    private String displayRelationshipName() {
        if (!isPlayerBaby()) {
            return data.contains("OwnerName")
                    ? data.getString("OwnerName").trim()
                    : "";
        }

        String parentA = data.contains("OwnerAName")
                ? data.getString("OwnerAName").trim()
                : data.contains("BiologicalParentAName")
                  ? data.getString("BiologicalParentAName").trim()
                  : "";
        String parentB = data.contains("OwnerBName")
                ? data.getString("OwnerBName").trim()
                : data.contains("BiologicalParentBName")
                  ? data.getString("BiologicalParentBName").trim()
                  : "";

        if (!parentA.isEmpty() && !parentB.isEmpty()) {
            return parentA + " & " + parentB;
        }
        if (!parentA.isEmpty()) return parentA;
        if (!parentB.isEmpty()) return parentB;
        return data.contains("OwnerName")
                ? data.getString("OwnerName").trim()
                : "";
    }

    /**
     * Renders the Baby's status section using the same vanilla HUD sprites
     * from minecraft:textures/gui/icons.png.  This is detail-view only:
     * there are deliberately no labels or numeric values on the status bars.
     * The vanilla-style XP level number is rendered separately beside the
     * hunger/air row, outside the XP bar.
     *
     * The optional bars follow the same visibility idea as the player HUD:
     * absorption, armor and air are omitted while their value is not active.
     */
    private void renderStatusBar(
            GuiGraphics graphics,
            int x,
            int y,
            int contentRight,
            String token
    ) {
        switch (token) {
            case "__STATUS_ARMOR__" -> renderCenteredArmorRow(graphics, x, y, contentRight);
            case "__STATUS_ABSORPTION_OXYGEN__" -> renderAbsorptionOxygenRow(graphics, x, y, contentRight);
            case "__STATUS_HEALTH_HUNGER__" -> renderHealthHungerRow(graphics, x, y, contentRight);
            case "__STATUS_XP__" -> renderCenteredXpRow(graphics, x, y, contentRight);
            default -> {
            }
        }
    }

    private void renderCenteredArmorRow(GuiGraphics graphics, int x, int y, int contentRight) {
        if (armorPoints <= 0) return;
        // Armor belongs to the same left-side HUD column as Health, not centered.
        renderArmorIcons(graphics, x, y);
    }

    private void renderAbsorptionOxygenRow(GuiGraphics graphics, int x, int y, int contentRight) {
        if (absorption > 0.0F) {
            renderAbsorptionIcons(graphics, x, y);
        }

        if (air < maxAir) {
            int width = STATUS_ICON_COUNT * STATUS_ICON_STEP + 1;
            int airX = Math.max(x, contentRight - width);
            renderAirIcons(graphics, airX, y);
        }
    }

    private void renderHealthHungerRow(GuiGraphics graphics, int x, int y, int contentRight) {
        renderHeartIcons(graphics, x, y);

        int width = STATUS_ICON_COUNT * STATUS_ICON_STEP + 1;
        int hungerX = Math.max(x, contentRight - width);
        renderHungerIcons(graphics, hungerX, y);
    }

    private void renderCenteredXpRow(GuiGraphics graphics, int x, int y, int contentRight) {
        int barWidth = Math.max(1, contentRight - x);
        int barX = x;

        renderXpLevelCentered(graphics, barX, barWidth, y);
        renderXpBar(graphics, barX, y + 10, barWidth);
    }

    private void renderXpLevelCentered(GuiGraphics graphics, int barX, int barWidth, int y) {
        if (level <= 0) return;
        Font font = Minecraft.getInstance().font;
        String levelText = String.valueOf(level);
        int levelWidth = font.width(levelText);
        int levelX = barX + (barWidth - levelWidth) / 2;

        // Vanilla-style outlined XP level text centered over the XP bar.
        graphics.drawString(font, Component.literal(levelText), levelX - 1, y, 0xFF000000, false);
        graphics.drawString(font, Component.literal(levelText), levelX + 1, y, 0xFF000000, false);
        graphics.drawString(font, Component.literal(levelText), levelX, y - 1, 0xFF000000, false);
        graphics.drawString(font, Component.literal(levelText), levelX, y + 1, 0xFF000000, false);
        graphics.drawString(font, Component.literal(levelText), levelX, y, 0xFF80FF20, false);
    }

    private void renderHeartIcons(GuiGraphics graphics, int x, int y) {
        boolean hardcore = isHardcoreHud();
        int heartV = hardcore ? 45 : 0;
        HudAnimationState state = hudAnimationState(stableIdentity(data));
        long tick = clientTick();

        int healthCeil = (int) Math.ceil(Math.max(0.0F, health));
        int healthLast = state == null ? healthCeil : Math.max(0, state.displayHealth);
        int maxHealthCeil = Math.max(1, (int) Math.ceil(maxHealth));
        int regenerationIndex = -1;
        if (hasEffect("minecraft:regeneration")) {
            regenerationIndex = (int) (tick % Math.max(1, maxHealthCeil + 5));
        }

        // Minecraft 1.20.1 HeartType textureIndex in icons.png:
        // NORMAL=0, POISIONED=1, WITHERED=2, ABSORBING=3, FROZEN=4.
        // Absorption is rendered separately below, so the health row only
        // needs NORMAL / POISIONED / WITHERED / FROZEN here.
        int heartType = healthHeartTypeIndex();
        boolean blinkTexture = heartType <= 2;

        Random animationRandom = new Random(tick * 312871L);
        boolean highlight = isHealthHighlightActive(state, tick);

        for (int i = 0; i < STATUS_ICON_COUNT; i++) {
            float slotStart = i * (maxHealth / STATUS_ICON_COUNT);
            float slotEnd = (i + 1) * (maxHealth / STATUS_ICON_COUNT);
            int iconU = 16;
            boolean half = false;

            if (health >= slotEnd) {
                iconU = 52 + heartType * 36;
            } else if (health > slotStart) {
                iconU = 61 + heartType * 36;
                half = true;
            }

            int iconY = y;
            if (healthCeil <= 4) {
                iconY += animationRandom.nextInt(2);
            }
            if (i == regenerationIndex) {
                iconY -= 2;
            }

            int iconX = x + i * STATUS_ICON_STEP;
            if (highlight) {
                graphics.blit(GUI_ICONS, iconX, iconY, 0, 25, heartV,
                        ICON_SIZE, ICON_SIZE, 256, 256);
            } else {
                graphics.blit(GUI_ICONS, iconX, iconY, 0, 16, heartV,
                        ICON_SIZE, ICON_SIZE, 256, 256);
            }

            if (highlight && blinkTexture) {
                int oldSlotValue = i * 2 + 1;
                if (oldSlotValue < healthLast) {
                    graphics.blit(GUI_ICONS, iconX, iconY, 0,
                            70 + heartType * 36, heartV,
                            ICON_SIZE, ICON_SIZE, 256, 256);
                } else if (oldSlotValue == healthLast) {
                    graphics.blit(GUI_ICONS, iconX, iconY, 0,
                            79 + heartType * 36, heartV,
                            ICON_SIZE, ICON_SIZE, 256, 256);
                }
            }

            renderHeartIcon(graphics, iconX, iconY, heartV, iconU, half, false);
        }
    }

    /**
     * Returns the legacy 1.20.1 icons.png health-heart texture index.
     * Poison = green, Wither = black, Frozen = blue. Absorption is a
     * separate golden-heart row and therefore is not selected here.
     */
    private int healthHeartTypeIndex() {
        if (hasEffect("minecraft:poison")) return 1;
        if (hasEffect("minecraft:wither")) return 2;
        if (isFullyFrozenHud()) return 4;
        return 0;
    }

    private boolean isFullyFrozenHud() {
        // Player#isFullyFrozen() reaches its full-freeze state at 140 ticks.
        return getInt("TicksFrozen", 0) >= 140;
    }

    private void renderHeartIcon(
            GuiGraphics graphics,
            int x,
            int y,
            int v,
            int heartU,
            boolean half
    ) {
        renderHeartIcon(graphics, x, y, v, heartU, half, true);
    }

    private void renderHeartIcon(
            GuiGraphics graphics,
            int x,
            int y,
            int v,
            int heartU,
            boolean half,
            boolean drawContainer
    ) {
        if (drawContainer) {
            graphics.blit(GUI_ICONS, x, y, 0, 16, v,
                    ICON_SIZE, ICON_SIZE, 256, 256);
        }
        graphics.blit(GUI_ICONS, x, y, 0, heartU, v,
                ICON_SIZE, ICON_SIZE, 256, 256);
    }

    private void renderAbsorptionIcons(GuiGraphics graphics, int x, int y) {
        if (absorption <= 0.0F) return;

        boolean hardcore = isHardcoreHud();
        int v = hardcore ? 45 : 0;
        renderTenValueIcons(graphics, x, y, absorption, 20.0F, 16, 160, 169, v, true);
    }

    private void renderArmorIcons(GuiGraphics graphics, int x, int y) {
        if (armorPoints <= 0) return;
        renderTenValueIcons(graphics, x, y, armorPoints, 20.0F, 16, 34, 34, 9, false);
    }

    private void renderHungerIcons(GuiGraphics graphics, int x, int y) {
        boolean hungerEffect = hasEffect("minecraft:hunger");
        int emptyU = hungerEffect ? 133 : FOOD_EMPTY_U;
        int fullU = hungerEffect ? 88 : FOOD_FULL_U;
        int halfU = hungerEffect ? 97 : FOOD_HALF_U;

        long tick = clientTick();
        boolean shake = saturation <= 0.0F
                && ((int) (tick % ((int) Math.floor(hunger) * 3L + 1L)) == 0);
        Random animationRandom = new Random(tick * 312871L);

        // Vanilla renders food right-to-left. Keep that exact slot order and
        // apply its starvation/saturation shake to the complete icon.
        for (int i = 0; i < STATUS_ICON_COUNT; i++) {
            float slotStart = i * (MAX_HUNGER / (float) STATUS_ICON_COUNT);
            float slotEnd = (i + 1) * (MAX_HUNGER / (float) STATUS_ICON_COUNT);
            int u = emptyU;

            if (hunger >= slotEnd) {
                u = fullU;
            } else if (hunger > slotStart) {
                u = halfU;
            }

            int iconX = x + (STATUS_ICON_COUNT - 1 - i) * STATUS_ICON_STEP;
            int iconY = y;
            if (shake) {
                iconY += animationRandom.nextInt(3) - 1;
            }

            graphics.blit(
                    GUI_ICONS, iconX, iconY, 0, u, FOOD_V,
                    ICON_SIZE, ICON_SIZE, 256, 256
            );
        }
    }

    private void renderAirIcons(GuiGraphics graphics, int x, int y) {
        // Minecraft 1.20.1 uses the legacy icons.png atlas. AIR_SPRITE and
        // AIR_BURSTING_SPRITE are the 1.20.2+ sprite names and must not be used
        // in a 1.20.1 build. The equivalent Vanilla pipeline is U=16 for a
        // normal bubble and U=25 for the single partial/bursting bubble.
        if (air >= maxAir && !wet) return;

        int full = (int) Math.ceil((double) (air - 2) * 10.0D / (double) maxAir);
        int partial = (int) Math.ceil((double) air * 10.0D / (double) maxAir) - full;
        full = Math.max(0, Math.min(STATUS_ICON_COUNT, full));
        partial = Math.max(0, Math.min(1, partial));

        // Vanilla anchors the air bar on the right and renders slots from
        // right to left. Consequently air is depleted visually left -> right
        // and refilled right -> left. No persistent empty-bubble layer exists.
        int bubbleCount = Math.min(STATUS_ICON_COUNT, full + partial);
        for (int i = 0; i < bubbleCount; i++) {
            int iconX = x + (STATUS_ICON_COUNT - 1 - i) * STATUS_ICON_STEP;
            int u = i < full ? AIR_FULL_U : AIR_EMPTY_U;
            graphics.blit(
                    GUI_ICONS, iconX, y, 0, u, AIR_V,
                    ICON_SIZE, ICON_SIZE, 256, 256
            );
        }
    }

    /** Render ten 1.20.1 vanilla HUD slots with full/half/empty states. */
    private void renderTenValueIcons(
            GuiGraphics graphics,
            int x,
            int y,
            float value,
            float max,
            int emptyU,
            int fullU,
            int halfU,
            int v,
            boolean halfSupported
    ) {
        float clampedValue = Math.max(0.0F, Math.min(max, value));
        for (int i = 0; i < STATUS_ICON_COUNT; i++) {
            float slotStart = i * (max / STATUS_ICON_COUNT);
            float slotEnd = (i + 1) * (max / STATUS_ICON_COUNT);
            int u = emptyU;

            if (clampedValue >= slotEnd) {
                u = fullU;
            } else if (halfSupported && clampedValue > slotStart) {
                u = halfU;
            }

            graphics.blit(
                    GUI_ICONS,
                    x + i * STATUS_ICON_STEP,
                    y,
                    0,
                    u,
                    v,
                    ICON_SIZE,
                    ICON_SIZE,
                    256,
                    256
            );
        }
    }

    private long clientTick() {
        // Tooltip detail animations must continue while an inventory screen
        // is open. In singleplayer the world can be paused, so level gameTime
        // may stop advancing and freeze the oxygen splash on one frame. Use
        // real client time for presentation-only animation state instead.
        return System.currentTimeMillis() / 50L;
    }

    private HudAnimationState hudAnimationState(String identity) {
        return HUD_ANIMATION_STATES.get(identity);
    }

    private void updateHudAnimationState(String identity) {
        long tick = clientTick();
        HudAnimationState state = HUD_ANIMATION_STATES.get(identity);
        if (state == null) {
            HUD_ANIMATION_STATES.put(identity, new HudAnimationState(health, hunger, air, maxAir));
            return;
        }

        if (Float.compare(health, state.previousHealth) != 0) {
            state.healthRecoveryFlash = health > state.previousHealth;
            state.healthFlashUntil = tick + (state.healthRecoveryFlash
                    ? HEALTH_RECOVERY_FLASH_TICKS
                    : HEALTH_DAMAGE_FLASH_TICKS);
            state.previousHealth = health;
            state.lastHealthTime = System.currentTimeMillis();
        }

        // Vanilla keeps the previous displayed health during the short blink
        // window, then settles it to the current health after the display state
        // has had time to age. This is the important part for damage flashes.
        if (System.currentTimeMillis() - state.lastHealthTime > 1000L) {
            state.displayHealth = (int) Math.ceil(Math.max(0.0F, health));
            state.lastHealthTime = System.currentTimeMillis();
        }

        if (Float.compare(hunger, state.previousHunger) != 0) {
            state.previousHunger = hunger;
        }
        state.previousAir = air;
    }

    private boolean isHealthHighlightActive(HudAnimationState state, long tick) {
        if (state == null || tick >= state.healthFlashUntil) return false;
        return ((state.healthFlashUntil - tick) / 3L) % 2L == 1L;
    }

    private boolean isHardcoreHud() {
        return Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getLevelData().isHardcore();
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
                emptyTexture = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/empty_armor_slot_helmet.png");
                itemIndex = 3;
                listName = "ArmorItems";
            }
            case "CHEST" -> {
                label = "Chest";
                emptyTexture = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/empty_armor_slot_chestplate.png");
                itemIndex = 2;
                listName = "ArmorItems";
            }
            case "LEGS" -> {
                label = "Legs";
                emptyTexture = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/empty_armor_slot_leggings.png");
                itemIndex = 1;
                listName = "ArmorItems";
            }
            case "FEET" -> {
                label = "Feet";
                emptyTexture = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/empty_armor_slot_boots.png");
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
            // Use the vanilla ItemStack renderer AND its decorations so the
            // Baby equipment row shows the real durability bar exactly like
            // a normal Minecraft inventory slot.
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(font, stack, x, y, "");
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
        // Baby Item data stores live equipment in BabyEquipment. Prefer that
        // representation so the tooltip reads the exact ItemStack, including
        // its Damage value, enchantments and other item NBT.
        if (data.contains("BabyEquipment", 10)) {
            CompoundTag equipment = data.getCompound("BabyEquipment");
            String key = switch (listName) {
                case "ArmorItems" -> switch (index) {
                    case 3 -> "Head";
                    case 2 -> "Chest";
                    case 1 -> "Legs";
                    case 0 -> "Feet";
                    default -> "";
                };
                case "HandItems" -> index == 0 ? "MainHand" : index == 1 ? "OffHand" : "";
                default -> "";
            };

            if (!key.isEmpty() && equipment.contains(key, 10)) {
                CompoundTag itemTag = equipment.getCompound(key);
                if (!itemTag.isEmpty() && itemTag.contains("id")) {
                    return ItemStack.of(itemTag);
                }
            }
        }

        // Compatibility fallback for older Baby Item data.
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
            boolean statusBarRow = lines.get(i).startsWith("__STATUS_");
            contentHeight += effectRow ? EFFECT_HEIGHT
                    : equipmentRow ? EQUIPMENT_ROW_HEIGHT
                      : statusBarRow ? STATUS_BAR_ROW_HEIGHT
                        : LINE_HEIGHT;
        }
        int usableHeight = detailViewportHeight() - DETAIL_FOOTER_HEIGHT;
        return Math.max(0, contentHeight + INNER_PAD * 2 - usableHeight);
    }

    private int summaryWidth(Font font) {
        int width = font.width("Tip:");
        for (String hintLine : wrapText(font, "Hold SHIFT to view details", MAX_SUMMARY_CONTENT_WIDTH)) {
            width = Math.max(width, font.width(hintLine));
        }
        for (String hintLine : wrapText(font, "Or hold SHIFT + Right Click to view baby inventory", MAX_SUMMARY_CONTENT_WIDTH)) {
            width = Math.max(width, font.width(hintLine));
        }
        width = Math.max(width, ICON_SIZE + TEXT_GAP + font.width(formatHealth()));
        width = Math.max(width, ICON_SIZE + TEXT_GAP + font.width("Hunger: 20.0 / 20.0 [FL]"));
        width = Math.max(width, ICON_SIZE + TEXT_GAP + font.width("Level: 0 [XP: 0 / 7]"));
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
        lines += wrapText(font, "Hold SHIFT to view details", MAX_SUMMARY_CONTENT_WIDTH).length;
        lines += wrapText(font, "Or hold SHIFT + Right Click to view baby inventory", MAX_SUMMARY_CONTENT_WIDTH).length;
        return lines;
    }

    private String[] tipLines(Font font, int maxWidth) {
        return wrapText(font, currentTip(), maxWidth);
    }

    private String currentTip() {
        return TIPS[currentTipIndex % TIPS.length];
    }

    /** Called when the pointer leaves the Baby item tooltip. */
    public static void resetHoverSession() {
        hoverIdentity = "";
    }

    /** Select a tip only when the hovered Baby slot actually changes. */
    public static void beginHoverSession(String identity) {
        if (identity == null) {
            identity = "";
        }
        if (!identity.equals(hoverIdentity)) {
            int next = currentTipIndex;
            if (TIPS.length > 1) {
                next = TIP_RANDOM_INDEX.nextInt(TIPS.length);
                if (next == currentTipIndex) {
                    next = (next + 1) % TIPS.length;
                }
            }
            currentTipIndex = next;
            hoverIdentity = identity;
        }
    }

    private static final java.util.Random TIP_RANDOM_INDEX = new java.util.Random();

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

    private String effectBarKey(MobEffectInstance effect) {
        // Key only by the Baby UUID + effect + amplifier. Do not use the full
        // serialized Baby snapshot: live NBT fields change every tick and
        // would create a brand-new bar state while the same effect is active.
        return stableIdentity(data) + "|"
                + effect.getEffect() + "|" + effect.getAmplifier();
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