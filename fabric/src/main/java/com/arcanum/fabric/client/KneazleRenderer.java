package com.arcanum.fabric.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.KneazleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class KneazleRenderer extends GeoEntityRenderer<KneazleEntity> {
    public KneazleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(
                ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "kneazle")));
        this.shadowRadius = 0.25f;
    }
}
