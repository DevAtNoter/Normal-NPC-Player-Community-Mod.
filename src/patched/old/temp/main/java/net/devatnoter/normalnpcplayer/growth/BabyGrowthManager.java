package net.devatnoter.normalnpcplayer.growth;

import net.devatnoter.normalnpcplayer.advancement.AdvancementManager;
import net.devatnoter.normalnpcplayer.capability.PlayerFamilyCapability;
import net.devatnoter.normalnpcplayer.entity.AdultPlayerMobEntity;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.devatnoter.normalnpcplayer.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class BabyGrowthManager {

    private static final String GROWTH_TRIGGERED_TAG =
            "normalnpcplayer_FreeTheEndGrowthTriggered";

    private BabyGrowthManager() {
    }

    public static boolean growOwnedBaby(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        BabyNPCPlayerEntity baby =
                findOwnedBabyEntity(level, player);

        if (baby != null
                && transformEntity(level, baby)) {
            markGrowthTriggered(player);
            AdvancementManager
                    .grantTheParentsMissionWasAccomplished(player);
            return true;
        }

        // Preserve the existing carrier-item growth path.
        for (int slot = 0;
             slot < player.getInventory().getContainerSize();
             slot++) {

            ItemStack stack =
                    player.getInventory().getItem(slot);

            if (!BabyNPCPlayerItem.isBabyStack(stack)) {
                continue;
            }

            CompoundTag data =
                    BabyNPCPlayerItem.copyEntityData(stack);

            if (!data.hasUUID("OwnerUUID")
                    || !player.getUUID().equals(
                    data.getUUID("OwnerUUID")
            )) {
                continue;
            }

            if (transformItem(
                    level,
                    player,
                    slot,
                    data
            )) {
                markGrowthTriggered(player);
                AdvancementManager
                        .grantTheParentsMissionWasAccomplished(player);
                return true;
            }
        }

        return false;
    }

    private static BabyNPCPlayerEntity findOwnedBabyEntity(
            ServerLevel level,
            ServerPlayer player
    ) {
        for (BabyNPCPlayerEntity baby :
                level.getEntitiesOfClass(
                        BabyNPCPlayerEntity.class,
                        player.getBoundingBox().inflate(128.0D)
                )) {

            if (player.getUUID().equals(
                    baby.getOwnerUUID()
            )) {
                return baby;
            }
        }

        try {
            final BabyNPCPlayerEntity[] result =
                    {null};

            player.getCapability(
                    PlayerFamilyCapability.PLAYER_FAMILY
            ).ifPresent(data -> {
                UUID childUUID = data.getChildUUID();

                if (childUUID == null) {
                    return;
                }

                Entity entity = level.getEntity(childUUID);

                if (entity instanceof BabyNPCPlayerEntity baby
                        && player.getUUID().equals(
                        baby.getOwnerUUID()
                )) {
                    result[0] = baby;
                }
            });

            return result[0];
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean hasGrowthAlreadyTriggered(
            ServerPlayer player
    ) {
        return player.getPersistentData()
                .getBoolean(GROWTH_TRIGGERED_TAG);
    }

    public static void markGrowthTriggered(
            ServerPlayer player
    ) {
        player.getPersistentData()
                .putBoolean(GROWTH_TRIGGERED_TAG, true);
    }

    /**
     * Growth identity rule:
     *
     * Baby has CustomName -> preserve that name and DO NOT create a random
     * PlayerMobProfileName.
     *
     * Baby has no CustomName -> create a random PlayerMob profile.
     */
    private static AdultPlayerMobEntity createAdult(
            ServerLevel level,
            boolean randomizeProfile
    ) {
        AdultPlayerMobEntity adult =
                ModEntities.ADULT_PLAYER_MOB
                        .get()
                        .create(level);

        if (adult == null) {
            return null;
        }

        if (randomizeProfile) {
            adult.setRandomIdentity();
        } else {
            adult.clearPlayerProfile();
        }

        return adult;
    }

    private static boolean transformEntity(
            ServerLevel level,
            BabyNPCPlayerEntity baby
    ) {
        Component preservedName =
                baby.hasCustomName()
                        ? baby.getCustomName()
                        : null;

        AdultPlayerMobEntity adult =
                createAdult(
                        level,
                        preservedName == null
                );

        if (adult == null) {
            return false;
        }

        if (preservedName != null) {
            adult.setPreservedCustomName(preservedName);
        }

        adult.setHealth(
                Math.max(
                        0.1F,
                        Math.min(
                                baby.getHealth(),
                                adult.getMaxHealth()
                        )
                )
        );

        // Preserve equipment if a future Baby implementation has it.
        copyEquipment(baby, adult);

        adult.moveTo(
                baby.getX(),
                baby.getY(),
                baby.getZ(),
                baby.getYRot(),
                baby.getXRot()
        );

        if (!level.noCollision(
                adult,
                adult.getBoundingBox()
        )) {
            adult.discard();
            return false;
        }

        if (!level.addFreshEntity(adult)) {
            adult.discard();
            return false;
        }

        baby.remove(
                Entity.RemovalReason.DISCARDED
        );

        setFamilyChildUUID(
                level,
                baby.getOwnerUUID(),
                adult.getUUID()
        );

        return true;
    }

    private static boolean transformItem(
            ServerLevel level,
            ServerPlayer owner,
            int slot,
            CompoundTag babyData
    ) {
        Component preservedName =
                extractCustomName(babyData);

        AdultPlayerMobEntity adult =
                createAdult(
                        level,
                        preservedName == null
                );

        if (adult == null) {
            return false;
        }

        if (preservedName != null) {
            adult.setPreservedCustomName(preservedName);
        }

        float health =
                babyData.contains("Health")
                        ? babyData.getFloat("Health")
                        : 20.0F;

        adult.setHealth(
                Math.max(
                        0.1F,
                        Math.min(
                                health,
                                adult.getMaxHealth()
                        )
                )
        );

        restoreEquipmentFromNbt(
                adult,
                babyData
        );

        adult.moveTo(
                owner.getX(),
                owner.getY(),
                owner.getZ(),
                owner.getYRot(),
                0.0F
        );

        if (!level.noCollision(
                adult,
                adult.getBoundingBox()
        )) {
            adult.discard();
            return false;
        }

        if (!level.addFreshEntity(adult)) {
            adult.discard();
            return false;
        }

        owner.getInventory()
                .setItem(slot, ItemStack.EMPTY);
        owner.getInventory().setChanged();

        setFamilyChildUUID(
                level,
                owner.getUUID(),
                adult.getUUID()
        );

        return true;
    }

    private static void copyEquipment(
            BabyNPCPlayerEntity from,
            AdultPlayerMobEntity to
    ) {
        to.setItemSlot(
                EquipmentSlot.MAINHAND,
                from.getItemBySlot(EquipmentSlot.MAINHAND).copy()
        );
        to.setItemSlot(
                EquipmentSlot.OFFHAND,
                from.getItemBySlot(EquipmentSlot.OFFHAND).copy()
        );
        to.setItemSlot(
                EquipmentSlot.FEET,
                from.getItemBySlot(EquipmentSlot.FEET).copy()
        );
        to.setItemSlot(
                EquipmentSlot.LEGS,
                from.getItemBySlot(EquipmentSlot.LEGS).copy()
        );
        to.setItemSlot(
                EquipmentSlot.CHEST,
                from.getItemBySlot(EquipmentSlot.CHEST).copy()
        );
        to.setItemSlot(
                EquipmentSlot.HEAD,
                from.getItemBySlot(EquipmentSlot.HEAD).copy()
        );
    }

    private static void restoreEquipmentFromNbt(
            AdultPlayerMobEntity adult,
            CompoundTag data
    ) {
        // Let vanilla LivingEntity/Mob NBT remain authoritative when these
        // tags exist. This method only handles explicit saved equipment.
        if (data.contains("HandItems")) {
            var hands = data.getList("HandItems", 10);

            if (hands.size() > 0) {
                adult.setItemSlot(
                        EquipmentSlot.MAINHAND,
                        ItemStack.of(hands.getCompound(0))
                );
            }

            if (hands.size() > 1) {
                adult.setItemSlot(
                        EquipmentSlot.OFFHAND,
                        ItemStack.of(hands.getCompound(1))
                );
            }
        }

        if (data.contains("ArmorItems")) {
            var armor = data.getList("ArmorItems", 10);

            if (armor.size() > 0) {
                adult.setItemSlot(
                        EquipmentSlot.FEET,
                        ItemStack.of(armor.getCompound(0))
                );
            }

            if (armor.size() > 1) {
                adult.setItemSlot(
                        EquipmentSlot.LEGS,
                        ItemStack.of(armor.getCompound(1))
                );
            }

            if (armor.size() > 2) {
                adult.setItemSlot(
                        EquipmentSlot.CHEST,
                        ItemStack.of(armor.getCompound(2))
                );
            }

            if (armor.size() > 3) {
                adult.setItemSlot(
                        EquipmentSlot.HEAD,
                        ItemStack.of(armor.getCompound(3))
                );
            }
        }
    }

    private static void setFamilyChildUUID(
            ServerLevel level,
            UUID ownerUUID,
            UUID childUUID
    ) {
        if (ownerUUID == null || childUUID == null) {
            return;
        }

        ServerPlayer owner =
                level.getServer()
                        .getPlayerList()
                        .getPlayer(ownerUUID);

        if (owner == null) {
            return;
        }

        owner.getCapability(
                PlayerFamilyCapability.PLAYER_FAMILY
        ).ifPresent(data ->
                data.setChildUUID(childUUID)
        );
    }

    private static Component extractCustomName(
            CompoundTag babyData
    ) {
        if (!babyData.contains("CustomName")) {
            return null;
        }

        String raw = babyData.getString("CustomName");

        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return Component.Serializer.fromJson(raw);
        } catch (Exception ignored) {
            return Component.literal(raw);
        }
    }
}
