package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server/world-persistent storage for Babies that must log out together with
 * their Player. This is deliberately not Player.getPersistentData(): the
 * logout event can occur after the normal player-save point, so Player NBT is
 * not a reliable place to queue a new logout snapshot.
 */
public final class BabyLogoutSavedData extends SavedData {
    private static final String DATA_NAME = NormalNPCPlayer.MOD_ID + "_baby_logout";
    private static final String ENTRIES_TAG = "Babies";
    private static final String PLAYER_UUID_TAG = "PlayerUUID";

    private final Map<UUID, CompoundTag> babies = new HashMap<>();

    public static BabyLogoutSavedData load(CompoundTag tag) {
        BabyLogoutSavedData data = new BabyLogoutSavedData();
        ListTag list = tag.getList(ENTRIES_TAG, 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID(PLAYER_UUID_TAG)) {
                continue;
            }
            CompoundTag baby = entry.getCompound("Baby").copy();
            if (!baby.isEmpty()) {
                data.babies.put(entry.getUUID(PLAYER_UUID_TAG), baby);
            }
        }
        return data;
    }

    public static BabyLogoutSavedData get(net.minecraft.server.level.ServerLevel level) {
        net.minecraft.server.level.ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                BabyLogoutSavedData::load,
                BabyLogoutSavedData::new,
                DATA_NAME
        );
    }

    public void put(UUID playerUUID, CompoundTag babyTag) {
        babies.put(playerUUID, babyTag.copy());
        setDirty();
    }

    public CompoundTag take(UUID playerUUID) {
        CompoundTag result = babies.remove(playerUUID);
        if (result != null) {
            setDirty();
            return result.copy();
        }
        return null;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, CompoundTag> entry : babies.entrySet()) {
            CompoundTag record = new CompoundTag();
            record.putUUID(PLAYER_UUID_TAG, entry.getKey());
            record.put("Baby", entry.getValue().copy());
            list.add(record);
        }
        tag.put(ENTRIES_TAG, list);
        return tag;
    }
}
