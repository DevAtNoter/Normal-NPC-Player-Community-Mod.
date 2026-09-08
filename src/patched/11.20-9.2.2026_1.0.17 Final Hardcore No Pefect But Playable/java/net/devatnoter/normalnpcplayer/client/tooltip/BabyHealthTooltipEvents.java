package net.devatnoter.normalnpcplayer.client.tooltip;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Either;
import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.devatnoter.normalnpcplayer.client.screen.BabyInventoryScreen;
import net.devatnoter.normalnpcplayer.network.BabyItemClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
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

            // Inside Baby Inventory every hovered item uses the normal
            // Minecraft item tooltip. The Baby-card tooltip is only for the
            // Baby Item in ordinary inventory contexts.
            if (Minecraft.getInstance().screen instanceof BabyInventoryScreen) {
                return;
            }

            if (!BabyNPCPlayerItem.isBabyStack(stack)) {
                return;
            }

            // Keep the ItemStack itself untouched. Only the Baby status
            // numbers and active effects are replaced in the tooltip snapshot.
            var data = BabyNPCPlayerItem.copyEntityData(stack);
            if (data.hasUUID("UUID")) {
                BabyItemClientState.State live = BabyItemClientState.get(data.getUUID("UUID"));
                if (live != null) {
                    live.applyTo(data);
                }
            }

            // GatherComponents is part of the vanilla tooltip construction
            // pipeline. At this point the mouse still refers to the actual
            // hovered slot; the tooltip panel has not been drawn over it yet.
            // Use that concrete slot only to detect a REAL hover change.
            // Rebuilding this component every frame is normal vanilla behavior
            // and must never reset scroll state.
            if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> containerScreen) {
                var slot = containerScreen.getSlotUnderMouse();
                if (slot != null && BabyNPCPlayerItem.isBabyStack(slot.getItem())) {
                    CompoundTag slotData = BabyNPCPlayerItem.copyEntityData(slot.getItem());
                    String identity = slotData.hasUUID("UUID")
                            ? slotData.getUUID("UUID").toString()
                            : "slot:" + slot.index;
                    BabyHealthTooltipClientComponent.beginHoverSession(identity);
                }
            }

            event.getTooltipElements().clear();
            event.getTooltipElements().add(
                    Either.right(
                            (TooltipComponent) new BabyHealthTooltipComponent(data)
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

        /*
         * Do not use ScreenEvent.Render.Post to drive tooltip hover state.
         * Tooltip rendering is itself allowed to rebuild every frame, and a
         * tooltip can cover the cursor so getSlotUnderMouse() may transiently
         * become null. GatherComponents above is the correct place to observe
         * the actual tooltip source before the panel is rendered.
         */

        /**
         * Keep the Baby tip hover lifecycle independent from tooltip rebuilding.
         * Vanilla may rebuild the tooltip every frame, but moving the cursor to
         * another slot or empty screen area must end the Baby hover session so
         * returning to the Baby can select a new tip.
         *
         * This handler only touches the Baby tip selector; it does not change
         * tooltip positioning, scrolling, inventory state, or item data.
         */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void screenRenderPre(ScreenEvent.Render.Pre event) {
            if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen)) {
                BabyHealthTooltipClientComponent.resetHoverSession();
                lastHoveredSlot = null;
                return;
            }

            // Forge 1.20.1 has no ScreenEvent.MouseMoved. Render.Pre runs
            // before the screen/tooltips are drawn, so the hovered slot is
            // still available here without changing any tooltip rendering.
            var slot = containerScreen.getSlotUnderMouse();
            if (slot == null || !BabyNPCPlayerItem.isBabyStack(slot.getItem())) {
                BabyHealthTooltipClientComponent.resetHoverSession();
                lastHoveredSlot = slot;
                return;
            }

            if (slot != lastHoveredSlot) {
                lastHoveredSlot = slot;
                CompoundTag slotData = BabyNPCPlayerItem.copyEntityData(slot.getItem());
                String identity = slotData.hasUUID("UUID")
                        ? slotData.getUUID("UUID").toString()
                        : "slot:" + slot.index;
                BabyHealthTooltipClientComponent.beginHoverSession(identity);
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
