package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.ThestralEntity;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Thestral GeckoLib renderer'ı — geckolib/models/entity/thestral.geo.json + textures/entity/thestral.png. */
public class ThestralRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<ThestralEntity, R> {
    public ThestralRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Arcanum.id("thestral")));
        this.shadowRadius = 0.7f;
    }
}
