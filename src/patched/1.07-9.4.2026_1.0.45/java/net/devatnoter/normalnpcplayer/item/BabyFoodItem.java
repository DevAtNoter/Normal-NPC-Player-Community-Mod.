package net.devatnoter.normalnpcplayer.item;

import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/** Baby-only food items. Players cannot consume these directly. */
public final class BabyFoodItem extends Item {
    public enum Kind { MILK_BOTTLE, GOLDEN_MILK_BOTTLE, ENCHANTED_GOLDEN_MILK_BOTTLE }

    public record FoodData(float food, float saturation, float absorption,
                           List<MobEffectInstance> effects, ItemStack emptyReturn) {}

    private final Kind kind;

    public BabyFoodItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    /** Only the enchanted golden milk bottle uses the vanilla enchantment glint. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return kind == Kind.ENCHANTED_GOLDEN_MILK_BOTTLE;
    }

    public static boolean isBabyFood(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BabyFoodItem;
    }

    public static FoodData getFoodData(ItemStack stack) {
        if (!(stack.getItem() instanceof BabyFoodItem food)) {
            return new FoodData(0, 0, 0, List.of(), ItemStack.EMPTY);
        }
        return switch (food.kind) {
            case MILK_BOTTLE -> new FoodData(1.5F, 2.5F, 0F, List.of(), new ItemStack(Items.GLASS_BOTTLE));
            case GOLDEN_MILK_BOTTLE -> new FoodData(4F, 9.6F, 4.0F,
                    List.of(new MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 100, 1),
                            new MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, 2400, 0)),
                    new ItemStack(Items.GLASS_BOTTLE));
            case ENCHANTED_GOLDEN_MILK_BOTTLE -> new FoodData(4F, 9.6F, 16.0F,
                    List.of(new MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 400, 1),
                            new MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, 2400, 3),
                            new MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 6000, 0),
                            new MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 6000, 0)),
                    new ItemStack(Items.GLASS_BOTTLE));
        };
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Players may drink these items, but they never modify the player's
        // Food/Saturation values. Baby feeding is handled separately by the
        // Baby entity, dropped-item pickup, or carried-Baby interaction event.
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity entity) {
        // Vanilla's ItemUtils pipeline consumes exactly one item from a
        // stackable drink and returns the remaining stack, while inserting
        // the container item separately. Returning the one-item Glass Bottle
        // directly would replace the whole 16-stack.
        if (entity instanceof Player player) {
            ItemStack empty = getFoodData(stack).emptyReturn();
            if (!empty.isEmpty()) {
                return ItemUtils.createFilledResult(stack, player, empty);
            }
        }
        return stack;
    }
}
