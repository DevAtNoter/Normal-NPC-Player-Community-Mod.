package net.devatnoter.normalnpcplayer.ai.growth;

import net.devatnoter.normalnpcplayer.entity.GrowthChildPlayerMobEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Communication boundary between players and the GrowthChild AI.
 * Commands become AI input; queries read state/memory and do not mutate it.
 */
public final class GrowthChildCommunication {
    private GrowthChildCommunication() {}

    public static void receive(GrowthChildPlayerMobEntity child, ServerPlayer sender,
                               GrowthChildCommand.Channel channel, String text) {
        if (!child.isAuthorized(sender)) return;
        String raw = text == null ? "" : text.trim();
        String normalized = raw.toLowerCase(Locale.ROOT);
        GrowthChildCommand.Type type = classify(normalized);
        GrowthChildCommand command = new GrowthChildCommand(sender.getUUID(), channel, type, raw, normalized);
        child.setAICommand(command);

        if (type == GrowthChildCommand.Type.QUERY) {
            sender.sendSystemMessage(Component.literal(answerQuery(child, normalized)));
        } else if (type == GrowthChildCommand.Type.ATTENTION) {
            child.setCurrentTask("listening to " + sender.getName().getString());
            child.sayFamily("Yes?", 24.0D);
        } else {
            child.setCurrentTask("processing command");
            child.sayFamily("I understand. I will decide how to handle it.", 24.0D);
        }
    }

    private static GrowthChildCommand.Type classify(String text) {
        if (text.isBlank()) return GrowthChildCommand.Type.ATTENTION;
        if (text.endsWith("?")) return GrowthChildCommand.Type.QUERY;
        String t = text.toLowerCase(Locale.ROOT);
        if (t.equals("stop") || t.equals("follow me") || t.equals("follow") || t.equals("hold position") || t.equals("hold") || t.equals("wander around home") || t.equals("wander home") || t.equals("wander") || t.equals("come home") ||
                t.startsWith("go ") || t.startsWith("collect ") || t.startsWith("mine ") ||
                t.startsWith("guard ") || t.startsWith("follow ")) return GrowthChildCommand.Type.COMMAND;
        return GrowthChildCommand.Type.UNKNOWN;
    }

    public static String answerQuery(GrowthChildPlayerMobEntity e, String query) {
        GrowthChildState s = GrowthChildState.capture(e);
        if (query.contains("mining result")) return "Mining Result: " + e.getAIStats().miningSummary();
        if (query.contains("wood collection")) return "Wood Collection: " + e.getAIStats().woodSummary();
        if (query.contains("mob defeat") || query.contains("mob defeated")) return "Mob Defeat: " + e.getAIStats().mobDefeatSummary();
        if (query.contains("health status")) return String.format("Health Status: %.1f / %.1f", s.health, s.maxHealth);
        if (query.contains("food status")) return "Food Status: " + s.foodLevel + "/20, saturation " + String.format("%.1f", s.foodSaturation);
        if (query.contains("inventory status")) return "Inventory Status: " + inventoryCount(e) + " item stacks carried.";
        if (query.contains("equipment status")) return "Equipment Status: main hand=" + itemName(s.mainHand) + ", off hand=" + itemName(s.offHand);
        if (query.contains("current task")) return "Current Task: " + s.currentTask;
        if (query.contains("location")) return "Location: " + e.blockPosition() + " in " + e.level().dimension().location();
        if (query.contains("threat status")) return "Threat Status: " + (s.target != null ? "engaged with " + s.target.getName().getString() : "no current target");
        if (query.contains("bed status")) return "Bed Status: " + (s.hasBed ? "own bed assigned" : "no own bed assigned");
        if (query.contains("progress")) return "Progress: task=" + s.currentTask + ", daily state=" + s.dailyState;
        return "I do not have a structured answer for that question yet.";
    }

    private static int inventoryCount(GrowthChildPlayerMobEntity e) {
        int count = 0;
        for (int i = 0; i < e.getTraitInventory().getContainerSize(); i++) if (!e.getTraitInventory().getItem(i).isEmpty()) count++;
        return count;
    }

    private static String itemName(ItemStack s) { return s.isEmpty() ? "empty" : s.getHoverName().getString(); }
}
