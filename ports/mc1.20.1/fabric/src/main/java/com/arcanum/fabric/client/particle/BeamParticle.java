package com.arcanum.fabric.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import com.arcanum.util.ArcanumColorParticleOption;

/**
 * Kalıcı ışın parçası — büyünün geçtiği hat ~1.5-2.5 sn boyunca havada asılı
 * kalan renkli bir ışın olarak görünür (Avada'nın yeşil izi gibi). Hareketsizdir;
 * ömrünün %40'ından sonra sönmeye başlar ve hafifçe incelir.
 */
public class BeamParticle extends TextureSheetParticle {

    protected BeamParticle(ClientLevel level, double x, double y, double z,
                           ArcanumColorParticleOption color, SpriteSet sprites) {
        super(level, x, y, z);
        this.lifetime = 32 + this.random.nextInt(16);
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;
        this.quadSize = 0.055F + this.random.nextFloat() * 0.03F;
        this.alpha = 0.85F;
        this.setColor(color.getRed(), color.getGreen(), color.getBlue());
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
        if (t > 0.4F) {
            float f = (t - 0.4F) / 0.6F;
            this.alpha = 0.85F * (1.0F - f);
            this.quadSize *= 0.985F;
        }
    }

    public static class Provider implements ParticleProvider<ArcanumColorParticleOption> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(ArcanumColorParticleOption type, ClientLevel level,
                                       double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new BeamParticle(level, x, y, z, type, this.sprites);
        }
    }
}
