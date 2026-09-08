package net.devatnoter.normalnpcplayer.client.screen;

import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.menu.BabyInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.devatnoter.normalnpcplayer.registry.ModEntities;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.devatnoter.normalnpcplayer.equipment.BabyEquipmentLogic;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom Baby inventory screen using vanilla Minecraft visual language.
 * It deliberately does not use the Player Inventory screen layout.
 */
public final class BabyInventoryScreen extends AbstractContainerScreen<BabyInventoryMenu> {
    private BabyNPCPlayerEntity itemPreview;
    private static final ResourceLocation INVENTORY_TEXTURE =
            AbstractContainerScreen.INVENTORY_LOCATION;

    private static final int SLOT_U = 7;
    private static final int SLOT_V = 83;

    public BabyInventoryScreen(
            BabyInventoryMenu menu,
            net.minecraft.world.entity.player.Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 230;
        titleLabelY = 6;
        inventoryLabelY = 134;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // Vanilla container screens dim the world behind the GUI. Keep the
        // same interaction model, but use a single black translucent veil
        // across the entire screen so the Baby GUI remains visually stable.
        graphics.fill(0, 0, width, height, 0x99000000);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        final int x = leftPos;
        final int y = topPos;

        // Minecraft 1.20.1 container geometry: 18 px slot pitch, 8 px
        // left inset, and the same classic inventory panel treatment.
        // The panel is extended vertically only because this screen contains
        // Baby inventory in addition to the owner's inventory.
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFFC6C6C6);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + 2, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 2, y + imageHeight - 1, 0xFFFFFFFF);
        graphics.fill(x + 1, y + imageHeight - 2, x + imageWidth - 1, y + imageHeight - 1, 0xFF555555);
        graphics.fill(x + imageWidth - 2, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFF555555);

        // Baby equipment: exact vanilla 18 px vertical pitch.
        drawSlot(graphics, x + 34, y + 24);
        drawSlot(graphics, x + 34, y + 42);
        drawSlot(graphics, x + 34, y + 60);
        drawSlot(graphics, x + 34, y + 78);
        drawSlot(graphics, x + 124, y + 24);
        drawSlot(graphics, x + 124, y + 42);

        // Baby's nine-slot storage row.
        drawSlotRow(graphics, x + 8, y + 112, 9);

        // Owner inventory: vanilla 3 x 9 + 9-slot hotbar geometry.
        drawSlotGrid(graphics, x + 8, y + 146, 9, 3);
        drawSlotRow(graphics, x + 8, y + 204, 9);

        BabyNPCPlayerEntity baby = menu.getBaby();
        if (baby == null && menu.isItemMode()) {
            baby = getOrCreateItemPreview();
        }
        // Keep the preview viewport black regardless of whether the entity
        // has finished being created. This is the vanilla-style render well
        // behind the Baby model.
        graphics.fill(x + 52, y + 24, x + 124, y + 96, 0xFF000000);

        if (baby != null) {
            // Item mode uses a client-only render proxy. A render proxy is not
            // gameplay, so never gate it on isAlive(). Keep its lifecycle
            // stable while the inventory/equipment NBT changes.
            if (menu.isItemMode()) {
                syncPreviewEquipment(baby, menu.getBabyItemData());
            }
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics,
                    x + 88,
                    y + 91,
                    54,
                    (float) (x + 88 - mouseX),
                    (float) (y + 72 - mouseY),
                    baby
            );
        }
    }


    private BabyNPCPlayerEntity getOrCreateItemPreview() {
        CompoundTag data = menu.getBabyItemData();
        if (data == null || data.isEmpty() || minecraft.level == null) return null;

        // The preview is created once per opened Item GUI. Equipment and
        // inventory edits must NOT recreate the gameplay entity proxy every
        // time a slot changes; that was the reason the model disappeared.
        if (itemPreview != null) {
            return itemPreview;
        }

        // saveWithoutId() intentionally does not contain an entity-type ID, so
        // EntityType.create(data, level) cannot reconstruct this Baby. Create the
        // registered Baby entity first, then load the serialized Baby state into
        // that client-only render proxy.
        BabyNPCPlayerEntity preview = ModEntities.BABY_NPC_PLAYER.get().create(minecraft.level);
        if (preview == null) return null;
        preview.load(data.copy());
        // itemRenderMode is runtime-only and is intentionally not serialized.
        // It is required by the Baby's GeckoLib controller when rendering a
        // Baby that exists only as an ItemStack.
        preview.setItemRenderMode(true);
        preview.setInvisible(false);

        preview.noPhysics = true;
        preview.setNoGravity(true);
        preview.setDeltaMovement(Vec3.ZERO);
        preview.setPos(0.0D, 0.0D, 0.0D);
        preview.setYRot(0.0F);
        preview.setXRot(0.0F);
        preview.yRotO = 0.0F;
        preview.xRotO = 0.0F;
        preview.yHeadRot = 0.0F;
        preview.yHeadRotO = 0.0F;
        preview.yBodyRot = 0.0F;
        preview.yBodyRotO = 0.0F;
        // A preview must remain renderable even if the serialized Baby was at
        // zero health when the Item GUI was opened. Its health is irrelevant
        // to rendering.
        if (preview.getHealth() <= 0.0F) {
            preview.setHealth(Math.max(1.0F, preview.getMaxHealth()));
        }
        syncPreviewEquipment(preview, data);
        itemPreview = preview;
        return itemPreview;
    }

    private static void syncPreviewEquipment(BabyNPCPlayerEntity preview, CompoundTag data) {
        if (preview == null || data == null) return;
        CompoundTag equipment = BabyEquipmentLogic.getOrCreateEquipment(data);
        preview.setItemSlot(EquipmentSlot.MAINHAND, BabyEquipmentLogic.read(equipment, "MainHand"));
        preview.setItemSlot(EquipmentSlot.OFFHAND, BabyEquipmentLogic.read(equipment, "OffHand"));
        preview.setItemSlot(EquipmentSlot.HEAD, BabyEquipmentLogic.read(equipment, "Head"));
        preview.setItemSlot(EquipmentSlot.CHEST, BabyEquipmentLogic.read(equipment, "Chest"));
        preview.setItemSlot(EquipmentSlot.LEGS, BabyEquipmentLogic.read(equipment, "Legs"));
        preview.setItemSlot(EquipmentSlot.FEET, BabyEquipmentLogic.read(equipment, "Feet"));
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.blit(INVENTORY_TEXTURE, x, y, SLOT_U, SLOT_V, 18, 18, 256, 256);
    }

    private static void drawSlotRow(GuiGraphics graphics, int x, int y, int count) {
        for (int i = 0; i < count; i++) drawSlot(graphics, x + i * 18, y);
    }

    private static void drawSlotGrid(GuiGraphics graphics, int x, int y, int cols, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                drawSlot(graphics, x + col * 18, y + row * 18);
            }
        }
    }

    /**
     * Minecraft 1.20.1 container-screen render order:
     * 1) dim the world, 2) render the container + slots, 3) render the
     * mouse-over tooltip last.  AbstractContainerScreen does not make the
     * tooltip pass explicit for custom screens, so we do it here.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.literal("Baby Equipment"), 8, 7, 0x404040, false);
        graphics.drawString(font, Component.literal("Baby Inventory"), 8, 102, 0x404040, false);
        graphics.drawString(font, Component.literal("Owner Inventory"), 8, 136, 0x404040, false);
    }
}
