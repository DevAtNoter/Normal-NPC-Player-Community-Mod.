package net.devatnoter.normalnpcplayer.equipment;

import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

/** Single source of truth for Baby equipment rules. */
public final class BabyEquipmentLogic {
    public static final String EQUIPMENT_TAG = "BabyEquipment";
    public static final String MAINHAND_ROLE_TAG = "BabyMainhandRole";
    public static final String MANUAL_PREFIX = "BabyEquipmentManual_";

    private BabyEquipmentLogic() {}

    public enum MainhandRole { NONE, SWORD, AXE, PICKAXE, SHOVEL, HOE }

    public static String manualKey(EquipmentSlot slot) { return MANUAL_PREFIX + slot.getName(); }

    public static MainhandRole roleOf(ItemStack stack) {
        if (stack.isEmpty()) return MainhandRole.NONE;
        Item item = stack.getItem();
        if (item instanceof SwordItem) return MainhandRole.SWORD;
        if (item instanceof AxeItem) return MainhandRole.AXE;
        if (item instanceof PickaxeItem) return MainhandRole.PICKAXE;
        if (item instanceof ShovelItem) return MainhandRole.SHOVEL;
        if (item instanceof HoeItem) return MainhandRole.HOE;
        return MainhandRole.NONE;
    }

    public static boolean isMainhandCandidate(ItemStack stack) { return roleOf(stack) != MainhandRole.NONE; }

    public static int rolePriority(MainhandRole role) {
        return switch (role) {
            case SWORD -> 50; case AXE -> 40; case PICKAXE -> 30; case SHOVEL -> 20; case HOE -> 10; default -> 0;
        };
    }

