package net.devatnoter.normalnpcplayer.menu;

import net.devatnoter.normalnpcplayer.equipment.BabyEquipmentLogic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** NBT-backed Baby inventory used while the Baby exists only as an ItemStack. */
public final class BabyItemInventoryContainer implements Container {
    private final CompoundTag data;
    private final ItemStack babyStack;
    private final ItemStack[] storage = new ItemStack[9];

    public BabyItemInventoryContainer(ItemStack babyStack, CompoundTag data) {
        this.babyStack = babyStack;
        this.data = data;
        for (int i = 0; i < storage.length; i++) storage[i] = ItemStack.EMPTY;
        if (data.contains("BabyInventory", 9)) {
            var list = data.getList("BabyInventory", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                int slot = entry.getByte("Slot");
                if (slot >= 0 && slot < 9 && entry.contains("Item", 10)) storage[slot] = ItemStack.of(entry.getCompound("Item"));
            }
        }
    }

    @Override public int getContainerSize() { return 9; }
    @Override public boolean isEmpty() {
        for (ItemStack stack : storage) if (!stack.isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < 9 ? storage[slot] : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= 9) return ItemStack.EMPTY;
        ItemStack current = storage[slot];
        if (current.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = current.split(Math.min(amount, current.getCount()));
        if (current.isEmpty()) storage[slot] = ItemStack.EMPTY;
        setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= 9) return ItemStack.EMPTY;
        ItemStack result = storage[slot]; storage[slot] = ItemStack.EMPTY; setChanged(); return result;
    }
    @Override public void setItem(int slot, ItemStack stack) { if (slot >= 0 && slot < 9) { storage[slot] = stack.copy(); setChanged(); } }
    @Override public void setChanged() { syncToData(); }
    @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return babyStack != null && !babyStack.isEmpty(); }
    @Override public void clearContent() { for (int i = 0; i < 9; i++) storage[i] = ItemStack.EMPTY; setChanged(); }

    public ItemStack getEquipment(EquipmentSlot slot) {
        return BabyEquipmentLogic.read(BabyEquipmentLogic.getOrCreateEquipment(data), key(slot));
    }
    public void setEquipment(EquipmentSlot slot, ItemStack stack) {
        BabyEquipmentLogic.write(BabyEquipmentLogic.getOrCreateEquipment(data), key(slot), stack);
        setChanged();
    }
    private static String key(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> "MainHand"; case OFFHAND -> "OffHand"; case HEAD -> "Head";
            case CHEST -> "Chest"; case LEGS -> "Legs"; case FEET -> "Feet"; default -> slot.getName();
        };
    }
    public CompoundTag data() { return data; }
    public void syncToData() {
        var list = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < 9; i++) {
            if (storage[i].isEmpty()) continue;
            CompoundTag entry = new CompoundTag(); entry.putByte("Slot", (byte)i); entry.put("Item", storage[i].save(new CompoundTag())); list.add(entry);
        }
        data.put("BabyInventory", list);
    }
}
