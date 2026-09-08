package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.capability.PlayerFamilyCapability;
import net.devatnoter.normalnpcplayer.family.FamilyManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NormalNPCPlayer.MOD_ID)
public final class PlayerJoinEvents {

    private PlayerJoinEvents() {
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        NormalNPCPlayer.LOGGER.info("========== PLAYER LOGIN ==========");

        NormalNPCPlayer.LOGGER.info(
                "Player : {}",
                player.getGameProfile().getName()
        );

        NormalNPCPlayer.LOGGER.info(
                "Hardcore : {}",
                player.server.isHardcore()
        );

        // ทำงานเฉพาะ Hardcore
        if (!player.server.isHardcore()) {
            NormalNPCPlayer.LOGGER.info("Not Hardcore World.");
            return;
        }

        var capability =
                player.getCapability(PlayerFamilyCapability.PLAYER_FAMILY);

        if (!capability.isPresent()) {

            NormalNPCPlayer.LOGGER.error(
                    "PlayerFamily Capability NOT FOUND!"
            );

            return;
        }

        capability.ifPresent(data -> {

            NormalNPCPlayer.LOGGER.info(
                    "firstChild = {}",
                    data.hasFirstChild()
            );

            NormalNPCPlayer.LOGGER.info(
                    "childUUID = {}",
                    data.getChildUUID()
            );

            /*
             * TODO
             *
             * อนาคตจะเปลี่ยนเป็น
             *
             * FamilyManager.findChild(player)
             *
             * เพื่อตรวจว่า UUID ยังมี Entity อยู่จริงหรือไม่
             */

            if (data.getChildUUID() != null) {

                NormalNPCPlayer.LOGGER.info(
                        "Child already exists."
                );

                return;
            }

            NormalNPCPlayer.LOGGER.info(
                    "Creating first child..."
            );

            FamilyManager.createFirstChild(player);

            NormalNPCPlayer.LOGGER.info(
                    "Finished creating first child."
            );

        });

        NormalNPCPlayer.LOGGER.info("==================================");
    }
}