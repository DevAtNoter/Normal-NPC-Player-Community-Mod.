package net.devatnoter.normalnpcplayer.menu;

import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.registry.ModMenus;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Baby inventory + the opening player's normal inventory.
 * The Baby side is intentionally small: 9 storage slots + 6 equipment slots.
 */
public final class BabyInventoryMenu extends AbstractContainerMenu {
    private final BabyNPCPlayerEntity baby;
    private final Container babyInventory;
    private final Inventory playerInventory;

    public BabyInventoryMenu(int id, Inventory playerInventory, BabyNPCPlayerEntity baby) {
        super(ModMenus.BABY_INVENTORY.get(), id);
        this.playerInventory = playerInventory;
        this.baby = baby;
        this.babyInventory = baby != null
                ? baby.getBabyInventory()
                : new net.minecraft.world.SimpleContainer(9);

        // Baby storage: one vanilla-style 9-slot row.
        for (int i = 0; i < 9; i++) {
            final int slot = i;
            addSlot(new Slot(babyInventory, i, 8 + i * 18, 112) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !isBabyCarrier(stack);
                }
            });
        }

        // Baby equipment, using the same slot geometry as the vanilla player inventory.
        addSlot(new EquipmentSlotView(baby, EquipmentSlot.HEAD, 8, 24));
        addSlot(new EquipmentSlotView(baby, EquipmentSlot.CHEST, 8, 42));
        addSlot(new EquipmentSlotView(baby, EquipmentSlot.LEGS, 8, 60));
        addSlot(new EquipmentSlotView(baby, EquipmentSlot.FEET, 8, 78));
        addSlot(new EquipmentSlotView(baby, EquipmentSlot.MAINHAND, 134, 24));
        addSlot(new EquipmentSlotView(baby, EquipmentSlot.OFFHAND, 134, 42));

        // Opening player's main inventory: 27 slots.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        8 + col * 18, 146 + row * 18));
            }
        }

        // Opening player's hotbar: 9 slots.
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col,
                    8 + col * 18, 204));
        }

        // Freeze the real Baby while its owner is using this menu. This is
        // server-side state only; it does not alter the head-riding system.
        if (baby != null && !playerInventory.player.level().isClientSide
                && (baby.getOwnerUUID() == null
                    || baby.getOwnerUUID().equals(playerInventory.player.getUUID())
                    || baby.isPlayerBabyOwner(playerInventory.player))) {
            baby.setInventoryOpen(true, playerInventory.player.getUUID());
        }
    }

    public BabyInventoryMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
        this(id, playerInventory,
                playerInventory.player.level().getEntity(data.readVarInt()) instanceof BabyNPCPlayerEntity baby
                        ? baby : null);
    }

    private static boolean isBabyCarrier(ItemStack stack) {
        return stack.getItem() instanceof net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
    }

    public BabyNPCPlayerEntity getBaby() {
        return baby;
    }

    @Override
    public boolean stillValid(Player player) {
        return baby != null
                && baby.isAlive()
                && player.distanceToSqr(baby) <= 64.0D
                && (baby.getOwnerUUID() == null
                    || baby.getOwnerUUID().equals(player.getUUID())
                    || baby.isPlayerBabyOwner(player));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot source = slots.get(index);
        if (!source.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack original = source.getItem().copy();
        ItemStack moving = source.getItem();

        final int BABY_STORAGE_START = 0;
        final int BABY_STORAGE_END = 9;
        final int BABY_EQUIPMENT_START = 9;
        final int BABY_EQUIPMENT_END = 15;
        final int PLAYER_START = 15;
        final int PLAYER_END = 51;

        if (index < BABY_EQUIPMENT_END) {
            if (!moveItemStackTo(moving, PLAYER_START, PLAYER_END, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Prefer an equipment slot when the item has a natural armor slot.
            if (!moveIntoBabyEquipment(moving, BABY_EQUIPMENT_START, BABY_EQUIPMENT_END)) {
                if (!moveItemStackTo(moving, BABY_STORAGE_START, BABY_STORAGE_END, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (moving.isEmpty()) {
            source.set(ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        return original;
    }

    private boolean moveIntoBabyEquipment(ItemStack stack, int start, int end) {
        for (int i = start; i < end; i++) {
            Slot slot = slots.get(i);
            if (slot.mayPlace(stack) && !slot.hasItem()) {
                ItemStack one = stack.split(1);
                slot.set(one);
                slot.setChanged();
                return true;
            }
        }
        return false;
    }

    @Override
    public void removed(Player player) {
        if (baby != null && !player.level().isClientSide
                && baby.getInventoryViewerUUID() != null
                && baby.getInventoryViewerUUID().equals(player.getUUID())) {
            baby.setInventoryOpen(false, null);
        }
        super.removed(player);
    }

    private static final class EquipmentSlotView extends Slot {
        private final BabyNPCPlayerEntity baby;
        private final EquipmentSlot equipmentSlot;

        private EquipmentSlotView(BabyNPCPlayerEntity baby, EquipmentSlot equipmentSlot, int x, int y) {
            super(new net.minecraft.world.SimpleContainer(1), 0, x, y);
            this.baby = baby;
            this.equipmentSlot = equipmentSlot;
        }

        @Override
        public ItemStack getItem() {
            return baby == null ? ItemStack.EMPTY : baby.getItemBySlot(equipmentSlot);
        }

        @Override
        public void set(ItemStack stack) {
            if (baby != null) {
                baby.setItemSlot(equipmentSlot, stack.copy());
            }
            setChanged();
        }

        @Override
        public ItemStack remove(int amount) {
            ItemStack current = getItem();
            if (current.isEmpty()) return ItemStack.EMPTY;
            ItemStack removed = current.copy();
            removed.setCount(Math.min(amount, current.getCount()));
            current.shrink(removed.getCount());
            set(current);
            return removed;
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            ResourceLocation sprite;

            switch (equipmentSlot) {
                case HEAD -> sprite = InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
                case CHEST -> sprite = InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
                case LEGS -> sprite = InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
                case FEET -> sprite = InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
                case OFFHAND -> sprite = InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
                case MAINHAND -> sprite = new ResourceLocation(
                        "minecraft",
                        "item/empty_slot_sword"
                );
                default -> {
                    return null;
                }
            }

            return Pair.of(InventoryMenu.BLOCK_ATLAS, sprite);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (baby == null || isBabyCarrier(stack)) return false;
            return switch (equipmentSlot) {
                case HEAD, CHEST, LEGS, FEET -> stack.getItem() instanceof ArmorItem armor
                        && armor.getEquipmentSlot() == equipmentSlot;
                case MAINHAND, OFFHAND -> true;
                default -> false;
            };
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
