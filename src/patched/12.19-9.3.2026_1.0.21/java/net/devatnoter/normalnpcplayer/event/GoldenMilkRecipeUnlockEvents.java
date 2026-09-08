package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * Unlocks the Golden Milk Bottle recipe through the recipe book only.
 *
 * Requirement:
 *  1. The player has a Milk Bottle in their inventory.
 *  2. The player has already unlocked at least one recipe related to Gold.
 *
 * This is deliberately NOT an advancement.
 *
 * Forge / Minecraft: 1.20.1
 */
@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class GoldenMilkRecipeUnlockEvents {

    private static final ResourceLocation GOLDEN_MILK_RECIPE =
            new ResourceLocation(
                    NormalNPCPlayer.MOD_ID,
                    "golden_milk_bottle"
            );

    private GoldenMilkRecipeUnlockEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        // No Milk Bottle = do nothing.
        if (!hasMilkBottle(player)) {
            return;
        }

        var recipeManager = player.server.getRecipeManager();
        var goldenMilk = recipeManager.byKey(GOLDEN_MILK_RECIPE);

        // Golden Milk Bottle recipe does not exist.
        if (goldenMilk.isEmpty()) {
            return;
        }

        // The Golden Milk Bottle recipe is already known.
        if (player.getRecipeBook().contains(goldenMilk.get())) {
            return;
        }

        // Require an already-unlocked Gold-related recipe.
        if (!hasUnlockedGoldRecipe(player)) {
            return;
        }

        // Forge / Minecraft 1.20.1.
        player.awardRecipesByKey(new ResourceLocation[]{
                GOLDEN_MILK_RECIPE
        });
    }

    private static boolean hasMilkBottle(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.MILK_BOTTLE.get())) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasUnlockedGoldRecipe(ServerPlayer player) {
        var recipeManager = player.server.getRecipeManager();

        /*
         * RecipeManager#getRecipeIds() returns the IDs of all loaded
         * recipes. We then resolve each ID with byKey() so we can check
         * whether that recipe is already known by the player's recipe book.
         *
         * No explicit RecipeHolder import is required here.
         */
        for (ResourceLocation id : recipeManager.getRecipeIds().toList()) {
            if (!id.getPath().toLowerCase(Locale.ROOT).contains("gold")) {
                continue;
            }

            var recipe = recipeManager.byKey(id);

            if (recipe.isPresent()
                    && player.getRecipeBook().contains(recipe.get())) {
                return true;
            }
        }

        return false;
    }
}