package net.devatnoter.normalnpcplayer.recipe;

import net.devatnoter.normalnpcplayer.registry.ModItems;
import net.devatnoter.normalnpcplayer.registry.ModRecipeSerializers;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class BunchOfRosesRecipe extends CustomRecipe {
    public BunchOfRosesRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        if (container.getWidth() != 3 || container.getHeight() != 3) return false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (i == 0 || i == 1 || i == 3) {
                if (!stack.is(ModItems.ROSE.get()) || stack.getCount() < 8) return false;
            } else if (i == 4) {
                if (!stack.is(Items.PINK_WOOL) || stack.getCount() < 1) return false;
            } else if (i == 8) {
                if (!stack.is(ModItems.DIAMOND_NUGGET.get()) || stack.getCount() < 3) return false;
            } else if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, net.minecraft.core.RegistryAccess registryAccess) {
        return new ItemStack(ModItems.BUNCH_OF_ROSES.get());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public ItemStack getResultItem(net.minecraft.core.RegistryAccess registryAccess) {
        return new ItemStack(ModItems.BUNCH_OF_ROSES.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.BUNCH_OF_ROSES.get();
    }

    @Override
    public boolean isSpecial() {
        return false;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int i : new int[]{0, 1, 3}) {
            ItemStack stack = container.getItem(i).copy();
            // Crafting consumes one item from each slot after getRemainingItems().
            // Return count - 7 so the total consumed amount is exactly 8.
            stack.shrink(7);
            remaining.set(i, stack);
        }
        ItemStack diamondNuggets = container.getItem(8).copy();
        // Same rule: shrink by 2 here, then the crafting system consumes one more,
        // for a total of exactly 3 diamond nuggets.
        diamondNuggets.shrink(2);
        remaining.set(8, diamondNuggets);
        return remaining;
    }
}
