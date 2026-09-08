package net.devatnoter.normalnpcplayer.guide;

import net.devatnoter.normalnpcplayer.config.NNPConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class GuideBookManager {
    public static final String CHILD_CARE_BOOK = "normalnpcplayer:child_care_guide";
    public static final String BABY_COMBAT_BOOK = "normalnpcplayer:baby_combat";

    private GuideBookManager() {}

    public static ItemStack createPatchouliGuideBook(String bookId) {
        Item guideBook = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("patchouli", "guide_book"));
        if (guideBook == Items.AIR) {
            return ItemStack.EMPTY;
        }

        ItemStack book = new ItemStack(guideBook);
        book.getOrCreateTag().putString("patchouli:book", bookId);
        return book;
    }

    public static ItemStack createChildCareBook() {
        return createPatchouliGuideBook(CHILD_CARE_BOOK);
    }

    public static ItemStack createBabyCombatBook() {
        return createPatchouliGuideBook(BABY_COMBAT_BOOK);
    }

    public static void giveAtBirth(ServerPlayer player) {
        if (player == null || !NNPConfig.arePatchouliBooksEnabled() || !NNPConfig.isGiveAtBirthMode()) {
            return;
        }

        giveOrDrop(player, createChildCareBook());
        giveOrDrop(player, createBabyCombatBook());
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
