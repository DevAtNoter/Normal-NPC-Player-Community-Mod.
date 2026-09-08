package net.devatnoter.normalnpcplayer.registry;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private ModEffects() {}

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, NormalNPCPlayer.MOD_ID);

    public static final RegistryObject<MobEffect> AFFECTION = EFFECTS.register(
            "affection",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xF48FB1) {
            }
    );

    public static void register(IEventBus eventBus) {
        EFFECTS.register(eventBus);
    }
}
