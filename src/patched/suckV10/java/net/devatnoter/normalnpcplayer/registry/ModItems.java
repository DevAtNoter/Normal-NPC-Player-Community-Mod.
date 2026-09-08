package net.devatnoter.normalnpcplayer.registry;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.devatnoter.normalnpcplayer.item.BabyFoodItem;
import net.devatnoter.normalnpcplayer.item.SuspiciousBottleItem;
import net.devatnoter.normalnpcplayer.item.TotemOfBabyCombatItem;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
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

    public static final RegistryObject<Item> MILK_BOTTLE = ITEMS.register(
            "milk_bottle", () -> new BabyFoodItem(new Item.Properties().stacksTo(16), BabyFoodItem.Kind.MILK_BOTTLE));

    public static final RegistryObject<Item> GOLDEN_MILK_BOTTLE = ITEMS.register(
            "golden_milk_bottle", () -> new BabyFoodItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), BabyFoodItem.Kind.GOLDEN_MILK_BOTTLE));

    public static final RegistryObject<Item> ENCHANTED_GOLDEN_MILK_BOTTLE = ITEMS.register(
            "enchanted_golden_milk_bottle", () -> new BabyFoodItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), BabyFoodItem.Kind.ENCHANTED_GOLDEN_MILK_BOTTLE));

    public static final RegistryObject<Item> SUSPICIOUS_BOTTLE = ITEMS.register(
            "suspecious_bottle", () -> new SuspiciousBottleItem(new Item.Properties()));

    // Affection items
    public static final RegistryObject<Item> ROSE = ITEMS.register(
            "rose", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> BUNCH_OF_ROSES = ITEMS.register(
            "bunch_of_roses", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> SILVER_RING = ITEMS.register(
            "silver_ring", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> GOLDEN_RING = ITEMS.register(
            "golden_ring", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> DIAMOND_RING = ITEMS.register(
            "diamond_ring", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    public static final RegistryObject<Item> DIAMOND_GEM_FRAGMENT = ITEMS.register(
            "diamond_gem_fragment", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> BABY_NPC_PLAYER_SPAWN_EGG =
            ITEMS.register("baby_npc_player_spawn_egg",
                    () -> new ForgeSpawnEggItem(
                            ModEntities.BABY_NPC_PLAYER,
                            0xF6D7B0,
                            0x6EC6FF,
                            new Item.Properties()
                    ));

    public static final RegistryObject<Item> TOTEM_OF_BABY_COMBAT = ITEMS.register(
            "totem_of_baby_combat",
            () -> new TotemOfBabyCombatItem(new Item.Properties())
    );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}