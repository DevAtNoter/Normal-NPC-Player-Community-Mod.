package net.devatnoter.normalnpcplayer.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.entity.ExperienceOrb;

/**
 * Shared Player-style death handling for NPC player entities.
 *
 * This deliberately treats inventory/equipment generically: no weapon,
 * armor, enchantment, or item-specific death rules live here.
 */
public final class PlayerLikeDeathDrop {
    private PlayerLikeDeathDrop() {}

    public static void drop(
            LivingEntity entity,
            Container inventory,
            int experienceLevel
    ) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        // Match the vanilla player's keepInventory behavior.
        if (level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }

        // Player death XP: level * 7, capped at 100 XP.
        int xp = Math.min(100, Math.max(0, experienceLevel) * 7);
        if (xp > 0) {
            ExperienceOrb.award(level, entity.position(), xp);
        }

        // Drop every custom inventory slot exactly as stored, preserving
        // NBT, enchantments, durability, custom names, etc.
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                entity.spawnAtLocation(stack.copy());
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }

        // Drop both hands and all four armor slots. No per-item rules.
        dropEquipmentSlot(entity, EquipmentSlot.MAINHAND);
        dropEquipmentSlot(entity, EquipmentSlot.OFFHAND);
        dropEquipmentSlot(entity, EquipmentSlot.HEAD);
        dropEquipmentSlot(entity, EquipmentSlot.CHEST);
        dropEquipmentSlot(entity, EquipmentSlot.LEGS);
        dropEquipmentSlot(entity, EquipmentSlot.FEET);
    }

    private static void dropEquipmentSlot(LivingEntity entity, EquipmentSlot slot) {
        ItemStack stack = entity.getItemBySlot(slot);
        if (stack.isEmpty()) {
            return;
        }

        entity.spawnAtLocation(stack.copy());
        entity.setItemSlot(slot, ItemStack.EMPTY);
    }
}
