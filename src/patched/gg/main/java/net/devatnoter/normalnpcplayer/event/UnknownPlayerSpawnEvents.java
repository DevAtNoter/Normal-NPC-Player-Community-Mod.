package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.entity.UnknownPlayerMobEntity;
import net.devatnoter.normalnpcplayer.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
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

    // Encounter cadence: the first encounter and every later encounter are
    // scheduled independently in the 1-3 minute range.
    private static final int FIRST_DELAY = 20 * 3;
    private static final int MIN_DELAY = 20 * 60;
    private static final int MAX_DELAY = 20 * 60 * 3;
    private static final Map<ServerLevel, Map<UUID, Integer>> NEXT_ATTEMPT = new HashMap<>();

    private UnknownPlayerSpawnEvents() {
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
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) {
            return;
        }

        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension() != Level.OVERWORLD) continue;

            // IMPORTANT: the schedule belongs to this actual ServerLevel
            // instance, not only to the player's UUID. Creating/opening a new
            // world must therefore create a fresh encounter timer even when
            // the same player UUID enters it.
            Map<UUID, Integer> worldAttempts = NEXT_ATTEMPT.computeIfAbsent(
                    level,
                    ignored -> new HashMap<>()
            );

            for (ServerPlayer player : level.players()) {
                UUID id = player.getUUID();

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
