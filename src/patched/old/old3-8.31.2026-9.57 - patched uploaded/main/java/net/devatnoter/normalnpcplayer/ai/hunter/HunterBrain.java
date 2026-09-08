package net.devatnoter.normalnpcplayer.ai.hunter;

import net.devatnoter.normalnpcplayer.entity.AdultPlayerMobEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.phys.Vec3;

/** Explicitly selected hunter. It never receives the hunter trait randomly. */
public final class HunterBrain {
    private HunterBrain() {}

    public static void registerGoals(AdultPlayerMobEntity e) {
        e.targetSelector.addGoal(0, new HurtByTargetGoal(e) {
            @Override public boolean canUse() {
                return isHunter(e) && super.canUse();
            }
        });

        e.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(e, Player.class, true) {
            @Override public boolean canUse() {
                return isHunter(e) && super.canUse();
            }
        });

        e.goalSelector.addGoal(2, new MeleeAttackGoal(e, 1.2D, true) {
            @Override public boolean canUse() {
                return isHunter(e) && super.canUse();
            }
            @Override public boolean canContinueToUse() {
                return isHunter(e) && super.canContinueToUse();
            }
        });

        e.goalSelector.addGoal(3, new RangedAttackGoal(e, 1.1D, 20, 18.0F) {
            @Override public boolean canUse() {
                return isHunter(e)
                        && e.getItemBySlot(EquipmentSlot.MAINHAND).getItem() instanceof BowItem
                        && super.canUse();
            }
            @Override public boolean canContinueToUse() {
                return isHunter(e)
                        && e.getItemBySlot(EquipmentSlot.MAINHAND).getItem() instanceof BowItem
                        && super.canContinueToUse();
            }
        });

        e.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(e, 1.0D) {
            @Override public boolean canUse() {
                return isHunter(e) && super.canUse();
            }
        });
    }

    public static void tick(AdultPlayerMobEntity e) {
        if (e.level().isClientSide || !e.isAlive()) return;

        ServerPlayer target = findExplicitTarget(e);
        if (target == null) return;

        e.setTarget(target);
        e.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double distance = e.distanceToSqr(target);
        e.setSprinting(true);

        if (distance > 10.0D * 10.0D) {
            Vec3 pos = target.position();
            e.getNavigation().moveTo(pos.x, pos.y, pos.z, speed(e));
        }

        if (e.onGround() && distance > 4.0D * 4.0D && distance < 12.0D * 12.0D
                && e.getRandom().nextFloat() < 0.08F) {
            e.getJumpControl().jump();
        }
    }

    private static ServerPlayer findExplicitTarget(AdultPlayerMobEntity e) {
        String configured = e.getHunterTargetName();

        if (!configured.isBlank()) {
            ServerPlayer named = e.level().getServer().getPlayerList().getPlayerByName(configured);
            if (named != null && named.isAlive()) return named;
        }

        // "First player of the world": when no explicit HunterTarget is stored,
        // bind once to the first currently available server player and persist
        // that name through AdultPlayerMobEntity's NBT field.
        ServerPlayer first = e.level().getServer().getPlayerList().getPlayers()
                .stream()
                .findFirst()
                .orElse(null);

        if (first != null) {
            e.setHunterTargetName(first.getGameProfile().getName());
        }

        return first;
    }

    private static double speed(AdultPlayerMobEntity e) {
        return switch (e.getStyle()) {
            case CASUAL -> 1.05D;
            case NORMAL -> 1.20D;
            case HARD -> 1.35D;
            case HARDCORE -> 1.50D;
        };
    }

    private static boolean isHunter(AdultPlayerMobEntity e) {
        return e.getTrait() == AdultPlayerMobEntity.PlayerMobTrait.HUNTER;
    }
}
