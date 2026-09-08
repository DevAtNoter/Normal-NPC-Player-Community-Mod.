package net.devatnoter.normalnpcplayer.ai.normal;

import net.devatnoter.normalnpcplayer.entity.AdultPlayerMobEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Server-side high-level behavior for PlayerMobTrait.NORMAL. */
public final class NormalBrain {
    private static final int ACTION_INTERVAL = 10;
    private static final int SOCIAL_INTERVAL = 35;
    private static final int BUILD_INTERVAL = 12;
    private static final int MINE_INTERVAL = 20;

    private NormalBrain() {}

    public static void registerGoals(AdultPlayerMobEntity e) {
        e.goalSelector.addGoal(2, new MeleeAttackGoal(e, 1.15D, true) {
            @Override public boolean canUse() { return e.getTrait() == AdultPlayerMobEntity.PlayerMobTrait.NORMAL && super.canUse(); }
            @Override public boolean canContinueToUse() { return e.getTrait() == AdultPlayerMobEntity.PlayerMobTrait.NORMAL && super.canContinueToUse(); }
        });
        e.goalSelector.addGoal(3, new RangedAttackGoal(e, 1.0D, 20, 15.0F) {
            @Override public boolean canUse() { return isNormalBow(e) && super.canUse(); }
            @Override public boolean canContinueToUse() { return isNormalBow(e) && super.canContinueToUse(); }
        });
        e.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(e, 1.0D) {
            @Override public boolean canUse() { return e.getTrait() == AdultPlayerMobEntity.PlayerMobTrait.NORMAL && super.canUse(); }
        });
        e.goalSelector.addGoal(5, new LookAtPlayerGoal(e, Player.class, 10.0F));
        e.goalSelector.addGoal(6, new RandomLookAroundGoal(e));
        e.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(e, Monster.class, true) {
            @Override public boolean canUse() { return e.getTrait() == AdultPlayerMobEntity.PlayerMobTrait.NORMAL && super.canUse(); }
        });
    }

    private static boolean isNormalBow(AdultPlayerMobEntity e) {
        return e.getTrait() == AdultPlayerMobEntity.PlayerMobTrait.NORMAL
                && e.getItemBySlot(EquipmentSlot.MAINHAND).getItem() instanceof BowItem
                && hasArrow(e);
    }

    private static boolean hasArrow(AdultPlayerMobEntity e) {
        for (int i = 0; i < e.getTraitInventory().getContainerSize(); i++)
            if (e.getTraitInventory().getItem(i).getItem() instanceof ArrowItem) return true;
        return false;
    }

    public static void tick(AdultPlayerMobEntity e) {
        if (e.level().isClientSide || !e.isAlive()) return;
        int age = e.tickCount;
        if (age % SOCIAL_INTERVAL == 0) social(e);
        if (age % ACTION_INTERVAL == 0) {
            pickup(e); shield(e); chooseHand(e);
        }
        if (age % BUILD_INTERVAL == 0) { deposit(e); placeOrTill(e); }
        if (age % MINE_INTERVAL == 0) mine(e);
        dangerCrouch(e);
        if (e.getTarget() != null && e.getTarget().isAlive() && e.getHealth() <= e.getMaxHealth() * .30F) flee(e);
    }

    private static void social(AdultPlayerMobEntity e) {
        Player p = e.level().getNearestPlayer(e, 7.0D);
        if (p != null && p.isAlive()) {
            e.setShiftKeyDown(true);
            if (e.onGround() && e.getRandom().nextFloat() < .35F) e.getJumpControl().jump();
        } else e.setShiftKeyDown(false);
    }

    private static void dangerCrouch(AdultPlayerMobEntity e) {
        if (!e.onGround()) return;
        Vec3 f = e.getLookAngle();
        BlockPos ahead = e.blockPosition().offset((int)Math.round(f.x), 0, (int)Math.round(f.z));
        BlockPos ground = null;
        for (int y = ahead.getY(); y >= Math.max(e.level().getMinBuildHeight(), ahead.getY()-6); y--) {
            BlockPos p = new BlockPos(ahead.getX(), y, ahead.getZ());
            if (!e.level().getBlockState(p).isAir()) { ground = p; break; }
        }
        e.setShiftKeyDown(ground == null || e.blockPosition().getY() - ground.getY() >= 3);
    }

    private static void flee(AdultPlayerMobEntity e) {
        Vec3 away = e.position().subtract(e.getTarget().position());
        if (away.lengthSqr() < .01) away = new Vec3(1,0,0);
        away = away.normalize().scale(8);
        e.getNavigation().moveTo(e.getX()+away.x, e.getY(), e.getZ()+away.z, 1.25D);
        e.setSprinting(true);
    }

    private static void pickup(AdultPlayerMobEntity e) {
        SimpleContainer inv = e.getTraitInventory();
        for (ItemEntity item : e.level().getEntitiesOfClass(ItemEntity.class, e.getBoundingBox().inflate(2.5D))) {
            if (!item.isAlive() || !inv.canAddItem(item.getItem())) continue;
            ItemStack rem = inv.addItem(item.getItem().copy());
            if (rem.isEmpty()) item.discard(); else item.setItem(rem);
        }
    }

    private static void deposit(AdultPlayerMobEntity e) {
        if (!(e.level() instanceof ServerLevel sl)) return;
        SimpleContainer inv=e.getTraitInventory();
        for(Direction d:Direction.Plane.HORIZONTAL) {
            BlockEntity be=sl.getBlockEntity(e.blockPosition().relative(d));
            if(!(be instanceof Container c)) continue;
            for(int i=0;i<inv.getContainerSize();i++) {
                ItemStack s=inv.getItem(i); if(s.isEmpty()) continue;
                for(int j=0;j<c.getContainerSize()&&!s.isEmpty();j++) {
                    ItemStack x=c.getItem(j);
                    if(!x.isEmpty()&&!ItemStack.isSameItemSameTags(x,s)) continue;
                    int room=x.isEmpty()?s.getMaxStackSize()-0:x.getMaxStackSize()-x.getCount();
                    if(room<=0) continue;
                    int n=Math.min(room,s.getCount());
                    if(x.isEmpty()) c.setItem(j,s.split(n)); else {x.grow(n);s.shrink(n);c.setItem(j,x);}
                }
                inv.setItem(i,s);
            }
            c.setChanged(); return;
        }
    }

    private static void shield(AdultPlayerMobEntity e) {
        ItemStack off=e.getItemBySlot(EquipmentSlot.OFFHAND);
        if(!(off.getItem() instanceof ShieldItem)) return;
        if(e.hurtTime>0 && !e.isUsingItem()) e.startUsingItem(InteractionHand.OFF_HAND);
        else if(e.hurtTime==0 && e.isUsingItem() && e.getUsedItemHand()==InteractionHand.OFF_HAND) e.stopUsingItem();
    }

    private static void chooseHand(AdultPlayerMobEntity e) {
        if(e.getTarget()==null||!e.getTarget().isAlive()) return;
        ItemStack main=e.getItemBySlot(EquipmentSlot.MAINHAND), off=e.getItemBySlot(EquipmentSlot.OFFHAND);
        boolean far=e.distanceToSqr(e.getTarget())>8*8;
        if(far && off.getItem() instanceof BowItem && !(main.getItem() instanceof BowItem)) swap(e,main,off);
        else if(!far && off.getItem() instanceof SwordItem && !(main.getItem() instanceof SwordItem)) swap(e,main,off);
    }
    private static void swap(AdultPlayerMobEntity e,ItemStack a,ItemStack b){e.setItemSlot(EquipmentSlot.MAINHAND,b.copy());e.setItemSlot(EquipmentSlot.OFFHAND,a.copy());}

    private static void placeOrTill(AdultPlayerMobEntity e) {
        if (!(e.level() instanceof ServerLevel sl)) return;

        // The NPC is a PathfinderMob, not a Player. Therefore autonomous
        // farming/building is performed directly on the server.

        // Hoe dirt/grass into farmland.
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = e.getItemInHand(hand);
            if (!(stack.getItem() instanceof HoeItem)) continue;

            BlockPos farmlandPos = e.blockPosition().below();
            var state = sl.getBlockState(farmlandPos);

            if ((state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK))
                    && sl.getBlockState(farmlandPos.above()).isAir()) {

                sl.setBlock(
                        farmlandPos,
                        Blocks.FARMLAND.defaultBlockState(),
                        3
                );

                stack.hurtAndBreak(
                        1,
                        e,
                        living -> living.broadcastBreakEvent(hand)
                );
                return;
            }
        }

        // Place a simple block from the NPC's internal inventory.
        BlockPos target = e.blockPosition().relative(e.getDirection());
        if (!sl.getBlockState(target).canBeReplaced()) return;

        BlockPos support = target.below();
        if (sl.getBlockState(support).isAir()) return;

        SimpleContainer inv = e.getTraitInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);

            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
                continue;
            }

            var blockState = blockItem.getBlock().defaultBlockState();

            if (!blockState.canSurvive(sl, target)) {
                continue;
            }

            if (sl.setBlock(target, blockState, 3)) {
                stack.shrink(1);
                inv.setItem(i, stack);
            }

            return;
        }
    }

    private static void mine(AdultPlayerMobEntity e) {
        if(!(e.level() instanceof ServerLevel sl)||e.getTarget()!=null) return;
        BlockPos p=e.blockPosition().relative(e.getDirection());
        var st=sl.getBlockState(p);
        if(st.isAir()||st.is(Blocks.BEDROCK)||st.is(Blocks.OBSIDIAN)||!st.getFluidState().isEmpty()) return;
        if(st.is(Blocks.CHEST)||st.is(Blocks.TRAPPED_CHEST)||st.is(Blocks.FURNACE)||st.is(Blocks.CRAFTING_TABLE)) return;
        if(st.is(Blocks.DIRT)||st.is(Blocks.GRASS_BLOCK)||st.is(Blocks.SAND)||st.is(Blocks.GRAVEL)||st.is(Blocks.OAK_LOG)||st.is(Blocks.BIRCH_LOG)||st.is(Blocks.SPRUCE_LOG)||st.is(Blocks.JUNGLE_LOG)||st.is(Blocks.ACACIA_LOG)||st.is(Blocks.DARK_OAK_LOG)) sl.destroyBlock(p,true,e);
    }
}