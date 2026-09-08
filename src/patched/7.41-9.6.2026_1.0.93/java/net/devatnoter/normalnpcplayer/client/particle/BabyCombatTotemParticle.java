package net.devatnoter.normalnpcplayer.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Small bright-red shards emitted continuously by the Baby Combat Totem effect. */
public class BabyCombatTotemParticle extends TextureSheetParticle {

    private BabyCombatTotemParticle(ClientLevel level, double x, double y, double z,
                                    double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        setColor(1.0F, 0.03F, 0.03F);
        setAlpha(1.0F);
        quadSize = 0.12F + random.nextFloat() * 0.08F;
        lifetime = 22 + random.nextInt(11);
        gravity = 0.085F;
        friction = 0.93F;
        pickSprite(sprites);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public void tick() {
        super.tick();
        if (age > lifetime - 7) {
            alpha = Mth.clamp((lifetime - age) / 7.0F, 0.0F, 1.0F);
        }
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
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new BabyCombatTotemParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
