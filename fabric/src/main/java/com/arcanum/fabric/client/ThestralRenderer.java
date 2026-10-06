package com.arcanum.fabric.client;

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
                ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "thestral")));
        this.shadowRadius = 0.7f;
    }
}
