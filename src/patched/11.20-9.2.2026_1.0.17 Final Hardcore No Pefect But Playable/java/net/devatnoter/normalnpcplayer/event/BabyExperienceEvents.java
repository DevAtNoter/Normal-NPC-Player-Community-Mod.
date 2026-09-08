package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lets a carried Baby compete with its holder for XP orbs without spawning a
 * hidden entity. A carried Baby has no physical position of its own, so a
 * player/Baby tie is resolved by alternating pickups: Baby, Player, Baby...
 */
@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class BabyExperienceEvents {

    private static final String NEXT_BABY_XP_TAG =
            "NormalNPCPlayerNextBabyXp";

    private BabyExperienceEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerPickupXp(PlayerXpEvent.PickupXp event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ItemStack babyStack = BabyNPCPlayerItem.findCarriedBaby(player);
        if (babyStack.isEmpty()) {
            return;
        }

        CompoundTag playerData = player.getPersistentData();
        boolean babyGetsNext = !playerData.contains(NEXT_BABY_XP_TAG)
                || playerData.getBoolean(NEXT_BABY_XP_TAG);

        if (!babyGetsNext) {
            // Player wins this pickup. Toggle so the next contested orb goes
            // to the carried Baby.
            playerData.putBoolean(NEXT_BABY_XP_TAG, true);
            return;
        }

        ExperienceOrb orb = event.getOrb();
        int value = Math.max(0, orb.getValue());
        if (value <= 0) {
            return;
        }

        if (BabyNPCPlayerItem.addExperienceToCarriedBaby(
                babyStack,
                value,
                player
        )) {
            orb.discard();
            event.setCanceled(true);
            playerData.putBoolean(NEXT_BABY_XP_TAG, false);
        }
    }
}
