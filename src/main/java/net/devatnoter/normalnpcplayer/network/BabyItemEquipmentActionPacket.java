package net.devatnoter.normalnpcplayer.network;

import net.devatnoter.normalnpcplayer.equipment.BabyEquipmentLogic;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client request to apply/swap an item directly on a carried Baby Item. */
public final class BabyItemEquipmentActionPacket {
    private final int inventorySlot;
    private final EquipmentSlot equipmentSlot;

    public BabyItemEquipmentActionPacket(int inventorySlot, EquipmentSlot equipmentSlot) {
        this.inventorySlot = inventorySlot;
        this.equipmentSlot = equipmentSlot;
    }

    public static void encode(BabyItemEquipmentActionPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.inventorySlot);
        buf.writeEnum(packet.equipmentSlot);
    }

    public static BabyItemEquipmentActionPacket decode(FriendlyByteBuf buf) {
        return new BabyItemEquipmentActionPacket(
                buf.readVarInt(),
                buf.readEnum(EquipmentSlot.class)
        );
    }

    public static void handle(BabyItemEquipmentActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> apply(packet, context.getSender()));
        context.setPacketHandled(true);
    }

    private static void apply(BabyItemEquipmentActionPacket packet, ServerPlayer player) {
        if (player == null) return;
        if (packet.inventorySlot < 0 || packet.inventorySlot >= player.getInventory().getContainerSize()) return;

        ItemStack babyStack = player.getInventory().getItem(packet.inventorySlot);
        if (!BabyNPCPlayerItem.isBabyStack(babyStack)) return;

        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty() || BabyNPCPlayerItem.isBabyStack(carried)) return;

        EquipmentSlot slot = packet.equipmentSlot;
        if (slot == null) return;

        // Elytra is a chest-equipment item in vanilla gameplay, but it does
        // not extend ArmorItem. Treat it explicitly as valid CHEST equipment
        // so the same rule is enforced by the server for every equipment path.
        if (carried.getItem() instanceof ElytraItem) {
            if (slot != EquipmentSlot.CHEST) return;
        } else if (carried.getItem() instanceof ArmorItem armor) {
            if (armor.getEquipmentSlot() != slot) return;
        } else if (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) {
            return;
        }

        // Main/offhand accept any normal item. Armor has the stricter rule above.
        CompoundTagData data = new CompoundTagData(BabyNPCPlayerItem.liveDataForInventory(babyStack));
        ItemStack current = data.getEquipment(slot);

        // Armor remains one-per-slot. Hand equipment preserves the full
        // carried stack, so a stack of 64 blocks can occupy the Baby's hand.
        boolean handSlot = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
        ItemStack incoming = handSlot ? carried.copy() : carried.copyWithCount(1);
        ItemStack cursorRemainder = carried.copy();
        if (handSlot) {
            cursorRemainder = ItemStack.EMPTY;
        } else {
            cursorRemainder.shrink(1);
        }

        data.setEquipment(slot, incoming);
        data.markManual(slot);
        if (slot == EquipmentSlot.MAINHAND) {
            data.data.putString(
                    BabyEquipmentLogic.MAINHAND_ROLE_TAG,
                    BabyEquipmentLogic.roleOf(incoming).name()
            );
        }

        BabyNPCPlayerItem.commitInventoryData(babyStack, data.data);

        // Return the previous Baby equipment to the player's cursor, preserving
        // any additional item count that was already carried.
        if (!current.isEmpty()) {
            if (cursorRemainder.isEmpty()) {
                cursorRemainder = current.copy();
            } else if (ItemStack.isSameItemSameTags(cursorRemainder, current)
                    && cursorRemainder.getCount() < cursorRemainder.getMaxStackSize()) {
                int move = Math.min(current.getCount(), cursorRemainder.getMaxStackSize() - cursorRemainder.getCount());
                cursorRemainder.grow(move);
                current.shrink(move);
                if (!current.isEmpty()) {
                    // A one-slot equipment item cannot safely disappear. Put any
                    // impossible remainder back into the player's inventory.
                    if (!player.getInventory().add(current.copy())) {
                        player.drop(current.copy(), false);
                    }
                }
            } else {
                // Different items cannot share the cursor. Put the old item into
                // the player's inventory instead of deleting it.
                if (!player.getInventory().add(current.copy())) {
                    player.drop(current.copy(), false);
                }
            }
        }

        player.containerMenu.setCarried(cursorRemainder);
        player.containerMenu.broadcastChanges();
    }

    /** Small local adapter keeps the packet independent from the menu classes. */
    private static final class CompoundTagData {
        private final net.minecraft.nbt.CompoundTag data;
        private final net.minecraft.nbt.CompoundTag equipment;

        private CompoundTagData(net.minecraft.nbt.CompoundTag data) {
            this.data = data;
            this.equipment = BabyEquipmentLogic.getOrCreateEquipment(data);
        }

        private ItemStack getEquipment(EquipmentSlot slot) {
            return BabyEquipmentLogic.read(equipment, key(slot));
        }

        private void setEquipment(EquipmentSlot slot, ItemStack stack) {
            BabyEquipmentLogic.write(equipment, key(slot), stack);
        }

        private void markManual(EquipmentSlot slot) {
            data.putBoolean(BabyEquipmentLogic.manualKey(slot), true);
        }

        private static String key(EquipmentSlot slot) {
            return switch (slot) {
                case MAINHAND -> "MainHand";
                case OFFHAND -> "OffHand";
                case HEAD -> "Head";
                case CHEST -> "Chest";
                case LEGS -> "Legs";
                case FEET -> "Feet";
                default -> slot.getName();
            };
        }
    }
}
