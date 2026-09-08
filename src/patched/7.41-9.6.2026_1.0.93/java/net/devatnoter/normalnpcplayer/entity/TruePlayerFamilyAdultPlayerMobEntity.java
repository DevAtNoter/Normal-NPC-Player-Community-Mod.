package net.devatnoter.normalnpcplayer.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Adult form of a Player Baby born through the True Player Family system.
 *
 * This is intentionally a separate entity type from ordinary AdultPlayerMobEntity.
 * It keeps biological lineage so a grown child remains identifiable as family.
 */
public class TruePlayerFamilyAdultPlayerMobEntity extends AdultPlayerMobEntity {

    private static final String TRUE_FAMILY_TAG = "TruePlayerFamilyAdult";
    private static final String PARENT_A_UUID_TAG = "TrueFamilyParentAUUID";
    private static final String PARENT_A_NAME_TAG = "TrueFamilyParentAName";
    private static final String PARENT_B_UUID_TAG = "TrueFamilyParentBUUID";
    private static final String PARENT_B_NAME_TAG = "TrueFamilyParentBName";
    private static final String OWNER_UUID_TAG = "TrueFamilyOwnerUUID";
    private static final String OWNER_NAME_TAG = "TrueFamilyOwnerName";

    private UUID parentAUUID;
    private String parentAName = "";
    private UUID parentBUUID;
    private String parentBName = "";
    private UUID ownerUUID;
    private String ownerName = "";

    public TruePlayerFamilyAdultPlayerMobEntity(
            EntityType<? extends PathfinderMob> entityType,
            Level level
    ) {
        super(entityType, level);
    }

    public void setTrueFamilyLineage(
            UUID parentAUUID, String parentAName,
            UUID parentBUUID, String parentBName,
            UUID ownerUUID, String ownerName
    ) {
        this.parentAUUID = parentAUUID;
        this.parentAName = parentAName == null ? "" : parentAName;
        this.parentBUUID = parentBUUID;
        this.parentBName = parentBName == null ? "" : parentBName;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    public boolean isTruePlayerFamilyAdult() {
        return true;
    }

    public UUID getTrueFamilyParentAUUID() { return parentAUUID; }
    public UUID getTrueFamilyParentBUUID() { return parentBUUID; }
    public UUID getTrueFamilyOwnerUUID() { return ownerUUID; }
    public String getTrueFamilyParentAName() { return parentAName; }
    public String getTrueFamilyParentBName() { return parentBName; }
    public String getTrueFamilyOwnerName() { return ownerName; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TRUE_FAMILY_TAG, true);
        if (parentAUUID != null) tag.putUUID(PARENT_A_UUID_TAG, parentAUUID);
        tag.putString(PARENT_A_NAME_TAG, parentAName);
        if (parentBUUID != null) tag.putUUID(PARENT_B_UUID_TAG, parentBUUID);
        tag.putString(PARENT_B_NAME_TAG, parentBName);
        if (ownerUUID != null) tag.putUUID(OWNER_UUID_TAG, ownerUUID);
        tag.putString(OWNER_NAME_TAG, ownerName);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        parentAUUID = tag.hasUUID(PARENT_A_UUID_TAG) ? tag.getUUID(PARENT_A_UUID_TAG) : null;
        parentAName = tag.getString(PARENT_A_NAME_TAG);
        parentBUUID = tag.hasUUID(PARENT_B_UUID_TAG) ? tag.getUUID(PARENT_B_UUID_TAG) : null;
        parentBName = tag.getString(PARENT_B_NAME_TAG);
        ownerUUID = tag.hasUUID(OWNER_UUID_TAG) ? tag.getUUID(OWNER_UUID_TAG) : null;
        ownerName = tag.getString(OWNER_NAME_TAG);
    }
}
