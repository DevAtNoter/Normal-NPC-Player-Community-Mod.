package net.devatnoter.normalnpcplayer.registry;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * The single minecraft:love effect.
 *
 * Love is a gameplay/status effect only. It intentionally emits no potion
 * particles and no automatic heart particles. PlayerBreedingManager owns all
 * breeding heart visuals explicitly.
 */
public final class LoveEffect extends MobEffect {
    public LoveEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xF48FB1);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        // Intentionally empty.
        // Love must never spawn particles by itself. The breeding manager
        // controls breeding hearts explicitly.
    }
}
