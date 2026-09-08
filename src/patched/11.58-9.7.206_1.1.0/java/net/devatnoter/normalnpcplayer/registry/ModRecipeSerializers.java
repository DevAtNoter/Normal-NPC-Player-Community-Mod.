package net.devatnoter.normalnpcplayer.registry;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.recipe.BunchOfRosesRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, NormalNPCPlayer.MOD_ID);

    public static final RegistryObject<RecipeSerializer<BunchOfRosesRecipe>> BUNCH_OF_ROSES =
            SERIALIZERS.register("bunch_of_roses", () -> new SimpleCraftingRecipeSerializer<>(BunchOfRosesRecipe::new));

    private ModRecipeSerializers() {}

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }
}
