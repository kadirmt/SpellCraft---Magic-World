package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.PhoenixEntity;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;

/**
 * Phoenix modeli — geo/animasyon iki varyant için ortak (kaynakta da öyleydi),
 * yalnızca doku {@link PhoenixEntity#getVariant()}'a göre değişir
 * (0 = textures/entity/phoenix.png, 1 = textures/entity/phoenix_v2.png).
 * <p>
 * GeckoLib 5: model/doku çözümü entity yerine render-state alır → varyant
 * {@link #addAdditionalStateData} içinde {@link #VARIANT} bileti ile render-state'e yazılır.
 */
public class PhoenixModel extends GeoModel<PhoenixEntity> {
    /** Senkron varyant (0/1) — render-state'e çıkarım aşamasında yazılır. */
    public static final DataTicket<Integer> VARIANT =
            DataTicket.create("arcanum_phoenix_variant", Integer.class);

    private static final Identifier MODEL = Arcanum.id("entity/phoenix");
    private static final Identifier ANIMATION = Arcanum.id("entity/phoenix");
    private static final Identifier TEXTURE = Arcanum.id("textures/entity/phoenix.png");
    private static final Identifier TEXTURE_V2 = Arcanum.id("textures/entity/phoenix_v2.png");

    @Override
    public void addAdditionalStateData(PhoenixEntity entity, Object relatedObject, GeoRenderState renderState) {
        renderState.addGeckolibData(VARIANT, entity.getVariant());
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        Integer v = renderState.getGeckolibData(VARIANT);
        return v != null && v == 1 ? TEXTURE_V2 : TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(PhoenixEntity entity) {
        return ANIMATION;
    }
}
