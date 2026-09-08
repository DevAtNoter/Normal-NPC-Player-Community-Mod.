package net.devatnoter.normalnpcplayer.family;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.registry.ModEntities;
import net.minecraft.server.level.ServerPlayer;

public final class ChildSpawner {

    private ChildSpawner() {
    }

    /**
     * Spawn the player's first child.
     *
     * Order is intentional:
     *
     * 1. Create entity.
     * 2. Set owner.
     * 3. Put entity at the player's exact position.
     * 4. Add entity to the world.
     * 5. Create the real passenger relationship.
     *
     * The baby is NOT spawned at an offset around the player.
     */
    public static BabyNPCPlayerEntity spawn(ServerPlayer player) {

        BabyNPCPlayerEntity baby =
                ModEntities.BABY_NPC_PLAYER.get().create(player.serverLevel());

        if (baby == null) {
            NormalNPCPlayer.LOGGER.error(
                    "Unable to create BabyNPCPlayerEntity."
            );
            return null;
        }

        baby.setOwnerUUID(player.getUUID());
        baby.setOwnerName(player.getGameProfile().getName());
        baby.setProfileName(player.getGameProfile().getName());
        baby.setBiologicalParent(player);
        /*
         * Pick the appearance exactly once for the newly created first Baby.
         * Reconstructed Babies from ItemStack never pass through this path.
         */
        baby.initializeRandomAppearance();

        /*
         * Start exactly at the player's position.
         * The actual head position is handled by beginInitialRide().
         */
        baby.moveTo(
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                0.0F
        );

        NormalNPCPlayer.LOGGER.info(
                "Preparing Baby NPC {} for owner {} ({})",
                baby.getUUID(),
                baby.getOwnerName(),
                baby.getOwnerUUID()
        );

        /*
         * The entity must exist in the world before startRiding().
         */
        player.serverLevel().addFreshEntity(baby);

        baby.beginInitialRide(player);

        if (baby.getVehicle() != player) {
            NormalNPCPlayer.LOGGER.error(
                    "Life child head mount FAILED: Baby={} Owner={}",
                    baby.getUUID(),
                    player.getUUID()
            );
        } else {
            NormalNPCPlayer.LOGGER.info(
                    "Baby head passenger active: Baby={} Owner={}",
                    baby.getUUID(),
                    player.getUUID()
            );
        }

        return baby;
    }
}
