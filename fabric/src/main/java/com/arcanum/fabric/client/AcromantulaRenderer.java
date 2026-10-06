package com.arcanum.fabric.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.AcromantulaEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class AcromantulaRenderer extends GeoEntityRenderer<AcromantulaEntity> {
    public AcromantulaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(
                ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "acromantula")));
        this.shadowRadius = 0.7f;
    }
}
