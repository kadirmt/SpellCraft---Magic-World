package com.arcanum.fabric.client;

import com.arcanum.entity.UnicornEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Unicorn GeckoLib renderer'ı — doku varyantı UnicornModel'de çözülür; eyer
 * emissive parıltısı da "_glowmask" katmanıyla otomatik (saddle bone gizliyken
 * glow da görünmez, GeckoLib aynı baked model/bone durumunu paylaşır).
 */
public class UnicornRenderer extends GeoEntityRenderer<UnicornEntity> {
    public UnicornRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new UnicornModel());
        this.shadowRadius = 0.7f;
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
