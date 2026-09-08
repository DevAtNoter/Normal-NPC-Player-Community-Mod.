package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.entity.AdultPlayerMobEntity;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.entity.UnknownPlayerMobEntity;
import net.devatnoter.normalnpcplayer.entity.TruePlayerFamilyAdultPlayerMobEntity;
import net.devatnoter.normalnpcplayer.entity.TruePlayerHarcoreChildAdult;
import net.devatnoter.normalnpcplayer.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class ModEntityEvents {

    private ModEntityEvents() {}

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(
                ModEntities.BABY_NPC_PLAYER.get(),
                BabyNPCPlayerEntity.createAttributes().build()
        );

        event.put(
                ModEntities.ADULT_PLAYER_MOB.get(),
                AdultPlayerMobEntity.createAttributes().build()
        );

        event.put(
                ModEntities.UNKNOWN_PLAYER.get(),
                UnknownPlayerMobEntity.createAttributes().build()
        );

        event.put(
                ModEntities.TRUE_PLAYER_HARCORE_CHILD_ADULT.get(),
                TruePlayerHarcoreChildAdult.createAttributes().build()
        );


        event.put(
                ModEntities.TRUE_PLAYER_FAMILY_ADULT.get(),
                TruePlayerFamilyAdultPlayerMobEntity.createAttributes().build()
        );
    }
}
