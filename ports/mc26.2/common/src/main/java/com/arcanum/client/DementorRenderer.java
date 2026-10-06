package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.DementorEntity;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Dementor GeckoLib renderer'ı — geckolib/models/entity/dementor.geo.json + textures/entity/dementor.png.
 * Kafa takibi YOK (kökte {@code DefaultedEntityGeoModel(id, false)}).
 */
public class DementorRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<DementorEntity, R> {
    public DementorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Arcanum.id("dementor")));
        this.shadowRadius = 0.0f;
    }
}
