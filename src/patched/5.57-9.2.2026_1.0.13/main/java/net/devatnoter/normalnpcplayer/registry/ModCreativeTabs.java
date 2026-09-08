package net.devatnoter.normalnpcplayer.registry;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {

    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NormalNPCPlayer.MOD_ID);

    public static final RegistryObject<CreativeModeTab> NORMAL_NPC_PLAYER_TAB =
            CREATIVE_MODE_TABS.register("normal_npc_player",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.normalnpcplayer"))
                            .icon(() -> new ItemStack(ModItems.BABY_NPC_PLAYER_SPAWN_EGG.get()))
                            .displayItems((parameters, output) ->
                                    output.accept(ModItems.BABY_NPC_PLAYER_SPAWN_EGG.get()))
                            .build());

    /** Dedicated Baby food / feeding tab. */
    public static final RegistryObject<CreativeModeTab> BABY_FOOD_TAB =
            CREATIVE_MODE_TABS.register("baby_food",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.normalnpcplayer.baby_food"))
                            .icon(() -> new ItemStack(ModItems.MILK_BOTTLE.get()))
                            .displayItems((parameters, output) -> {
                                output.accept(Items.MILK_BUCKET);
                                output.accept(ModItems.MILK_BOTTLE.get());
                                output.accept(ModItems.GOLDEN_MILK_BOTTLE.get());
                                output.accept(ModItems.ENCHANTED_GOLDEN_MILK_BOTTLE.get());
                                output.accept(ModItems.SUSPICIOUS_BOTTLE.get());
                            })
                            .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
