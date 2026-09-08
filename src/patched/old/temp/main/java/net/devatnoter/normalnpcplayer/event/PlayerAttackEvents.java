package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.advancement.AdvancementManager;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NormalNPCPlayer.MOD_ID)
public final class PlayerAttackEvents {

    private PlayerAttackEvents() {}

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!(event.getTarget() instanceof BabyNPCPlayerEntity baby)) {
            return;
        }

        if (baby.getOwnerUUID() == null || !baby.getOwnerUUID().equals(player.getUUID())) {
            return;
        }

        AdvancementManager.grantWhyHitBabyForNoReason(player);
    }
}
