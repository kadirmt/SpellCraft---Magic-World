package com.arcanum.forge.client.render.renderer;

import com.arcanum.Arcanum;
import com.arcanum.entity.HippogriffEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class HippogriffRenderer extends GeoEntityRenderer<HippogriffEntity> {
    public HippogriffRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(
                new ResourceLocation(Arcanum.MODID, "hippogriff")));
        this.shadowRadius = 0.6f;
    }
}
