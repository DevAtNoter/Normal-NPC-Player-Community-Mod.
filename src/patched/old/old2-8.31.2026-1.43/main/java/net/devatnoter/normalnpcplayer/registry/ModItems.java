package net.devatnoter.normalnpcplayer.registry;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {

    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, NormalNPCPlayer.MOD_ID);

    /**
     * Persistent Baby carrier. Hidden from Creative mode.
     */
    public static final RegistryObject<Item> BABY_NPC_PLAYER_ITEM =
            ITEMS.register(
                    "baby_npc_player",
                    () -> new BabyNPCPlayerItem(
                            new Item.Properties()
                    )
            );

    public static final RegistryObject<Item> BABY_NPC_PLAYER_SPAWN_EGG =
            ITEMS.register("baby_npc_player_spawn_egg",
                    () -> new ForgeSpawnEggItem(
                            ModEntities.BABY_NPC_PLAYER,
                            0xF6D7B0,
                            0x6EC6FF,
                            new Item.Properties()
                    ));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}