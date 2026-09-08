package net.devatnoter.normalnpcplayer.menu;

import com.mojang.datafixers.util.Pair;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.equipment.BabyEquipmentLogic;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.devatnoter.normalnpcplayer.registry.ModMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/** Baby inventory + the opening player's normal inventory. */
public final class BabyInventoryMenu extends AbstractContainerMenu {
    private final BabyNPCPlayerEntity baby;
    private final Inventory playerInventory;
    private final ItemStack babyItem;
    private final int babyItemSlot;
    private final CompoundTag babyItemData;
    private final BabyItemInventoryContainer itemContainer;
    private final BabyEquipmentContainer equipmentContainer;

    public BabyInventoryMenu(int id, Inventory playerInventory, BabyNPCPlayerEntity baby) {
        super(ModMenus.BABY_INVENTORY.get(), id);
        this.playerInventory = playerInventory;
        this.baby = baby;
        this.babyItem = ItemStack.EMPTY;
        this.babyItemSlot = -1;
        this.babyItemData = null;
        this.itemContainer = null;
        this.equipmentContainer = new BabyEquipmentContainer(baby, null);
        addBabySlots(baby == null ? new net.minecraft.world.SimpleContainer(9) : baby.getBabyInventory());
        addEquipmentSlots();
        addPlayerSlots(playerInventory);
        if (baby != null && !playerInventory.player.level().isClientSide
                && (baby.getOwnerUUID() == null || baby.getOwnerUUID().equals(playerInventory.player.getUUID()) || baby.isPlayerBabyOwner(playerInventory.player))) {
            baby.setInventoryOpen(true, playerInventory.player.getUUID());
        }
    }

    public BabyInventoryMenu(int id, Inventory playerInventory, ItemStack babyItem, CompoundTag data, int babyItemSlot) {
        super(ModMenus.BABY_INVENTORY.get(), id);
        this.playerInventory = playerInventory;
        this.baby = null;
        this.babyItem = babyItem;
        this.babyItemSlot = babyItemSlot;
        this.babyItemData = data == null ? new CompoundTag() : data;
        this.itemContainer = new BabyItemInventoryContainer(babyItem, this.babyItemData);
        this.equipmentContainer = new BabyEquipmentContainer(null, this.babyItemData);
        addBabySlots(itemContainer);
        addEquipmentSlots();
        addPlayerSlots(playerInventory);
    }

