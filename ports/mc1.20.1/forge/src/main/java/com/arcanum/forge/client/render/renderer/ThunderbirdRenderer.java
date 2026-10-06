package com.arcanum.forge.client.render.renderer;

import com.arcanum.Arcanum;
import com.arcanum.entity.ThunderbirdEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Thunderbird GeckoLib renderer'ı — geo/entity/thunderbird.geo.json + textures/entity/thunderbird.png. */
public class ThunderbirdRenderer extends GeoEntityRenderer<ThunderbirdEntity> {
    public ThunderbirdRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(
                new ResourceLocation(Arcanum.MODID, "thunderbird")));
        this.shadowRadius = 0.5f;
    }
}
