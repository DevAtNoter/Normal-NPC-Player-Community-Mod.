package net.devatnoter.normalnpcplayer.client.screen;

import net.devatnoter.normalnpcplayer.config.NNPConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class GuideBookConfigScreen extends Screen {
    private final Screen parent;
    private boolean enabled;
    private NNPConfig.GuideBookDistributionMode mode;

    public GuideBookConfigScreen(Screen parent) {
        super(Component.translatable("config.normalnpcplayer.title"));
        this.parent = parent;
        this.enabled = NNPConfig.PATCHOULI_BOOKS_ENABLED.get();
        this.mode = NNPConfig.GUIDE_BOOK_DISTRIBUTION_MODE.get();
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int y = this.height / 2 - 35;

        addRenderableWidget(Button.builder(enabledText(), button -> {
            enabled = !enabled;
            button.setMessage(enabledText());
        }).bounds(center - 110, y, 220, 20).build());

        addRenderableWidget(Button.builder(modeText(), button -> {
            mode = mode == NNPConfig.GuideBookDistributionMode.CINEMATIC_INTRO_DEFAULT
                    ? NNPConfig.GuideBookDistributionMode.GIVE_AT_BIRTH
                    : NNPConfig.GuideBookDistributionMode.CINEMATIC_INTRO_DEFAULT;
            button.setMessage(modeText());
        }).bounds(center - 110, y + 28, 220, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> saveAndClose())
                .bounds(center - 110, y + 64, 220, 20).build());
    }

    private Component enabledText() {
        return Component.translatable("config.normalnpcplayer.patchouli_books", enabled ? "ON" : "OFF");
    }

    private Component modeText() {
        return Component.translatable(mode == NNPConfig.GuideBookDistributionMode.CINEMATIC_INTRO_DEFAULT
                ? "config.normalnpcplayer.mode.cinematic"
                : "config.normalnpcplayer.mode.birth");
    }

    private void saveAndClose() {
        NNPConfig.PATCHOULI_BOOKS_ENABLED.set(enabled);
        NNPConfig.GUIDE_BOOK_DISTRIBUTION_MODE.set(mode);
        NNPConfig.SPEC.save();
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        saveAndClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 75, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
