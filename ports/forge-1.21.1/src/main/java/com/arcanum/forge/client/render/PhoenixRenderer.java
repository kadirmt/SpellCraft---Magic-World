package com.arcanum.forge.client.render;

import com.arcanum.entity.PhoenixEntity;
import com.arcanum.forge.client.model.PhoenixModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Phoenix GeckoLib renderer'ı — doku varyantı PhoenixModel'de çözülür. */
public class PhoenixRenderer extends GeoEntityRenderer<PhoenixEntity> {
    public PhoenixRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PhoenixModel());
        this.shadowRadius = 0.3f;
    }
}
