package net.devatnoter.normalnpcplayer.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ColoredNameItem extends Item {
    private final ChatFormatting color;

    public ColoredNameItem(Properties properties, ChatFormatting color) {
        super(properties);
        this.color = color;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack)).withStyle(color);
    }
}
