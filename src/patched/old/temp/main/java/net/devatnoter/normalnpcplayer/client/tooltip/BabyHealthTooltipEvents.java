package net.devatnoter.normalnpcplayer.client.tooltip;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Either;
import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Client-side Baby tooltip registration, centering and SHIFT scrolling. */
@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        value = net.minecraftforge.api.distmarker.Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class BabyHealthTooltipEvents {

    private static final int SCREEN_MARGIN = 12;
    private static net.minecraft.world.inventory.Slot lastHoveredSlot;

    private BabyHealthTooltipEvents() {
    }

    @SubscribeEvent
    public static void registerClientTooltipFactory(
            RegisterClientTooltipComponentFactoriesEvent event
    ) {
        // One composite component owns both the compact summary and the SHIFT
        // detail viewport. This prevents multiple tooltip components from
        // fighting over width/height/position.
        event.register(
                BabyHealthTooltipComponent.class,
                BabyHealthTooltipClientComponent::new
        );
    }

    @Mod.EventBusSubscriber(
            modid = NormalNPCPlayer.MOD_ID,
            value = net.minecraftforge.api.distmarker.Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.FORGE
    )
    public static final class GatherEvents {

        private GatherEvents() {
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void gather(RenderTooltipEvent.GatherComponents event) {
            ItemStack stack = event.getItemStack();

            if (!BabyNPCPlayerItem.isBabyStack(stack)) {
                BabyHealthTooltipClientComponent.resetScroll();
                BabyHealthTooltipClientComponent.resetHoverSession();
                return;
            }

            event.getTooltipElements().clear();
            event.getTooltipElements().add(
                    Either.right(
                            (TooltipComponent) new BabyHealthTooltipComponent(
                                    BabyNPCPlayerItem.copyEntityData(stack)
                            )
                    )
            );

            event.setMaxWidth(
                    Math.min(240, Math.max(180, event.getScreenWidth() - SCREEN_MARGIN * 2))
            );
        }

        /*
         * Do NOT override RenderTooltipEvent.Pre positioning here.
         *
         * Vanilla already positions item tooltips relative to the hovered
         * slot/item and flips the tooltip when it would leave the screen.
         * The old centered-panel override was the reason the normal Baby
         * tooltip ignored the hovered item position.
         */

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void positionShiftTooltip(RenderTooltipEvent.Pre event) {
            if (!BabyNPCPlayerItem.isBabyStack(event.getItemStack())
                    || !isShiftDown()) {
                return;
            }

            for (var component : event.getComponents()) {
                if (component instanceof BabyHealthTooltipClientComponent babyTooltip) {
                    int detailHeight = babyTooltip.getHeight();

                    // Keep the detail frame vertically anchored to the hovered
                    // item/cursor. Vanilla still handles the horizontal side
                    // choice and screen overflow.
                    int anchoredY = event.getY() - detailHeight / 2;
                    event.setY(anchoredY);
                    break;
                }
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void screenRender(ScreenEvent.Render.Post event) {
            if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen)) {
                lastHoveredSlot = null;
                BabyHealthTooltipClientComponent.resetHoverSession();
                return;
            }

            var slot = containerScreen.getSlotUnderMouse();
            if (slot == null || !BabyNPCPlayerItem.isBabyStack(slot.getItem())) {
                lastHoveredSlot = null;
                BabyHealthTooltipClientComponent.resetHoverSession();
                return;
            }

            // A new slot is a new hover session, even when the Baby stacks
            // contain identical data. This is the actual mouse-hover boundary;
            // do not infer it from tooltip rebuild timing.
            if (slot != lastHoveredSlot) {
                BabyHealthTooltipClientComponent.resetHoverSession();
                lastHoveredSlot = slot;
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void screenClosing(ScreenEvent.Closing event) {
            lastHoveredSlot = null;
            BabyHealthTooltipClientComponent.resetHoverSession();
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void mouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
            if (!isShiftDown()
                    || !BabyHealthTooltipClientComponent.hasScrollableContent()) {
                return;
            }

            BabyHealthTooltipClientComponent.scroll(event.getScrollDelta());
            event.setCanceled(true);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void keyPressed(ScreenEvent.KeyPressed.Pre event) {
            if (!isShiftDown()
                    || !BabyHealthTooltipClientComponent.hasScrollableContent()) {
                return;
            }

            int key = event.getKeyCode();
            if (key == GLFW.GLFW_KEY_DOWN) {
                BabyHealthTooltipClientComponent.scroll(-1.0D);
                event.setCanceled(true);
            } else if (key == GLFW.GLFW_KEY_UP) {
                BabyHealthTooltipClientComponent.scroll(1.0D);
                event.setCanceled(true);
            } else if (key == GLFW.GLFW_KEY_PAGE_DOWN) {
                BabyHealthTooltipClientComponent.scrollPage(1);
                event.setCanceled(true);
            } else if (key == GLFW.GLFW_KEY_PAGE_UP) {
                BabyHealthTooltipClientComponent.scrollPage(-1);
                event.setCanceled(true);
            }
        }

        private static boolean isShiftDown() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getWindow() == null) {
                return false;
            }

            long window = minecraft.getWindow().getWindow();
            return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        }
    }
}
