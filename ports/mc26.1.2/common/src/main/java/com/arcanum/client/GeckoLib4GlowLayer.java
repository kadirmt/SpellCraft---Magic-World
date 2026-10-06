package com.arcanum.client;

import com.arcanum.client.beam.ArcanumRenderTypes;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * 1.21.1 (GeckoLib 4.7.7) {@code AutoGlowingGeoLayer} görünümünü GeckoLib 5'te birebir veren parlama katmanı.
 *
 * <p>GeckoLib 5 ile iki fark vardı:
 * <ol>
 *   <li><b>Doku sözleşmesi</b> — GL4 glowmask'ı yalnız maske sayar (parlama = taban rengi, taban soyulur);
 *       GL5 glowmask'ın kendi RGB'sini çizer. Çözüm dokularda: {@code ports/mc26.1.2/tools/GlowmaskBake.java}
 *       dokuları GL5 sözleşmesine fırınlar (bkz. {@link UnicornRenderer}).</li>
 *   <li><b>Render tipi</b> — GL4 {@code geo_glowing_layer} = entity-translucent-emissive shader'ı (lightmap yok,
 *       yüz/yön gölgelemesi VAR); GL5 {@code geckolib_emissive} pipeline'ı {@code NO_CARDINAL_LIGHTING} ile yüz
 *       gölgesini kapatıyor → parlama 1.21.1'den ~%32 parlak. Bu sınıf yalnız render tipini
 *       {@link ArcanumRenderTypes#geoGlowLayer} ile değiştirir.</li>
 * </ol>
 * Görünmezlik / parlayan-varlık dalları GL4 (ve GL5 üst sınıfı) ile aynı: izleyene görünür görünmez varlık →
 * translucent, parlayan+görünmez → yalnız outline, görünmez → çizilmez. Parlaklık (tam ışık) ve doku yolu
 * ({@code _glowmask.png}) üst sınıftan gelir.
 */
public class GeckoLib4GlowLayer<T extends GeoAnimatable, R extends GeoRenderState>
        extends AutoGlowingGeoLayer<T, Void, R> {

    public GeckoLib4GlowLayer(GeoRenderer<T, Void, R> renderer) {
        super(renderer);
    }

    @Override
    protected @Nullable RenderType getRenderType(R renderState) {
        Identifier texture = getTextureResource(renderState);
        if (renderState instanceof EntityRenderState entityState) {
            boolean invisible = entityState.isInvisible;
            if (invisible && !Boolean.TRUE.equals(
                    renderState.getOrDefaultGeckolibData(DataTickets.INVISIBLE_TO_PLAYER, false))) {
                return RenderTypes.entityTranslucentCullItemTarget(texture);
            }
            if (entityState.appearsGlowing()) {
                return invisible ? RenderTypes.outline(texture) : ArcanumRenderTypes.geoGlowLayer(texture, true);
            }
            return invisible ? null : ArcanumRenderTypes.geoGlowLayer(texture, false);
        }
        return ArcanumRenderTypes.geoGlowLayer(texture, false);
    }
}
