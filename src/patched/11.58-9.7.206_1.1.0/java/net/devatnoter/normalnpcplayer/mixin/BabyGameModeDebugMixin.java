package net.devatnoter.normalnpcplayer.mixin;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerGameMode.class)
public abstract class BabyGameModeDebugMixin {
    @Inject(method = "changeGameModeForPlayer", at = @At("HEAD"))
    private void normalnpcplayer$debugGameModeChange(GameType gameType, CallbackInfo ci) {
        ServerPlayerGameMode mode = (ServerPlayerGameMode) (Object) this;
        ServerPlayer player = ((GameModePlayerAccessor) mode).normalnpcplayer$getPlayer();
        if (player == null) return;

        NormalNPCPlayer.LOGGER.info(
                "[BABY-GAMEMODE-DEBUG] changeGameModeForPlayer | player={} tick={} from={} to={} mainBaby={} offhandBaby={} vehicle={} passengers={} menu={}",
                player.getGameProfile().getName(),
                player.level().getGameTime(),
                mode.getGameModeForPlayer(),
                gameType,
                net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem.isBabyStack(player.getMainHandItem()),
                net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem.isBabyStack(player.getOffhandItem()),
                player.isPassenger() ? player.getVehicle().getClass().getSimpleName() : "none",
                player.getPassengers().size(),
                player.containerMenu.getClass().getSimpleName()
        );
    }
}
