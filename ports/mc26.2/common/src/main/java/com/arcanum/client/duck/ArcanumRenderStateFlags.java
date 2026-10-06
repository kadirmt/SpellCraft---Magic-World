package com.arcanum.client.duck;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * "Duck" arayüzü: {@link LivingEntityRenderState}'e (mixin ile) iki Arcanum bayrağı ekler.
 *
 * <p>26.x render hattında katmanlar ({@code RenderLayer#submit}) artık ENTITY görmez, yalnız
 * render-state görür. Kökteki (1.21.1) katman mixin'lerinin koşulu
 * {@code CloakOfInvisibilityItem.isWearing(entity) || ClientUmbraForms.has(entity.getId())}
 * idi; bu koşul artık {@code LivingEntityRenderer#extractRenderState} anında (entity elimizdeyken)
 * hesaplanıp buraya yazılır ({@code mixin.client.LivingEntityRendererMixin}), katman mixin'leri
 * ({@code HumanoidArmorLayerMixin}, {@code CustomHeadLayerMixin}, {@code ItemInHandLayerMixin},
 * {@code WingsLayerMixin}) yalnız bu bayrağı okur.
 *
 * <p>Bayrak {@code LivingEntityRenderState} seviyesinde tutulur (Humanoid/Avatar'a daraltılmaz):
 * kökteki {@code isWearing(LivingEntity)} HER canlıda çalışıyordu — parite için tüm canlı
 * render-state'leri kapsanır.
 *
 * <p>BİLEREK {@code com.arcanum.mixin} paketinin DIŞINDA: Mixin, mixin paketindeki sınıfların
 * doğrudan yüklenmesine izin vermez (IllegalClassLoadError); katman mixin'leri bu arayüze cast eder.
 */
public interface ArcanumRenderStateFlags {

    /** Görünmezlik Pelerini (CHEST slotu) giyili mi — extract anında yazılır. */
    boolean arcanum$isCloaked();

    void arcanum$setCloaked(boolean cloaked);

    /** Umbravolo kara duman formunda mı (ClientUmbraForms, sunucu-senkronlu) — extract anında yazılır. */
    boolean arcanum$isUmbraForm();

    void arcanum$setUmbraForm(boolean umbraForm);

    /**
     * Kökteki katman mixin koşulunun birebir karşılığı: pelerin giyiliyse VEYA Umbravolo
     * formundaysa zırh/kafa/el/kanat katmanları çizilmez.
     */
    static boolean shouldHideLayers(LivingEntityRenderState state) {
        return state instanceof ArcanumRenderStateFlags f
                && (f.arcanum$isCloaked() || f.arcanum$isUmbraForm());
    }
}
