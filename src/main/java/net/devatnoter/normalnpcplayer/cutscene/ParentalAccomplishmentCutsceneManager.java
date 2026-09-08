package net.devatnoter.normalnpcplayer.cutscene;

import net.devatnoter.normalnpcplayer.capability.PlayerFamilyCapability;
import net.devatnoter.normalnpcplayer.network.ModNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** Starts the one-time server-wide Parental Accomplishment ceremony. */
public final class ParentalAccomplishmentCutsceneManager {
    private ParentalAccomplishmentCutsceneManager() {}

    public static void tryTrigger(ServerPlayer achiever) {
        if (achiever == null) {
            return;
        }

        ServerLevel level = achiever.serverLevel();
        ParentalAccomplishmentCutsceneSavedData data =
                ParentalAccomplishmentCutsceneSavedData.get(level);

        // The growth path has already replaced the Baby and updated
        // PlayerFamilyCapability by this point. Resolve the actual Child first;
        // only then consume the one-time server-wide claim.
        UUID childUUID = achiever.getCapability(PlayerFamilyCapability.PLAYER_FAMILY)
                .map(dataValue -> dataValue.getChildUUID())
                .orElse(null);

        if (childUUID == null) {
            return;
        }

        Entity child = level.getEntity(childUUID);
        if (child == null) {
            return;
        }

        if (!data.claim(achiever.getUUID())) {
            return;
        }

        int achieverId = achiever.getId();
        int childId = child.getId();
        long startGameTime = level.getGameTime() + 5L;
        long sceneSeed = achiever.getUUID().getMostSignificantBits() ^ achiever.getUUID().getLeastSignificantBits() ^ child.getUUID().getMostSignificantBits();

        // Deliberately use level.players(): only players currently in the same
        // dimension receive the cinematic. No cross-dimension camera hijack.
        for (ServerPlayer viewer : level.players()) {
            ModNetwork.sendParentalAccomplishmentCutscene(viewer, achieverId, childId, startGameTime, sceneSeed);
        }
    }
}