    public static int materialRank(ItemStack stack) {
        if (stack.isEmpty()) return -1;
        String name = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())).toLowerCase();
        if (name.contains("netherite")) return 6;
        if (name.contains("diamond")) return 5;
        if (name.contains("golden") || name.contains("gold")) return 4;
        if (name.contains("iron")) return 3;
        if (name.contains("chainmail")) return 2;
        if (name.contains("stone")) return 1;
        if (name.contains("wooden") || name.contains("wood")) return 0;
        if (name.contains("turtle")) return 2;
        if (stack.getItem() instanceof TieredItem tiered) return tiered.getTier().getLevel();
        return 0;
    }

    public static int armorScore(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem armor)) return Integer.MIN_VALUE;
        return materialRank(stack) * 10000 + armor.getDefense()
                + Math.round(armor.getToughness() * 10.0F) * 10
                + Math.min(999, stack.getMaxDamage() > 0 ? stack.getMaxDamage() : 0) / 1000;
    }

    public static boolean isBetterArmor(ItemStack candidate, ItemStack current) {
        if (!(candidate.getItem() instanceof ArmorItem ca)) return false;
        if (current.isEmpty()) return true;
        if (!(current.getItem() instanceof ArmorItem)) return true;
        return ca.getEquipmentSlot() == ((ArmorItem) current.getItem()).getEquipmentSlot()
                && armorScore(candidate) > armorScore(current);
    }

    public static int toolScore(ItemStack stack) {
        if (!isMainhandCandidate(stack)) return Integer.MIN_VALUE;
        int tier = materialRank(stack);
        int damage = 0;
        if (stack.getItem() instanceof SwordItem sword) damage = Math.round(sword.getDamage());
        else if (stack.getItem() instanceof AxeItem axe) damage = Math.round(axe.getAttackDamage());
        return tier * 1000 + damage;
    }

    public static boolean isBetterSameRole(ItemStack candidate, ItemStack current, MainhandRole role) {
        return role != MainhandRole.NONE && roleOf(candidate) == role
                && roleOf(current) == role && toolScore(candidate) > toolScore(current);
    }

    public static boolean isManual(BabyNPCPlayerEntity baby, EquipmentSlot slot) {
        return baby.getPersistentData().getBoolean(manualKey(slot));
    }

    public static void markManual(BabyNPCPlayerEntity baby, EquipmentSlot slot) {
        baby.getPersistentData().putBoolean(manualKey(slot), true);
        if (slot == EquipmentSlot.MAINHAND) {
            baby.getPersistentData().putString(MAINHAND_ROLE_TAG, roleOf(baby.getItemBySlot(slot)).name());
        }
    }

    public static void markAuto(BabyNPCPlayerEntity baby, EquipmentSlot slot) {
        baby.getPersistentData().putBoolean(manualKey(slot), false);
    }

    public static MainhandRole getRole(BabyNPCPlayerEntity baby) {
        String value = baby.getPersistentData().getString(MAINHAND_ROLE_TAG);
        try { return MainhandRole.valueOf(value); } catch (IllegalArgumentException ignored) { return MainhandRole.NONE; }
    }

    public static void tickEntity(BabyNPCPlayerEntity baby) {
        if (baby.level().isClientSide || baby.isInventoryOpen() || baby.isInitialRideActive()) return;
        // First consume a Totem, then select the first Mainhand role by a
        // fixed priority (Sword > Axe > Pickaxe > Shovel > Hoe), so two
        // different weapon/tool types can never cause role flapping.
        for (int pass = 0; pass < 2; pass++) {
            int bestSlot = -1;
            int bestPriority = -1;
            for (int i = 0; i < baby.getBabyInventory().getContainerSize(); i++) {
                ItemStack stored = baby.getBabyInventory().getItem(i);
                if (stored.isEmpty()) continue;
                if (pass == 0 && stored.is(Items.TOTEM_OF_UNDYING)) { bestSlot = i; break; }
                if (pass == 1 && isMainhandCandidate(stored)) {
                    int priority = rolePriority(roleOf(stored));
                    if (priority > bestPriority) { bestPriority = priority; bestSlot = i; }
                }
            }
            if (bestSlot >= 0) {
                ItemStack stored = baby.getBabyInventory().getItem(bestSlot);
                if (tryAutoEquip(baby, stored)) { baby.getBabyInventory().setItem(bestSlot, stored); return; }
            }
        }
        AABB box = baby.getBoundingBox().inflate(0.75D);
        List<ItemEntity> items = baby.level().getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.getItem().isEmpty() && !e.getItem().is(Items.AIR));
        items.sort(Comparator.comparingDouble(baby::distanceToSqr));
        if (getRole(baby) == MainhandRole.NONE) {
            items.sort((a, b) -> Integer.compare(rolePriority(roleOf(b.getItem())), rolePriority(roleOf(a.getItem()))));
        }
        for (ItemEntity entity : items) {
            ItemStack stack = entity.getItem();
            if (stack.getItem() instanceof net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem) continue;
            if (tryAutoEquip(baby, stack)) {
                if (stack.isEmpty()) entity.discard(); else entity.setItem(stack);
                return;
            }
            ItemStack remainder = addToInventory(baby.getBabyInventory(), stack.copy());
            int taken = stack.getCount() - remainder.getCount();
            if (taken > 0) {
                stack.shrink(taken);
                if (stack.isEmpty()) entity.discard(); else entity.setItem(stack);
                return;
            }
        }
    }

    /**
     * SimpleContainer/Container in this 1.20.1 mapping does not expose
     * the ItemStack#addItem helper used by newer container APIs. Insert the
     * stack into the Baby inventory and return any remainder.
     */
    private static ItemStack addToInventory(net.minecraft.world.Container container, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack remainder = stack.copy();
        for (int i = 0; i < container.getContainerSize() && !remainder.isEmpty(); i++) {
            ItemStack existing = container.getItem(i);
            if (existing.isEmpty()) {
                container.setItem(i, remainder.copy());
                remainder = ItemStack.EMPTY;
                break;
            }
            if (ItemStack.isSameItemSameTags(existing, remainder)) {
                int space = Math.min(existing.getMaxStackSize(), container.getMaxStackSize()) - existing.getCount();
                if (space > 0) {
                    int moved = Math.min(space, remainder.getCount());
                    existing.grow(moved);
                    container.setItem(i, existing);
                    remainder.shrink(moved);
                }
            }
        }
        return remainder;
    }

    public static boolean tryAutoEquip(BabyNPCPlayerEntity baby, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof ArmorItem armor) {
            EquipmentSlot slot = armor.getEquipmentSlot();
            if (!isManual(baby, slot) && isBetterArmor(stack, baby.getItemBySlot(slot))) {
                ItemStack old = baby.getItemBySlot(slot).copy();
                baby.setItemSlot(slot, stack.copyWithCount(1));
                markAuto(baby, slot);
                if (!old.isEmpty()) {
                    ItemStack rem = addToInventory(baby.getBabyInventory(), old);
                    if (!rem.isEmpty()) baby.level().addFreshEntity(new ItemEntity(baby.level(), baby.getX(), baby.getY() + 0.2D, baby.getZ(), rem));
                }
                stack.shrink(1);
                return true;
            }
            return false;
        }
        if (stack.is(Items.TOTEM_OF_UNDYING) && !isManual(baby, EquipmentSlot.OFFHAND)) {
            ItemStack currentOffhand = baby.getItemBySlot(EquipmentSlot.OFFHAND).copy();
            if (currentOffhand.isEmpty() || !currentOffhand.is(Items.TOTEM_OF_UNDYING)) {
                baby.setItemSlot(EquipmentSlot.OFFHAND, stack.copyWithCount(1));
                markAuto(baby, EquipmentSlot.OFFHAND);
                if (!currentOffhand.isEmpty()) {
                    ItemStack rem = addToInventory(baby.getBabyInventory(), currentOffhand);
                    if (!rem.isEmpty()) baby.level().addFreshEntity(new ItemEntity(baby.level(), baby.getX(), baby.getY() + 0.2D, baby.getZ(), rem));
                }
                stack.shrink(1);
                return true;
            }
        }
        MainhandRole role = getRole(baby);
        if (isMainhandCandidate(stack) && !isManual(baby, EquipmentSlot.MAINHAND)) {
            if (role == MainhandRole.NONE) {
                role = roleOf(stack);
                baby.getPersistentData().putString(MAINHAND_ROLE_TAG, role.name());
                baby.setItemSlot(EquipmentSlot.MAINHAND, stack.copyWithCount(1));
                markAuto(baby, EquipmentSlot.MAINHAND);
                stack.shrink(1);
                return true;
            }
            if (isBetterSameRole(stack, baby.getItemBySlot(EquipmentSlot.MAINHAND), role)) {
                ItemStack old = baby.getItemBySlot(EquipmentSlot.MAINHAND).copy();
                baby.setItemSlot(EquipmentSlot.MAINHAND, stack.copyWithCount(1));
                markAuto(baby, EquipmentSlot.MAINHAND);
                ItemStack rem = addToInventory(baby.getBabyInventory(), old);
                if (!rem.isEmpty()) baby.level().addFreshEntity(new ItemEntity(baby.level(), baby.getX(), baby.getY() + 0.2D, baby.getZ(), rem));
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    /** Auto-equip items already stored in the Baby inventory using the same rules. */
    public static void autoEquipSerialized(CompoundTag data) {
        CompoundTag eq = getOrCreateEquipment(data);
        net.minecraft.nbt.ListTag inv = data.contains("BabyInventory", 9) ? data.getList("BabyInventory", 10) : new net.minecraft.nbt.ListTag();
        for (int i = 0; i < inv.size(); i++) {
            CompoundTag entry = inv.getCompound(i);
            if (!entry.contains("Item", 10)) continue;
            ItemStack stack = ItemStack.of(entry.getCompound("Item"));
            if (tryAutoEquipSerialized(data, stack)) {
                entry.put("Item", stack.save(new CompoundTag()));
                if (stack.isEmpty()) { inv.remove(i); i--; }
            }
        }
        data.put("BabyInventory", inv);
    }

    private static boolean isSerializedManual(CompoundTag data, EquipmentSlot slot) {
        return data.getBoolean(manualKey(slot));
    }

    private static void markSerializedAuto(CompoundTag data, EquipmentSlot slot) {
        data.putBoolean(manualKey(slot), false);
    }

    private static MainhandRole serializedRole(CompoundTag data) {
        String value = data.getString(MAINHAND_ROLE_TAG);
        try { return MainhandRole.valueOf(value); } catch (IllegalArgumentException ignored) { return MainhandRole.NONE; }
    }

    private static boolean tryAutoEquipSerialized(CompoundTag data, ItemStack stack) {
        CompoundTag eq = getOrCreateEquipment(data);
        if (stack.getItem() instanceof ArmorItem armor) {
            EquipmentSlot slot = armor.getEquipmentSlot();
            ItemStack current = read(eq, key(slot));
            if (!isSerializedManual(data, slot) && isBetterArmor(stack, current)) {
                if (!current.isEmpty() && !addSerializedInventoryItem(data, current)) return false;
                write(eq, key(slot), stack.copyWithCount(1));
                markSerializedAuto(data, slot);
                stack.shrink(1);
                return true;
            }
            return false;
        }
        if (stack.is(Items.TOTEM_OF_UNDYING) && !isSerializedManual(data, EquipmentSlot.OFFHAND)) {
            ItemStack current = read(eq, "OffHand");
            if (current.isEmpty() || !current.is(Items.TOTEM_OF_UNDYING)) {
                if (!current.isEmpty() && !addSerializedInventoryItem(data, current)) return false;
                write(eq, "OffHand", stack.copyWithCount(1));
                markSerializedAuto(data, EquipmentSlot.OFFHAND);
                stack.shrink(1);
                return true;
            }
        }
        MainhandRole role = serializedRole(data);
        if (isMainhandCandidate(stack) && !isSerializedManual(data, EquipmentSlot.MAINHAND)) {
            ItemStack current = read(eq, "MainHand");
            if (role == MainhandRole.NONE) {
                role = roleOf(stack);
                data.putString(MAINHAND_ROLE_TAG, role.name());
                write(eq, "MainHand", stack.copyWithCount(1));
                markSerializedAuto(data, EquipmentSlot.MAINHAND);
                stack.shrink(1);
                return true;
            }
            if (isBetterSameRole(stack, current, role)) {
                if (!current.isEmpty() && !addSerializedInventoryItem(data, current)) return false;
                write(eq, "MainHand", stack.copyWithCount(1));
                markSerializedAuto(data, EquipmentSlot.MAINHAND);
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static boolean addSerializedInventoryItem(CompoundTag data, ItemStack stack) {
        if (stack.isEmpty()) return true;
        net.minecraft.nbt.ListTag inv = data.contains("BabyInventory", 9) ? data.getList("BabyInventory", 10) : new net.minecraft.nbt.ListTag();
        for (int slot = 0; slot < 9; slot++) {
            boolean occupied = false;
            for (int i = 0; i < inv.size(); i++) if (inv.getCompound(i).getByte("Slot") == slot) { occupied = true; break; }
            if (!occupied) {
                CompoundTag entry = new CompoundTag(); entry.putByte("Slot", (byte) slot); entry.put("Item", stack.save(new CompoundTag())); inv.add(entry); data.put("BabyInventory", inv); return true;
            }
        }
        return false;
    }

    private static String key(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> "MainHand"; case OFFHAND -> "OffHand"; case HEAD -> "Head";
            case CHEST -> "Chest"; case LEGS -> "Legs"; case FEET -> "Feet"; default -> slot.getName();
        };
    }

    public static void tickEntityPassiveEffects(BabyNPCPlayerEntity baby) {
        if (baby.getItemBySlot(EquipmentSlot.HEAD).is(Items.TURTLE_HELMET)
                && baby.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
            baby.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 220, 0, false, false, true));
        }
    }

    public static void tickSerializedPassiveEffects(CompoundTag data, boolean underwater) {
        ItemStack head = read(getOrCreateEquipment(data), "Head");
        if (!head.is(Items.TURTLE_HELMET) || !underwater) return;
        net.minecraft.nbt.ListTag effects = data.contains("ActiveEffects", 9)
                ? data.getList("ActiveEffects", 10) : new net.minecraft.nbt.ListTag();
        MobEffectInstance incoming = new MobEffectInstance(MobEffects.WATER_BREATHING, 220, 0, false, false, true);
        for (int i = 0; i < effects.size(); i++) {
            MobEffectInstance existing = MobEffectInstance.load(effects.getCompound(i));
            if (existing != null && existing.getEffect() == MobEffects.WATER_BREATHING) {
                existing.update(incoming);
                CompoundTag merged = new CompoundTag();
                existing.save(merged);
                effects.set(i, merged);
                data.put("ActiveEffects", effects);
                return;
            }
        }
        CompoundTag e = new CompoundTag();
        incoming.save(e);
        effects.add(e);
        data.put("ActiveEffects", effects);
    }

    public static CompoundTag getOrCreateEquipment(CompoundTag data) {
        if (!data.contains(EQUIPMENT_TAG, 10)) data.put(EQUIPMENT_TAG, new CompoundTag());
        return data.getCompound(EQUIPMENT_TAG);
    }
    public static ItemStack read(CompoundTag eq, String key) { return eq.contains(key, 10) ? ItemStack.of(eq.getCompound(key)) : ItemStack.EMPTY; }
    public static void write(CompoundTag eq, String key, ItemStack stack) { eq.put(key, stack.save(new CompoundTag())); }

    public static void persistEntityEquipmentTags(BabyNPCPlayerEntity baby, CompoundTag data) {
        CompoundTag eq = getOrCreateEquipment(data);
        write(eq, "MainHand", baby.getItemBySlot(EquipmentSlot.MAINHAND));
        write(eq, "OffHand", baby.getItemBySlot(EquipmentSlot.OFFHAND));
        write(eq, "Head", baby.getItemBySlot(EquipmentSlot.HEAD));
        write(eq, "Chest", baby.getItemBySlot(EquipmentSlot.CHEST));
        write(eq, "Legs", baby.getItemBySlot(EquipmentSlot.LEGS));
        write(eq, "Feet", baby.getItemBySlot(EquipmentSlot.FEET));
        for (EquipmentSlot slot : EquipmentSlot.values()) data.putBoolean(manualKey(slot), isManual(baby, slot));
        data.putString(MAINHAND_ROLE_TAG, getRole(baby).name());
    }
}
