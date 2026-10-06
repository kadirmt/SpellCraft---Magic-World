package com.arcanum.client;

import com.arcanum.client.beam.ArcanumRenderTypes;
import com.arcanum.entity.FiendfyreDragonEntity;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jspecify.annotations.Nullable;

/**
 * Alev ejderhasının ADDITIVE parlama katmanı.
 *
 * <p>GeckoLib'in {@link AutoGlowingGeoLayer}'ını taban alır — yani
 * {@code diabolica_dragon_glowmask.png} gövdeyle aynı poz/kemik durumuyla ikinci bir geçişte
 * (üst sıra, {@code order(1)}) çizilir (SnowyOwl/Unicorn'da kanıtlı boru hattı). TEK farkı render tipi:
 * varsayılan "glowing" tipi yerine {@link ArcanumRenderTypes#flameEntityAdditive} kullanılır →
 * alev damarları ve gözler {@code LIGHTNING} blend ile TOPLANIR
 * (13. turun yıldırım ışınlarıyla aynı additive karakter): üst üste binen damarlar
 * beyaza doyar, ejderha karanlıkta gerçekten ateş gibi yanar.
 *
 * <p>Gövdenin kendisi {@link DiabolicaDragonRenderer} tarafından translucent-emissive
 * çizilir; bu katman onun ÜSTÜNE biner. İkisinin ayrılması bilinçlidir: saf additive
 * bir gövde aydınlık gökyüzünde silinir, saf translucent bir gövde ise karanlıkta
 * parlamaz — bu iki katman birlikte her ışıkta okunur bir alev yaratığı verir.
 *
 * <p><b>Doku sözleşmesi (GL4 birebir):</b> 1.21.1'de GL4 {@code AutoGlowingTexture} glowmask'ı YALNIZ MASKE sayar:
 * parlama geçişi 1752 maske pikselinde TABAN dokunun rengini çizer ve taban dokudan bu pikseller silinir (gövde
 * geçişi onları çizmez). GL5 glowmask'ın kendi RGB'sini çizer ve tabanı soymaz → translucent-emissive gövde +
 * additive parlama aynı pikselde üst üste binip beyaza doyuyordu. {@code diabolica_dragon.png} ve
 * {@code diabolica_dragon_glowmask.png} bu yüzden {@code ports/mc26.1.2/tools/GlowmaskBake.java} ile kök 1.21.1
 * dokularından GL5 sözleşmesine fırınlandı (taban soyulu, parlama = taban rengi × maske alfası).
 *
 * <p>GeckoLib 5: katman render-state ile çalışır (entity'ye erişim YOK); {@code getTextureResource(state)}
 * üst sınıfta zaten {@code _glowmask.png} yolunu (önbellekli) döndürür. Parlaklık üst sınıfın
 * {@code FULL_SKY} ışığıyla verilir (emissive shader lightmap okumaz — etkisiz).
 */
public class DiabolicaDragonGlowLayer<R extends LivingEntityRenderState & GeoRenderState>
        extends AutoGlowingGeoLayer<FiendfyreDragonEntity, Void, R> {

    public DiabolicaDragonGlowLayer(GeoRenderer<FiendfyreDragonEntity, Void, R> renderer) {
        super(renderer);
    }

    @Override
    protected @Nullable RenderType getRenderType(R renderState) {
        if (renderState.isInvisible) {
            return null; // görünmezlik (Umbravolo vb.) parlamayı da yutar
        }
        return ArcanumRenderTypes.flameEntityAdditive(getTextureResource(renderState));
    }
}
