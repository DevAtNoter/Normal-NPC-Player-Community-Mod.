package net.devatnoter.normalnpcplayer.family;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.advancement.AdvancementManager;
import net.devatnoter.normalnpcplayer.capability.PlayerFamilyCapability;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.server.level.ServerPlayer;

public final class FamilyManager {

    private FamilyManager() {
    }

    /**
     * สร้างลูกคนแรกของผู้เล่น
     */
    public static BabyNPCPlayerEntity createFirstChild(ServerPlayer player) {

        NormalNPCPlayer.LOGGER.info("FamilyManager.createFirstChild()");

        BabyNPCPlayerEntity baby = ChildSpawner.spawn(player);

        if (baby == null) {

            NormalNPCPlayer.LOGGER.error(
                    "Failed to spawn BabyNPCPlayerEntity."
            );

            return null;
        }

        player.getCapability(PlayerFamilyCapability.PLAYER_FAMILY).ifPresent(data -> {

            data.setChildUUID(baby.getUUID());

            if (!data.hasFirstChild()) {

                data.setFirstChild(true);

                AdvancementManager.grantYourFirstChild(player);

            }

        });

        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        BabyNPCPlayerEntity.playFamilyHearts(level, baby);

        NormalNPCPlayer.LOGGER.info(
                "First Child UUID = {}",
                baby.getUUID()
        );

        return baby;
    }

    /**
     * ผู้เล่นมีลูกอยู่หรือไม่
     */
    public static boolean hasChild(ServerPlayer player) {

        return player.getCapability(PlayerFamilyCapability.PLAYER_FAMILY)
                .map(data -> data.getChildUUID() != null)
                .orElse(false);
    }

}