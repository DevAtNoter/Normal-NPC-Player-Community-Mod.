package net.devatnoter.normalnpcplayer.capability;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NormalNPCPlayer.MOD_ID)
public final class PlayerFamilyEvents {

    private PlayerFamilyEvents() {}

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {

        if (!(event.getObject() instanceof Player)) {
            return;
        }

        event.addCapability(
                NormalNPCPlayer.id("player_family"),
                new PlayerFamilyProvider()
        );

        NormalNPCPlayer.LOGGER.info("PlayerFamily capability attached.");
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {

        event.getOriginal().reviveCaps();

        event.getOriginal()
                .getCapability(PlayerFamilyCapability.PLAYER_FAMILY)
                .ifPresent(oldCap ->

                        event.getEntity()
                                .getCapability(PlayerFamilyCapability.PLAYER_FAMILY)
                                .ifPresent(newCap -> {

                                    newCap.setFirstChild(oldCap.hasFirstChild());
                                    newCap.setChildUUID(oldCap.getChildUUID());

                                })

                );

        event.getOriginal().invalidateCaps();
    }
}