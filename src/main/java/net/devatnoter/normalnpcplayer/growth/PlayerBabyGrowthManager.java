package net.devatnoter.normalnpcplayer.growth;

import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Time-based growth for Player Babies.
 *
 * 0 Hearty = 7 Minecraft days, 100 Hearty = 3 Minecraft days.
 * Growth progress is accumulated dynamically, so sustained good care speeds
 * the Baby up and sustained poor care slows it down without resetting progress.
 */
public final class PlayerBabyGrowthManager {
    private PlayerBabyGrowthManager() {}

    public static void tick(BabyNPCPlayerEntity baby) {
        if (!(baby.level() instanceof ServerLevel)) return;
        if (baby.getBabyType() != net.devatnoter.normalnpcplayer.breeding.BabyType.PLAYER) return;
        if (baby.isPlayerBabyGrown()) return;

        updateHearty(baby);
        updateDistrust(baby);

        int targetTicks = Math.max(
                BabyNPCPlayerEntity.PLAYER_GROWTH_MIN_TICKS,
                Math.min(BabyNPCPlayerEntity.PLAYER_GROWTH_MAX_TICKS, baby.getPlayerGrowthTargetTicks())
        );

        // Growth follows the world's DAY-TIME clock, not the number of entity
        // ticks processed. This deliberately makes /time add and /time set
        // advance the Baby's biological age too. Seven Minecraft days is
        // 168,000 day-time ticks, so even a 0-Hearty Baby reaches its maximum
        // seven-day threshold when the world is advanced by seven days.
        long now = ((ServerLevel) baby.level()).getDayTime();
        long last = baby.getGrowthLastDayTime();
        if (last <= 0L) {
            last = now;
        }

        long elapsed = now - last;
        if (elapsed > 0L) {
            baby.setGrowthProgress(
                    baby.getGrowthProgress() + (elapsed / (double) targetTicks)
            );
            baby.setGrowthLastDayTime(now);
        } else if (elapsed < 0L) {
            // The world clock was moved backwards. Re-anchor without granting
            // negative growth or a future catch-up burst.
            baby.setGrowthLastDayTime(now);
        }
        if (baby.getGrowthProgress() >= 1.0D) {
            if (BabyGrowthManager.growPlayerBaby(baby)) {
                baby.setGrowthState("grown");
            } else {
                // Keep the Baby retryable if adult spawning is temporarily blocked.
                baby.setGrowthProgress(0.999999D);
            }
        }
    }

    private static void updateHearty(BabyNPCPlayerEntity baby) {
        float health = baby.getHealth();
        if (health + 0.001F < baby.getHeartyLastHealth()) {
            // Being actually hurt is a major negative event.
            baby.addHearty(-8.0D);
            baby.setHeartyFearTicks(200);
        }
        baby.setHeartyLastHealth(health);

        if (baby.getHeartyFearTicks() > 0) {
            baby.setHeartyFearTicks(baby.getHeartyFearTicks() - 1);
            if (baby.tickCount % 20 == 0) baby.addHearty(-0.20D);
        }

        boolean threatened = isThreatened(baby);
        if (threatened) {
            baby.setHeartySafetyTicks(0);
            if (baby.tickCount % 20 == 0) baby.addHearty(-0.10D);
        } else {
            baby.setHeartySafetyTicks(baby.getHeartySafetyTicks() + 1);
            // Sustained safety is a slow positive influence, not an instant reward.
            if (baby.getHeartySafetyTicks() >= 200 && baby.tickCount % 20 == 0) {
                baby.addHearty(0.10D);
            }
        }

        // Food is evaluated continuously. Full food is the ideal condition.
        if (baby.tickCount % 20 == 0) {
            float food = baby.getFoodLevelExact();
            if (food >= 18.0F) {
                baby.addHearty(0.15D);
            } else if (food >= 14.0F) {
                baby.addHearty(0.05D);
            } else if (food < 6.0F) {
                baby.addHearty(-0.30D);
            } else if (food < 10.0F) {
                baby.addHearty(-0.15D);
            }
        }
    }

    private static void updateDistrust(BabyNPCPlayerEntity baby) {
        // Distrust is deliberately slower to change than Hearty. It represents
        // accumulated confidence in the people around the Baby, not momentary
        // fear. Feeding reduces it immediately; long safe periods reduce it
        // gradually. Threats do not directly spike it because damage is handled
        // by BabyNPCPlayerEntity.hurt(), where the attacker is known.
        if (baby.tickCount % 20 != 0) return;

        if (baby.getHeartyFearTicks() > 0 || isThreatened(baby)) {
            baby.addDistrust(0.10D);
            return;
        }

        if (baby.getHearty() >= 60.0D) {
            baby.addDistrust(-0.05D);
        }
    }

    private static boolean isThreatened(BabyNPCPlayerEntity baby) {
        if (baby.getLastHurtByMob() != null && baby.tickCount - baby.getLastHurtByMobTimestamp() <= 100) {
            return true;
        }

        return baby.level().getEntitiesOfClass(
                LivingEntity.class,
                baby.getBoundingBox().inflate(6.0D),
                entity -> entity instanceof Enemy && entity.isAlive()
        ).stream().findAny().isPresent();
    }
}
