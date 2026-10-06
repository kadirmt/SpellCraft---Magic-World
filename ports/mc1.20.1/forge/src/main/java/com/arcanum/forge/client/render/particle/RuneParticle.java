package com.arcanum.forge.client.render.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import com.arcanum.util.ArcanumColorParticleOption;
import net.minecraft.util.Mth;

/**
 * Rün glifi — kast çemberleri ve öğrenme ritüelleri. 8 farklı gliften rastgele
 * seçer, yavaşça yükselir, belirip kaybolur (sinüs alfa eğrisi), hafif döner.
 */
public class RuneParticle extends TextureSheetParticle {

    protected RuneParticle(ClientLevel level, double x, double y, double z,
                           double dx, double dy, double dz,
                           ArcanumColorParticleOption color, SpriteSet sprites) {
        super(level, x, y, z);
        this.lifetime = 26 + this.random.nextInt(16);
        this.gravity = 0.0F;
        this.friction = 0.98F;
        this.hasPhysics = false;
        this.xd = dx;
        this.yd = dy + 0.015;
        this.zd = dz;
        this.quadSize = 0.11F + this.random.nextFloat() * 0.05F;
        this.setColor(color.getRed(), color.getGreen(), color.getBlue());
        this.roll = (this.random.nextFloat() - 0.5F) * 0.6F;
        this.oRoll = this.roll;
        this.alpha = 0.0F;
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
        this.alpha = Mth.sin(t * Mth.PI); // belir → parla → sön
        this.oRoll = this.roll;
        this.roll += 0.015F;
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
            return new RuneParticle(level, x, y, z, dx, dy, dz, type, this.sprites);
        }
    }
}
