package net.devatnoter.normalnpcplayer.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes vanilla AbstractArrow ground state to the Baby combat controller. */
@Mixin(AbstractArrow.class)
public interface ThrownTridentGroundAccessor {
    @Accessor("inGround")
    boolean normalNPCPlayer$isInGround();
}
