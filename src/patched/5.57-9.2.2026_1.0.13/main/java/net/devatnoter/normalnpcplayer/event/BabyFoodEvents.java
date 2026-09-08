package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.item.BabyFoodItem;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.devatnoter.normalnpcplayer.registry.ModItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NormalNPCPlayer.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BabyFoodEvents {
    private static final String MILK_WINDOW_START_TAG = "NNPMilkHarvestWindowStart";
    private static final String MILK_HARVEST_COUNT_TAG = "NNPMilkHarvestCount";
    private static final String COW_MILK_WINDOW_START_TAG = "NNPCowBottleMilkWindowStart";
    private static final String COW_MILK_HARVEST_COUNT_TAG = "NNPCowBottleMilkHarvestCount";
    private static final long MILK_WINDOW_TICKS = 6L * 60L * 20L;
    private static final int PLAYER_MILK_HARVEST_LIMIT = 5;
    private static final int COW_BOTTLE_MILK_HARVEST_LIMIT = 3;

    private BabyFoodEvents() {}

    /**
     * Each player can be milked five times per six-minute window. The quota
     * belongs to the player being milked, so different friends do not share
     * one another's milk reserve.
     */
    private static boolean tryTakePlayerMilk(Player source) {
        var data = source.getPersistentData();
        long now = source.level().getGameTime();
        long windowStart = data.contains(MILK_WINDOW_START_TAG)
                ? data.getLong(MILK_WINDOW_START_TAG) : now;
        int count = data.contains(MILK_HARVEST_COUNT_TAG)
                ? data.getInt(MILK_HARVEST_COUNT_TAG) : 0;

        if (now - windowStart >= MILK_WINDOW_TICKS || now < windowStart) {
            windowStart = now;
            count = 0;
        }
        if (count >= PLAYER_MILK_HARVEST_LIMIT) {
            return false;
        }

        data.putLong(MILK_WINDOW_START_TAG, windowStart);
        data.putInt(MILK_HARVEST_COUNT_TAG, count + 1);
        return true;
    }

    /**
     * Vanilla Milk Bucket remains drinkable by Players. It does not add food
     * or saturation; the normal Milk Bucket behavior clears effects and returns
     * the empty bucket. Baby feeding is handled by the Baby-specific paths.
     */
    @SubscribeEvent
    public static void onMilkBucketUse(PlayerInteractEvent.RightClickItem event) {
        if (!event.getItemStack().is(Items.MILK_BUCKET)) return;
        // Intentionally do not cancel: let the vanilla Milk Bucket drink path run.
    }

    private static boolean tryTakeCowBottleMilk(Cow cow) {
        var data = cow.getPersistentData();
        long now = cow.level().getGameTime();
        long windowStart = data.contains(COW_MILK_WINDOW_START_TAG)
                ? data.getLong(COW_MILK_WINDOW_START_TAG) : now;
        int count = data.contains(COW_MILK_HARVEST_COUNT_TAG)
                ? data.getInt(COW_MILK_HARVEST_COUNT_TAG) : 0;

        if (now - windowStart >= MILK_WINDOW_TICKS || now < windowStart) {
            windowStart = now;
            count = 0;
        }
        if (count >= COW_BOTTLE_MILK_HARVEST_LIMIT) {
            return false;
        }

        data.putLong(COW_MILK_WINDOW_START_TAG, windowStart);
        data.putInt(COW_MILK_HARVEST_COUNT_TAG, count + 1);
        return true;
    }

    @SubscribeEvent
    public static void onCowBottle(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof Cow cow)) return;
        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(event.getHand());
        if (!held.is(Items.GLASS_BOTTLE)) return;

        if (!player.level().isClientSide) {
            if (!tryTakeCowBottleMilk(cow)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }
            ItemStack filled = new ItemStack(ModItems.MILK_BOTTLE.get());
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
                if (held.isEmpty()) player.setItemInHand(event.getHand(), filled);
                else player.getInventory().placeItemBackInInventory(filled);
            } else {
                player.getInventory().placeItemBackInInventory(filled);
            }
            player.playNotifySound(SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onPlayerMilkHarvest(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof Player target)) return;
        Player actor = event.getEntity();
        if (actor == target) return;

        ItemStack held = actor.getItemInHand(event.getHand());
        boolean bottle = held.is(Items.GLASS_BOTTLE);
        boolean bucket = held.is(Items.BUCKET);
        if (!bottle && !bucket) return;

        if (!actor.level().isClientSide) {
            if (!tryTakePlayerMilk(target)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }

            ItemStack filled;
            if (bucket) {
                filled = new ItemStack(Items.MILK_BUCKET);
            } else if (actor.level().random.nextFloat() < 0.10F) {
                filled = new ItemStack(ModItems.SUSPICIOUS_BOTTLE.get());
            } else {
                filled = new ItemStack(ModItems.MILK_BOTTLE.get());
            }

            if (!actor.getAbilities().instabuild) {
                held.shrink(1);
                if (held.isEmpty()) actor.setItemInHand(event.getHand(), filled);
                else actor.getInventory().placeItemBackInInventory(filled);
            } else {
                actor.getInventory().placeItemBackInInventory(filled);
            }
            actor.playNotifySound(SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Owner-only two-hand interaction while the Baby is carried as an ItemStack. */
    @SubscribeEvent
    public static void onCarriedBabyFeed(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack active = player.getItemInHand(event.getHand());
        InteractionHand otherHand = event.getHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack other = player.getItemInHand(otherHand);

        ItemStack babyStack = BabyNPCPlayerItem.isBabyStack(active) ? active :
                BabyNPCPlayerItem.isBabyStack(other) ? other : ItemStack.EMPTY;
        ItemStack foodStack = babyStack == active ? other : active;
        if (babyStack.isEmpty() || (!BabyFoodItem.isBabyFood(foodStack) && !foodStack.is(Items.MILK_BUCKET))) return;

        if (!BabyNPCPlayerItem.feedCarriedBaby(babyStack, foodStack, player)) return;

        if (!player.getAbilities().instabuild) {
            // Capture the container type BEFORE consuming the food stack.
            // After shrink(1), a single Milk Bucket becomes EMPTY, so checking
            // foodStack.is(MILK_BUCKET) afterwards incorrectly returned a glass bottle.
            boolean wasBucket = foodStack.is(Items.MILK_BUCKET);
            boolean wasBabyBottle = BabyFoodItem.isBabyFood(foodStack);
            foodStack.shrink(1);

            if (wasBucket) {
                player.getInventory().placeItemBackInInventory(new ItemStack(Items.BUCKET));
            } else if (wasBabyBottle) {
                player.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE));
            }
        }
        player.playNotifySound(SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 0.72F, 1.18F);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
