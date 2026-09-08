package net.devatnoter.normalnpcplayer.client.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Minecraft 1.20.1 PlayerModel keeps cloak private.
 * This accessor exposes only that one ModelPart to the client renderer.
 */
@Mixin(PlayerModel.class)
public interface PlayerModelAccessor {

    @Accessor("cloak")
    ModelPart normalnpcplayer$getCloak();
}
