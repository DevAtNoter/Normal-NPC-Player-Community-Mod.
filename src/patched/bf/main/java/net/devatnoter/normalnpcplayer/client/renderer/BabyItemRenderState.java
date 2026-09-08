package net.devatnoter.normalnpcplayer.client.renderer;

import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;

/**
 * Persistent client-side state for a Baby rendered from an ItemStack.
 *
 * <p>This is deliberately not the Baby's gameplay state. Gameplay/survival
 * data remains authoritative in the ItemStack NBT and can change every tick.
 * This object owns only renderer-facing state that must survive between
 * render calls, such as the proxy entity, appearance snapshot and future
 * interaction/animation state.</p>
 */
public final class BabyItemRenderState {

    private final BabyNPCPlayerEntity entity;
    private int textureIndex;

    // Reserved render-state clock for future interact/emote animations.
    // It is intentionally independent from ItemStack survival updates.
    private long renderFrames;

    public BabyItemRenderState(
            BabyNPCPlayerEntity entity,
            int textureIndex
    ) {
        this.entity = entity;
        this.textureIndex = textureIndex;
    }

    public BabyNPCPlayerEntity entity() {
        return entity;
    }

    public int textureIndex() {
        return textureIndex;
    }

    public long renderFrames() {
        return renderFrames;
    }

    /**
     * Called from rendering when an appearance field changed. This must never
     * recreate the entity, model, or GeckoLib controller.
     */
    public void syncAppearance(int newTextureIndex) {
        if (textureIndex == newTextureIndex) {
            return;
        }
        textureIndex = newTextureIndex;
        entity.setTextureIndex(newTextureIndex);
    }

    /**
     * Advances only renderer-owned time. Future interaction animation logic
     * can use this without touching the Baby's survival NBT.
     */
    public void advanceRenderFrame() {
        renderFrames++;
    }
}
