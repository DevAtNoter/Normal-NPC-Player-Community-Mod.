package net.devatnoter.normalnpcplayer.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class ParentalAccomplishmentCutscenePacket {
    private final int achieverEntityId;
    private final int childEntityId;
    private final long startGameTime;
    private final long sceneSeed;

    public ParentalAccomplishmentCutscenePacket(
            int achieverEntityId,
            int childEntityId,
            long startGameTime,
            long sceneSeed
    ) {
        this.achieverEntityId = achieverEntityId;
        this.childEntityId = childEntityId;
        this.startGameTime = startGameTime;
        this.sceneSeed = sceneSeed;
    }

    public static void encode(ParentalAccomplishmentCutscenePacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.achieverEntityId);
        buf.writeVarInt(packet.childEntityId);
        buf.writeLong(packet.startGameTime);
        buf.writeLong(packet.sceneSeed);
    }

    public static ParentalAccomplishmentCutscenePacket decode(FriendlyByteBuf buf) {
        return new ParentalAccomplishmentCutscenePacket(
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readLong(),
                buf.readLong()
        );
    }

    public static void handle(ParentalAccomplishmentCutscenePacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.devatnoter.normalnpcplayer.client.cutscene.ParentalAccomplishmentCutsceneClient.start(
                        packet.achieverEntityId,
                        packet.childEntityId,
                        packet.startGameTime,
                        packet.sceneSeed
                )
        ));
        context.setPacketHandled(true);
    }
}
