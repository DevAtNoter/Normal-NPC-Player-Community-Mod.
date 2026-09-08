package net.devatnoter.normalnpcplayer.ai.hunter;

import net.devatnoter.normalnpcplayer.entity.AdultPlayerMobEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;

/** Hunter trait: explicitly selected by NBT; never randomly assigned. */
public final class HunterBrain {
    private HunterBrain() {}

    public static void registerGoals(AdultPlayerMobEntity e) {
        e.targetSelector.addGoal(0, new HurtByTargetGoal(e) {
            @Override public boolean canUse() { return e.getTrait()==AdultPlayerMobEntity.PlayerMobTrait.HUNTER && super.canUse(); }
        });
        e.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(e, Player.class, true) {
            @Override public boolean canUse() { return e.getTrait()==AdultPlayerMobEntity.PlayerMobTrait.HUNTER && super.canUse(); }
        });
        e.goalSelector.addGoal(2, new MeleeAttackGoal(e,1.2D,true) {
            @Override public boolean canUse() { return e.getTrait()==AdultPlayerMobEntity.PlayerMobTrait.HUNTER && super.canUse(); }
            @Override public boolean canContinueToUse() { return e.getTrait()==AdultPlayerMobEntity.PlayerMobTrait.HUNTER && super.canContinueToUse(); }
        });
        e.goalSelector.addGoal(3, new RangedAttackGoal(e,1.1D,20,18.0F) {
            @Override public boolean canUse() { return e.getTrait()==AdultPlayerMobEntity.PlayerMobTrait.HUNTER && e.getItemBySlot(EquipmentSlot.MAINHAND).getItem() instanceof BowItem && super.canUse(); }
        });
        e.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(e,1.0D) {
            @Override public boolean canUse() { return e.getTrait()==AdultPlayerMobEntity.PlayerMobTrait.HUNTER && super.canUse(); }
        });
    }

    public static void tick(AdultPlayerMobEntity e) {
        if (e.level().isClientSide || !e.isAlive()) return;
        // Hunter intentionally does not acquire a random profile/identity.
        // Target selection is player-based; future versions can bind an explicit UUID/name here.
    }
}
