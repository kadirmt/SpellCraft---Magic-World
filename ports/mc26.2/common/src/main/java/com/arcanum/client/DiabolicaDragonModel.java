package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.FiendfyreDragonEntity;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;

/**
 * Protego Diabolica alev ejderhasının GeckoLib modeli.
 *
 * <p>Tek varyant — {@code geometry.diabolica_dragon} (17 kemik / 43 küp, ~4.1 blok
 * uzunluk, ~3.1 blok kanat açıklığı). Klipler:
 * {@code animation.diabolica_dragon.fly} (loop 2.0 sn),
 * {@code .lunge} (0.8 sn), {@code .roar} (1.2 sn),
 * {@code .dissolve} (0.6 sn, hold_on_last_frame) — süreleri
 * {@link FiendfyreDragonEntity} içindeki tick sabitleriyle birebir eşleşir.
 *
 * <p>Doku 128×128 tam opak mavi alev gradyanı; parlayan alev damarları ve gözler
 * ayrı {@code diabolica_dragon_glowmask.png} katmanındadır
 * (bkz. {@link DiabolicaDragonGlowLayer}).
 *
 * <p>GeckoLib 5 kimlikleri: model/animasyon {@code geckolib/models|animations/} altındaki göreli yol,
 * öneksiz + uzantısız ({@code arcanum:entity/diabolica_dragon} →
 * {@code assets/arcanum/geckolib/models/entity/diabolica_dragon.geo.json} ve
 * {@code assets/arcanum/geckolib/animations/entity/diabolica_dragon.animation.json}); doku tam yol.
 * Model/doku artık entity değil render-state alır (tek varyant → sabit).
 */
public class DiabolicaDragonModel extends GeoModel<FiendfyreDragonEntity> {

    private static final Identifier MODEL = Arcanum.id("entity/diabolica_dragon");
    private static final Identifier TEXTURE = Arcanum.id("textures/entity/diabolica_dragon.png");
    private static final Identifier ANIMATION = Arcanum.id("entity/diabolica_dragon");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(FiendfyreDragonEntity entity) {
        return ANIMATION;
    }
}
