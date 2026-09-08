package net.devatnoter.normalnpcplayer.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Slot.class)
public interface BabyItemSlotAccessor {

    @Accessor("container")
    Container normalnpcplayer$getContainer();
}