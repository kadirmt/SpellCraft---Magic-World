package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.PatronusEntity;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Patronus modeli — üç görsel varyantın (0=baykuş, 1=kurt, 2=geyik) geo/doku/animasyon
 * dosyaları AYRIDIR ama animasyon klip adları ORTAKTIR
 * ({@code animation.patronus.idle/.walk/.attack}) — böylece {@link PatronusEntity}'deki
 * tek animasyon controller'ı üç varyanta da hizmet eder (PhoenixModel deseni).
 *
 * <p>GeckoLib 5: model/doku çözümü render sırasında ENTITY ALMAZ (render-state alır) → varyant,
 * çıkarım anında ({@link #addAdditionalStateData}) {@link #VARIANT} bileti ile render-state'e yazılır ve
 * {@link #getModelResource}/{@link #getTextureResource} oradan okur. Animasyon çözümü hâlâ entity alır.
 * Kimlikler: model/animasyon {@code arcanum:entity/patronus_<tür>} ({@code geckolib/models|animations/} altı,
 * öneksiz + uzantısız), doku tam yol {@code arcanum:textures/entity/patronus_<tür>.png}.
 */
public class PatronusModel extends GeoModel<PatronusEntity> {

    /** Patronus varyantı (render-state verisi). GeckoLib bilet önbelleği global → {@code arcanum_} önekli kimlik. */
    public static final DataTicket<Byte> VARIANT = DataTicket.create("arcanum_patronus_variant", Byte.class);

    /** Varyant → asset kimliği (geo/animasyon/doku dosya adlarının ortak kökü). */
    private static String variantName(byte variant) {
        return switch (variant) {
            case PatronusEntity.VARIANT_WOLF -> "patronus_wolf";
            case PatronusEntity.VARIANT_STAG -> "patronus_stag";
            default -> "patronus_owl";
        };
    }

    /** Render-state'teki varyant; veri yoksa (çıkarım dışı bir çağrı) kökteki {@code default} dalı = baykuş. */
    private static byte variant(GeoRenderState renderState) {
        Byte v = renderState.getGeckolibData(VARIANT);
        return v == null ? PatronusEntity.VARIANT_OWL : v;
    }

    @Override
    public void addAdditionalStateData(PatronusEntity entity, @Nullable Object relatedObject, GeoRenderState renderState) {
        renderState.addGeckolibData(VARIANT, entity.getVariant());
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return Arcanum.id("entity/" + variantName(variant(renderState)));
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return Arcanum.id("textures/entity/" + variantName(variant(renderState)) + ".png");
    }

    @Override
    public Identifier getAnimationResource(PatronusEntity entity) {
        return Arcanum.id("entity/" + variantName(entity.getVariant()));
    }
}
