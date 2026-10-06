package com.arcanum.fabric.client;

import com.arcanum.client.beam.ArcanumRenderTypes;
import com.arcanum.entity.FiendfyreDragonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.cache.texture.GeoAbstractTexture;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Alev ejderhasının ADDITIVE parlama katmanı.
 *
 * <p>GeckoLib'in {@link AutoGlowingGeoLayer}'ını taban alır — yani
 * {@code diabolica_dragon_glowmask.png} çalışma anında taban dokuyla harmanlanır
 * (SnowyOwl/Unicorn'da kanıtlı boru hattı). TEK farkı render tipi: varsayılan
 * "geo_glowing_layer" tipi yerine {@link ArcanumRenderTypes#flameEntityAdditive}
 * kullanılır → alev damarları ve gözler {@code LIGHTNING_TRANSPARENCY} ile TOPLANIR
 * (13. turun yıldırım ışınlarıyla aynı additive karakter): üst üste binen damarlar
 * beyaza doyar, ejderha karanlıkta gerçekten ateş gibi yanar.
 *
 * <p>Gövdenin kendisi {@link DiabolicaDragonRenderer} tarafından translucent-emissive
 * çizilir; bu katman onun ÜSTÜNE biner. İkisinin ayrılması bilinçlidir: saf additive
 * bir gövde aydınlık gökyüzünde silinir, saf translucent bir gövde ise karanlıkta
 * parlamaz — bu iki katman birlikte her ışıkta okunur bir alev yaratığı verir.
 *
 * <h3>1.20.1 / GeckoLib 4.8.4 FARKLARI (kök 1.21.1 + 4.7.7 sürümünden)</h3>
 * <ul>
 *   <li>{@code getRenderType} imzası TEK argümanlıdır ({@code MultiBufferSource} yok)
 *       — javap ile doğrulandı.</li>
 *   <li>{@code AutoGlowingTexture.getEmissiveResource(...)} bu sürümde <b>private</b>.
 *       Yerine iki adım: (1) {@link AutoGlowingTexture#getRenderType(ResourceLocation)}
 *       YAN ETKİSİ için çağrılır — emissive dokuyu üretip TextureManager'a kaydeder
 *       (idempotent: {@code GeoAbstractTexture.generateTexture} zaten kayıtlıysa hiçbir
 *       şey yapmaz, memoize'lı); (2) emissive yol GeckoLib'in kendi genel
 *       {@link GeoAbstractTexture#appendToPath} yardımcısıyla BİREBİR aynı şekilde
 *       türetilir ("_glowmask" eki) — elle string birleştirme YOK.</li>
 *   <li>Taban {@code render()} dönen tipi null'a karşı KONTROL ETMEZ (doğrudan
 *       {@code bufferSource.getBuffer(...)} çağırır) → görünmezlik kontrolü
 *       {@code getRenderType}'ta null döndürerek değil, {@link #render} içinde erken
 *       çıkışla yapılır (kökteki null yolu burada NPE olurdu).</li>
 * </ul>
 */
public class DiabolicaDragonGlowLayer extends AutoGlowingGeoLayer<FiendfyreDragonEntity> {

    /** GeckoLib'in emissive doku eki — {@code AutoGlowingTexture.APPENDIX} ile aynı. */
    private static final String GLOW_APPENDIX = "_glowmask";

    public DiabolicaDragonGlowLayer(GeoRenderer<FiendfyreDragonEntity> renderer) {
        super(renderer);
    }

    @Override
    protected RenderType getRenderType(FiendfyreDragonEntity animatable) {
        ResourceLocation base = getTextureResource(animatable);
        // YAN ETKİ İÇİN: emissive dokuyu üret + TextureManager'a kaydet (dönen tip
        // kullanılmaz; biz additive olanı istiyoruz). Bu çağrı olmadan glowmask
        // GeckoLib'in maske boru hattından geçmez.
        AutoGlowingTexture.getRenderType(base);
        return ArcanumRenderTypes.flameEntityAdditive(
                GeoAbstractTexture.appendToPath(base, GLOW_APPENDIX));
    }

    @Override
    public void render(PoseStack poseStack, FiendfyreDragonEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        if (animatable.isInvisible()) {
            return; // görünmezlik (Umbravolo vb.) parlamayı da yutar
        }
        super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer,
                partialTick, packedLight, packedOverlay);
    }
}
