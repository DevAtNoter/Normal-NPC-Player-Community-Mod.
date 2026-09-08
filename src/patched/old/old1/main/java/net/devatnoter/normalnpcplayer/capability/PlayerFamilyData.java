package net.devatnoter.normalnpcplayer.capability;

import java.util.UUID;

public class PlayerFamilyData implements IPlayerFamily {

    /**
     * ผู้เล่นเคยมีลูกคนแรกแล้วหรือยัง
     */
    private boolean firstChild = false;

    /**
     * UUID ของลูกคนปัจจุบัน
     */
    private UUID childUUID = null;

    public boolean hasFirstChild() {
        return firstChild;
    }

    public void setFirstChild(boolean firstChild) {
        this.firstChild = firstChild;
    }

    public UUID getChildUUID() {
        return childUUID;
    }

    public void setChildUUID(UUID childUUID) {
        this.childUUID = childUUID;
    }

    public void clearChild() {
        this.childUUID = null;
    }
}