package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.SnowyOwlEntity;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Kar Baykuşu GeckoLib renderer'ı — geckolib/models/entity/snowy_owl.geo.json +
 * textures/entity/snowy_owl.png. Gözler karanlıkta parlar (GeckoLib'in
 * yerleşik "_glowmask" katmanı: textures/entity/snowy_owl_glowmask.png).
 * Dokular GL5 glowmask sözleşmesine göre fırınlıdır (taban soyulu + parlama = taban rengi; GL4 davranışı —
 * bkz. {@link UnicornRenderer} ve {@code ports/mc26.1.2/tools/GlowmaskBake.java}).
 */
public class SnowyOwlRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<SnowyOwlEntity, R> {
    public SnowyOwlRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Arcanum.id("snowy_owl")));
        this.shadowRadius = 0.35f;
        withRenderLayer(new GeckoLib4GlowLayer<SnowyOwlEntity, R>(this));
    }
}
