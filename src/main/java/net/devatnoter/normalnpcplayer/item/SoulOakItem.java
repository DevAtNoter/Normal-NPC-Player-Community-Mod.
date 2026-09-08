package net.devatnoter.normalnpcplayer.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Soul Oak item with a custom-colored display name.
 */
public class SoulOakItem extends Item {

    public SoulOakItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack)
                .copy()
                .withStyle(style -> style.withColor(0xBFDcff));
    }
}
