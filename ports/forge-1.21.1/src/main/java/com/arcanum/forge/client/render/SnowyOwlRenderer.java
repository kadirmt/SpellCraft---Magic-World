package com.arcanum.forge.client.render;

import com.arcanum.Arcanum;
import com.arcanum.entity.SnowyOwlEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Kar Baykuşu GeckoLib renderer'ı — geo/entity/snowy_owl.geo.json +
 * textures/entity/snowy_owl.png. Gözler karanlıkta parlar (GeckoLib'in
 * yerleşik "_glowmask" katmanı: textures/entity/snowy_owl_glowmask.png).
 */
public class SnowyOwlRenderer extends GeoEntityRenderer<SnowyOwlEntity> {
    public SnowyOwlRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(
                ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "snowy_owl")));
        this.shadowRadius = 0.35f;
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
