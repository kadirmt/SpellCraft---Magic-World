package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.GrindylowEntity;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class GrindylowRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<GrindylowEntity, R> {
    public GrindylowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Arcanum.id("grindylow")));
        this.shadowRadius = 0.3f;
    }
}
