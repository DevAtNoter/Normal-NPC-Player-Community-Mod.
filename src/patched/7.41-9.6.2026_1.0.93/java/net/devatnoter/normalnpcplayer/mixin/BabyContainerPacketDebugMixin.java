package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class BabyContainerPacketDebugMixin {
    @Inject(method = "handleContainerClick", at = @At("HEAD"))
    private void normalnpcplayer$debugContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        ServerPlayer player = listener.player;
        if (player == null) return;

        NormalNPCPlayer.LOGGER.info(
                "[BABY-PACKET-DEBUG] handleContainerClick RECEIVED | player={} tick={} packetContainerId={} playerContainerId={} slot={} button={} clickType={} carried={} selected={} main={} offhand={} menu={}",
                player.getGameProfile().getName(),
                player.level().getGameTime(),
                packet.getContainerId(),
                player.containerMenu.containerId,
                packet.getSlotNum(),
                packet.getButtonNum(),
                packet.getClickType(),
                packet.getCarriedItem().getItem(),
                player.getInventory().selected,
                player.getMainHandItem().getItem(),
                player.getOffhandItem().getItem(),
                player.containerMenu.getClass().getSimpleName()
        );

        if (BabyNPCPlayerItem.isBabyStack(packet.getCarriedItem())
                || BabyNPCPlayerItem.isBabyStack(player.getMainHandItem())
                || BabyNPCPlayerItem.isBabyStack(player.getOffhandItem())) {
            NormalNPCPlayer.LOGGER.info(
                    "[BABY-PACKET-DEBUG] BABY STATE AT PACKET | vehicle={} passengerCount={} mainBaby={} offhandBaby={} carriedBaby={}",
                    player.isPassenger() ? player.getVehicle().getClass().getSimpleName() : "none",
                    player.getPassengers().size(),
                    BabyNPCPlayerItem.isBabyStack(player.getMainHandItem()),
                    BabyNPCPlayerItem.isBabyStack(player.getOffhandItem()),
                    BabyNPCPlayerItem.isBabyStack(packet.getCarriedItem())
            );
        }
    }
}
