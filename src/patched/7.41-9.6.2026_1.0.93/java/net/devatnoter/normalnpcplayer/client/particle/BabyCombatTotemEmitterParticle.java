package net.devatnoter.normalnpcplayer.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.devatnoter.normalnpcplayer.registry.ModParticles;

/**
 * Invisible emitter that continuously releases the red totem shards for 3 seconds.
 * The emitter itself does not travel; only the emitted shards do.
 */
public class BabyCombatTotemEmitterParticle extends TextureSheetParticle {

    private static final int EMIT_TICKS = 60;

    private BabyCombatTotemEmitterParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        lifetime = EMIT_TICKS;
        gravity = 0.0F;
        friction = 1.0F;
        setAlpha(0.0F);
        quadSize = 0.0F;
    }

    @Override
    public void tick() {
        if (age++ >= lifetime) {
            remove();
            return;
        }

        // Emit a small stream every tick. The speed is deliberately modest so
        // the effect spreads to roughly a 2-3 block radius instead of flying away.
        int count = 2 + (random.nextFloat() < 0.35F ? 1 : 0);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double horizontalSpeed = 0.10D + random.nextDouble() * 0.08D;
            double vx = Math.cos(angle) * horizontalSpeed;
            double vz = Math.sin(angle) * horizontalSpeed;
            double vy = 0.18D + random.nextDouble() * 0.18D;

            double spawnRadius = random.nextDouble() * 0.18D;
            double sx = x + Math.cos(angle) * spawnRadius;
            double sy = y + random.nextDouble() * 0.22D;
            double sz = z + Math.sin(angle) * spawnRadius;

            level.addParticle(
                    ModParticles.TOTEM_OF_BABY_COMBAT.get(),
                    sx, sy, sz,
                    vx, vy, vz
            );
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.NO_RENDER;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        public Provider(SpriteSet sprites) {
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new BabyCombatTotemEmitterParticle(level, x, y, z);
        }
    }
}
