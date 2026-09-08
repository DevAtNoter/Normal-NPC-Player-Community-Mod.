package net.devatnoter.normalnpcplayer.client.model;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

/**
 * Dedicated player-model layers for AdultPlayerMobEntity.
 *
 * We intentionally do not bake ModelLayers.PLAYER here. The adult renderer
 * needs the complete 1.20.1 PlayerModel root (including ear and cloak), and
 * owning the layer definition makes the renderer independent of any other
 * model-layer registration.
 */
public final class AdultPlayerModelLayers {

    public static final ModelLayerLocation PLAYER =
            new ModelLayerLocation(
                    NormalNPCPlayer.id("adult_player"),
                    "main"
            );

    public static final ModelLayerLocation PLAYER_SLIM =
            new ModelLayerLocation(
                    NormalNPCPlayer.id("adult_player_slim"),
                    "main"
            );

    private AdultPlayerModelLayers() {
    }

    public static LayerDefinition createWideLayer() {
        return LayerDefinition.create(
                PlayerModel.createMesh(
                        new CubeDeformation(0.0F),
                        false
                ),
                64,
                64
        );
    }

    public static LayerDefinition createSlimLayer() {
        return LayerDefinition.create(
                PlayerModel.createMesh(
                        new CubeDeformation(0.0F),
                        true
                ),
                64,
                64
        );
    }
}
