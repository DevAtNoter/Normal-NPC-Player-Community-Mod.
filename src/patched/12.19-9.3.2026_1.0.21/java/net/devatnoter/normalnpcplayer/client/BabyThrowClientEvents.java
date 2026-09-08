package net.devatnoter.normalnpcplayer.client;

import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.devatnoter.normalnpcplayer.network.ModNetwork;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side input and HUD for the carried-Baby throw.
 *
 * This intentionally lives beside the existing ClientEvents class so the
 * existing hotbar/Q behavior is untouched.
 */
@Mod.EventBusSubscriber(
        modid = "normalnpcplayer",
        value = net.minecraftforge.api.distmarker.Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class BabyThrowClientEvents {
    private static boolean charging;
    private static int chargeTicks;

    private static final int MAX_CHARGE_TICKS = 20;

    private BabyThrowClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            reset(false);
            return;
        }

        if (minecraft.screen != null) {
            if (charging) {
                ModNetwork.sendBabyThrowCancel();
            }
            reset(false);
            return;
        }

        boolean hasBaby = hasCarriedBabyWithFreeOtherHand(minecraft);
        boolean sprintDown = minecraft.options.keySprint.isDown();
        boolean rDown = isRKeyDown(minecraft);
        boolean useDown = minecraft.options.keyUse.isDown();
        boolean attackDown = minecraft.options.keyAttack.isDown();

        /*
         * Throw activation requires all three inputs:
         * Sprint + physical R + right-click held.
         *
         * Sprint remains Minecraft's actual configurable Sprint KeyMapping.
         * R is intentionally the physical R key as requested.
         */
        if (!charging) {
            if (hasBaby && sprintDown && rDown && useDown) {
                charging = true;
                chargeTicks = 0;
                ModNetwork.sendBabyThrowStart();
                minecraft.player.displayClientMessage(
                        Component.literal("Cancel by Left Click"),
                        true
                );
            }
            return;
        }

        // Left click always cancels the charge. It is intentionally checked
        // before the right-click release so no throw can slip through on the
        // same client tick.
        if (attackDown) {
            ModNetwork.sendBabyThrowCancel();
            reset(false);
            return;
        }

        if (!hasBaby || !sprintDown || !rDown) {
            ModNetwork.sendBabyThrowCancel();
            reset(false);
            return;
        }

        if (useDown) {
            chargeTicks = Math.min(MAX_CHARGE_TICKS, chargeTicks + 1);
            return;
        }

        float charge = chargeTicks / (float) MAX_CHARGE_TICKS;
        ModNetwork.sendBabyThrow(charge);
        reset(false);
    }

    @SubscribeEvent
    public static void renderThrowPower(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        if (!charging) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }

        /*
         * Render from RenderGuiEvent.Post rather than waiting for the vanilla
         * JUMP_BAR overlay. Forge documents that an inactive vanilla overlay
         * does not fire its RenderGuiOverlayEvent at all, which is exactly what
         * happens when the player is not riding a horse. The custom throw bar
         * therefore owns its render pass and simply uses the vanilla horse/XP
         * bar geometry: 182x5, centered, y = height - 29.
         */
        int width = 182;
        int height = 5;
        int x = (event.getWindow().getGuiScaledWidth() - width) / 2;
        int y = event.getWindow().getGuiScaledHeight() - 29;

        float power = Math.max(0.0F, Math.min(1.0F,
                chargeTicks / (float) MAX_CHARGE_TICKS));
        int fill = Math.round(width * power);

        GuiGraphics graphics = event.getGuiGraphics();
        ResourceLocation bar = new ResourceLocation(
                "normalnpcplayer",
                "textures/gui/throw_power_bar.png"
        );

        // Our own Minecraft-style blue bar. Row 0 is the empty frame; row 1
        // is the blue fill. Both are 182x5 and are rendered at native GUI size.
        graphics.blit(bar, x, y, 0, 0, width, height, width, 10);
        if (fill > 0) {
            graphics.blit(bar, x, y, 0, height, fill, height, width, 10);
        }
    }

    private static boolean hasCarriedBabyWithFreeOtherHand(Minecraft minecraft) {
        var main = minecraft.player.getItemInHand(InteractionHand.MAIN_HAND);
        var off = minecraft.player.getItemInHand(InteractionHand.OFF_HAND);

        // Baby in either hand is valid, but the other hand must be empty.
        return BabyNPCPlayerItem.isBabyStack(main)
                ? off.isEmpty()
                : BabyNPCPlayerItem.isBabyStack(off) && main.isEmpty();
    }

    private static boolean isRKeyDown(Minecraft minecraft) {
        long window = minecraft.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
    }

    private static void reset(boolean sendCancel) {
        if (sendCancel) {
            ModNetwork.sendBabyThrowCancel();
        }
        charging = false;
        chargeTicks = 0;
    }
}
