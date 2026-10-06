package com.arcanum.forge.client.render;

import com.arcanum.Arcanum;
import com.arcanum.entity.DementorEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Dementor GeckoLib renderer'ı — geo/entity/dementor.geo.json + textures/entity/dementor.png.
 */
public class DementorRenderer extends GeoEntityRenderer<DementorEntity> {
    public DementorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(
                ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "dementor"), false));
        this.shadowRadius = 0.0f;
    }
}
