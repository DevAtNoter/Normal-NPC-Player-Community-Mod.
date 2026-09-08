package net.devatnoter.normalnpcplayer.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Mob effects used by Normal NPC Player.
 *
 * Love intentionally uses the vanilla namespace so the command is:
 *   /effect give <player> minecraft:love <seconds>
 *
 * It is still the same effect used by the player-breeding system. The effect
 * itself never creates potion or heart particles; PlayerBreedingManager remains
 * responsible for all breeding heart particles.
 */
public final class ModEffects {
    private ModEffects() {}

    /** Vanilla-looking ID requested for the Love effect. */
    public static final ResourceLocation LOVE_ID =
            ResourceLocation.fromNamespaceAndPath("minecraft", "love");

    /**
     * RegistryObject is bound to the exact minecraft:love registry entry.
     * The entry itself is registered from RegisterEvent because DeferredRegister
     * is normally scoped to the mod namespace.
     */
    public static final RegistryObject<MobEffect> LOVE =
            RegistryObject.create(LOVE_ID, ForgeRegistries.MOB_EFFECTS);

    public static void register(IEventBus eventBus) {
        eventBus.addListener((RegisterEvent event) -> {
            if (event.getRegistryKey().equals(Registries.MOB_EFFECT)) {
                event.register(Registries.MOB_EFFECT, helper ->
                        helper.register(LOVE_ID, new LoveEffect()));
            }
        });
    }
}
