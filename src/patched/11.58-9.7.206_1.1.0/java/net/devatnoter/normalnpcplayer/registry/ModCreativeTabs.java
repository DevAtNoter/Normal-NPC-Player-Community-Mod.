package net.devatnoter.normalnpcplayer.registry;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.guide.GuideBookManager;
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
                            .displayItems((parameters, output) -> {
                                output.accept(ModItems.BABY_NPC_PLAYER_SPAWN_EGG.get());
                                output.accept(ModItems.TOTEM_OF_BABY_COMBAT.get());
                                output.accept(ModItems.TOTEM_OF_BABY_GATHERING.get());
                                output.accept(ModItems.TOTEM_OF_BABY_ADVENTURE.get());

                                // Patchouli guide books. These are the actual Patchouli guide_book
                                // ItemStacks with their book id stored in the Patchouli NBT tag.
                                output.accept(GuideBookManager.createChildCareBook());
                                output.accept(GuideBookManager.createBabyCombatBook());
                            })
                            .build());

    /** Dedicated Affection item tab. */
    public static final RegistryObject<CreativeModeTab> AFFECTION_ITEMS_TAB =
            CREATIVE_MODE_TABS.register("affection_items",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.normalnpcplayer.affection_items"))
                            .icon(() -> new ItemStack(ModItems.BUNCH_OF_ROSES.get()))
                            .displayItems((parameters, output) -> {
                                output.accept(ModItems.ROSE.get());
                                output.accept(ModItems.BUNCH_OF_ROSES.get());
                                output.accept(ModItems.SILVER_RING.get());
                                output.accept(ModItems.GOLDEN_RING.get());
                                output.accept(ModItems.DIAMOND_RING.get());
                                output.accept(ModItems.DIAMOND_GEM_FRAGMENT.get());
                            })
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
