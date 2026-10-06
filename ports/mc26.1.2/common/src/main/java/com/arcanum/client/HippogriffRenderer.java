package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.HippogriffEntity;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class HippogriffRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<HippogriffEntity, R> {
    public HippogriffRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Arcanum.id("hippogriff")));
        this.shadowRadius = 0.6f;
    }
}
