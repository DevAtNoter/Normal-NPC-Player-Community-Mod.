package net.devatnoter.normalnpcplayer.cutscene;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/** Persistent server-wide state: only the first Parental Accomplishment starts the ceremony. */
public final class ParentalAccomplishmentCutsceneSavedData extends SavedData {
    private static final String DATA_NAME = NormalNPCPlayer.MOD_ID + "_parental_accomplishment_cutscene";
    private static final String TRIGGERED_TAG = "Triggered";
    private static final String PLAYER_UUID_TAG = "PlayerUUID";

    private boolean triggered;
    private UUID playerUUID;

    public static ParentalAccomplishmentCutsceneSavedData load(CompoundTag tag) {
        ParentalAccomplishmentCutsceneSavedData data = new ParentalAccomplishmentCutsceneSavedData();
        data.triggered = tag.getBoolean(TRIGGERED_TAG);
        if (tag.hasUUID(PLAYER_UUID_TAG)) {
            data.playerUUID = tag.getUUID(PLAYER_UUID_TAG);
        }
        return data;
    }

    public static ParentalAccomplishmentCutsceneSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                ParentalAccomplishmentCutsceneSavedData::load,
                ParentalAccomplishmentCutsceneSavedData::new,
                DATA_NAME
        );
    }

    public boolean isTriggered() {
        return triggered;
    }

    public boolean claim(UUID playerUUID) {
        if (triggered) {
            return false;
        }
        triggered = true;
        this.playerUUID = playerUUID;
        setDirty();
        return true;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean(TRIGGERED_TAG, triggered);
        if (playerUUID != null) {
            tag.putUUID(PLAYER_UUID_TAG, playerUUID);
        }
        return tag;
    }
}
