package com.arcanum.client;

import com.arcanum.entity.UnicornEntity;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Unicorn GeckoLib renderer'ı — doku varyantı UnicornModel'de çözülür; eyer
 * emissive parıltısı da "_glowmask" katmanıyla otomatik (saddle bone gizliyken
 * glow da görünmez, GeckoLib aynı baked model/bone durumunu paylaşır).
 * Glowmask dokusu da varyanta göre seçilir (unicorn_N_glowmask.png).
 *
 * <p><b>GeckoLib 4 → 5 glowmask sözleşmesi:</b> 1.21.1'de (GL4) glowmask yalnız MASKEYDİ — parlama o konumlarda
 * TABAN dokunun rengini (maske alfasıyla) çizer, taban o pikselleri kaybederdi (göz bandı siyah/beyaz). GL5
 * glowmask'ı doğrudan renk olarak çizer ve tabanı soymaz. Görünüm birebir kalsın diye {@code unicorn_N.png} ve
 * {@code unicorn_N_glowmask.png} GL5 sözleşmesine göre {@code ports/mc26.1.2/tools/GlowmaskBake.java} ile kök
 * 1.21.1 dokularından fırınlandı (GL4 {@code GeoGlowingTextureMeta.createImageMask} dönüşümünün birebir aynısı).
 * Parlama render tipi de GL4 ile aynıdır ({@link GeckoLib4GlowLayer}: yüz gölgelemeli translucent-emissive; GL5'in
 * {@code NO_CARDINAL_LIGHTING}'li emissive pipeline'ı 1.21.1'den ~%32 parlak çiziyordu).
 */
public class UnicornRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<UnicornEntity, R> {
    public UnicornRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new UnicornModel());
        this.shadowRadius = 0.7f;
        withRenderLayer(new GeckoLib4GlowLayer<UnicornEntity, R>(this));
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> info, BoneSnapshots snapshots) {
        // Eski 4.x setHidden(!saddled) hem kemiği hem çocuklarını gizliyordu →
        // 5.x'te skipRender yalnız kemiği gizler, çocuklar için skipChildrenRender da şart.
        boolean saddled = Boolean.TRUE.equals(info.getGeckolibData(UnicornModel.SADDLED));
        snapshots.ifPresent("saddle", bone -> bone.skipRender(!saddled).skipChildrenRender(!saddled));
    }
}
