package com.arcanum.fabric.client;

import com.arcanum.client.beam.ArcanumRenderTypes;
import com.arcanum.entity.FiendfyreDragonEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Protego Diabolica alev ejderhası renderer'ı — "gerçek ateş gibi yansın" katmanı.
 *
 * <p>İki teknik BİRLEŞTİRİLDİ:
 * <ul>
 *   <li>14. tur Patronus: {@code translucent + emissive} → varlık ışık seviyesinden
 *       bağımsız TAM PARLAK çizilir, gölge yok</li>
 *   <li>13. tur büyü ışınları ({@code ArcanumRenderTypes}): {@code NO_CULL} +
 *       {@code COLOR_WRITE} (derinliğe yazmaz) → iç içe geçen boyun/kuyruk/kanat
 *       segmentleri birbirini kesmez ve yanıp sönmez; parlama katmanı ADDITIVE</li>
 * </ul>
 * Gövde {@link ArcanumRenderTypes#flameEntity} (tam parlak translucent) ile,
 * alev damarları + gözler {@link DiabolicaDragonGlowLayer} (ADDITIVE glowmask) ile
 * çizilir. Gerekçe DiabolicaDragonGlowLayer javadoc'undadır.
 *
 * <p>Gölge yarıçapı 0: bu havada süzülen bir alev tezahürü, katı bir yaratık değil.
 *
 * <p>1.20.1 PORT NOTU: GeckoLib 4.8.4'te {@code getRenderType(T, ResourceLocation,
 * MultiBufferSource, float)} imzası 4.7.7 ile AYNI (port ağacındaki
 * {@code PatronusRenderer} da bu imzayı kullanıyor) — değişiklik gerekmedi.
 */
public class DiabolicaDragonRenderer extends GeoEntityRenderer<FiendfyreDragonEntity> {

    public DiabolicaDragonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DiabolicaDragonModel());
        this.shadowRadius = 0.0f;
        this.addRenderLayer(new DiabolicaDragonGlowLayer(this));
    }

    @Override
    public RenderType getRenderType(FiendfyreDragonEntity entity, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return ArcanumRenderTypes.flameEntity(texture);
    }
}
