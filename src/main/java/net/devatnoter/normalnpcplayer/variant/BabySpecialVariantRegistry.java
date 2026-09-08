package net.devatnoter.normalnpcplayer.variant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.util.RandomSource;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creator/profile-specific Baby appearance registry.
 *
 * <p>Match is case-insensitive and whitespace-insensitive. A profile may have
 * one or many special variants. When many are registered, one is selected
 * exactly once at Baby creation and its ID/texture is then persisted in the
 * Baby's NBT, so it will never reroll during item pickup, reload, or restart.</p>
 */
public final class BabySpecialVariantRegistry {
    private static final Map<String, List<Entry>> ENTRIES = new ConcurrentHashMap<>();

    /*
     * CREATOR SPECIAL VARIANTS
     * -------------------------
     * One profile may register multiple textures; one is selected once at
     * Baby creation and then persisted as SpecialVariant + TextureId.
     */
    static {
        // Ryj
        register(
                "ryjdxz",
                "baby_allenth9991",
                "normalnpcplayer:textures/entity/baby/special/baby_allenth9991.png"
        );

        register(
                "ryjdxz",
                "baby_allenth9992",
                "normalnpcplayer:textures/entity/baby/special/baby_allenth9992.png"
        );
        register(
                "ryjdxz",
                "baby_shiraori1",
                "normalnpcplayer:textures/entity/baby/special/baby_shiraori1.png"
        );

        register(
                "ryjdxz",
                "baby_shiraori2",
                "normalnpcplayer:textures/entity/baby/special/baby_shiraori2.png"
        );

        // AllenTH999
        register(
                "AllenTH999",
                "baby_allenth9991",
                "normalnpcplayer:textures/entity/baby/special/baby_allenth9991.png"
        );

        register(
                "AllenTH999",
                "baby_allenth9992",
                "normalnpcplayer:textures/entity/baby/special/baby_allenth9992.png"
        );

        // Sh1raori
        register(
                "sh1raori",
                "baby_shiraori1",
                "normalnpcplayer:textures/entity/baby/special/baby_shiraori1.png"
        );

        register(
                "sh1raori",
                "baby_shiraori2",
                "normalnpcplayer:textures/entity/baby/special/baby_shiraori2.png"
        );
    }

    private BabySpecialVariantRegistry() {
    }

    public static void register(String profileName, String id, String textureId) {
        String key = normalize(profileName);

        if (key.isEmpty()
                || id == null
                || id.isBlank()
                || textureId == null
                || textureId.isBlank()) {
            throw new IllegalArgumentException(
                    "SpecialVariant profile/id/texture cannot be blank"
            );
        }

        ENTRIES.computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(new Entry(id.trim(), textureId.trim()));
    }

    public static Selection pickForProfile(String profileName, RandomSource random) {
        List<Entry> entries = ENTRIES.get(normalize(profileName));

        if (entries == null || entries.isEmpty()) {
            return null;
        }

        Entry entry = entries.get(random.nextInt(entries.size()));
        return new Selection(entry.id(), entry.textureId());
    }

    public static boolean hasSpecialVariant(String profileName) {
        List<Entry> entries = ENTRIES.get(normalize(profileName));
        return entries != null && !entries.isEmpty();
    }

    private static String normalize(String profileName) {
        return profileName == null
                ? ""
                : profileName.trim().toLowerCase(Locale.ROOT);
    }

    private record Entry(String id, String textureId) {
    }

    public record Selection(String id, String textureId) {
    }
}