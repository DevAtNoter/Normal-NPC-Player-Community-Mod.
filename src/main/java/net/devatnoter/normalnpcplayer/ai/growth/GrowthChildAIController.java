package net.devatnoter.normalnpcplayer.ai.growth;

import net.devatnoter.normalnpcplayer.entity.GrowthChildPlayerMobEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;
import java.util.UUID;

/**
 * Canonical AI boundary for GrowthChild.
 *
 * The controller observes Entity State + World Perception, accepts external
 * commands, chooses an intention, and drives the character through entity APIs.
 * It does not maintain a duplicate Player State model.
 */
public final class GrowthChildAIController {
    private GrowthChildAIController() {}

    public static void tick(GrowthChildPlayerMobEntity e) {
        if (!(e.level() instanceof ServerLevel level) || e.isDeadOrDying()) return;
        GrowthChildState state = GrowthChildState.capture(e);
        GrowthChildPerception.Snapshot perception = GrowthChildPerception.capture(e, level);
        GrowthChildCommand command = e.getAICommand();
        if (command != null) {
            processCommand(e, level, command);
            e.clearAICommand();
        }
        // Keep perception/state creation here even while individual action modules
        // are migrated. Future actions consume these read-only views instead of
        // adding world mutation logic to the brain.
        if (!perception.hostileEntities().isEmpty() && state.target == null && !e.isFollowing()) {
            e.setCurrentTask("assessing nearby threats");
        }
    }

    private static void processCommand(GrowthChildPlayerMobEntity e, ServerLevel level, GrowthChildCommand command) {
        if (command.type() != GrowthChildCommand.Type.COMMAND) return;
        String text = command.normalizedText().trim();
        ServerPlayer sender = level.getServer().getPlayerList().getPlayer(command.sender());
        if (sender == null) return;

        if (text.equals("stop") || text.equals("stop doing that")) {
            e.setFollowTarget(null);
            e.setCurrentIntent(GrowthChildPlayerMobEntity.AIIntent.IDLE);
            e.setCurrentTask("idle");
            e.getNavigation().stop();
            e.sayFamily("I will stop.", 24.0D);
            return;
        }
        if (text.equals("follow me") || text.equals("follow")) {
            e.setGuarding(false);
            e.setFollowTarget(sender);
            e.setCurrentIntent(GrowthChildPlayerMobEntity.AIIntent.FOLLOW);
            e.setCurrentTask("following " + sender.getName().getString());
            e.sayFamily("I will follow you.", 24.0D);
            return;
        }
        if (text.equals("hold position") || text.equals("hold")) {
            e.setFollowTarget(null);
            e.setGuarding(false);
            e.setHoldPosition(e.position());
            e.setCurrentIntent(GrowthChildPlayerMobEntity.AIIntent.HOLD_POSITION);
            e.setCurrentTask("holding position");
            e.getNavigation().stop();
            e.sayFamily("I will hold my position.", 24.0D);
            return;
        }
        if (text.equals("wander around home") || text.equals("wander home") || text.equals("wander")) {
            e.setFollowTarget(null);
            e.setGuarding(false);
            e.setCurrentIntent(GrowthChildPlayerMobEntity.AIIntent.WANDER_HOME);
            e.setCurrentTask("wandering around home");
            e.sayFamily("I will wander around home.", 24.0D);
            return;
        }
        if (text.equals("come home") || text.equals("go home")) {
            e.setFollowTarget(null);
            e.setCurrentIntent(GrowthChildPlayerMobEntity.AIIntent.GO_HOME);
            e.setCurrentTask("returning home");
            e.sayFamily("I am going home.", 24.0D);
            return;
        }
        if (text.startsWith("guard")) {
            e.setFollowTarget(null);
            e.setCurrentIntent(GrowthChildPlayerMobEntity.AIIntent.GUARD);
            e.setGuarding(true);
            e.setCurrentTask("guarding");
            e.sayFamily("I will guard this area.", 24.0D);
            return;
        }
        if (text.startsWith("mine")) {
            e.setCurrentIntent(GrowthChildPlayerMobEntity.AIIntent.MINING);
            e.setCurrentTask("mining");
            e.sayFamily("I will mine.", 24.0D);
            return;
        }
        if (text.startsWith("collect wood") || text.startsWith("go collect wood") || text.equals("wood collection")) {
            e.setCurrentIntent(GrowthChildPlayerMobEntity.AIIntent.WOOD_COLLECTION);
            e.setCurrentTask("collecting wood");
            e.sayFamily("I will collect wood.", 24.0D);
            return;
        }
        if (text.startsWith("go ")) {
            e.setCurrentIntent(GrowthChildPlayerMobEntity.AIIntent.CUSTOM);
            e.setCurrentTask(text);
            e.sayFamily("I received the command: " + command.rawText(), 24.0D);
        }
    }
}
