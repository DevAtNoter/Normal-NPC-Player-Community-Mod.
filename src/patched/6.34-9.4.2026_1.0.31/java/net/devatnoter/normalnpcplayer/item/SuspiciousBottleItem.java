package net.devatnoter.normalnpcplayer.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/** Player-only gimmick bottle: it cannot be fed to a Baby. */
public final class SuspiciousBottleItem extends Item {
    /**
     * Suspicious Bottle always causes nausea, then rolls one additional
     * positive or negative effect. The two pools are intentionally mixed so
     * every drink is unpredictable.
     */
    private static final List<MobEffectInstance> POSSIBLE_EFFECTS = List.of(
            // Good
            new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0),
            new MobEffectInstance(MobEffects.JUMP, 600, 0),
            new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0),
            new MobEffectInstance(MobEffects.DIG_SPEED, 600, 0),
            new MobEffectInstance(MobEffects.REGENERATION, 100, 0),
            new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0),
            new MobEffectInstance(MobEffects.WATER_BREATHING, 1200, 0),
            new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 0),
            // Bad
            new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 0),
            new MobEffectInstance(MobEffects.WEAKNESS, 600, 0),
            new MobEffectInstance(MobEffects.POISON, 100, 0),
            new MobEffectInstance(MobEffects.HUNGER, 600, 0),
            new MobEffectInstance(MobEffects.BLINDNESS, 200, 0),
            new MobEffectInstance(MobEffects.CONFUSION, 300, 0),
            new MobEffectInstance(MobEffects.WITHER, 100, 0)
    );

    public SuspiciousBottleItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack) { return 32; }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity entity) {
        if (entity instanceof Player player) {
            if (!level.isClientSide) {
                // Every Suspicious Bottle is bad news in a predictable way:
                // Nausea is guaranteed for 10 seconds. The second effect is
                // the random good/bad roll.
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
                MobEffectInstance template = POSSIBLE_EFFECTS.get(level.random.nextInt(POSSIBLE_EFFECTS.size()));
                player.addEffect(new MobEffectInstance(template));
                level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK,
                        SoundSource.PLAYERS, 0.8F, 1.0F);
            }
            if (!player.getAbilities().instabuild) {
                if (stack.getCount() == 1) {
                    return new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE);
                }
                stack.shrink(1);
                player.getInventory().placeItemBackInInventory(
                        new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE)
                );
            }
        }
        return stack;
    }
}
