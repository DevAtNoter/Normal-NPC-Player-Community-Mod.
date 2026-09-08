package net.devatnoter.normalnpcplayer.capability;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

public final class PlayerFamilyCapability {

    private PlayerFamilyCapability() {}

    public static final Capability<IPlayerFamily> PLAYER_FAMILY =
            CapabilityManager.get(new CapabilityToken<>() {});
}