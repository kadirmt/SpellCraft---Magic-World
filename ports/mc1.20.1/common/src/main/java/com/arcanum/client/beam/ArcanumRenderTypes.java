package com.arcanum.client.beam;

import java.util.function.Function;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Büyü ışınları için custom {@link RenderType} — "trampoline" deseni:
 * {@code RenderType.create} ve state shard'ları protected olduğundan, bu sınıf
 * RenderType'ı extend eder (instantiate edilmez, yalnızca statik erişim sağlar).
 *
 * <p>"Solid ışık" görünümünün 4 bileşeni (Iron Man addon analizi):
 * <ul>
 *   <li>{@code POSITION_COLOR} format → UV/texture YOK, ışın düz renkli (sprite değil)</li>
 *   <li>{@code LIGHTNING_TRANSPARENCY} → additive blend (src_alpha, ONE): üst üste binen
 *       katmanlar TOPLANIR, merkez beyaza doyar = "yanmış parlak çekirdek + renkli hale"</li>
 *   <li>{@code NO_CULL} → prizma yüzlerinin ikisi de görünür (kamera içinden geçse bile)</li>
 *   <li>{@code COLOR_WRITE} → depth'e YAZMAZ: iç içe glow kabukları birbirini kesmez
 *       (depth TESTİ açık kalır → duvar arkasında ışın görünmez)</li>
 * </ul>
 * Lightmap format'ta hiç yok → ışın dünya ışığından bağımsız her zaman "fullbright".
 *
 * <p>1.20.1 PORT NOTU: {@code create} imzası ve dört shard (POSITION_COLOR_SHADER,
 * LIGHTNING_TRANSPARENCY, NO_CULL, COLOR_WRITE) 1.20.1'de aynı adlarla mevcut —
 * sınıf 1.21.1 kaynağından değişiklik gerektirmeden derlenir.
 */
public final class ArcanumRenderTypes extends RenderType {

    /** Asla çağrılmaz — yalnızca protected statiklere erişim için subclass gerekli. */
    private ArcanumRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                               boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    public static final RenderType SPELL_BEAM = create(
            "arcanum:spell_beam",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    // ===================== alev varlıkları (Protego Diabolica ejderhası) =====================

    /**
     * "Alev varlığı" GÖVDE katmanı — dokulu, tam parlak, derinliğe yazmayan translucent.
     * <ul>
     *   <li>{@code NEW_ENTITY} format → {@link RenderType#entityCutoutNoCull} gibi UV+normal
     *       taşır, yani GeckoLib modeli normal şekilde dokulanır (SPELL_BEAM'in
     *       {@code POSITION_COLOR} formatı doku taşımadığı için burada kullanılamaz)</li>
     *   <li>{@code RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER} → lightmap yok sayılır,
     *       varlık karanlıkta da tam parlak (ateşin kendi ışığı)</li>
     *   <li>{@code TRANSLUCENT_TRANSPARENCY} → gövde daha aydınlık gökyüzünde bile OKUNUR
     *       kalır; ateş parlaması additive olan {@link #flameEntityAdditive} katmanından gelir</li>
     *   <li>{@code NO_CULL} → alev kanatları iki yüzünden de görünür</li>
     *   <li>{@code COLOR_WRITE} → derinliğe YAZMAZ: iç içe geçen boyun/kuyruk/kanat
     *       segmentleri birbirini kesmez, z-fighting kaynaklı yanıp sönme olmaz
     *       (derinlik TESTİ açık kalır → duvar arkasında görünmez)</li>
     * </ul>
     * Doku başına memoize: her karede yeni RenderType üretmek buffer batch'ini bozar.
     *
     * <p>1.20.1 PORT NOTU: {@code DefaultVertexFormat.NEW_ENTITY},
     * {@code RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER}, {@code TRANSLUCENT_TRANSPARENCY},
     * {@code OVERLAY} ve {@code TextureStateShard(loc, blur, mipmap)} 1.20.1'de aynı
     * adlarla mevcut (javap ile doğrulandı) — 1.21.1 kaynağından değişiklik gerekmedi.
     */
    private static final Function<ResourceLocation, RenderType> FLAME_ENTITY = Util.memoize(
            texture -> create("arcanum:flame_entity",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS, 1536, true, true,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(false)));

    /**
     * "Alev varlığı" PARLAMA katmanı — gövdenin üstüne binen ADDITIVE geçiş.
     * {@link #FLAME_ENTITY} ile tek farkı {@code LIGHTNING_TRANSPARENCY} (src_alpha, ONE):
     * üst üste binen alev damarları TOPLANIR, çekirdek beyaza doyar — SPELL_BEAM'deki
     * yıldırım ışınlarıyla aynı "yanmış çekirdek + renkli hale" karakteri.
     * Glowmask dokusuyla kullanılır (bkz. {@code DiabolicaDragonGlowLayer}).
     */
    private static final Function<ResourceLocation, RenderType> FLAME_ENTITY_ADDITIVE = Util.memoize(
            texture -> create("arcanum:flame_entity_additive",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS, 1536, true, true,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(false)));

    /** Alev varlığı gövde katmanı (tam parlak, derinliğe yazmaz). */
    public static RenderType flameEntity(ResourceLocation texture) {
        return FLAME_ENTITY.apply(texture);
    }

    /** Alev varlığı additive parlama katmanı (glowmask ile). */
    public static RenderType flameEntityAdditive(ResourceLocation texture) {
        return FLAME_ENTITY_ADDITIVE.apply(texture);
    }
}
