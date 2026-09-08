package net.devatnoter.normalnpcplayer.registry;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, NormalNPCPlayer.MOD_ID);

    public static final RegistryObject<SoundEvent> BABY_IDLE =
            register("baby.idle");

    public static final RegistryObject<SoundEvent> BABY_HUNGRY =
            register("baby.hungry");

    public static final RegistryObject<SoundEvent> BABY_SCARED =
            register("baby.scared");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String path) {
        return SOUNDS.register(
                path,
                () -> SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(NormalNPCPlayer.MOD_ID, path)
                )
        );
    }
}
