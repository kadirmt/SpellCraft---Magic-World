package com.arcanum.fabric.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.DeathEaterEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * DeathEater GeckoLib renderer'ı — geo/entity/death_eater.geo.json + textures/entity/death_eater.png.
 */
public class DeathEaterRenderer extends GeoEntityRenderer<DeathEaterEntity> {
    public DeathEaterRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(
                ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "death_eater"), true));
        this.shadowRadius = 0.5f;
    }
}
