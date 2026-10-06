package com.arcanum.fabric.client;

import com.arcanum.entity.PatronusEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Patronus GeckoLib renderer'ı — varyant bazlı model çözümü {@link PatronusModel}'de.
 * Dokular yarı saydam (alpha 130-235) spektral tasarım: varsayılan cutout render tipi
 * saydamlığı ATAR; bu yüzden translucent-emissive kullanılır — hem hayalet saydamlığı
 * hem de ışık seviyesinden bağımsız tam parlak "ruhani ışıma" verir.
 */
public class PatronusRenderer extends GeoEntityRenderer<PatronusEntity> {
    public PatronusRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PatronusModel());
        this.shadowRadius = 0.0f; // ruhani varlık — gölgesi yok
    }

    @Override
    public RenderType getRenderType(PatronusEntity entity, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }
}
