package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.TrollEntity;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Troll GeckoLib renderer'ı — geckolib/models/entity/troll.geo.json + textures/entity/troll.png. */
public class TrollRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<TrollEntity, R> {
    public TrollRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Arcanum.id("troll")));
        this.shadowRadius = 0.9f;
    }
}
