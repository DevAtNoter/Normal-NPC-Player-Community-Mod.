package net.devatnoter.normalnpcplayer.registry;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.loot.EnchantedGoldenMilkLootModifier;
import net.devatnoter.normalnpcplayer.loot.AffectionChestLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;

public final class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> REGISTRY =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, NormalNPCPlayer.MOD_ID);

    public static final RegistryObject<Codec<EnchantedGoldenMilkLootModifier>> ENCHANTED_GOLDEN_MILK =
            REGISTRY.register("enchanted_golden_milk_loot", EnchantedGoldenMilkLootModifier.CODEC);

    public static final RegistryObject<Codec<AffectionChestLootModifier>> AFFECTION_CHEST =
            REGISTRY.register("affection_chest_loot", () -> AffectionChestLootModifier.CODEC);

    private ModLootModifiers() {}

    public static void register(IEventBus bus) { REGISTRY.register(bus); }
}
