package com.arcanum.forge.client.render.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Büyü kıvılcımı — beyaz-sıcak yıldız çakımı. 4 kare sprite'tan rastgele seçer,
 * tam parlak çizilir, titreşen alfa ile "ışıldar", sönerken küçülür.
 */
public class SpellSparkParticle extends TextureSheetParticle {
    private final float twinklePhase;

    protected SpellSparkParticle(ClientLevel level, double x, double y, double z,
                                 double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.lifetime = 14 + this.random.nextInt(10);
        this.gravity = 0.0F;
        this.friction = 0.92F;
        this.hasPhysics = false;
        this.quadSize *= 0.50F + this.random.nextFloat() * 0.40F;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.twinklePhase = this.random.nextFloat() * Mth.TWO_PI;
        // hafif soğuk beyaz — renkli ışımaların üstünde "çekirdek" görevi görür
        this.rCol = 0.92F;
        this.gCol = 0.97F;
        this.bCol = 1.00F;
        this.pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        float t = (float) this.age / (float) this.lifetime;
        float fade = (1.0F - t) * (1.0F - t);
        float twinkle = 0.75F + 0.25F * Mth.sin(this.twinklePhase + this.age * 0.9F);
        this.alpha = fade * twinkle;
        this.quadSize *= 0.97F;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new SpellSparkParticle(level, x, y, z, dx, dy, dz, this.sprites);
        }
    }
}
