package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.entity.AdultPlayerMobEntity;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * Fired immediately before an NPC Player message is delivered.
 * Cancel it to suppress the message or mutate the message for another system.
 */
@Cancelable
public final class NPCPlayerChatEvent extends Event {
    private final AdultPlayerMobEntity npc;
    private String message;

    public NPCPlayerChatEvent(AdultPlayerMobEntity npc, String message) {
        this.npc = npc;
        this.message = message == null ? "" : message;
    }

    public AdultPlayerMobEntity getNpc() {
        return npc;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message == null ? "" : message;
    }
}
