package com.arcanum.fabric.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.FiendfyreDragonEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

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
 */
public class DiabolicaDragonModel extends GeoModel<FiendfyreDragonEntity> {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "geo/entity/diabolica_dragon.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "textures/entity/diabolica_dragon.png");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "animations/entity/diabolica_dragon.animation.json");

    @Override
    public ResourceLocation getModelResource(FiendfyreDragonEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(FiendfyreDragonEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(FiendfyreDragonEntity entity) {
        return ANIMATION;
    }
}
