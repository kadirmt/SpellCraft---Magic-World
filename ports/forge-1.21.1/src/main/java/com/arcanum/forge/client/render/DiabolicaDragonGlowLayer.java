package com.arcanum.forge.client.render;

import com.arcanum.client.beam.ArcanumRenderTypes;
import com.arcanum.entity.FiendfyreDragonEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Alev ejderhasının ADDITIVE parlama katmanı.
 *
 * <p>GeckoLib'in {@link AutoGlowingGeoLayer}'ını taban alır — yani
 * {@code diabolica_dragon_glowmask.png} çalışma anında taban dokuyla harmanlanır
 * (SnowyOwl/Unicorn'da kanıtlı boru hattı, bu portta da aynı sürüm: GeckoLib 4.7.7).
 * TEK farkı render tipi: varsayılan "glowing" tipi yerine
 * {@link ArcanumRenderTypes#flameEntityAdditive} kullanılır → alev damarları ve gözler
 * {@code LIGHTNING_TRANSPARENCY} ile TOPLANIR (13. turun yıldırım ışınlarıyla aynı
 * additive karakter): üst üste binen damarlar beyaza doyar, ejderha karanlıkta
 * gerçekten ateş gibi yanar.
 *
 * <p>Gövdenin kendisi {@link DiabolicaDragonRenderer} tarafından translucent-emissive
 * çizilir; bu katman onun ÜSTÜNE biner. İkisinin ayrılması bilinçlidir: saf additive
 * bir gövde aydınlık gökyüzünde silinir, saf translucent bir gövde ise karanlıkta
 * parlamaz — bu iki katman birlikte her ışıkta okunur bir alev yaratığı verir.
 */
public class DiabolicaDragonGlowLayer extends AutoGlowingGeoLayer<FiendfyreDragonEntity> {

    public DiabolicaDragonGlowLayer(GeoRenderer<FiendfyreDragonEntity> renderer) {
        super(renderer);
    }

    @Override
    @Nullable
    protected RenderType getRenderType(FiendfyreDragonEntity animatable, @Nullable MultiBufferSource bufferSource) {
        if (animatable.isInvisible()) {
            return null; // görünmezlik (Umbravolo vb.) parlamayı da yutar
        }
        return ArcanumRenderTypes.flameEntityAdditive(
                AutoGlowingTexture.getEmissiveResource(getTextureResource(animatable)));
    }
}