    public BabyInventoryMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
        this(id, playerInventory, decode(playerInventory, data));
    }

    private BabyInventoryMenu(int id, Inventory playerInventory, Decoded decoded) {
        super(ModMenus.BABY_INVENTORY.get(), id);
        this.playerInventory = playerInventory;
        this.baby = decoded.baby;
        this.babyItem = decoded.item;
        this.babyItemSlot = decoded.itemSlot;
        this.babyItemData = decoded.data;
        this.itemContainer = decoded.item == null || decoded.item.isEmpty() ? null : new BabyItemInventoryContainer(decoded.item, decoded.data);
        this.equipmentContainer = new BabyEquipmentContainer(decoded.baby, decoded.data);
        addBabySlots(baby != null ? baby.getBabyInventory() : itemContainer);
        addEquipmentSlots();
        addPlayerSlots(playerInventory);
    }

    private static Decoded decode(Inventory inv, FriendlyByteBuf data) {
        int mode = data.readByte();
        if (mode == 0) {
            int entityId = data.readVarInt();
            var entity = inv.player.level().getEntity(entityId);
            return new Decoded(entity instanceof BabyNPCPlayerEntity b ? b : null, ItemStack.EMPTY, -1, null);
        }
        int slot = data.readVarInt();
        CompoundTag nbt = data.readNbt();
        ItemStack stack = slot >= 0 && slot < inv.getContainerSize() ? inv.getItem(slot) : ItemStack.EMPTY;
        return new Decoded(null, stack, slot, nbt == null ? new CompoundTag() : nbt);
    }

    private record Decoded(BabyNPCPlayerEntity baby, ItemStack item, int itemSlot, CompoundTag data) {}

    private void addBabySlots(Container container) {
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(container, i, 8 + i * 18, 112) {
                @Override public boolean mayPlace(ItemStack stack) { return !BabyNPCPlayerItem.isBabyStack(stack); }
            });
        }
    }

    private void addEquipmentSlots() {
        addSlot(new EquipmentSlotView(EquipmentSlot.HEAD, 0, 34, 24));
        addSlot(new EquipmentSlotView(EquipmentSlot.CHEST, 1, 34, 42));
        addSlot(new EquipmentSlotView(EquipmentSlot.LEGS, 2, 34, 60));
        addSlot(new EquipmentSlotView(EquipmentSlot.FEET, 3, 34, 78));
        addSlot(new EquipmentSlotView(EquipmentSlot.MAINHAND, 4, 124, 24));
        addSlot(new EquipmentSlotView(EquipmentSlot.OFFHAND, 5, 124, 42));
    }

    private void addPlayerSlots(Inventory inv) {
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 146 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 204));
    }

    public BabyNPCPlayerEntity getBaby() { return baby; }
    public boolean isItemMode() { return baby == null && itemContainer != null; }
    public ItemStack getBabyItem() { return babyItem; }
    public CompoundTag getBabyItemData() { return babyItemData; }

    @Override public boolean stillValid(Player player) {
        if (isItemMode()) {
            return babyItemSlot >= 0 && babyItemSlot < player.getInventory().getContainerSize()
                    && BabyNPCPlayerItem.isBabyStack(player.getInventory().getItem(babyItemSlot));
        }
        return baby != null && baby.isAlive() && player.distanceToSqr(baby) <= 64.0D
                && (baby.getOwnerUUID() == null || baby.getOwnerUUID().equals(player.getUUID()) || baby.isPlayerBabyOwner(player));
    }

    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
        /*
         * A Baby carrier is never a valid payload for any Baby-side slot.
         */
        if (slotId >= 0 && slotId < 15
                && slotId < slots.size()
                && BabyNPCPlayerItem.isBabyStack(getCarried())) {
            return;
        }

        /*
         * Chest equipment accepts both a chestplate and an Elytra.  Elytra is
         * not an ArmorItem, so handle the direct cursor -> Baby chest-slot
         * replacement explicitly.  When the slot already contains a
         * chestplate, the chestplate goes back to the cursor (true swap),
         * matching normal inventory drag/click behaviour.
         */
        /*
         * Also support the actual inventory drag gesture (QUICK_CRAFT).
         * Vanilla quick-craft does not replace an occupied armor slot, so let
         * vanilla finish its normal drag bookkeeping first and then perform
         * the Elytra/chestplate swap if the cursor is still holding the
         * Elytra.  This keeps the normal quick-craft state machine intact.
         */
        if (slotId == 10
                && clickType == net.minecraft.world.inventory.ClickType.QUICK_CRAFT
                && !getCarried().isEmpty()
                && getCarried().getItem() instanceof net.minecraft.world.item.ElytraItem
                && (button >> 2) == 2) {
            super.clicked(slotId, button, clickType, player);

            ItemStack carriedAfterDrag = getCarried().copy();
            Slot chestSlot = slots.get(slotId);
            ItemStack equippedAfterDrag = chestSlot.getItem().copy();

            if (!carriedAfterDrag.isEmpty()
                    && carriedAfterDrag.getItem() instanceof net.minecraft.world.item.ElytraItem
                    && !equippedAfterDrag.isEmpty()
                    && chestSlot.mayPlace(carriedAfterDrag)) {
                chestSlot.setByPlayer(carriedAfterDrag.copyWithCount(1));
                setCarried(equippedAfterDrag);
                broadcastChanges();
            }
            return;
        }

        if (slotId == 10
                && clickType == net.minecraft.world.inventory.ClickType.PICKUP
                && !getCarried().isEmpty()
                && getCarried().getItem() instanceof net.minecraft.world.item.ElytraItem) {
            Slot chestSlot = slots.get(slotId);
            ItemStack carried = getCarried().copy();
            ItemStack equipped = chestSlot.getItem().copy();

            if (chestSlot.mayPlace(carried)) {
                // Equip exactly one Elytra. If Baby already wears a chestplate
                // (or another chest item), return it to the cursor instead of
                // deleting it or moving it into Baby storage.
                chestSlot.setByPlayer(carried.copyWithCount(1));
                if (equipped.isEmpty()) {
                    carried.shrink(1);
                    setCarried(carried);
                } else {
                    setCarried(equipped);
                }
                broadcastChanges();
                return;
            }
        }

        super.clicked(slotId, button, clickType, player);
        // EquipmentSlotView may commit the Baby ItemStack during super.clicked().
        // Broadcast only after the entire click has finished to avoid re-entry.
        broadcastChanges();
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasItem()) return ItemStack.EMPTY;

        ItemStack original = source.getItem().copy();
        ItemStack moving = source.getItem().copy();
        boolean moved;

        // A Baby carrier can never be quick-transferred from the owner's
        // inventory into Baby storage/equipment. Returning here is important
        // even though the normal slot validation also rejects Baby, because
        // quickMoveStack() has its own explicit routing path.
        if (index >= 15 && BabyNPCPlayerItem.isBabyStack(moving)) {
            return ItemStack.EMPTY;
        }

        // Baby storage (0-8) and Baby equipment (9-14) always quick-transfer
        // directly into the player's inventory. Do not use
        // AbstractContainerMenu.moveItemStackTo() for these custom slots.
        if (index < 15) {
            moved = moveIntoPlayerInventory(moving, player.getInventory());
        } else {
            // Player inventory -> Baby equipment first. The equipment order is
            // fixed and each slot decides whether the item is valid. If no
            // equipment slot accepts it, route the remainder to Baby storage.
            moved = moveIntoBabyEquipment(moving, 9, 15);
            if (!moving.isEmpty()) {
                boolean storageMoved = moveIntoBabyInventory(moving);
                moved = moved || storageMoved;
            }
        }

        if (!moved) return ItemStack.EMPTY;

        if (moving.isEmpty()) {
            source.set(ItemStack.EMPTY);
        } else {
            source.set(moving);
        }
        source.setChanged();

        // Finish the whole quick-move transaction before synchronizing the
        // menu. EquipmentSlotView may update the Baby ItemStack while the
        // transaction is running, so broadcasting from inside setItem() would
        // re-enter the container update path and can freeze the client.
        broadcastChanges();
        return original;
    }

    private boolean moveIntoPlayerInventory(ItemStack stack, Inventory inventory) {
        if (stack.isEmpty()) return false;
        boolean moved = false;

        // Merge into existing compatible stacks first.
        for (int i = 0; i < inventory.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack target = inventory.getItem(i);
            if (target.isEmpty() || !ItemStack.isSameItemSameTags(target, stack)) continue;
            int limit = Math.min(target.getMaxStackSize(), inventory.getMaxStackSize());
            int space = limit - target.getCount();
            if (space <= 0) continue;
            int amount = Math.min(space, stack.getCount());
            target.grow(amount);
            inventory.setItem(i, target);
            stack.shrink(amount);
            moved = true;
        }

        // Then use empty player slots.
        for (int i = 0; i < inventory.getContainerSize() && !stack.isEmpty(); i++) {
            if (!inventory.getItem(i).isEmpty()) continue;
            inventory.setItem(i, stack.copy());
            stack.setCount(0);
            moved = true;
        }
        return moved;
    }

    private boolean moveIntoBabyInventory(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Container container = itemContainer != null ? itemContainer : baby.getBabyInventory();
        boolean moved = false;

        for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack target = container.getItem(i);
            if (target.isEmpty() || !ItemStack.isSameItemSameTags(target, stack)) continue;
            int limit = Math.min(target.getMaxStackSize(), container.getMaxStackSize());
            int space = limit - target.getCount();
            if (space <= 0) continue;
            int amount = Math.min(space, stack.getCount());
            target.grow(amount);
            container.setItem(i, target);
            stack.shrink(amount);
            moved = true;
        }

        for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
            if (!container.getItem(i).isEmpty()) continue;
            container.setItem(i, stack.copy());
            stack.setCount(0);
            moved = true;
        }
        return moved;
    }

    private boolean moveIntoBabyEquipment(ItemStack stack, int start, int end) {
        if (stack.isEmpty()) return false;
        for (int i = start; i < end; i++) {
            Slot slot = slots.get(i);
            if (slot.hasItem() || !slot.mayPlace(stack)) continue;
            // Armor remains one-per-slot, but hand equipment keeps the full
            // carried stack (e.g. 64 blocks).
            ItemStack moved = (slot instanceof EquipmentSlotView equipment
                    && (equipment.equipmentSlot == EquipmentSlot.MAINHAND
                    || equipment.equipmentSlot == EquipmentSlot.OFFHAND))
                    ? stack.split(stack.getCount())
                    : stack.split(1);
            slot.set(moved);
            slot.setChanged();
            if (slot instanceof EquipmentSlotView equipment) equipment.markManual();
            return true;
        }
        return false;
    }

    /**
     * Item-mode equipment is stored inside the Baby ItemStack. Keep that
     * ItemStack authoritative immediately after a menu equipment change so
     * the client-side Baby item renderer sees armor/hand swaps while the
     * inventory screen is still open.
     */
    private void syncItemModeEquipmentImmediately() {
        if (!isItemMode()) return;
        if (babyItemSlot < 0 || babyItemSlot >= playerInventory.getContainerSize()) return;

        ItemStack live = playerInventory.getItem(babyItemSlot);
        if (!BabyNPCPlayerItem.isBabyStack(live)) return;

        BabyNPCPlayerItem.commitInventoryData(live, babyItemData);
        playerInventory.setChanged();
        // Do not broadcast here. This method can be called from Slot.setItem()
        // while a click/quick-move transaction is still in progress. The
        // caller broadcasts once after the transaction has completed.
    }

    @Override public void removed(Player player) {
        if (isItemMode()) {
            if (babyItemSlot >= 0 && babyItemSlot < player.getInventory().getContainerSize()) {
                ItemStack live = player.getInventory().getItem(babyItemSlot);
                if (BabyNPCPlayerItem.isBabyStack(live)) {
                    net.devatnoter.normalnpcplayer.equipment.BabyEquipmentLogic.autoEquipSerialized(babyItemData);
                    BabyNPCPlayerItem.commitInventoryData(live, babyItemData);
                }
            }
        } else if (baby != null && !player.level().isClientSide && baby.getInventoryViewerUUID() != null
                && baby.getInventoryViewerUUID().equals(player.getUUID())) {
            baby.setInventoryOpen(false, null);
        }
        super.removed(player);
    }

    private final class EquipmentSlotView extends Slot {
        private final EquipmentSlot equipmentSlot;

        EquipmentSlotView(EquipmentSlot equipmentSlot, int containerSlot, int x, int y) {
            super(equipmentContainer, containerSlot, x, y);
            this.equipmentSlot = equipmentSlot;
        }

        @Override public void set(ItemStack stack) {
            super.set(stack);
            // set() is used by our explicit quick-transfer path. Normal GUI
            // clicks additionally go through setByPlayer/onTake.
            setChanged();
        }

        @Override public void onTake(Player player, ItemStack stack) {
            markManual();
            super.onTake(player, stack);
        }

        @Override public void setByPlayer(ItemStack stack) {
            super.setByPlayer(stack);
            markManual();
        }

        void markManual() {
            if (baby != null) {
                BabyEquipmentLogic.markManual(baby, equipmentSlot);
            } else if (babyItemData != null) {
                babyItemData.putBoolean(BabyEquipmentLogic.manualKey(equipmentSlot), true);
                if (equipmentSlot == EquipmentSlot.MAINHAND) {
                    babyItemData.putString(
                            BabyEquipmentLogic.MAINHAND_ROLE_TAG,
                            BabyEquipmentLogic.roleOf(getItem()).name()
                    );
                }
            }
        }

        @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            ResourceLocation sprite = switch (equipmentSlot) {
                case HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
                case CHEST -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
                case LEGS -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
                case FEET -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
                case OFFHAND -> InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
                case MAINHAND -> ResourceLocation.fromNamespaceAndPath("minecraft", "item/empty_slot_sword");
                default -> null;
            };
            return sprite == null ? null : Pair.of(InventoryMenu.BLOCK_ATLAS, sprite);
        }

        @Override public boolean mayPlace(ItemStack stack) {
            if (BabyNPCPlayerItem.isBabyStack(stack)) return false;
            return switch (equipmentSlot) {
                case HEAD, LEGS, FEET ->
                        stack.getItem() instanceof ArmorItem armor
                                && armor.getEquipmentSlot() == equipmentSlot;
                case CHEST ->
                        (stack.getItem() instanceof ArmorItem armor
                                && armor.getEquipmentSlot() == EquipmentSlot.CHEST)
                                || stack.getItem() instanceof net.minecraft.world.item.ElytraItem;
                case MAINHAND, OFFHAND -> true;
                default -> false;
            };
        }

        @Override public int getMaxStackSize() {
            return equipmentSlot == EquipmentSlot.MAINHAND
                    || equipmentSlot == EquipmentSlot.OFFHAND
                    ? 64
                    : 1;
        }
    }

    /** Six-slot real backing container so AbstractContainerMenu can track
     * equipment changes just like normal Minecraft inventory slots. */
    private final class BabyEquipmentContainer implements Container {
        private static final EquipmentSlot[] SLOTS = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        };
        private final BabyNPCPlayerEntity baby;
        private final CompoundTag data;

        BabyEquipmentContainer(BabyNPCPlayerEntity baby, CompoundTag data) {
            this.baby = baby;
            this.data = data;
        }

        @Override public int getContainerSize() { return 6; }
        @Override public boolean isEmpty() {
            for (int i = 0; i < 6; i++) if (!getItem(i).isEmpty()) return false;
            return true;
        }
        @Override public ItemStack getItem(int index) {
            if (index < 0 || index >= 6) return ItemStack.EMPTY;
            EquipmentSlot slot = SLOTS[index];
            if (baby != null) return baby.getItemBySlot(slot);
            if (data == null) return ItemStack.EMPTY;
            return BabyEquipmentLogic.read(
                    BabyEquipmentLogic.getOrCreateEquipment(data),
                    key(slot)
            );
        }
        @Override public ItemStack removeItem(int index, int amount) {
            ItemStack current = getItem(index);
            if (current.isEmpty()) return ItemStack.EMPTY;
            ItemStack result = current.copyWithCount(Math.min(amount, current.getCount()));
            current.shrink(result.getCount());
            setItem(index, current);
            return result;
        }
        @Override public ItemStack removeItemNoUpdate(int index) {
            ItemStack current = getItem(index);
            if (current.isEmpty()) return ItemStack.EMPTY;
            setItem(index, ItemStack.EMPTY);
            return current;
        }
        @Override public void setItem(int index, ItemStack stack) {
            if (index < 0 || index >= 6) return;
            EquipmentSlot slot = SLOTS[index];
            if (baby != null) baby.setItemSlot(slot, stack.copy());
            else if (data != null) {
                BabyEquipmentLogic.write(
                        BabyEquipmentLogic.getOrCreateEquipment(data), key(slot), stack.copy()
                );
                // This is an NBT-backed Baby Item, not a live entity. Commit
                // the changed equipment to the actual player inventory stack
                // now instead of waiting for removed()/closing the screen.
                syncItemModeEquipmentImmediately();
            }
            setChanged();
        }
        @Override public void setChanged() {
            if (baby != null) {
                baby.getBabyInventory().setChanged();
            }
        }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() {
            for (int i = 0; i < 6; i++) setItem(i, ItemStack.EMPTY);
        }
        private static String key(EquipmentSlot slot) {
            return switch (slot) {
                case MAINHAND -> "MainHand"; case OFFHAND -> "OffHand"; case HEAD -> "Head";
                case CHEST -> "Chest"; case LEGS -> "Legs"; case FEET -> "Feet"; default -> slot.getName();
            };
        }
    }

}