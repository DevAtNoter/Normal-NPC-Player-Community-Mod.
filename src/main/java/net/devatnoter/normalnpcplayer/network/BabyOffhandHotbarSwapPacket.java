package net.devatnoter.normalnpcplayer.network;

import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public final class BabyOffhandHotbarSwapPacket {
    private final int hotbarSlot;

    public BabyOffhandHotbarSwapPacket(int hotbarSlot) {
        this.hotbarSlot = hotbarSlot;
    }

    public static void encode(BabyOffhandHotbarSwapPacket packet, net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeVarInt(packet.hotbarSlot);
    }

    public static BabyOffhandHotbarSwapPacket decode(net.minecraft.network.FriendlyByteBuf buf) {
        return new BabyOffhandHotbarSwapPacket(buf.readVarInt());
    }

    public static void handle(BabyOffhandHotbarSwapPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> apply(packet, context.getSender()));
        context.setPacketHandled(true);
    }

    private static void apply(BabyOffhandHotbarSwapPacket packet, ServerPlayer player) {
        if (player == null || packet.hotbarSlot < 0 || packet.hotbarSlot > 8) return;
        ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (!BabyNPCPlayerItem.isBabyStack(offhand)) return;

        ItemStack selected = player.getInventory().getItem(packet.hotbarSlot);
        player.getInventory().setItem(packet.hotbarSlot, offhand.copy());
        player.setItemInHand(InteractionHand.OFF_HAND, selected.copy());
        player.getInventory().selected = packet.hotbarSlot;
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
}
