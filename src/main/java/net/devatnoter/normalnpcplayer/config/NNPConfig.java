package net.devatnoter.normalnpcplayer.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class NNPConfig {
    public enum GuideBookDistributionMode {
        CINEMATIC_INTRO_DEFAULT,
        GIVE_AT_BIRTH
    }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue PATCHOULI_BOOKS_ENABLED;
    public static final ForgeConfigSpec.EnumValue<GuideBookDistributionMode> GUIDE_BOOK_DISTRIBUTION_MODE;
    public static final ForgeConfigSpec.BooleanValue GROWTH_CHILD_SPEECH;
    public static final ForgeConfigSpec.BooleanValue NPC_FOLLOW_PLAYER_GAME_RULE;
    public static final ForgeConfigSpec.BooleanValue NPC_PLAYER_RESPAWN;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("Guide Book");

        PATCHOULI_BOOKS_ENABLED = builder
                .comment("Enable the Patchouli guide books provided by Normal NPC Player.")
                .translation("config.normalnpcplayer.patchouli_books_enabled")
                .define("patchouliBooksEnabled", false);

        GUIDE_BOOK_DISTRIBUTION_MODE = builder
                .comment(
                        "How Patchouli guide books are distributed.",
                        "CINEMATIC_INTRO_DEFAULT: the UnknownPlayerMobEntity cinematic intro delivers the book.",
                        "GIVE_AT_BIRTH: the guide books are given directly when a Player Baby is born."
                )
                .translation("config.normalnpcplayer.guide_book_distribution_mode")
                .defineEnum("guideBookDistributionMode", GuideBookDistributionMode.GIVE_AT_BIRTH);

        builder.pop();

        builder.push("Growth Child Speech");
        GROWTH_CHILD_SPEECH = builder
                .comment("Allow Growth Child NPCs to speak through text chat.", "Default: OFF")
                .translation("config.normalnpcplayer.growth_child_speech")
                .define("growthChildSpeech", false);
        builder.pop();

        builder.push("Player NPC");
        NPC_FOLLOW_PLAYER_GAME_RULE = builder.comment("Allow Player NPC survival behavior to follow applicable vanilla Game Rules.").define("npcFollowPlayerGameRule", true);
        NPC_PLAYER_RESPAWN = builder.comment("Allow Growth Child Player NPCs to respawn in Survival/Adventure. Hardcore always remains permanent death.").define("npcPlayerRespawn", true);
        builder.pop();
        SPEC = builder.build();
    }

    private NNPConfig() {}

    public static boolean arePatchouliBooksEnabled() {
        return PATCHOULI_BOOKS_ENABLED.get();
    }

    public static boolean isCinematicIntroMode() {
        return GUIDE_BOOK_DISTRIBUTION_MODE.get() == GuideBookDistributionMode.CINEMATIC_INTRO_DEFAULT;
    }

    public static boolean isGrowthChildSpeechEnabled() { return GROWTH_CHILD_SPEECH.get(); }

    public static boolean followPlayerGameRule() { return NPC_FOLLOW_PLAYER_GAME_RULE.get(); }
    public static boolean playerNpcRespawnEnabled() { return NPC_PLAYER_RESPAWN.get(); }

    public static boolean isGiveAtBirthMode() {
        return GUIDE_BOOK_DISTRIBUTION_MODE.get() == GuideBookDistributionMode.GIVE_AT_BIRTH;
    }
}
