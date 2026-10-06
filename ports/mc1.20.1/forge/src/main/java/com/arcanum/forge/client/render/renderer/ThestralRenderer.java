package com.arcanum.forge.client.render.renderer;

import com.arcanum.Arcanum;
import com.arcanum.entity.ThestralEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Thestral GeckoLib renderer'ı — geo/entity/thestral.geo.json + textures/entity/thestral.png. */
public class ThestralRenderer extends GeoEntityRenderer<ThestralEntity> {
    public ThestralRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(
                new ResourceLocation(Arcanum.MODID, "thestral")));
        this.shadowRadius = 0.7f;
    }
}
