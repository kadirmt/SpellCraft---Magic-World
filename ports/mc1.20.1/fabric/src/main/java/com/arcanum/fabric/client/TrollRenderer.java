package com.arcanum.fabric.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.TrollEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Troll GeckoLib renderer'ı — geo/entity/troll.geo.json + textures/entity/troll.png. */
public class TrollRenderer extends GeoEntityRenderer<TrollEntity> {
    public TrollRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(
                new ResourceLocation(Arcanum.MODID, "troll")));
        this.shadowRadius = 0.9f;
    }
}
