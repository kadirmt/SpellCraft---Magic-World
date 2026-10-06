package com.arcanum.client;

import com.arcanum.entity.PhoenixEntity;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Phoenix GeckoLib renderer'ı — doku varyantı PhoenixModel'de çözülür. */
public class PhoenixRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<PhoenixEntity, R> {
    public PhoenixRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PhoenixModel());
        this.shadowRadius = 0.3f;
    }
}
