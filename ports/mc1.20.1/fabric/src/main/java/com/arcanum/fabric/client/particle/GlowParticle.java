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
 * Yumuşak ışıma küresi — büyü patlamaları/auralar için. Rengi ArcanumColorParticleOption
 * taşır; tam parlak (fullbright) çizilir, zamanla küçülüp söner.
 */
public class GlowParticle extends TextureSheetParticle {

    protected GlowParticle(ClientLevel level, double x, double y, double z,
                           double dx, double dy, double dz,
                           ArcanumColorParticleOption color, SpriteSet sprites) {
        super(level, x, y, z);
        this.lifetime = 14 + this.random.nextInt(10);
        this.gravity = 0.0F;
        this.friction = 0.90F;
        this.hasPhysics = false;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.quadSize = 0.10F + this.random.nextFloat() * 0.10F;
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
        this.alpha = (1.0F - t) * (1.0F - t) * 0.9F + 0.1F;
        this.quadSize *= 0.965F;
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
            return new GlowParticle(level, x, y, z, dx, dy, dz, type, this.sprites);
        }
    }
}
