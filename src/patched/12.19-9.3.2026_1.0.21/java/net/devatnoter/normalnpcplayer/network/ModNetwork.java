package net.devatnoter.normalnpcplayer.network;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.resources.ResourceLocation;

public final class ModNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NormalNPCPlayer.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int packetId = 0;
    private static boolean registered;

    private ModNetwork() {}

    public static void register() {
        if (registered) return;
        registered = true;

        CHANNEL.registerMessage(
                packetId++,
                BabyItemSyncPacket.class,
                BabyItemSyncPacket::encode,
                BabyItemSyncPacket::decode,
                BabyItemSyncPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                BabyThrowPacket.class,
                BabyThrowPacket::encode,
                BabyThrowPacket::decode,
                BabyThrowPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
        );
    }

    public static void sendBabyItemState(
            net.minecraft.server.level.ServerPlayer player,
            BabyItemSyncPacket packet
    ) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendBabyThrowStart() {
        CHANNEL.sendToServer(BabyThrowPacket.start());
    }

    public static void sendBabyThrow(float charge) {
        CHANNEL.sendToServer(BabyThrowPacket.throwBaby(charge));
    }

    public static void sendBabyThrowCancel() {
        CHANNEL.sendToServer(BabyThrowPacket.cancel());
    }
}
