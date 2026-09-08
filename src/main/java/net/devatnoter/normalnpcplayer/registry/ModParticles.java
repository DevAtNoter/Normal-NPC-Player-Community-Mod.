package net.devatnoter.normalnpcplayer.registry;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, NormalNPCPlayer.MOD_ID);

    public static final RegistryObject<SimpleParticleType> TOTEM_OF_BABY_COMBAT =
            PARTICLE_TYPES.register("totem_of_baby_combat", () -> new SimpleParticleType(false));


    private ModParticles() {}

    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        PARTICLE_TYPES.register(bus);
    }
}
