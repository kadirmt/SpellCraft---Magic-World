package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.KneazleEntity;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class KneazleRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<KneazleEntity, R> {
    public KneazleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Arcanum.id("kneazle")));
        this.shadowRadius = 0.25f;
    }
}
