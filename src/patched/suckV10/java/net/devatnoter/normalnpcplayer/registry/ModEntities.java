package net.devatnoter.normalnpcplayer.registry;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.entity.AdultPlayerMobEntity;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.entity.UnknownPlayerMobEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {

    private ModEntities() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(
                    ForgeRegistries.ENTITY_TYPES,
                    NormalNPCPlayer.MOD_ID
            );

    public static final RegistryObject<EntityType<BabyNPCPlayerEntity>> BABY_NPC_PLAYER =
            ENTITY_TYPES.register(
                    "baby_npc_player",
                    () -> EntityType.Builder
                            .<BabyNPCPlayerEntity>of(
                                    BabyNPCPlayerEntity::new,
                                    MobCategory.CREATURE
                            )
                            .sized(0.6F, 0.9F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("baby_npc_player")
            );

    public static final RegistryObject<EntityType<AdultPlayerMobEntity>> ADULT_PLAYER_MOB =
            ENTITY_TYPES.register(
                    "adult_player_mob",
                    () -> EntityType.Builder
                            .<AdultPlayerMobEntity>of(
                                    AdultPlayerMobEntity::new,
                                    MobCategory.CREATURE
                            )
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("adult_player_mob")
            );

    public static final RegistryObject<EntityType<UnknownPlayerMobEntity>> UNKNOWN_PLAYER =
            ENTITY_TYPES.register(
                    "unknown_player",
                    () -> EntityType.Builder
                            .<UnknownPlayerMobEntity>of(
                                    UnknownPlayerMobEntity::new,
                                    MobCategory.CREATURE
                            )
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(8)
                            .updateInterval(3)
                            .build("unknown_player")
            );

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
