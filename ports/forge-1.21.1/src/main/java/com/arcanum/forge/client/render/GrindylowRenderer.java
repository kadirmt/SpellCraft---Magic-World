package com.arcanum.forge.client.render;

import com.arcanum.Arcanum;
import com.arcanum.entity.GrindylowEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GrindylowRenderer extends GeoEntityRenderer<GrindylowEntity> {
    public GrindylowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(
                ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "grindylow")));
        this.shadowRadius = 0.3f;
    }
}
