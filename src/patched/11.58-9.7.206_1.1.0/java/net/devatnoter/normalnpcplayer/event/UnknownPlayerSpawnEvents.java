package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.entity.UnknownPlayerMobEntity;
import net.devatnoter.normalnpcplayer.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Natural spawning and encounter scheduling for Unknown Player. */
@Mod.EventBusSubscriber(modid = NormalNPCPlayer.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UnknownPlayerSpawnEvents {

    /**
     * Stage encounter spawning is intentionally disabled while the encounter
     * state machine is being rebuilt. The working movement/projectile/item
     * methods remain in UnknownPlayerMobEntity and are not deleted.
     */
    private static final boolean ENCOUNTER_SPAWNING_ENABLED = false;

    // Encounter cadence: the first encounter and every later encounter are
    // scheduled independently in the 1-3 minute range.
    private static final int FIRST_DELAY = 20 * 3;
    private static final int MIN_DELAY = 20 * 60;
    private static final int MAX_DELAY = 20 * 60 * 3;
    private static final Map<ServerLevel, Map<UUID, Integer>> NEXT_ATTEMPT = new HashMap<>();
    private static final Map<ServerLevel, Map<UUID, PendingSecondEncounter>> SECOND_ENCOUNTERS = new HashMap<>();
    private static final Map<ServerLevel, Map<UUID, PendingSecondFollower>> SECOND_FOLLOWERS = new HashMap<>();
    private static final Map<ServerLevel, Map<UUID, PendingState3>> STATE3 = new HashMap<>();
    private static final int SECOND_DELAY = 20 * 5;
    private static final int SECOND_FOLLOWER_DELAY = 0;
    private static final String ENCOUNTER_COMPLETED_TAG = "NormalNPCPlayerUnknownEncounterCompleted";
    private static final int STATE3_DELAY = 20 * 2;
    private static final double STATE3_CHANCE = 1.0D;

    private UnknownPlayerSpawnEvents() {
    }

    private record PendingSecondEncounter(
            String profileName,
            UUID profileUuid,
            boolean slim,
            int ticksRemaining
    ) {}

    private record PendingSecondFollower(
            UUID targetUuid,
            UUID attackerUuid,
            int ticksRemaining
    ) {}

    private record PendingState3(
            UUID targetUuid,
            String profileName,
            UUID profileUuid,
            boolean slim,
            double returnX,
            double returnY,
            double returnZ,
            int ticksRemaining
    ) {}

    /** Schedules State 2 exactly five seconds after the first Unknown Player leaves. */
    public static void scheduleSecondEncounter(
            ServerLevel level,
            ServerPlayer target,
            String profileName,
            UUID profileUuid,
            boolean slim
    ) {
        if (!ENCOUNTER_SPAWNING_ENABLED) return;
        if (level == null || target == null || profileName == null || profileName.isBlank()) return;
        // State 1/2 is a Hardcore-only encounter.
        if (!level.getServer().isHardcore() || isEncounterCompleted(target)) return;

        SECOND_ENCOUNTERS
                .computeIfAbsent(level, ignored -> new HashMap<>())
                .put(target.getUUID(), new PendingSecondEncounter(profileName, profileUuid, slim, SECOND_DELAY));

        // Prevent the normal encounter scheduler from creating another NPC
        // while State 2 is waiting to start.
        NEXT_ATTEMPT
                .computeIfAbsent(level, ignored -> new HashMap<>())
                .put(target.getUUID(), 20 * 60 * 3);
    }

    @Mod.EventBusSubscriber(modid = NormalNPCPlayer.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void registerSpawnPlacement(SpawnPlacementRegisterEvent event) {
            event.register(
                    ModEntities.UNKNOWN_PLAYER.get(),
                    net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    UnknownPlayerMobEntity::checkSpawnRules,
                    SpawnPlacementRegisterEvent.Operation.REPLACE
            );
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ENCOUNTER_SPAWNING_ENABLED) return;
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension() != Level.OVERWORLD) continue;
            // Unknown Player encounters exist only in Hardcore worlds.
            if (!event.getServer().isHardcore()) continue;

            // Actor 2 uses a real 25-tick (1.25 second) delay, so follower
            // processing must run every server tick rather than once per second.
            processSecondFollowers(level);
            processState3(level);

            if (event.getServer().getTickCount() % 20 != 0) {
                continue;
            }

            // IMPORTANT: the schedule belongs to this actual ServerLevel
            // instance, not only to the player's UUID. Creating/opening a new
            // world must therefore create a fresh encounter timer even when
            // the same player UUID enters it.
            Map<UUID, Integer> worldAttempts = NEXT_ATTEMPT.computeIfAbsent(
                    level,
                    ignored -> new HashMap<>()
            );

            processSecondEncounters(level, worldAttempts);

            for (ServerPlayer player : level.players()) {
                UUID id = player.getUUID();

                // Hardcore death turns the player into spectator. Never let a
                // queued timer resurrect the Unknown Player encounter after death.
                if (!player.isAlive() || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                    worldAttempts.remove(id);
                    Map<UUID, PendingSecondEncounter> pendingSecond = SECOND_ENCOUNTERS.get(level);
                    if (pendingSecond != null) pendingSecond.remove(id);
                    Map<UUID, PendingSecondFollower> pendingFollower = SECOND_FOLLOWERS.get(level);
                    if (pendingFollower != null) pendingFollower.remove(id);
                    Map<UUID, PendingState3> pendingThird = STATE3.get(level);
                    if (pendingThird != null) pendingThird.remove(id);
                    continue;
                }

                // State 1 -> State 2 is a one-time scripted encounter for this
                // player. Once State 2 finishes, never schedule Unknown Player
                // encounters for this player again, even after relog/restart.
                if (isEncounterCompleted(player)) {
                    worldAttempts.remove(id);
                    Map<UUID, PendingSecondEncounter> pending = SECOND_ENCOUNTERS.get(level);
                    if (pending != null) pending.remove(id);
                    continue;
                }

                int remaining = worldAttempts.computeIfAbsent(
                        id,
                        ignored -> FIRST_DELAY
                );
                remaining -= 20;

                // Do not stack encounters. One Unknown Player is enough for the
                // intended "who joined?" illusion.
                if (hasActiveUnknown(level, player)) {
                    // Keep the existing cooldown while the current encounter
                    // is alive. Do not reroll it every second.
                    worldAttempts.put(id, remaining);
                    continue;
                }

                if (remaining > 0) {
                    worldAttempts.put(id, remaining);
                    continue;
                }

                UnknownPlayerMobEntity spawned = spawnHiddenUnknown(level, player);
                if (spawned != null) {
                    NormalNPCPlayer.LOGGER.info("Unknown Player spawned for {} in {} at {}", id, level.dimension().location(), spawned.blockPosition());
                    // First encounter is deliberately fast (3 seconds) so a
                    // newly created world gets the encounter immediately.
                    // Later encounters use the intended 1-3 minute cadence.
                    worldAttempts.put(id, randomDelay(level.random));
                } else {
                    // Failed to find a blind spot: retry fairly soon without
                    // turning the attempt into a visible spawn.
                    worldAttempts.put(id, 20 * 5);
                    NormalNPCPlayer.LOGGER.debug("Unknown Player spawn attempt failed for {} in {}", id, level.dimension().location());
                }
            }
        }

        // ServerLevel is the world-session key, so a newly created world
        // cannot inherit a timer from a previous world with the same player.
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            NEXT_ATTEMPT.remove(level);
            SECOND_ENCOUNTERS.remove(level);
            SECOND_FOLLOWERS.remove(level);
            STATE3.remove(level);
        }
    }

    private static void processSecondEncounters(
            ServerLevel level,
            Map<UUID, Integer> worldAttempts
    ) {
        Map<UUID, PendingSecondEncounter> pending = SECOND_ENCOUNTERS.get(level);
        if (pending == null || pending.isEmpty()) return;

        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            UUID targetUuid = entry.getKey();
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(targetUuid);

            if (target == null || !target.isAlive() || target.level() != level) {
                iterator.remove();
                continue;
            }

            PendingSecondEncounter state = entry.getValue();
            int remaining = state.ticksRemaining() - 20;
            if (remaining > 0) {
                entry.setValue(new PendingSecondEncounter(
                        state.profileName(), state.profileUuid(), state.slim(), remaining
                ));
                continue;
            }

            if (spawnSecondEncounter(level, target, state)) {
                // Remove the entry through the active iterator. spawnSecondEncounter()
                // may mark the player complete, but it must not structurally mutate
                // this map while the iterator is active.
                iterator.remove();
                worldAttempts.remove(targetUuid);
            } else {
                // Replace the value without changing HashMap structure.
                entry.setValue(new PendingSecondEncounter(
                        state.profileName(), state.profileUuid(), state.slim(), SECOND_DELAY
                ));
            }
        }
    }

    /** Spawns Actor 2 alongside Actor 1 so both actors enter the scene together. */
    private static void processSecondFollowers(ServerLevel level) {
        Map<UUID, PendingSecondFollower> pending = SECOND_FOLLOWERS.get(level);
        if (pending == null || pending.isEmpty()) return;

        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            PendingSecondFollower state = entry.getValue();
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(state.targetUuid());
            if (target == null || !target.isAlive() || target.level() != level) {
                iterator.remove();
                continue;
            }

            if (state.ticksRemaining() > 0) {
                entry.setValue(new PendingSecondFollower(
                        state.targetUuid(), state.attackerUuid(), state.ticksRemaining() - 1
                ));
                continue;
            }

            if (!(level.getEntity(state.attackerUuid()) instanceof UnknownPlayerMobEntity actor1)
                    || !actor1.isAlive()) {
                iterator.remove();
                continue;
            }

            UnknownPlayerMobEntity actor2 = ModEntities.UNKNOWN_PLAYER.get().create(level);
            if (actor2 == null) {
                entry.setValue(new PendingSecondFollower(
                        state.targetUuid(), state.attackerUuid(), 5
                ));
                continue;
            }

            assignUniqueActorIdentity(level, target, actor2, actor1.getProfileNameForMessage());
            actor2.setCustomName(Component.literal(actor2.getChatDisplayName()));
            actor2.setCustomNameVisible(true);

            Vec3 away = actor1.position().subtract(target.position());
            Vec3 flat = new Vec3(away.x, 0.0D, away.z);
            if (flat.lengthSqr() < 0.001D) flat = new Vec3(0.0D, 0.0D, 1.0D);
            flat = flat.normalize();
            Vec3 side = new Vec3(-flat.z, 0.0D, flat.x);
            Vec3 spawn = actor1.position().add(side.scale(2.25D)).add(0.0D, 0.0D, 0.0D);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(spawn.x), Mth.floor(spawn.z));
            spawn = new Vec3(spawn.x, y, spawn.z);

            actor2.moveTo(spawn.x, spawn.y, spawn.z, actor1.getYRot(), 0.0F);
            if (!level.noCollision(actor2) || isVisibleFromPlayer(target, actor2)) {
                entry.setValue(new PendingSecondFollower(
                        state.targetUuid(), state.attackerUuid(), 5
                ));
                continue;
            }

            actor2.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(spawn)), MobSpawnType.NATURAL, null, null);
            if (!level.addFreshEntity(actor2)) {
                entry.setValue(new PendingSecondFollower(
                        state.targetUuid(), state.attackerUuid(), 5
                ));
                continue;
            }

            // Actor 1 is the warning friend; Actor 2 is the attacker.
            actor2.configureSecondEncounter(target, false, actor1.getUUID());
            applyUniqueIdentityIfNeeded(level, target, actor2);
            actor1.setSecondEncounterPartner(actor2.getUUID());
            // onAddedToWorld happens before the State 2 target/partner is
            // configured, so explicitly add both actors to this player's
            // tab list after their identities and target are ready.
            actor1.sendTabListAdd();
            actor2.sendTabListAdd();

            target.sendSystemMessage(
                    Component.translatable("multiplayer.player.joined", Component.literal(actor2.getGameProfile().getName()))
                            .withStyle(net.minecraft.ChatFormatting.YELLOW)
            );
            iterator.remove();
        }
    }

    /** Rolls and schedules State 3 after a successful State 2. */
    public static void scheduleState3(
            ServerLevel level,
            ServerPlayer target,
            String profileName,
            UUID profileUuid,
            boolean slim,
            double returnX,
            double returnY,
            double returnZ
    ) {
        if (!ENCOUNTER_SPAWNING_ENABLED) return;
        if (level == null || target == null || profileName == null || profileName.isBlank()) return;
        if (!level.getServer().isHardcore() || !target.isAlive() || target.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR) return;
        if (level.getServer().getPlayerList().getPlayers().size() <= 0) return;
        if (level.random.nextDouble() >= STATE3_CHANCE) return;

        STATE3.computeIfAbsent(level, ignored -> new HashMap<>()).put(
                target.getUUID(),
                new PendingState3(
                        target.getUUID(), profileName, profileUuid, slim,
                        returnX, returnY, returnZ, STATE3_DELAY
                )
        );
    }

    private static void processState3(ServerLevel level) {
        Map<UUID, PendingState3> pending = STATE3.get(level);
        if (pending == null || pending.isEmpty()) return;

        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            UUID targetUuid = entry.getKey();
            PendingState3 state = entry.getValue();
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(targetUuid);

            if (target == null || !target.isAlive() || target.level() != level) {
                iterator.remove();
                continue;
            }

            int remaining = state.ticksRemaining() - 1;
            if (remaining > 0) {
                entry.setValue(new PendingState3(
                        state.targetUuid(), state.profileName(), state.profileUuid(), state.slim(),
                        state.returnX(), state.returnY(), state.returnZ(), remaining
                ));
                continue;
            }

            if (hasActiveUnknown(level, target)) {
                entry.setValue(new PendingState3(
                        state.targetUuid(), state.profileName(), state.profileUuid(), state.slim(),
                        state.returnX(), state.returnY(), state.returnZ(), 20
                ));
                continue;
            }

            if (spawnState3(level, target, state)) {
                iterator.remove();
            } else {
                entry.setValue(new PendingState3(
                        state.targetUuid(), state.profileName(), state.profileUuid(), state.slim(),
                        state.returnX(), state.returnY(), state.returnZ(), 20
                ));
            }
        }
    }

    private static boolean spawnState3(ServerLevel level, ServerPlayer target, PendingState3 state) {
        UnknownPlayerMobEntity actor2 = ModEntities.UNKNOWN_PLAYER.get().create(level);
        if (actor2 == null) return false;

        // State 3 deliberately reuses Actor 2's exact identity from State 2.
        actor2.configureThirdEncounter(
                target, state.profileName(), state.profileUuid(), state.slim(),
                state.returnX(), state.returnY(), state.returnZ()
        );

        Vec3 safeReturn = findSafeReturnPosition(level, actor2,
                state.returnX(), state.returnY(), state.returnZ());
        if (safeReturn == null) {
            actor2.discard();
            return false;
        }
        actor2.moveTo(safeReturn.x, safeReturn.y, safeReturn.z, actor2.getYRot(), actor2.getXRot());
        BlockPos pos = BlockPos.containing(safeReturn);

        actor2.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null, null);
        if (!level.addFreshEntity(actor2)) return false;

        actor2.sendTabListAdd();
        target.sendSystemMessage(
                Component.translatable("multiplayer.player.joined", Component.literal(actor2.getGameProfile().getName()))
                        .withStyle(net.minecraft.ChatFormatting.YELLOW)
        );
        return true;
    }

    private static Vec3 findSafeReturnPosition(
            ServerLevel level,
            UnknownPlayerMobEntity actor,
            double returnX,
            double returnY,
            double returnZ
    ) {
        // First choice is always the exact saved State 2 position. Only move by
        // a tiny amount if the terrain changed and that exact spot is no longer
        // safe. This preserves the intended "came back to the same place" beat.
        Vec3 exact = new Vec3(returnX, returnY, returnZ);
        if (isSafeReturnPosition(level, actor, exact)) return exact;

        for (int radius = 1; radius <= 2; radius++) {
            for (int i = 0; i < 16; i++) {
                double angle = Math.PI * 2.0D * i / 16.0D;
                double x = returnX + Math.cos(angle) * radius * 0.5D;
                double z = returnZ + Math.sin(angle) * radius * 0.5D;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
                Vec3 candidate = new Vec3(x, y, z);
                if (isSafeReturnPosition(level, actor, candidate)) return candidate;
            }
        }
        return null;
    }

    private static boolean isSafeReturnPosition(ServerLevel level, UnknownPlayerMobEntity actor, Vec3 pos) {
        if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(pos))) return false;
        if (pos.y <= level.getMinBuildHeight() + 1) return false;

        BlockPos feet = BlockPos.containing(pos.x, pos.y - 1.0D, pos.z);
        if (!level.getBlockState(feet).isSolid()) return false;
        BlockPos body = BlockPos.containing(pos);
        if (level.getBlockState(body).isSuffocating(level, body)) return false;
        BlockPos head = body.above();
        if (level.getBlockState(head).isSuffocating(level, head)) return false;
        if (!level.getFluidState(body).isEmpty() || !level.getFluidState(head).isEmpty()) return false;

        var box = actor.getBoundingBox().move(
                pos.x - actor.getX(), pos.y - actor.getY(), pos.z - actor.getZ());
        return level.noCollision(actor, box);
    }

    /**
     * Gives an Unknown Player a username that does not collide with the real
     * players or other active Unknown Players. Up to ten real players get a
     * globally unique fake-name pool; beyond ten, uniqueness is still enforced
     * inside the same player's encounter but cross-player reuse is allowed.
     */
    private static void assignUniqueActorIdentity(
            ServerLevel level,
            ServerPlayer target,
            UnknownPlayerMobEntity actor,
            String otherActorName
    ) {
        int realPlayers = level.getServer().getPlayerList().getPlayers().size();
        boolean globalUnique = realPlayers <= 10;

        for (int attempt = 0; attempt < 64; attempt++) {
            actor.setRandomIdentity();
            String candidate = actor.getProfileNameForMessage();
            if (candidate.isBlank()) continue;
            if (candidate.equalsIgnoreCase(otherActorName == null ? "" : otherActorName)) continue;
            if (target != null && candidate.equalsIgnoreCase(target.getGameProfile().getName())) continue;

            boolean used = false;
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                if (player.getGameProfile().getName().equalsIgnoreCase(candidate)) {
                    used = true;
                    break;
                }
            }
            if (used) continue;

            // Check active fake players around every real player. This covers
            // multiple loaded dimensions without creating a giant world-sized
            // AABB scan every time an identity is assigned.
            for (ServerPlayer scanPlayer : level.getServer().getPlayerList().getPlayers()) {
                if (!(scanPlayer.level() instanceof ServerLevel scanLevel)) continue;
                for (UnknownPlayerMobEntity mob : scanLevel.getEntitiesOfClass(
                        UnknownPlayerMobEntity.class,
                        scanPlayer.getBoundingBox().inflate(256.0D),
                        mob -> mob.isAlive() && mob != actor
                )) {
                    String mobName = mob.getProfileNameForMessage();
                    if (!mobName.isBlank() && candidate.equalsIgnoreCase(mobName)) {
                        ServerPlayer mobTarget = mob.getBoundTarget();
                        if (globalUnique || (target != null && mobTarget != null
                                && target.getUUID().equals(mobTarget.getUUID()))) {
                            used = true;
                            break;
                        }
                    }
                }
                if (used) break;
            }
            if (used) continue;

            return;
        }

        // The pool is finite. If all names are occupied, keep the random
        // identity rather than blocking the encounter indefinitely.
        actor.setRandomIdentity();
    }

    private static boolean spawnSecondEncounter(
            ServerLevel level,
            ServerPlayer target,
            PendingSecondEncounter state
    ) {
        RandomSource random = level.random;

        for (int attempt = 0; attempt < 80; attempt++) {
            Vec3 view = target.getViewVector(1.0F);
            Vec3 behind = new Vec3(-view.x, 0.0D, -view.z);
            if (behind.lengthSqr() < 0.001D) continue;
            behind = behind.normalize();
            Vec3 side = new Vec3(-behind.z, 0.0D, behind.x);

            double offset = (random.nextDouble() - 0.5D) * Math.toRadians(28.0D);
            double cos = Math.cos(offset);
            double sin = Math.sin(offset);
            Vec3 direction = new Vec3(
                    behind.x * cos + side.x * sin,
                    0.0D,
                    behind.z * cos + side.z * sin
            ).normalize();

            double distance = 20.0D + random.nextDouble() * 6.0D;
            int x = Mth.floor(target.getX() + direction.x * distance);
            int z = Mth.floor(target.getZ() + direction.z * distance);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);

            if (!level.getWorldBorder().isWithinBounds(pos)
                    || !level.getBlockState(pos.below()).isSolid()) continue;

            // Actor 1 is the friend who will WARN the attacker. Actor 2 is the
            // friend who performs the single critical hit. Actor 1 enters first.
            UnknownPlayerMobEntity actor1 = ModEntities.UNKNOWN_PLAYER.get().create(level);
            if (actor1 == null) continue;

            // Actor 1 keeps the original identity from State 1. Actor 2 gets a
            // fresh identity from the normal Unknown Player random profile pool.
            actor1.setProfileIdentity(
                    state.profileName(),
                    state.profileUuid(),
                    state.slim()
            );
            actor1.setCustomName(Component.literal(state.profileName()));
            double sideOffset = 1.35D;
            Vec3 spawnCenter = new Vec3(x + 0.5D, y, z + 0.5D);
            Vec3 spawnAttacker = spawnCenter.add(side.x * sideOffset, 0.0D, side.z * sideOffset);
            actor1.moveTo(spawnAttacker.x, spawnAttacker.y, spawnAttacker.z, target.getYRot(), 0.0F);
            if (!level.noCollision(actor1)) continue;
            if (isVisibleFromPlayer(target, actor1)) continue;

            actor1.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null, null);

            // Actor 1 and Actor 2 enter the scene together: Actor 1 is the
            // warning friend and Actor 2 is the attacker.
            if (!level.addFreshEntity(actor1)) continue;

            actor1.configureSecondEncounter(target, true, null);
            applyUniqueIdentityIfNeeded(level, target, actor1);
            // Actor 1 is already in the world, so its normal onAddedToWorld
            // hook ran before bindTarget(). Add it to this player's tab list
            // now that State 2 has configured the target.
            actor1.sendTabListAdd();

            SECOND_FOLLOWERS
                    .computeIfAbsent(level, ignored -> new HashMap<>())
                    .put(target.getUUID(), new PendingSecondFollower(
                            target.getUUID(),
                            actor1.getUUID(),
                            SECOND_FOLLOWER_DELAY
                    ));

            // State 3 follows State 2, so State 2 must not mark the encounter
            // complete. Completion is recorded only after State 3 finishes.

            target.sendSystemMessage(
                    Component.translatable("multiplayer.player.joined", Component.literal(actor1.getGameProfile().getName()))
                            .withStyle(net.minecraft.ChatFormatting.YELLOW)
            );
            return true;
        }

        return false;
    }

    /**
     * Persistent per-player completion flag. This survives relog/server
     * restart because it is stored in the player's Forge persistent data.
     */
    public static boolean isEncounterCompleted(ServerPlayer player) {
        return player != null
                && player.getPersistentData().getBoolean(ENCOUNTER_COMPLETED_TAG);
    }

    /** Cancels a queued State 2 when the Stage 1 actor dies naturally. */
    public static void cancelSecondEncounter(ServerLevel level, ServerPlayer player) {
        if (level == null || player == null) return;

        Map<UUID, PendingSecondEncounter> pending = SECOND_ENCOUNTERS.get(level);
        if (pending != null) {
            pending.remove(player.getUUID());
        }

        Map<UUID, PendingSecondFollower> followers = SECOND_FOLLOWERS.get(level);
        if (followers != null) {
            followers.remove(player.getUUID());
        }
    }

    public static void cancelState3(ServerLevel level, ServerPlayer player) {
        if (level == null || player == null) return;
        Map<UUID, PendingState3> pending = STATE3.get(level);
        if (pending != null) pending.remove(player.getUUID());
    }

    public static void markEncounterCompleted(ServerPlayer player) {
        if (player == null) return;

        player.getPersistentData().putBoolean(ENCOUNTER_COMPLETED_TAG, true);

        if (player.level() instanceof ServerLevel level) {
            Map<UUID, Integer> attempts = NEXT_ATTEMPT.get(level);
            if (attempts != null) attempts.remove(player.getUUID());

        }
    }

    /**
     * For up to 10 real players, all currently visible fake-player identities
     * are kept unique. Above 10 real players duplicates are intentionally allowed.
     */
    private static String chooseUniqueIdentityName(ServerLevel level, ServerPlayer target, String preferred) {
        int realPlayers = level.getServer().getPlayerList().getPlayerCount();
        if (realPlayers > 10) return preferred;

        java.util.Set<String> used = new java.util.HashSet<>();
        for (UnknownPlayerMobEntity mob : level.getEntitiesOfClass(
                UnknownPlayerMobEntity.class,
                target.getBoundingBox().inflate(4096.0D),
                mob -> mob.isAlive())) {
            String name = mob.getChatDisplayName();
            if (name != null && !name.isBlank()) used.add(name.toLowerCase(java.util.Locale.ROOT));
        }

        if (preferred != null && !used.contains(preferred.toLowerCase(java.util.Locale.ROOT))) {
            return preferred;
        }

        // Generate candidates through the existing Adult identity pool without
        // adding another public API just for name selection.
        for (int i = 0; i < 64; i++) {
            UnknownPlayerMobEntity probe = ModEntities.UNKNOWN_PLAYER.get().create(level);
            if (probe == null) break;
            assignUniqueActorIdentity(level, target, probe, null);
            String candidate = probe.getChatDisplayName();
            probe.discard();
            if (candidate != null && !candidate.isBlank()
                    && !used.contains(candidate.toLowerCase(java.util.Locale.ROOT))) {
                return candidate;
            }
        }
        return preferred;
    }

    private static void applyUniqueIdentityIfNeeded(ServerLevel level, ServerPlayer target, UnknownPlayerMobEntity actor) {
        if (level.getServer().getPlayerList().getPlayerCount() > 10) return;
        String current = actor.getChatDisplayName();
        String unique = chooseUniqueIdentityName(level, target, current);
        if (unique != null && !unique.equals(current)) {
            actor.setProfileIdentity(unique, null, actor.isSlim());
            actor.setCustomName(Component.literal(unique));
            actor.setCustomNameVisible(true);
        }
    }

    private static int randomDelay(RandomSource random) {
        return Mth.nextInt(random, MIN_DELAY, MAX_DELAY);
    }

    private static boolean hasActiveUnknown(ServerLevel level, ServerPlayer target) {
        return level.getEntitiesOfClass(
                UnknownPlayerMobEntity.class,
                target.getBoundingBox().inflate(64.0D),
                mob -> mob.isAlive() && target.getUUID().equals(mob.getBoundTarget() == null ? null : mob.getBoundTarget().getUUID())
        ).stream().findFirst().isPresent();
    }

    /**
     * Find a legal ground position 16-30 blocks away which is not visible from
     * the player's current camera. This is deliberately more strict than the
     * vanilla spawn check: the encounter should never pop into view.
     */
    private static UnknownPlayerMobEntity spawnHiddenUnknown(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.random;

        for (int attempt = 0; attempt < 80; attempt++) {
            // Vanilla camera direction, flattened to the horizontal plane.
            // The spawn point is restricted to a narrow cone directly behind
            // the player, never the side/front.
            Vec3 view = player.getViewVector(1.0F);
            Vec3 behind = new Vec3(-view.x, 0.0D, -view.z);
            if (behind.lengthSqr() < 0.001D) continue;
            behind = behind.normalize();
            Vec3 side = new Vec3(-behind.z, 0.0D, behind.x);

            double offset = (random.nextDouble() - 0.5D) * Math.toRadians(40.0D);
            double cos = Math.cos(offset);
            double sin = Math.sin(offset);
            Vec3 spawnDirection = new Vec3(
                    behind.x * cos + side.x * sin,
                    0.0D,
                    behind.z * cos + side.z * sin
            ).normalize();

            double distance = 18.0D + random.nextDouble() * 12.0D;
            int x = Mth.floor(player.getX() + spawnDirection.x * distance);
            int z = Mth.floor(player.getZ() + spawnDirection.z * distance);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);

            if (!level.getWorldBorder().isWithinBounds(pos)) continue;
            if (!level.getBlockState(pos.below()).isSolid()) continue;

            UnknownPlayerMobEntity mob = ModEntities.UNKNOWN_PLAYER.get().create(level);
            if (mob == null) continue;

            mob.moveTo(
                    x + 0.5D,
                    y,
                    z + 0.5D,
                    random.nextFloat() * 360.0F,
                    0.0F
            );

            if (!level.noCollision(mob)) continue;
            // The encounter position is already validated explicitly above.
            // Do not route this scripted encounter through vanilla natural
            // mob-light/spawn rules; those rules are unrelated to this
            // player-like event and can reject a perfectly valid blind spot.

            if (isVisibleFromPlayer(player, mob)) continue;

            mob.bindTarget(player);
            mob.finalizeSpawn(
                    level,
                    level.getCurrentDifficultyAt(pos),
                    MobSpawnType.NATURAL,
                    null,
                    null
            );

            if (level.addFreshEntity(mob)) {
                return mob;
            }
        }

        return null;
    }

    private static boolean isVisibleFromPlayer(ServerPlayer player, UnknownPlayerMobEntity mob) {
        Vec3 start = player.getEyePosition();
        Vec3 end = mob.getEyePosition();

        Vec3 delta = end.subtract(start);
        if (delta.lengthSqr() <= 0.001D) return true;

        Vec3 direction = delta.normalize();
        double dot = player.getViewVector(1.0F).dot(direction);

        // Only accept a true rear position. A value of -0.85 means the
        // candidate is more than 148 degrees around from the camera forward
        // vector, leaving a large safety margin from both screen edges.
        // A wall is NOT required: being behind the camera is itself the blind
        // spot. The previous code incorrectly required a block hit here, so
        // open/flat terrain rejected every candidate and the encounter kept
        // retrying forever.
        return dot > -0.85D;
    }


}
