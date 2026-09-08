package net.devatnoter.normalnpcplayer.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class TotemOfBabyCombatItem extends Item {

    public TotemOfBabyCombatItem(Properties properties) {
        super(properties.durability(100));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.normalnpcplayer.totem_of_baby_combat")
                .withStyle(ChatFormatting.RED);
    }
}
