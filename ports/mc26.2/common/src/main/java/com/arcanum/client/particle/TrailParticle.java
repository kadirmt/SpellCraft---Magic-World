package com.arcanum.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

/**
 * İnce iz partikülü — ışın gövdeleri için. Küçük, kısa ömürlü, hızlı sönümlü;
 * ışın çizgisi yüzlerce partikülden oluştuğu için ucuz olması önemlidir.
 */
public class TrailParticle extends SingleQuadParticle {

    protected TrailParticle(ClientLevel level, double x, double y, double z,
                            double dx, double dy, double dz,
                            ColorParticleOption color, TextureAtlasSprite sprite) {
        // 26.1: TextureSheetParticle + pickSprite kalktı → rastgele sprite sağlayıcıda
        // seçilip SingleQuadParticle ctor'una verilir (kökteki pickSprite ile aynı dağılım).
        super(level, x, y, z, sprite);
        this.lifetime = 6 + this.random.nextInt(6);
        this.gravity = 0.0F;
        this.friction = 0.95F;
        this.hasPhysics = false;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.quadSize = 0.045F + this.random.nextFloat() * 0.035F;
        this.setColor(color.getRed(), color.getGreen(), color.getBlue());
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        // kök: ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT (doku yarı saydam)
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return LightCoordsUtil.FULL_BRIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        float t = (float) this.age / (float) this.lifetime;
        this.alpha = 1.0F - t;
        this.quadSize *= 0.92F;
    }

    public static class Provider implements ParticleProvider<ColorParticleOption> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(ColorParticleOption type, ClientLevel level,
                                       double x, double y, double z,
                                       double dx, double dy, double dz,
                                       RandomSource random) {
            return new TrailParticle(level, x, y, z, dx, dy, dz, type, this.sprites.get(random));
        }
    }
}
