package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.ThunderbirdEntity;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Thunderbird GeckoLib renderer'ı — geckolib/models/entity/thunderbird.geo.json + textures/entity/thunderbird.png. */
public class ThunderbirdRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<ThunderbirdEntity, R> {
    public ThunderbirdRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Arcanum.id("thunderbird")));
        this.shadowRadius = 0.5f;
    }
}
