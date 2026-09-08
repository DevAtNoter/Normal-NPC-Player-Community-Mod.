package net.devatnoter.normalnpcplayer.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Client-only display snapshot for Baby Items.
 *
 * This state is deliberately separate from ItemStack NBT. It exists only so
 * live Baby numbers/effect count can update without replacing or reloading the
 * ItemStack used by Minecraft's renderer, equip animation, or normal tooltip.
 */
public final class BabyItemClientState {
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private BabyItemClientState() {}

    public static void update(UUID babyUuid, float health, float maxHealth,
                              float hunger, float saturation, float exhaustion,
                              int air, float absorption, int totalXp, int experienceLevel, ListTag effects) {
        if (babyUuid == null) return;
        STATES.put(babyUuid, new State(
                health, maxHealth, hunger, saturation, exhaustion,
                air, absorption, totalXp, experienceLevel, effects == null ? new ListTag() : (ListTag) effects.copy()
        ));
    }

    public static State get(UUID babyUuid) {
        return babyUuid == null ? null : STATES.get(babyUuid);
    }

    public static void clear() {
        STATES.clear();
    }

    public record State(
            float health,
            float maxHealth,
            float hunger,
            float saturation,
            float exhaustion,
            int air,
            float absorption,
            int totalXp,
            int experienceLevel,
            ListTag effects
    ) {
        public int effectCount() {
            return effects.size();
        }

        public void applyTo(CompoundTag data) {
            data.putFloat("Health", health);
            data.putFloat("AbsorptionAmount", absorption);
            data.putFloat("BabyFoodLevel", hunger);
            data.putFloat("Hunger", hunger);
            data.putFloat("BabySaturation", saturation);
            data.putFloat("Saturation", saturation);
            data.putFloat("BabyExhaustion", exhaustion);
            data.putFloat("Exhaustion", exhaustion);
            data.putInt("Air", air);
            data.putInt("XpTotal", totalXp);
            data.putInt("TotalExperience", totalXp);
            data.putInt("BabyTotalExperience", totalXp);
            data.putInt("XpLevel", experienceLevel);
            data.putInt("ExperienceLevel", experienceLevel);
            data.putInt("BabyExperienceLevel", experienceLevel);
            data.put("ActiveEffects", effects.copy());
        }
    }
}
