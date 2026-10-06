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
 * Arcanewood yaprak zerresi — biyomda/yaprak altında süzülen sihirli toz.
 * Camgöbeği-lavanta arası rastgele tonda, salınarak süzülür, yavaşça söner.
 */
public class LeafMoteParticle extends TextureSheetParticle {
    private final float swayPhase;

    protected LeafMoteParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.lifetime = 70 + this.random.nextInt(50);
        this.gravity = 0.02F;
        this.friction = 0.99F;
        this.hasPhysics = true;
        this.xd = (this.random.nextDouble() - 0.5) * 0.01;
        this.yd = -0.015 - this.random.nextDouble() * 0.01;
        this.zd = (this.random.nextDouble() - 0.5) * 0.01;
        this.quadSize = 0.045F + this.random.nextFloat() * 0.035F;
        this.swayPhase = this.random.nextFloat() * Mth.TWO_PI;
        // camgöbeği ↔ lavanta arası ton
        float mix = this.random.nextFloat();
        this.rCol = Mth.lerp(mix, 0.35F, 0.70F);
        this.gCol = Mth.lerp(mix, 0.88F, 0.55F);
        this.bCol = Mth.lerp(mix, 0.85F, 0.95F);
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
        // yatay salınım
        this.xd += Math.cos(this.swayPhase + this.age * 0.08) * 0.0008;
        this.zd += Math.sin(this.swayPhase + this.age * 0.08) * 0.0008;
        float t = (float) this.age / (float) this.lifetime;
        this.alpha = t < 0.1F ? t / 0.1F : (1.0F - t);
        if (this.onGround) {
            this.remove();
        }
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
            return new LeafMoteParticle(level, x, y, z, this.sprites);
        }
    }
}
