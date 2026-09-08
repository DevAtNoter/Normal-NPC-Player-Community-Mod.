package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.item.BabyFoodItem;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.ItemStackedOnOtherEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Handles feeding a carried Baby Item by clicking food onto it in the player inventory GUI. */
@Mod.EventBusSubscriber(modid = NormalNPCPlayer.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BabyFoodInventoryEvents {
    private BabyFoodInventoryEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onStacked(ItemStackedOnOtherEvent event) {

        if (event.getPlayer().level().isClientSide) return;

        ItemStack slotStack = event.getSlot().getItem();
        ItemStack a = event.getCarriedItem();
        ItemStack b = event.getStackedOnItem();

        ItemStack baby = BabyNPCPlayerItem.isBabyStack(slotStack) ? slotStack :
                BabyNPCPlayerItem.isBabyStack(a) ? a :
                BabyNPCPlayerItem.isBabyStack(b) ? b : ItemStack.EMPTY;
        if (baby.isEmpty()) return;

        ItemStack food = BabyFoodItem.isBabyFood(a) || a.is(Items.MILK_BUCKET) ? a :
                BabyFoodItem.isBabyFood(b) || b.is(Items.MILK_BUCKET) ? b : ItemStack.EMPTY;
        if (food.isEmpty()) return;

        if (!BabyNPCPlayerItem.feedCarriedBaby(baby, food, event.getPlayer())) return;

        if (!event.getPlayer().getAbilities().instabuild) {
            // Capture the exact source item BEFORE shrink(1). A single Milk
            // Bucket becomes EMPTY after consumption and must return a Bucket,
            // while Baby Milk Bottles must return a Glass Bottle.
            boolean wasBucket = food.is(Items.MILK_BUCKET);
            boolean wasBabyBottle = BabyFoodItem.isBabyFood(food);
            food.shrink(1);

            if (wasBucket) {
                event.getPlayer().getInventory().placeItemBackInInventory(new ItemStack(Items.BUCKET));
            } else if (wasBabyBottle) {
                event.getPlayer().getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE));
            }
        }

        event.getPlayer().playNotifySound(net.minecraft.sounds.SoundEvents.HONEY_DRINK,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.72F, 1.18F);
        event.setCanceled(true);
    }
}
