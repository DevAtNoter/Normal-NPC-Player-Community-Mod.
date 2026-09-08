package net.devatnoter.normalnpcplayer.ai.growth;

import net.devatnoter.normalnpcplayer.entity.GrowthChildPlayerMobEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only snapshot of the GrowthChild's own state for AI consumption.
 * The AI may read this state but does not own or mutate it.
 */
public final class GrowthChildState {
    public final float health;
    public final float maxHealth;
    public final int foodLevel;
    public final float foodSaturation;
    public final int experienceLevel;
    public final int totalExperience;
    public final double x, y, z;
    public final boolean onGround, swimming, sprinting, crouching, fallFlying, sleeping;
    public final boolean following, guarding, hasHome, hasBed;
    public final GrowthChildPlayerMobEntity.DailyState dailyState;
    public final GrowthChildPlayerMobEntity.CombatState combatState;
    public final String currentTask;
    public final ItemStack mainHand;
    public final ItemStack offHand;
    public final List<ItemStack> armor;
    public final BlockPos home;
    public final BlockPos bed;
    public final BlockPos birth;
    public final LivingEntity target;

    private GrowthChildState(GrowthChildPlayerMobEntity e) {
        health = e.getHealth();
        maxHealth = e.getMaxHealth();
        foodLevel = e.getFoodLevel();
        foodSaturation = e.getFoodSaturation();
        experienceLevel = e.getExperienceLevel();
        totalExperience = e.getTotalExperience();
        x = e.getX(); y = e.getY(); z = e.getZ();
        onGround = e.onGround();
        swimming = e.isSwimming();
        sprinting = e.isSprinting();
        crouching = e.isCrouching();
        fallFlying = e.isFallFlying();
        sleeping = e.isSleeping();
        following = e.isFollowing();
        guarding = e.isGuarding();
        hasHome = e.hasHome();
        hasBed = e.hasOwnBed();
        dailyState = e.getDailyState();
        combatState = e.getCombatState();
        currentTask = e.getCurrentTask();
        mainHand = e.getMainHandItem().copy();
        offHand = e.getOffhandItem().copy();
        armor = new ArrayList<>();
        for (ItemStack s : e.getArmorSlots()) armor.add(s.copy());
        home = e.getHomePos();
        bed = e.getOwnBed();
        birth = e.getBirthLocation();
        target = e.getTarget();
    }

    public static GrowthChildState capture(GrowthChildPlayerMobEntity e) {
        return new GrowthChildState(e);
    }

    public List<ItemStack> armor() {
        return Collections.unmodifiableList(armor);
    }
}
