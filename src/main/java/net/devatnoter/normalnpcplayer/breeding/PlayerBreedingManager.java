package net.devatnoter.normalnpcplayer.breeding;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.guide.GuideBookManager;
import net.devatnoter.normalnpcplayer.advancement.AdvancementManager;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.registry.ModEffects;
import net.devatnoter.normalnpcplayer.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = NormalNPCPlayer.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerBreedingManager {
    public static final String COOLDOWN_TAG = "NNPPlayerBreedingCooldownUntil";

    private static final Map<UUID, PlayerBreedingSession> SESSIONS = new HashMap<>();
    private static final int COOLDOWN_TICKS = 6000; // Vanilla animal breeding cooldown: 5 minutes.

    private PlayerBreedingManager() {}

    public static boolean isInSession(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static boolean isOnCooldown(ServerPlayer player) {
        return player.serverLevel().getGameTime() < player.getPersistentData().getLong(COOLDOWN_TAG);
    }

    public static boolean start(ServerPlayer initiator, ServerPlayer receiver) {
        if (initiator == null || receiver == null || initiator == receiver) return false;
        if (isInSession(initiator) || isInSession(receiver)) return false;
        if (isOnCooldown(initiator) || isOnCooldown(receiver)) return false;

        PlayerBreedingSession session = new PlayerBreedingSession(initiator.getUUID(), receiver.getUUID());
        SESSIONS.put(initiator.getUUID(), session);
        SESSIONS.put(receiver.getUUID(), session);
        addAffection(initiator);
        spawnHearts(initiator);
        return true;
    }

    public static boolean accept(ServerPlayer responder, ServerPlayer initiator) {
        PlayerBreedingSession session = SESSIONS.get(initiator.getUUID());
        if (session == null || !session.initiator().equals(initiator.getUUID())
                || !session.receiver().equals(responder.getUUID())) {
            return false;
        }

        if (isOnCooldown(initiator) || isOnCooldown(responder)) {
            fail(session, initiator, responder);
            return false;
        }

        addAffection(responder);
        session.setAccepted(true);
        spawnHearts(responder);
        return true;
    }

    private static void addAffection(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(
                ModEffects.LOVE.get(),
                PlayerBreedingSession.AFFECTION_DURATION_TICKS,
                0,
                false,
                false,
                true
        ));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Iterate over a snapshot so fail()/succeed() can safely remove sessions
        // from SESSIONS without causing ConcurrentModificationException.
        for (PlayerBreedingSession session : new java.util.HashSet<>(SESSIONS.values())) {

            ServerPlayer a = event.getServer().getPlayerList().getPlayer(session.initiator());
            ServerPlayer b = event.getServer().getPlayerList().getPlayer(session.receiver());

            if (a == null || b == null || !a.isAlive() || !b.isAlive()
                    || a.isSpectator() || b.isSpectator()
                    || a.level() != b.level()) {
                fail(session, a, b);
                continue;
            }

            if (!a.hasEffect(ModEffects.LOVE.get())
                    || (session.accepted() && !b.hasEffect(ModEffects.LOVE.get()))) {
                fail(session, a, b);
                continue;
            }

            // Until the receiver accepts, there is no proximity timer.
            if (!session.accepted()) {
                continue;
            }

            if (a.distanceToSqr(b) <= PlayerBreedingSession.BREEDING_DISTANCE_SQR) {
                int closeTicks = session.incrementCloseTicks();

                // Two visual states: a larger acceptance burst, followed by
                // small heart particles while the pair remains close.
                if (closeTicks % 10 == 0) {
                    spawnBreedingHearts(levelOf(a), a, 1);
                    spawnBreedingHearts(levelOf(b), b, 1);
                }

                if (closeTicks >= PlayerBreedingSession.BREEDING_DISTANCE_TICKS) {
                    succeed(session, a, b);
                }
            } else {
                session.resetCloseTicks();
            }
        }
    }

    private static void succeed(PlayerBreedingSession session, ServerPlayer initiator, ServerPlayer responder) {
        removeSession(session);
        initiator.removeEffect(ModEffects.LOVE.get());
        responder.removeEffect(ModEffects.LOVE.get());

        long cooldownUntil = Math.max(initiator.serverLevel().getGameTime(), responder.serverLevel().getGameTime())
                + COOLDOWN_TICKS;
        initiator.getPersistentData().putLong(COOLDOWN_TAG, cooldownUntil);
        responder.getPersistentData().putLong(COOLDOWN_TAG, cooldownUntil);

        ServerLevel level = responder.serverLevel();
        spawnHearts(level, initiator);
        spawnHearts(level, responder);
        spawnPlayerBaby(level, initiator, responder);

        // This is the first achievement in the The Family advancement tab.
        // Both biological parents receive it because both participated in the
        // Affection interaction that created the Player Baby.
        AdvancementManager.grantStarterOfTheStory(initiator);
        AdvancementManager.grantStarterOfTheStory(responder);
    }

    private static void spawnPlayerBaby(ServerLevel level, ServerPlayer parentA, ServerPlayer parentB) {
        BabyNPCPlayerEntity baby = ModEntities.BABY_NPC_PLAYER.get().create(level);
        if (baby == null) {
            NormalNPCPlayer.LOGGER.error("Unable to create Player Baby during breeding.");
            return;
        }

        // The responder is the legal owner/spawn side, while both players remain
        // immutable biological parents in the lineage data.
        baby.setBabyType(BabyType.PLAYER);
        baby.setBirthGameTime(level.getGameTime());
        baby.setOwnerUUID(parentB.getUUID());
        baby.setOwnerName(parentB.getGameProfile().getName());
        baby.setProfileName(parentA.getGameProfile().getName());
        baby.setBiologicalParents(parentA, parentB);
        baby.setBirthLocation(parentB.blockPosition());
        baby.setFoster(false);
        baby.setOrphaned(false);
        baby.initializeRandomAppearance();
        baby.moveTo(parentB.getX(), parentB.getY(), parentB.getZ(), parentB.getYRot(), 0.0F);
        level.addFreshEntity(baby);
        baby.beginInitialRide(parentB);
        GuideBookManager.giveAtBirth(parentA);
        GuideBookManager.giveAtBirth(parentB);
    }

    private static void fail(PlayerBreedingSession session, ServerPlayer a, ServerPlayer b) {
        // If the initiator's Love Effect expires before the receiver accepts,
        // only the initiator gets the timeout feedback. The receiver never
        // gets an Angry Villager particle for simply not accepting.
        boolean waitingForAcceptance = session != null && !session.accepted();

        removeSession(session);

        if (a != null && a.isAlive()) {
            a.removeEffect(ModEffects.LOVE.get());
            if (waitingForAcceptance) {
                spawnFailureParticles(a.serverLevel(), a);
            }
        }
        if (b != null && b.isAlive()) {
            b.removeEffect(ModEffects.LOVE.get());
            // Do not add/change failure particles for the non-accepting side.
        }
    }

    private static void removeSession(PlayerBreedingSession session) {
        if (session == null) return;
        SESSIONS.remove(session.initiator());
        SESSIONS.remove(session.receiver());
    }

    private static ServerLevel levelOf(ServerPlayer player) {
        return player.serverLevel();
    }

    private static void spawnHearts(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) spawnHearts(level, player);
    }

    // Initial / success heart burst.
    private static void spawnHearts(ServerLevel level, Entity entity) {
        level.sendParticles(ParticleTypes.HEART,
                entity.getX(), entity.getY() + entity.getBbHeight() + 0.35D, entity.getZ(),
                3, 0.25D, 0.15D, 0.25D, 0.02D);
    }

    // Continuous breeding hearts while both players stay within 1 block.
    private static void spawnBreedingHearts(ServerLevel level, Entity entity, int count) {
        level.sendParticles(ParticleTypes.HEART,
                entity.getX(), entity.getY() + entity.getBbHeight() + 0.55D, entity.getZ(),
                count, 0.18D, 0.10D, 0.18D, 0.01D);
    }

    private static void spawnFailureParticles(ServerLevel level, Entity entity) {
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                entity.getX(), entity.getY() + entity.getBbHeight() + 0.35D, entity.getZ(),
                3, 0.25D, 0.15D, 0.25D, 0.02D);
    }

}
