package com.arcanum.client;

import com.arcanum.entity.PatronusEntity;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Patronus GeckoLib renderer'ı — varyant bazlı model çözümü {@link PatronusModel}'de.
 * Dokular yarı saydam (alpha 130-235) spektral tasarım: varsayılan cutout render tipi
 * saydamlığı ATAR; bu yüzden translucent-emissive kullanılır — hem hayalet saydamlığı
 * hem de ışık seviyesinden bağımsız tam parlak "ruhani ışıma" verir.
 *
 * <p>GeckoLib 5 render-state kalıbı ({@code R extends LivingEntityRenderState & GeoRenderState}); render tipi
 * kökteki gibi HER DURUMDA vanilla {@code entityTranslucentEmissive(texture)} (26.1.2 ve 26.2'de aynı imza).
 */
public class PatronusRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<PatronusEntity, R> {

    public PatronusRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PatronusModel());
        this.shadowRadius = 0.0f; // ruhani varlık — gölgesi yok
    }

    @Override
    public RenderType getRenderType(R renderState, Identifier texture) {
        return RenderTypes.entityTranslucentEmissive(texture);
    }
}
