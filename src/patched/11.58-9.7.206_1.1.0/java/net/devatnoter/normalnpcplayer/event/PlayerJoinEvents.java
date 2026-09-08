package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.capability.PlayerFamilyCapability;
import net.devatnoter.normalnpcplayer.family.FamilyManager;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
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
        NormalNPCPlayer.LOGGER.info("Player : {}", player.getGameProfile().getName());
        NormalNPCPlayer.LOGGER.info("Hardcore : {}", player.server.isHardcore());

        // Restore before the family bootstrap checks childUUID.
        restoreSavedBaby(player);

        if (!player.server.isHardcore()) {
            NormalNPCPlayer.LOGGER.info("Not Hardcore World.");
            return;
        }

        var capability = player.getCapability(PlayerFamilyCapability.PLAYER_FAMILY);
        if (!capability.isPresent()) {
            NormalNPCPlayer.LOGGER.error("PlayerFamily Capability NOT FOUND!");
            return;
        }

        capability.ifPresent(data -> {
            NormalNPCPlayer.LOGGER.info("firstChild = {}", data.hasFirstChild());
            NormalNPCPlayer.LOGGER.info("childUUID = {}", data.getChildUUID());

            if (data.getChildUUID() != null) {
                NormalNPCPlayer.LOGGER.info("Child already exists.");
                return;
            }

            NormalNPCPlayer.LOGGER.info("Creating first child...");
            FamilyManager.createFirstChild(player);
            NormalNPCPlayer.LOGGER.info("Finished creating first child.");
        });

        NormalNPCPlayer.LOGGER.info("==================================");
    }

    /**
     * Save a Baby in world-persistent SavedData before the Player disappears.
     * This is reliable on both dedicated servers and integrated singleplayer.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        BabyNPCPlayerEntity baby = findOwnedBaby(player);
        if (baby == null || baby.isRemoved()) {
            return;
        }

        CompoundTag babyTag = new CompoundTag();
        baby.saveWithoutId(babyTag);

        // Do NOT rely only on getVehicle(): when a Player logs out, vanilla can
        // clear the passenger relationship before this event runs. Baby keeps
        // an explicit head-ride flag for exactly this logout boundary.
        // The automatic first head ride is deliberately NOT persisted.
        // If the player exits immediately after joining, the Baby may still
        // be physically riding the Player, but that does not mean the player
        // intentionally chose to keep the Baby on their head across saves.
        // Only a normal/persistent head ride is restored after relog/world load.
        // INITIAL and NECK_RIDE have different save semantics.
        //
        // INITIAL uses the same reliable logout snapshot mechanism as v20:
        // capture the complete Baby NBT in SavedData, then recreate it on the
        // next login and explicitly start INITIAL again. This preserves the
        // original INITIAL behavior instead of converting it into NECK_RIDE.
        //
        // NECK_RIDE is intentionally left on its existing path and key.
        boolean wasInitialHeadRide = baby.isInitialRideActive();
        boolean wasNeckRide = baby.isNeckRide();
        babyTag.putBoolean("NPPWasInitialHeadRide", wasInitialHeadRide);
        babyTag.putBoolean("NPPWasNeckRide", wasNeckRide);
        babyTag.putBoolean("NPPReturnWithPlayer", true);
        babyTag.putUUID("NPPReturnPlayerUUID", player.getUUID());

        // Save the actual world state at logout.
        babyTag.putDouble("NPPReturnX", baby.getX());
        babyTag.putDouble("NPPReturnY", baby.getY());
        babyTag.putDouble("NPPReturnZ", baby.getZ());
        babyTag.putFloat("NPPReturnYRot", baby.getYRot());
        babyTag.putFloat("NPPReturnXRot", baby.getXRot());
        babyTag.putString("NPPReturnDimension", baby.level().dimension().location().toString());

        // The Player persistent NBT was the wrong persistence boundary here:
        // PlayerLoggedOutEvent can be after the ordinary player-save point.
        // SavedData is explicitly written by Minecraft's world save system.
        BabyLogoutSavedData.get(player.serverLevel()).put(player.getUUID(), babyTag);

        // Remove the live entity so the Baby truly logs out with its Player.
        baby.discard();

        NormalNPCPlayer.LOGGER.info(
                "Saved Baby {} for logout of {} (headPassenger={})",
                baby.getUUID(), player.getGameProfile().getName(), wasNeckRide
        );
    }

    private static void restoreSavedBaby(ServerPlayer player) {
        CompoundTag babyTag = BabyLogoutSavedData.get(player.serverLevel()).take(player.getUUID());
        if (babyTag == null || !babyTag.getBoolean("NPPReturnWithPlayer")) {
            return;
        }

        if (babyTag.hasUUID("NPPReturnPlayerUUID")
                && !player.getUUID().equals(babyTag.getUUID("NPPReturnPlayerUUID"))) {
            return;
        }

        ServerLevel level = player.serverLevel();
        String savedDimension = babyTag.getString("NPPReturnDimension");
        if (!savedDimension.isEmpty()) {
            try {
                ResourceLocation dimensionId = ResourceLocation.parse(savedDimension);
                ServerLevel savedLevel = player.server.getLevel(
                        ResourceKey.create(Registries.DIMENSION, dimensionId)
                );
                if (savedLevel != null) {
                    level = savedLevel;
                }
            } catch (Exception ignored) {
                // Fall back to the current Player dimension.
            }
        }

        BabyNPCPlayerEntity baby = ModEntities.BABY_NPC_PLAYER.get().create(level);
        if (baby == null) {
            return;
        }

        baby.load(babyTag);
        baby.stopRiding();
        baby.setPos(
                babyTag.getDouble("NPPReturnX"),
                babyTag.getDouble("NPPReturnY"),
                babyTag.getDouble("NPPReturnZ")
        );
        baby.setYRot(babyTag.getFloat("NPPReturnYRot"));
        baby.setXRot(babyTag.getFloat("NPPReturnXRot"));
        baby.yHeadRot = baby.getYRot();
        baby.yBodyRot = baby.getYRot();

        if (!level.addFreshEntity(baby)) {
            baby.discard();
            return;
        }

        // Restore each lifecycle into the same lifecycle it had at logout.
        // INITIAL is restored as INITIAL, never as NECK_RIDE.
        boolean wasInitialHeadRide = babyTag.getBoolean("NPPWasInitialHeadRide");
        if (wasInitialHeadRide) {
            baby.beginInitialRide(player);
        }

        // Existing NECK_RIDE behavior is intentionally unchanged.
        boolean wasNeckRide = babyTag.contains("NPPWasNeckRide")
                ? babyTag.getBoolean("NPPWasNeckRide")
                : babyTag.getBoolean("NPPWasHeadPassenger"); // legacy saves
        if (!wasInitialHeadRide && wasNeckRide) {
            baby.beginNeckRide(player);
        }

        NormalNPCPlayer.LOGGER.info(
                "Restored Baby {} for player {} (initialHeadRide={}, neckRide={})",
                baby.getUUID(), player.getGameProfile().getName(),
                wasInitialHeadRide, wasNeckRide
        );
    }

    private static BabyNPCPlayerEntity findOwnedBaby(ServerPlayer player) {
        for (var passenger : player.getPassengers()) {
            if (passenger instanceof BabyNPCPlayerEntity baby
                    && player.getUUID().equals(baby.getOwnerUUID())) {
                return baby;
            }
        }

        final BabyNPCPlayerEntity[] result = new BabyNPCPlayerEntity[1];
        player.getCapability(PlayerFamilyCapability.PLAYER_FAMILY).ifPresent(data -> {
            if (data.getChildUUID() == null) {
                return;
            }
            var entity = player.serverLevel().getEntity(data.getChildUUID());
            if (entity instanceof BabyNPCPlayerEntity baby) {
                result[0] = baby;
            }
        });
        return result[0];
    }
}
