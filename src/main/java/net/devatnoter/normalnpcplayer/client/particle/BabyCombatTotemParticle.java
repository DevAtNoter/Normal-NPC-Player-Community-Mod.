package net.devatnoter.normalnpcplayer.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Isolated Totem-of-Undying-style particle for Totem of Baby Combat.
 *
 * Uses its own 8-frame glitter sprite set. The texture remains neutral and
 * the final Baby Combat color is applied in code, so no vanilla particle
 * textures or other particle systems are modified.
 */
public class BabyCombatTotemParticle extends SimpleAnimatedParticle {

    private BabyCombatTotemParticle(
            ClientLevel level,
            double x, double y, double z,
            double xd, double yd, double zd,
            SpriteSet sprites
    ) {
        super(level, x, y, z, sprites, 0.0F);

        this.setSpriteFromAge(sprites);

        // Vanilla TotemParticle-style motion shaping.
        this.xd *= 0.20D;
        this.yd *= 0.20D;
        this.zd *= 0.20D;

        this.xd += (random.nextFloat() - random.nextFloat()) * 0.1D;
        this.yd += (random.nextFloat() - random.nextFloat()) * 0.1D;
        this.zd += (random.nextFloat() - random.nextFloat()) * 0.1D;

        this.lifetime = 60 + random.nextInt(12);
        this.quadSize *= 3.0F;
        this.gravity = 0.0F;
        this.friction = 0.96F;
        this.setAlpha(1.0F);

        this.setColor(0xE51B23);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x, double y, double z,
                double xd, double yd, double zd
        ) {
            return new BabyCombatTotemParticle(
                    level, x, y, z,
                    xd, yd, zd,
                    sprites
            );
        }
    }
}
