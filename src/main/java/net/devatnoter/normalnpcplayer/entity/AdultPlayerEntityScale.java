package net.devatnoter.normalnpcplayer.entity;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;

/**
 * Single source of truth for Adult Player Mob world/model scale.
 *
 * The default values intentionally match the vanilla Java Player:
 * standing hitbox = 0.6 x 1.8 blocks, visual scale = 1.0.
 *
 * Change SCALE_X/Y/Z together when a deliberate global Adult scale change is
 * required. Hitbox dimensions and the rendered PlayerModel use the same values.
 */
public final class AdultPlayerEntityScale {
    public static final float SCALE_X = 1.0F;
    public static final float SCALE_Y = 1.0F;
    public static final float SCALE_Z = 1.0F;

    public static final float PLAYER_WIDTH = 0.6F;
    public static final float PLAYER_HEIGHT = 1.8F;

    private AdultPlayerEntityScale() {
    }

    public static float renderScale() {
        // Keep the renderer uniform unless the source-of-truth values are
        // deliberately changed to a uniform scale.
        return (SCALE_X + SCALE_Y + SCALE_Z) / 3.0F;
    }

    public static EntityDimensions dimensions(Pose pose) {
        float width = PLAYER_WIDTH * SCALE_X;
        float height = switch (pose) {
            case CROUCHING -> 1.5F * SCALE_Y;
            case SWIMMING, FALL_FLYING -> 0.6F * SCALE_Y;
            case SLEEPING -> 0.2F * SCALE_Y;
            default -> PLAYER_HEIGHT * SCALE_Y;
        };
        return EntityDimensions.scalable(width, height);
    }
}
