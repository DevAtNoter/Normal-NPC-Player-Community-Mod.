package net.devatnoter.normalnpcplayer.network;

import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Swaps the player's hands when the carried Baby is hovered in the offhand. */
public final class BabyHandSwapPacket {

    public static void encode(BabyHandSwapPacket packet, net.minecraft.network.FriendlyByteBuf buf) {
        // No payload.
    }

    public static BabyHandSwapPacket decode(net.minecraft.network.FriendlyByteBuf buf) {
        return new BabyHandSwapPacket();
    }

    public static void handle(BabyHandSwapPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> apply(context.getSender()));
        context.setPacketHandled(true);
    }

    private static void apply(ServerPlayer player) {
        if (player == null) return;

        ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (!BabyNPCPlayerItem.isBabyStack(offhand)) return;

        ItemStack mainhand = player.getItemInHand(InteractionHand.MAIN_HAND);

        player.setItemInHand(InteractionHand.MAIN_HAND, offhand.copy());
        player.setItemInHand(InteractionHand.OFF_HAND, mainhand.copy());

        // Baby in MAIN_HAND is locked to the currently selected hotbar slot.
        // If the other hand is now holding Baby, remove the hotbar slot lock.
        ItemStack babyInMain = player.getItemInHand(InteractionHand.MAIN_HAND);
        babyInMain.getOrCreateTag().putInt(
                BabyNPCPlayerItem.CARRIER_SLOT_TAG,
                player.getInventory().selected
        );

        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
}
