package net.devatnoter.normalnpcplayer.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Adult form of a Hardcore Baby.
 *
 * This entity is deliberately separate from the True Player Family adult
 * entity. It keeps the Hardcore Baby's single-parent/owner lineage and
 * always uses the Hardcore adult behavior style.
 */
public class TruePlayerHarcoreChildAdult extends AdultPlayerMobEntity {

    private static final String HARDCORE_CHILD_TAG = "TruePlayerHarcoreChildAdult";
    private static final String PARENT_UUID_TAG = "HardcoreChildParentUUID";
    private static final String PARENT_NAME_TAG = "HardcoreChildParentName";
    private static final String OWNER_UUID_TAG = "HardcoreChildOwnerUUID";
    private static final String OWNER_NAME_TAG = "HardcoreChildOwnerName";

    private UUID parentUUID;
    private String parentName = "";
    private UUID ownerUUID;
    private String ownerName = "";

    public TruePlayerHarcoreChildAdult(
            EntityType<? extends PathfinderMob> entityType,
            Level level
    ) {
        super(entityType, level);
        setStyle(Style.HARDCORE);
    }

    public void setHardcoreChildLineage(
            UUID parentUUID,
            String parentName,
            UUID ownerUUID,
            String ownerName
    ) {
        this.parentUUID = parentUUID;
        this.parentName = parentName == null ? "" : parentName;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    public boolean isTruePlayerHarcoreChildAdult() {
        return true;
    }

    public UUID getHardcoreChildParentUUID() {
        return parentUUID;
    }

    public String getHardcoreChildParentName() {
        return parentName;
    }

    public UUID getHardcoreChildOwnerUUID() {
        return ownerUUID;
    }

    public String getHardcoreChildOwnerName() {
        return ownerName;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(HARDCORE_CHILD_TAG, true);
        if (parentUUID != null) tag.putUUID(PARENT_UUID_TAG, parentUUID);
        tag.putString(PARENT_NAME_TAG, parentName);
        if (ownerUUID != null) tag.putUUID(OWNER_UUID_TAG, ownerUUID);
        tag.putString(OWNER_NAME_TAG, ownerName);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        parentUUID = tag.hasUUID(PARENT_UUID_TAG) ? tag.getUUID(PARENT_UUID_TAG) : null;
        parentName = tag.getString(PARENT_NAME_TAG);
        ownerUUID = tag.hasUUID(OWNER_UUID_TAG) ? tag.getUUID(OWNER_UUID_TAG) : null;
        ownerName = tag.getString(OWNER_NAME_TAG);
        setStyle(Style.HARDCORE);
    }
}
