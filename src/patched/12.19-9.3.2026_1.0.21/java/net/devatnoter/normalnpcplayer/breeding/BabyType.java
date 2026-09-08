package net.devatnoter.normalnpcplayer.breeding;

public enum BabyType {
    HARDCORE,
    PLAYER;

    public static BabyType fromId(String id) {
        if (id == null) return HARDCORE;
        try {
            return valueOf(id.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return HARDCORE;
        }
    }
}
