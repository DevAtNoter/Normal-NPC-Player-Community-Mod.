package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.config.NNPConfig;
import net.devatnoter.normalnpcplayer.entity.GrowthChildHardcorePlayerMobEntity;
import net.devatnoter.normalnpcplayer.entity.GrowthChildPlayerMobEntity;
import net.devatnoter.normalnpcplayer.entity.AdultPlayerMobEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Growth Child survival death/respawn policy.
 *
 * Normal Survival/Adventure Growth Children have exactly two respawn destinations:
 * 1) their own valid claimed bed;
 * 2) their original birth point when that bed is unavailable.
 *
 * Baby does not own/persist a bed for this system. The bed state belongs to the
 * Growth Child after growth, while the birth point is inherited from BabyGrowthManager.
 * Hardcore Growth Children remain permanently dead.
 */
@Mod.EventBusSubscriber(modid="normalnpcplayer")
public final class GrowthChildLifecycleEvents {
    private GrowthChildLifecycleEvents() {}

    private record RespawnDestination(ServerLevel level, BlockPos pos, boolean bed) {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof GrowthChildPlayerMobEntity child)) return;
        if (!NNPConfig.playerNpcRespawnEnabled() || child instanceof GrowthChildHardcorePlayerMobEntity) return;
        if (!(child.level() instanceof ServerLevel deathLevel) || deathLevel.getLevelData().isHardcore()) return;

        CompoundTag state = new CompoundTag();
        child.addAdditionalSaveData(state);

        RespawnDestination destination = chooseRespawn(child, deathLevel.getServer());
        if (destination == null) return;

        boolean keepInventory = NNPConfig.followPlayerGameRule()
                && deathLevel.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);

        if (!keepInventory) {
            // PlayerLikeDeathDrop awards this vanilla-like XP amount. Preserve the
            // exact remaining total XP across the reincarnation instead of guessing
            // from the level alone.
            int droppedXp = Math.min(100, Math.max(0, child.getExperienceLevel()) * 7);
            int remainingXp = Math.max(0, child.getTotalExperience() - droppedXp);
            int remainingLevel = 0;
            while (remainingLevel < 100
                    && AdultPlayerMobEntity.getXpAtLevel(remainingLevel + 1) <= remainingXp) {
                remainingLevel++;
            }
            state.putInt("PlayerMobTotalExperience", remainingXp);
            state.putInt("PlayerMobExperienceLevel", remainingLevel);

            // Equipment/inventory are dropped into the world. Do not duplicate them.
            state.remove("ArmorItems");
            state.remove("HandItems");
            state.remove("Inventory");
        }

        deathLevel.getServer().execute(() -> respawn(child, state, destination, keepInventory));
    }

    /**
     * Select exactly one of the two Growth Child respawn modes:
     * own valid bed first, otherwise original birth point.
     */
    private static RespawnDestination chooseRespawn(
            GrowthChildPlayerMobEntity child,
            MinecraftServer server
    ) {
        // Mode 1: own bed. The saved dimension matters because the bed can be
        // located in another loaded dimension.
        if (child.hasOwnBed() && child.getOwnBed() != null) {
            ServerLevel bedLevel = getServerLevel(server, child.getOwnBedDimensionForLifecycle());
            BlockPos bed = child.getOwnBed();
            if (bedLevel != null
                    && bedLevel.getBlockState(bed).is(net.minecraft.tags.BlockTags.BEDS)) {
                return new RespawnDestination(bedLevel, bed, true);
            }
        }

        // Mode 2: no usable own bed -> original birth point.
        BlockPos birth = child.getBirthLocation();
        if (birth != null) {
            ServerLevel birthLevel = getServerLevel(server, child.getBirthDimension());
            if (birthLevel != null) {
                return new RespawnDestination(birthLevel, birth, false);
            }
        }

        // No birth point is a corrupted/legacy state, not a third respawn mode.
        return null;
    }

    private static ServerLevel getServerLevel(MinecraftServer server, String dimensionId) {
        if (server == null || dimensionId == null || dimensionId.isBlank()) return null;
        try {
            ResourceLocation location = new ResourceLocation(dimensionId);
            ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, location);
            return server.getLevel(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void respawn(
            GrowthChildPlayerMobEntity dead,
            CompoundTag state,
            RespawnDestination destination,
            boolean keepInventory
    ) {
        if (dead.isAlive()) return;

        ServerLevel targetLevel = destination.level();
        var type = dead.getType();
        var entity = type.create(targetLevel);
        if (!(entity instanceof GrowthChildPlayerMobEntity child)) return;

        child.readAdditionalSaveData(state);
        BlockPos pos = destination.pos();
        child.moveTo(
                pos.getX() + 0.5D,
                pos.getY() + 0.1D,
                pos.getZ() + 0.5D,
                dead.getYRot(),
                dead.getXRot()
        );
        child.setHealth(child.getMaxHealth());

        if (!keepInventory) child.getTraitInventory().clearContent();

        if (targetLevel.addFreshEntity(child)) {
            String reason = destination.bed()
                    ? "I have respawned at my bed."
                    : "I could not use my bed, so I returned to where I was born.";
            child.chatNearby(reason, 18.0D);
        }
    }
}
