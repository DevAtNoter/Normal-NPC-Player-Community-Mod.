package net.devatnoter.normalnpcplayer.ai.growth;

import net.devatnoter.normalnpcplayer.entity.GrowthChildPlayerMobEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;

import java.util.List;

/**
 * External environment view. This class observes the world; it does not make decisions.
 */
public final class GrowthChildPerception {
    private GrowthChildPerception() {}

    public static Snapshot capture(GrowthChildPlayerMobEntity e, ServerLevel level) {
        List<LivingEntity> living = level.getEntitiesOfClass(
                LivingEntity.class, e.getBoundingBox().inflate(20.0D), x -> x.isAlive());
        List<Monster> hostile = level.getEntitiesOfClass(
                Monster.class, e.getBoundingBox().inflate(20.0D), x -> x.isAlive());
        List<ItemEntity> items = level.getEntitiesOfClass(
                ItemEntity.class, e.getBoundingBox().inflate(16.0D), x -> x.isAlive() && !x.getItem().isEmpty());
        return new Snapshot(e, living, hostile, items);
    }

    public record Snapshot(
            GrowthChildPlayerMobEntity self,
            List<LivingEntity> livingEntities,
            List<Monster> hostileEntities,
            List<ItemEntity> itemEntities
    ) {}
}
