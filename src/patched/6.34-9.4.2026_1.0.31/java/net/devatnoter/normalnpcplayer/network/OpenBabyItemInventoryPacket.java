package net.devatnoter.normalnpcplayer.network;

import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.devatnoter.normalnpcplayer.menu.BabyInventoryMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

/** Client request: open the Baby Item's inventory from the Player Inventory. */
public final class OpenBabyItemInventoryPacket {
    private final int inventorySlot;
    public OpenBabyItemInventoryPacket(int inventorySlot) { this.inventorySlot = inventorySlot; }
    public static void encode(OpenBabyItemInventoryPacket p, FriendlyByteBuf buf) { buf.writeVarInt(p.inventorySlot); }
    public static OpenBabyItemInventoryPacket decode(FriendlyByteBuf buf) { return new OpenBabyItemInventoryPacket(buf.readVarInt()); }
    public static void handle(OpenBabyItemInventoryPacket p, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || p.inventorySlot < 0 || p.inventorySlot >= player.getInventory().getContainerSize()) return;
            ItemStack stack = player.getInventory().getItem(p.inventorySlot);
            if (!BabyNPCPlayerItem.isBabyStack(stack)) return;
            var data = BabyNPCPlayerItem.liveDataForInventory(stack);
            NetworkHooks.openScreen(player, new net.minecraft.world.MenuProvider() {
                @Override public net.minecraft.network.chat.Component getDisplayName() {
                    return net.minecraft.network.chat.Component.literal("Baby Inventory");
                }
                @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.entity.player.Player ignored) {
                    return new BabyInventoryMenu(id, inventory, stack, data.copy(), p.inventorySlot);
                }
            }, buf -> { buf.writeByte(1); buf.writeVarInt(p.inventorySlot); buf.writeNbt(data.copy()); });
        });
        ctx.setPacketHandled(true);
    }
}
