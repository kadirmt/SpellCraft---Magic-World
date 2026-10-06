package com.arcanum.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;

/**
 * Dünya-uzayı özel geometri girişi — ortak mixin {@code LevelRenderer#submitEntities} TAIL buraya düşer
 * (iki loader'da AYNI kod yolu; kökteki Fabric {@code WorldRenderEvents.AFTER_ENTITIES} kaydının yerine).
 *
 * <p><b>PoseStack uzayı (vanilla kaynağından doğrulandı — g2-platform-contract.md §8):</b> gelen {@code poseStack}
 * {@code new PoseStack()} = BİRİM matristir; kamera DÖNÜŞÜ RenderSystem model-view yığınında, kamera KONUMU ise hiç
 * uygulanmamıştır. Vanilla her entity'yi {@code state.x - camX, ...} ile öteleyerek submit eder. Yani dünya koordinatı
 * {@code P} için: {@code poseStack.translate(P.x - cam.x, P.y - cam.y, P.z - cam.z)};
 * {@code cam = levelRenderState.cameraRenderState.pos}. Eksenler dünya eksenleridir (billboard için
 * {@code cameraRenderState.orientation} kullanılır).
 *
 * <p>Burada submit edilen geometri aynı karede çizilir: TAIL'den sonra vanilla block-entity/partikül submit'leri,
 * ardından {@code renderSolidFeatures()} / {@code renderTranslucentFeatures()} gelir (blend'li tipler translucent
 * geçişte, ana hedefe — kökteki AFTER_ENTITIES çiziminin karşılığı). PoseStack dengeli bırakılır (vanilla
 * {@code checkPoseStack} çağırıyor) — buradaki kod onu hiç değiştirmez.
 */
public final class ArcanumWorldRender {
    private ArcanumWorldRender() {}

    public static void onSubmitEntities(PoseStack poseStack, LevelRenderState state, SubmitNodeCollector collector) {
        // 13. tur büyü ışınları + düello kenetlenmesi (yıldırımlar, düğüm, kıvılcımlar)
        SpellLockRenderer.submit(poseStack, state, collector);
    }
}
