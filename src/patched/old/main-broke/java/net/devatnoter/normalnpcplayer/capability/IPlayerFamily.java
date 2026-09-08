package net.devatnoter.normalnpcplayer.capability;

import java.util.UUID;

public interface IPlayerFamily {

    boolean hasFirstChild();

    void setFirstChild(boolean value);

    UUID getChildUUID();

    void setChildUUID(UUID uuid);

    void clearChild();
}