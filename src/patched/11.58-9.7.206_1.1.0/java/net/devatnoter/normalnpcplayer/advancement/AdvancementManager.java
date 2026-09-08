package net.devatnoter.normalnpcplayer.advancement;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;

public final class AdvancementManager {

    private AdvancementManager() {
    }


    public static void grantStarterOfTheStory(ServerPlayer player) {

        Advancement advancement =
                player.server
                        .getAdvancements()
                        .getAdvancement(
                                ResourceLocation.fromNamespaceAndPath(
                                        NormalNPCPlayer.MOD_ID,
                                        "starter_of_the_story"
                                )
                        );

        if (advancement == null) {
            return;
        }

        AdvancementProgress progress =
                player.getAdvancements().getOrStartProgress(advancement);

        if (progress.isDone()) {
            return;
        }

        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    public static void grantYourFirstChild(ServerPlayer player) {

        Advancement advancement =
                player.server
                        .getAdvancements()
                        .getAdvancement(
                                ResourceLocation.fromNamespaceAndPath(
                                        NormalNPCPlayer.MOD_ID,
                                        "your_first_child"
                                ));

        if (advancement == null) {
            return;
        }

        AdvancementProgress progress =
                player.getAdvancements().getOrStartProgress(advancement);

        if (progress.isDone()) {
            return;
        }

        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }



    public static void grantSecondChild(ServerPlayer player) {

        Advancement advancement =
                player.server
                        .getAdvancements()
                        .getAdvancement(
                                ResourceLocation.fromNamespaceAndPath(
                                        NormalNPCPlayer.MOD_ID,
                                        "second_child"
                                )
                        );

        if (advancement == null) {
            return;
        }

        AdvancementProgress progress =
                player.getAdvancements().getOrStartProgress(advancement);

        if (progress.isDone()) {
            return;
        }

        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    public static void grantWhyHitBabyForNoReason(ServerPlayer player) {

        Advancement advancement =
                player.server
                        .getAdvancements()
                        .getAdvancement(
                                ResourceLocation.fromNamespaceAndPath(
                                        NormalNPCPlayer.MOD_ID,
                                        "why_hit_baby_for_no_reason"
                                )
                        );

        if (advancement == null) {
            return;
        }

        AdvancementProgress progress =
                player.getAdvancements().getOrStartProgress(advancement);

        if (progress.isDone()) {
            return;
        }

        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }


    public static void grantTheParentsMissionWasAccomplished(ServerPlayer player) {

        Advancement advancement =
                player.server
                        .getAdvancements()
                        .getAdvancement(
                                ResourceLocation.fromNamespaceAndPath(
                                        NormalNPCPlayer.MOD_ID,
                                        "the_parents_mission_was_accomplished"
                                )
                        );

        if (advancement == null) {
            return;
        }

        AdvancementProgress progress =
                player.getAdvancements().getOrStartProgress(advancement);

        if (progress.isDone()) {
            return;
        }

        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

}
