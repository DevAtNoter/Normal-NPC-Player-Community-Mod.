package net.devatnoter.normalnpcplayer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client-only text for keybinds whose physical key is configurable. */
public final class BabyClientKeybindText {
    private BabyClientKeybindText() {
    }

    /** Returns the key name currently bound to Minecraft's Swap Items With Offhand action. */
    public static Component getSwapOffhandKeyMessage() {
        Minecraft minecraft = Minecraft.getInstance();
        return Component.literal("( ").append(minecraft.options.keySwapOffhand.getTranslatedKeyMessage()).append(" )");
    }
}
