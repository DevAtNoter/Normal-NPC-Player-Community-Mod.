package net.devatnoter.normalnpcplayer.breeding;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.guide.GuideBookManager;
import net.devatnoter.normalnpcplayer.advancement.AdvancementManager;
import net.devatnoter.normalnpcplayer.capability.PlayerFamilyCapability;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Direct Player Baby birth used by commands/admin tools.
 *
 * This intentionally bypasses PlayerBreedingManager completely:
 * no Love Effect, breeding session, distance timer, or breeding cooldown.
 * The resulting Baby still uses the exact Player Baby lineage/spawn data.
 */
public final class PlayerBabyBirthManager {

    private PlayerBabyBirthManager() {
    }

    public static BabyNPCPlayerEntity birth(ServerPlayer parentA, ServerPlayer parentB) {
        if (parentA == null || parentB == null || parentA == parentB) {
            return null;
        }
        if (parentA.serverLevel() != parentB.serverLevel()) {
            return null;
        }

        ServerLevel level = parentB.serverLevel();
        BabyNPCPlayerEntity baby = createBaby(level, parentB);
        if (baby == null) return null;

        baby.setProfileName(parentA.getGameProfile().getName());
        baby.setBiologicalParents(parentA, parentB);
        finishBirth(level, baby, parentB);
        rememberAsCurrentChild(parentA, baby);
        rememberAsCurrentChild(parentB, baby);
        AdvancementManager.grantStarterOfTheStory(parentA);
        AdvancementManager.grantStarterOfTheStory(parentB);
        return baby;
    }

    /**
     * Directly creates a Player Baby with exactly one biological parent.
     * This does not pass through PlayerBreedingManager and does not invent a second parent.
     */
    public static BabyNPCPlayerEntity birth(ServerPlayer parent) {
        if (parent == null) return null;

        ServerLevel level = parent.serverLevel();
        BabyNPCPlayerEntity baby = createBaby(level, parent);
        if (baby == null) return null;

        baby.setProfileName(parent.getGameProfile().getName());
        baby.setSingleBiologicalParent(parent);
        finishBirth(level, baby, parent);
        rememberAsCurrentChild(parent, baby);
        AdvancementManager.grantStarterOfTheStory(parent);
        return baby;
    }

    private static void rememberAsCurrentChild(ServerPlayer parent, BabyNPCPlayerEntity baby) {
        parent.getCapability(PlayerFamilyCapability.PLAYER_FAMILY).ifPresent(data -> {
            data.setChildUUID(baby.getUUID());
            data.setFirstChild(true);
        });
    }

    private static BabyNPCPlayerEntity createBaby(ServerLevel level, ServerPlayer owner) {
        BabyNPCPlayerEntity baby = ModEntities.BABY_NPC_PLAYER.get().create(level);
        if (baby == null) {
            NormalNPCPlayer.LOGGER.error("Unable to create Player Baby from direct birth command.");
            return null;
        }

        baby.setBabyType(BabyType.PLAYER);
        baby.setBirthGameTime(level.getGameTime());
        baby.setGrowthLastDayTime(level.getDayTime());
        baby.setOwnerUUID(owner.getUUID());
        baby.setOwnerName(owner.getGameProfile().getName());
        baby.setFoster(false);
        baby.setOrphaned(false);
        baby.initializeRandomAppearance();
        return baby;
    }

    private static void finishBirth(ServerLevel level, BabyNPCPlayerEntity baby, ServerPlayer owner) {
        baby.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), 0.0F);
        level.addFreshEntity(baby);
        baby.beginInitialRide(owner);
        GuideBookManager.giveAtBirth(owner);
    }
}
