package com.arcanum.client;

import com.arcanum.client.beam.ArcanumRenderTypes;
import com.arcanum.entity.FiendfyreDragonEntity;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Protego Diabolica alev ejderhası renderer'ı — "gerçek ateş gibi yansın" katmanı.
 *
 * <p>İki teknik BİRLEŞTİRİLDİ:
 * <ul>
 *   <li>14. tur Patronus: {@code translucent + emissive} → varlık ışık seviyesinden
 *       bağımsız TAM PARLAK çizilir, gölge yok</li>
 *   <li>13. tur büyü ışınları ({@code ArcanumRenderTypes}): cull kapalı +
 *       derinliğe yazmaz → iç içe geçen boyun/kuyruk/kanat
 *       segmentleri birbirini kesmez ve yanıp sönmez; parlama katmanı ADDITIVE</li>
 * </ul>
 * Gövde {@link ArcanumRenderTypes#flameEntity} (tam parlak translucent) ile,
 * alev damarları + gözler {@link DiabolicaDragonGlowLayer} (ADDITIVE glowmask) ile
 * çizilir. Gerekçe DiabolicaDragonGlowLayer javadoc'unda.
 *
 * <p>Gölge yarıçapı 0: bu havada süzülen bir alev tezahürü, katı bir yaratık değil.
 *
 * <p>GeckoLib 5 render-state kalıbı: {@code R extends LivingEntityRenderState & GeoRenderState} (EntityRenderState'e
 * GeckoLib'in enjekte ettiği arayüzün derleme-zamanı görünürlüğüne yaslanmaz — iki loader'da aynı derlenir).
 * Kök (GeckoLib 4) gibi render tipi HER DURUMDA alev gövdesidir (görünmezlik dalı yok); görünmez-ama-izleyene-
 * görünür (seyirci) hâlinde GeckoLib'in {@code getRenderColor} alfa kısması 4.x ile aynı uygulanır.
 */
public class DiabolicaDragonRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<FiendfyreDragonEntity, R> {

    public DiabolicaDragonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DiabolicaDragonModel());
        this.shadowRadius = 0.0f;
        this.withRenderLayer(new DiabolicaDragonGlowLayer<>(this));
    }

    /**
     * 1.21.1'de entity ctor'u {@code noCulling = true} yapıyordu (model hitbox'tan çok daha büyük — ekran kenarında
     * kırpılmasın). 26.x'te {@code Entity.noCulling} yok; frustum kararı {@code EntityRenderer#shouldRender} →
     * {@code affectedByCulling}'de — false = kök davranışıyla aynı (mesafe kontrolü {@code entity.shouldRender} sürer).
     */
    @Override
    protected boolean affectedByCulling(FiendfyreDragonEntity entity) {
        return false;
    }

    @Override
    public RenderType getRenderType(R renderState, Identifier texture) {
        return ArcanumRenderTypes.flameEntity(texture);
    }
}
