package net.devatnoter.normalnpcplayer.client;

import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ScreenEvent;
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
         * Baby in the hotbar always has MAIN_HAND priority.
         */
        if (!BabyNPCPlayerItem.isBabyStack(
                minecraft.player.getItemInHand(
                        InteractionHand.OFF_HAND
                )
        )) {
            minecraft.player.getInventory().selected =
                    babySlot;
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
