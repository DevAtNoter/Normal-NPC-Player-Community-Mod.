package net.devatnoter.normalnpcplayer.ai.growth;

import java.util.UUID;

/** A command/query received from a player. It is input to the AI, not Player state. */
public record GrowthChildCommand(
        UUID sender,
        Channel channel,
        Type type,
        String rawText,
        String normalizedText
) {
    public enum Channel { TALK, WHISPER }
    public enum Type { COMMAND, QUERY, ATTENTION, UNKNOWN }
}
