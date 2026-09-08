package net.devatnoter.normalnpcplayer.breeding;

import java.util.UUID;

public final class PlayerBreedingSession {
    public static final int AFFECTION_DURATION_TICKS = 600; // 30 seconds
    public static final int BREEDING_DISTANCE_TICKS = 60;   // 3 seconds
    public static final double BREEDING_DISTANCE_SQR = 1.0D;

    private final UUID initiator;
    private final UUID receiver;
    private int closeTicks;
    private boolean accepted;

    public PlayerBreedingSession(UUID initiator, UUID receiver) {
        this.initiator = initiator;
        this.receiver = receiver;
    }

    public UUID initiator() { return initiator; }
    public UUID receiver() { return receiver; }
    public int closeTicks() { return closeTicks; }
    public boolean accepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
    public void resetCloseTicks() { closeTicks = 0; }
    public int incrementCloseTicks() { return ++closeTicks; }

    public boolean contains(UUID player) {
        return initiator.equals(player) || receiver.equals(player);
    }

    public UUID other(UUID player) {
        return initiator.equals(player) ? receiver : initiator;
    }
}
