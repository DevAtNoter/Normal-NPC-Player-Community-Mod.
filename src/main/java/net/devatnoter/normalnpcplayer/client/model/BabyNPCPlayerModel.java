package net.devatnoter.normalnpcplayer.client.model;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class BabyNPCPlayerModel extends GeoModel<BabyNPCPlayerEntity> {

    @Override
    public ResourceLocation getModelResource(BabyNPCPlayerEntity animatable) {
        return NormalNPCPlayer.id("geo/baby.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(BabyNPCPlayerEntity animatable) {
        String textureId = animatable.getTextureId();

        // SpecialVariant textures may be full ResourceLocations.
        if (textureId != null && textureId.contains(":")) {
            try {
                return ResourceLocation.parse(textureId);
            } catch (RuntimeException ignored) {
                // Fall through to the normal numbered Baby variant.
            }
        }

        return NormalNPCPlayer.id(
                "textures/entity/baby/baby" +
                        animatable.getTextureIndex() +
                        ".png"
        );
    }

    @Override
    public ResourceLocation getAnimationResource(BabyNPCPlayerEntity animatable) {
        return NormalNPCPlayer.id("animations/baby/baby.animation.json");
    }
}