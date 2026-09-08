package net.devatnoter.normalnpcplayer.client;

import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.entity.UnknownPlayerMobEntity;
import net.minecraftforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.devatnoter.normalnpcplayer.network.ModNetwork;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = "normalnpcplayer",
        value = net.minecraftforge.api.distmarker.Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class ClientEvents {

    private ClientEvents() {
    }

    /**
     * Unknown Player NPC chat is delivered through vanilla's disguised-chat
     * path because the NPC is not a real signed PlayerChatSession. Forge fires
     * ClientChatReceivedEvent after vanilla has decorated the message.
     *
     * Keep that decoration (so the line remains normal <Name> message chat),
     * but feed the final Component through ChatComponent.addMessage(Component)
     * instead of the signature/tag overload. This removes only the message
     * indicator on NPC speech; real player chat is untouched.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUnknownPlayerNpcChat(ClientChatReceivedEvent event) {
        if (event.isCanceled()) {
            return;
        }

        if (event instanceof ClientChatReceivedEvent.System) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        if (event.getSender() == null) {
            return;
        }

        net.minecraft.world.entity.Entity entity = null;

        for (net.minecraft.world.entity.Entity candidate : minecraft.level.entitiesForRendering()) {
            if (event.getSender().equals(candidate.getUUID())) {
                entity = candidate;
                break;
            }
        }

        if (!(entity instanceof UnknownPlayerMobEntity)) {
            return;
        }

        event.setCanceled(true);
        minecraft.gui.getChat().addMessage(event.getMessage());
    }

    /**
     * GAMEPLAY HUD RULE:
     *
     * If a Baby carrier exists in the hotbar, Baby owns MAIN_HAND.
     * Normal 1-9 selection still happens, but this restores the Baby slot.
     *
     * Q is consumed here at the START of the client tick, before the normal
     * gameplay keybind processing can call LocalPlayer.drop().
     *
     * The check is intentionally disabled while a Screen is open, so the
     * Inventory GUI keeps its already-working Q behavior.
     */
    @SubscribeEvent
    public static void onClientTick(
            TickEvent.ClientTickEvent event
    ) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        /*
         * Inventory/container GUI: do not touch Q here.
         */
        if (minecraft.screen != null) {
            return;
        }

        int babySlot = -1;

        for (int slot = 0; slot < 9; slot++) {
            if (BabyNPCPlayerItem.isBabyStack(
                    minecraft.player.getInventory()
                            .getItem(slot)
            )) {
                babySlot = slot;
                break;
            }
        }

        if (babySlot < 0) {
            return;
        }

        /*
         * Baby in the hotbar always owns MAIN_HAND. Consume every vanilla
         * hotbar-selection key before Minecraft#handleKeybinds can process it.
         * This removes the one-tick selection flicker that a late tick reset
         * would otherwise cause. Middle-click pick-block is also consumed
         * because it can select another hotbar slot.
         */
        if (BabyNPCPlayerItem.isBabyStack(
                minecraft.player.getItemInHand(
                        InteractionHand.OFF_HAND
                )
        )) {
            return;
        }

        boolean attemptedSelection = false;
        for (net.minecraft.client.KeyMapping key : minecraft.options.keyHotbarSlots) {
            while (key.consumeClick()) {
                attemptedSelection = true;
            }
        }
        while (minecraft.options.keyPickItem.consumeClick()) {
            attemptedSelection = true;
        }

        if (attemptedSelection
                || minecraft.player.getInventory().selected != babySlot) {
            minecraft.player.getInventory().selected = babySlot;
            if (attemptedSelection) {
                minecraft.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                                "You're carrying a Baby. Press (F) to switch hands and free your hand."
                        ),
                        true
                );
            }
        }

        /*
         * Consume Q only when Baby is actually in MAIN_HAND.
         * This happens BEFORE LocalPlayer.drop() is reached.
         */
        if (BabyNPCPlayerItem.isBabyStack(
                minecraft.player.getItemInHand(
                        InteractionHand.MAIN_HAND
                )
        )) {
            while (
                    minecraft.options.keyDrop.consumeClick()
            ) {
                // Never remove the Baby from the hotbar.
            }
        }
    }

    @SubscribeEvent
    public static void onMouseScrolling(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }

        if (!BabyNPCPlayerItem.isBabyStack(minecraft.player.getMainHandItem())) {
            return;
        }

        event.setCanceled(true);
        minecraft.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        "You're carrying a Baby. Press (F) to switch hands and free your hand."
                ),
                true
        );
    }

    /**
     * While the inventory GUI is open, the Baby carrier is a hard-locked slot.
     * Consume vanilla number-key swaps before the container can process them.
     */
    /**
     * Diagnostic only. Logs the exact screen/mouse event and every hovered
     * player-inventory slot when that slot contains a Baby. No cancellation.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onCreativeBabyMouseDebug(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        boolean found = false;

        for (net.minecraft.world.inventory.Slot slot : screen.getMenu().slots) {
            int x = screen.getGuiLeft() + slot.x;
            int y = screen.getGuiTop() + slot.y;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                found = true;
                NormalNPCPlayer.LOGGER.info(
                        "[BABY-CREATIVE-DEBUG] MOUSE PRE | x={} y={} button={} screen={} menuSlot={} containerSlot={} container={} item={} count={} shift={} ctrl={} alt={} carried={}",
                        mouseX, mouseY, event.getButton(), screen.getClass().getSimpleName(),
                        screen.getMenu().slots.indexOf(slot), slot.getContainerSlot(),
                        slot.container.getClass().getSimpleName(), slot.getItem().getItem(),
                        slot.getItem().getCount(), net.minecraft.client.gui.screens.Screen.hasShiftDown(),
                        net.minecraft.client.gui.screens.Screen.hasControlDown(),
                        net.minecraft.client.gui.screens.Screen.hasAltDown(),
                        screen.getMenu().getCarried().getItem()
                );
                if (BabyNPCPlayerItem.isBabyStack(slot.getItem())) {
                    NormalNPCPlayer.LOGGER.info("[BABY-CREATIVE-DEBUG] MOUSE PRE -> THIS IS BABY");
                }
                break;
            }
        }

        if (!found) {
            NormalNPCPlayer.LOGGER.info(
                    "[BABY-CREATIVE-DEBUG] MOUSE PRE | x={} y={} button={} -> NO MENU SLOT UNDER CURSOR | guiLeft={} guiTop={}",
                    mouseX, mouseY, event.getButton(), screen.getGuiLeft(), screen.getGuiTop()
            );
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onPlayerInventoryBabyNumberKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        int key = event.getKeyCode();
        if (key < GLFW.GLFW_KEY_1 || key > GLFW.GLFW_KEY_9) return;

        int babySlot = hoveredBabyInventorySlot(screen, minecraft);
        if (babySlot < 0) return;

        // Number keys are never allowed to swap the Baby with another hotbar slot.
        event.setCanceled(true);
        minecraft.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        "You're carrying a Baby. Press (F) to switch hands and free your hand."
                ), true);
    }

    /**
     * Consume Shift-click while the mouse is over a Baby carrier. Vanilla
     * otherwise executes QUICK_MOVE immediately on the client and can visibly
     * move the stack before the server-side safety net restores it.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onPlayerInventoryBabyShiftClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) return;
        if (event.getButton() != 0 || !net.minecraft.client.gui.screens.Screen.hasShiftDown()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        int babySlot = hoveredBabyInventorySlot(screen, minecraft);
        if (babySlot < 0) return;

        event.setCanceled(true);
        minecraft.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        "You're carrying a Baby. Press (F) to switch hands and free your hand."
                ), true);
    }

    private static int hoveredBabyInventorySlot(
            AbstractContainerScreen<?> screen,
            Minecraft minecraft
    ) {
        for (net.minecraft.world.inventory.Slot slot : screen.getMenu().slots) {
            if (slot.container != minecraft.player.getInventory()) continue;

            int slotX = screen.getGuiLeft() + slot.x;
            int slotY = screen.getGuiTop() + slot.y;
            if (minecraft.mouseHandler.xpos() * screen.width / minecraft.getWindow().getScreenWidth() >= slotX
                    && minecraft.mouseHandler.xpos() * screen.width / minecraft.getWindow().getScreenWidth() < slotX + 16
                    && minecraft.mouseHandler.ypos() * screen.height / minecraft.getWindow().getScreenHeight() >= slotY
                    && minecraft.mouseHandler.ypos() * screen.height / minecraft.getWindow().getScreenHeight() < slotY + 16) {
                int index = slot.getContainerSlot();
                if (index >= 0 && index < minecraft.player.getInventory().getContainerSize()
                        && BabyNPCPlayerItem.isBabyStack(minecraft.player.getInventory().getItem(index))) {
                    return index;
                }
            }
        }
        return -1;
    }

    /**
     * Inventory-only F behavior for an offhand Baby. Vanilla F targets the
     * hovered slot, so hovering the offhand Baby would otherwise swap it with
     * itself. We make F the single intentional shortcut for freeing the other
     * hand: when the cursor is on the offhand Baby, swap the two player hands.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onPlayerInventoryKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen)
                && !(event.getScreen() instanceof CreativeModeInventoryScreen)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        if (BabyNPCPlayerItem.isBabyStack(
                minecraft.player.getItemInHand(InteractionHand.OFF_HAND))) {
            int key = event.getKeyCode();
            if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
                ModNetwork.sendBabyOffhandHotbarSwap(key - GLFW.GLFW_KEY_1);
                event.setCanceled(true);
                return;
            }
        }

        if (event.getKeyCode() != GLFW.GLFW_KEY_F) return;

        if (!(event.getScreen() instanceof InventoryScreen)
                && !(event.getScreen() instanceof CreativeModeInventoryScreen)) return;

        minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        ItemStack offhand = minecraft.player.getItemInHand(InteractionHand.OFF_HAND);
        if (!BabyNPCPlayerItem.isBabyStack(offhand)) return;

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        double mouseX = minecraft.mouseHandler.xpos()
                * (double) screen.width / (double) minecraft.getWindow().getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos()
                * (double) screen.height / (double) minecraft.getWindow().getScreenHeight();

        for (net.minecraft.world.inventory.Slot slot : screen.getMenu().slots) {
            if (slot.container != minecraft.player.getInventory()) continue;
            if (slot.getContainerSlot() != 40) continue;

            int slotX = screen.getGuiLeft() + slot.x;
            int slotY = screen.getGuiTop() + slot.y;
            if (mouseX >= slotX && mouseX < slotX + 16
                    && mouseY >= slotY && mouseY < slotY + 16) {
                ModNetwork.sendBabyHandSwap();
                event.setCanceled(true);
                return;
            }
        }
    }

    /**
     * New Baby Item inventory opener: while the Player Inventory is open,
     * Ctrl + right-click a Baby Item. The server remains authoritative; this
     * client event only identifies the clicked player-inventory slot.
     */
    @SubscribeEvent
    public static void onPlayerInventoryCtrlRightClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (event.getButton() != 1 || !net.minecraft.client.gui.screens.Screen.hasShiftDown()) return;

        // Only these two vanilla screens are allowed to open the Baby Item
        // inventory. Do not use hard-coded 176x166 coordinates here: the
        // Creative Inventory has a different layout. Instead, resolve the
        // actual Slot under the mouse from the screen's menu.
        if (!(screen instanceof InventoryScreen)
                && !(screen instanceof CreativeModeInventoryScreen)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        int mouseX = (int) event.getMouseX();
        int mouseY = (int) event.getMouseY();
        int slotIndex = -1;

        for (net.minecraft.world.inventory.Slot slot : screen.getMenu().slots) {
            if (slot.container != minecraft.player.getInventory()) continue;

            int slotX = screen.getGuiLeft() + slot.x;
            int slotY = screen.getGuiTop() + slot.y;
            if (mouseX >= slotX && mouseX < slotX + 16
                    && mouseY >= slotY && mouseY < slotY + 16) {
                slotIndex = slot.getContainerSlot();
                break;
            }
        }

        if (slotIndex < 0
                || slotIndex >= minecraft.player.getInventory().getContainerSize()) return;
        if (!BabyNPCPlayerItem.isBabyStack(
                minecraft.player.getInventory().getItem(slotIndex))) return;

        ModNetwork.sendOpenBabyItemInventory(slotIndex);
        event.setCanceled(true);
    }


    /**
     * Direct Baby Item equipment while the Baby is carried in the Player
     * Inventory. The clicked Baby Item remains the target; the item currently
     * held by the container cursor is applied/swapped server-side.
     *
     * Controls:
     * - Left click + Armor -> matching Baby armor slot.
     * - Left Ctrl + Left click -> Baby main hand.
     * - Left Ctrl + Right click -> Baby offhand.
     */
    @SubscribeEvent
    public static void onCarriedBabyEquipmentClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (!(screen instanceof InventoryScreen)
                && !(screen instanceof CreativeModeInventoryScreen)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.containerMenu == null) return;

        final int button = event.getButton();
        final boolean control = net.minecraft.client.gui.screens.Screen.hasControlDown();

        // Resolve the actual player-inventory slot under the mouse. This keeps
        // the behavior independent of the vanilla screen's layout.
        int babySlot = -1;
        for (net.minecraft.world.inventory.Slot slot : screen.getMenu().slots) {
            if (slot.container != minecraft.player.getInventory()) continue;
            int slotX = screen.getGuiLeft() + slot.x;
            int slotY = screen.getGuiTop() + slot.y;
            if (event.getMouseX() >= slotX && event.getMouseX() < slotX + 16
                    && event.getMouseY() >= slotY && event.getMouseY() < slotY + 16) {
                int containerSlot = slot.getContainerSlot();
                if (containerSlot >= 0
                        && containerSlot < minecraft.player.getInventory().getContainerSize()
                        && BabyNPCPlayerItem.isBabyStack(minecraft.player.getInventory().getItem(containerSlot))) {
                    babySlot = containerSlot;
                }
                break;
            }
        }
        if (babySlot < 0) return;

        ItemStack carried = screen.getMenu().getCarried();
        if (carried.isEmpty() || BabyNPCPlayerItem.isBabyStack(carried)) return;

        EquipmentSlot target = null;

        if (button == 0 && !control && carried.getItem() instanceof ArmorItem armor) {
            target = armor.getEquipmentSlot();
        } else if (control && button == 0) {
            target = EquipmentSlot.MAINHAND;
        } else if (control && button == 1) {
            target = EquipmentSlot.OFFHAND;
        }

        if (target == null) return;

        ModNetwork.sendBabyItemEquipmentAction(babySlot, target);
        event.setCanceled(true);
    }

}
