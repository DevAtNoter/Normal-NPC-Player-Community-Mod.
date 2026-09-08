package net.devatnoter.normalnpcplayer.network;

import net.devatnoter.normalnpcplayer.entity.GrowthChildPlayerMobEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public final class GrowthChildGuardPacket {
    private final int entityId;
    public GrowthChildGuardPacket(int entityId){this.entityId=entityId;}
    public static void encode(GrowthChildGuardPacket p, net.minecraft.network.FriendlyByteBuf b){b.writeVarInt(p.entityId);}
    public static GrowthChildGuardPacket decode(net.minecraft.network.FriendlyByteBuf b){return new GrowthChildGuardPacket(b.readVarInt());}
    public static void handle(GrowthChildGuardPacket p, Supplier<NetworkEvent.Context> sup){NetworkEvent.Context c=sup.get();c.enqueueWork(()->{ServerPlayer player=c.getSender();if(player==null)return;Entity e=player.level().getEntity(p.entityId);if(e instanceof GrowthChildPlayerMobEntity child && e.distanceToSqr(player)<36)child.setGuardFromAuthorizedPlayer(player);});c.setPacketHandled(true);}
}
