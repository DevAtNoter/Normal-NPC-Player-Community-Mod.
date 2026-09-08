package net.devatnoter.normalnpcplayer.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.devatnoter.normalnpcplayer.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

import java.util.function.Supplier;

public final class EnchantedGoldenMilkLootModifier extends LootModifier {
    public static final Supplier<Codec<EnchantedGoldenMilkLootModifier>> CODEC = () ->
            RecordCodecBuilder.create(inst -> LootModifier.codecStart(inst)
                    .apply(inst, EnchantedGoldenMilkLootModifier::new));

    private EnchantedGoldenMilkLootModifier(net.minecraft.world.level.storage.loot.predicates.LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        int enchantedAppleCount = 0;
        for (ItemStack stack : generatedLoot) {
            if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                enchantedAppleCount += stack.getCount();
            }
        }
        if (enchantedAppleCount <= 0) return generatedLoot;

        int remaining = enchantedAppleCount;
        while (remaining > 0) {
            int count = Math.min(16, remaining);
            generatedLoot.add(new ItemStack(ModItems.ENCHANTED_GOLDEN_MILK_BOTTLE.get(), count));
            remaining -= count;
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
