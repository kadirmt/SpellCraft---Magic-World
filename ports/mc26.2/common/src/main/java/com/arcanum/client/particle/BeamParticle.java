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
 * Kalıcı ışın parçası — büyünün geçtiği hat ~1.5-2.5 sn boyunca havada asılı
 * kalan renkli bir ışın olarak görünür (Avada'nın yeşil izi gibi). Hareketsizdir;
 * ömrünün %40'ından sonra sönmeye başlar ve hafifçe incelir.
 */
public class BeamParticle extends SingleQuadParticle {

    protected BeamParticle(ClientLevel level, double x, double y, double z,
                           ColorParticleOption color, TextureAtlasSprite sprite) {
        // 26.1: TextureSheetParticle + pickSprite kalktı → rastgele sprite sağlayıcıda
        // seçilip SingleQuadParticle ctor'una verilir (kökteki pickSprite ile aynı dağılım).
        super(level, x, y, z, sprite);
        this.lifetime = 32 + this.random.nextInt(16);
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;
        this.quadSize = 0.055F + this.random.nextFloat() * 0.03F;
        this.alpha = 0.85F;
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
        if (t > 0.4F) {
            float f = (t - 0.4F) / 0.6F;
            this.alpha = 0.85F * (1.0F - f);
            this.quadSize *= 0.985F;
        }
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
            return new BeamParticle(level, x, y, z, type, this.sprites.get(random));
        }
    }
}
