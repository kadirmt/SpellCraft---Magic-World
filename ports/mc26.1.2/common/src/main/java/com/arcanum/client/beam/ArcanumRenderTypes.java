package com.arcanum.client.beam;

import java.util.function.BiFunction;
import java.util.function.Function;

import com.arcanum.Arcanum;
import com.arcanum.mixin.client.render.RenderPipelinesInvoker;
import com.arcanum.mixin.client.render.RenderTypeInvoker;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Büyü ışınları + alev varlıkları için custom {@link RenderType}'lar — 26.x {@code RenderPipeline} +
 * {@code RenderSetup} modeli. 1.21.1'deki "RenderType'ı extend eden trampolin" deseni 26.x'te ÇALIŞMAZ
 * (kurucu private, {@code RenderType.create} paket-özel, {@code RenderPipelines.register} private) →
 * ortak STATİK invoker mixin'leri ({@link RenderTypeInvoker}, {@link RenderPipelinesInvoker}); AW/AT yok.
 *
 * <p><b>26.2 İLERİ-UYUM (res-delta-26.2.md §1 kural 4-6):</b> pipeline/RenderType kurulumu YALNIZ bu dosyada;
 * 26.2'de değişen tek yer {@code RenderPipeline.Builder} çağrıları ({@code withUniform/withSampler} →
 * {@code withBindGroupLayout}, {@code withVertexFormat(fmt, Mode)} → {@code withVertexBinding(0, fmt)} +
 * {@code withPrimitiveTopology}). Derinlik testi {@link DepthStencilState#DEFAULT}'tan TÜRETİLİR (26.2 ters-Z'de
 * kendiliğinden doğru yön; elle {@code LESS_THAN_OR_EQUAL} YAZILMAZ); blend yalnız vanilla
 * {@link BlendFunction} sabitleri; RenderSetup builder'ının tampon-boyutu metodu ÇAĞRILMAZ (26.2'de yok; paylaşılan
 * tampon zaten büyür). Uniform/sampler bildirimleri vanilla snippet'lerinin birebir kopyasıdır (snippet
 * alanları vanilla'da private; Fabric transitive-AW'si / Forge AT'si onları geliştirme ortamında açıyor ama bu
 * loader'a özgü — ortak kod yaslanmaz; 26.2'de {@code MATRICES_PROJECTION_SNIPPET}/{@code FOG_SNIPPET} kalktı).
 *
 * <p>"Solid ışık" görünümünün 4 bileşeni (Iron Man addon analizi) — {@link #SPELL_BEAM}:
 * <ul>
 *   <li>{@code POSITION_COLOR} format → UV/texture YOK, ışın düz renkli (sprite değil)</li>
 *   <li>{@link BlendFunction#LIGHTNING} → additive blend (src_alpha, ONE): üst üste binen
 *       katmanlar TOPLANIR, merkez beyaza doyar = "yanmış parlak çekirdek + renkli hale"
 *       (1.21.1 {@code LIGHTNING_TRANSPARENCY} ile birebir)</li>
 *   <li>{@code withCull(false)} → prizma yüzlerinin ikisi de görünür (kamera içinden geçse bile)</li>
 *   <li>derinliğe YAZMAZ: iç içe glow kabukları birbirini kesmez
 *       (depth TESTİ açık kalır → duvar arkasında ışın görünmez)</li>
 * </ul>
 * Lightmap format'ta hiç yok → ışın dünya ışığından bağımsız her zaman "fullbright". Shader
 * {@code core/position_color}: SİS UYGULAMAZ (1.21.1 {@code POSITION_COLOR_SHADER} ile aynı). Vanilla
 * {@code RenderTypes.lightning()} bunun yerine GEÇMEZ: sis uygular, derinliğe yazar, WEATHER hedefine çizer.
 */
public final class ArcanumRenderTypes {

    private ArcanumRenderTypes() {}

    /** Derinlik TESTİ açık (vanilla varsayılan yönü), derinliğe YAZMA kapalı — 1.21.1 {@code COLOR_WRITE}. */
    private static final DepthStencilState TEST_NO_WRITE =
            new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false);

    // ===================== büyü ışınları (13. tur yıldırım ışınları + düello kenetlenmesi) =====================

    /**
     * Vanilla {@code DEBUG_QUADS} ile aynı şablon ({@code MATRICES_PROJECTION_SNIPPET} uniform'ları +
     * {@code core/position_color} + POSITION_COLOR/QUADS + cull kapalı); farkı additive blend ve derinliğe yazmaması.
     */
    private static final RenderPipeline SPELL_BEAM_PIPELINE = RenderPipelinesInvoker.arcanum$register(
            RenderPipeline.builder()
                    .withLocation(Arcanum.id("pipeline/spell_beam"))
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                    .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                    .withCull(false)
                    .withDepthStencilState(TEST_NO_WRITE)
                    .build());

    /**
     * Büyü ışını / kenetlenme tipi — {@code SubmitNodeCollector.submitCustomGeometry} ile kullanılır
     * (blend'li olduğundan vanilla bunu TRANSLUCENT feature geçişinde çizer; ana hedef, 1.21.1 ile aynı).
     * {@code sortOnUpload} 1.21.1'deki gibi açık.
     */
    public static final RenderType SPELL_BEAM = RenderTypeInvoker.arcanum$create("arcanum:spell_beam",
            RenderSetup.builder(SPELL_BEAM_PIPELINE)
                    .sortOnUpload()
                    .createRenderSetup());

    // ===================== alev varlıkları (Protego Diabolica ejderhası) =====================

    /**
     * Vanilla {@code RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE}'in BİREBİR kopyası ({@code ENTITY_EMISSIVE_SNIPPET}
     * = {@code MATRICES_FOG_LIGHT_DIR_SNIPPET} uniform'ları + {@code core/entity} + Sampler0 + ENTITY/QUADS +
     * {@code EMISSIVE}; üstüne {@code ALPHA_CUTOUT 0.1}, {@code PER_FACE_LIGHTING}, Sampler1 (overlay), cull kapalı,
     * derinliğe yazmaz) — TEK farkı blend: TRANSLUCENT yerine {@link BlendFunction#LIGHTNING}.
     */
    private static final RenderPipeline FLAME_ENTITY_ADDITIVE_PIPELINE = RenderPipelinesInvoker.arcanum$register(
            RenderPipeline.builder()
                    .withLocation(Arcanum.id("pipeline/flame_entity_additive"))
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                    .withUniform("Lighting", UniformType.UNIFORM_BUFFER)
                    .withVertexShader("core/entity")
                    .withFragmentShader("core/entity")
                    .withSampler("Sampler0")
                    .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
                    .withShaderDefine("EMISSIVE")
                    .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withSampler("Sampler1")
                    .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                    .withCull(false)
                    .withDepthStencilState(TEST_NO_WRITE)
                    .build());

    /**
     * "Alev varlığı" PARLAMA katmanı — gövdenin üstüne binen ADDITIVE geçiş.
     * {@link #flameEntity} ile tek farkı {@link BlendFunction#LIGHTNING} (src_alpha, ONE):
     * üst üste binen alev damarları TOPLANIR, çekirdek beyaza doyar — SPELL_BEAM'deki
     * yıldırım ışınlarıyla aynı "yanmış çekirdek + renkli hale" karakteri.
     * Glowmask dokusuyla kullanılır (bkz. {@code DiabolicaDragonGlowLayer}).
     * RenderSetup 1.21.1 ile aynı: doku + OVERLAY + affectsCrumbling + sortOnUpload, outline yok.
     * Doku başına memoize: her karede yeni RenderType üretmek buffer batch'ini bozar.
     */
    private static final Function<Identifier, RenderType> FLAME_ENTITY_ADDITIVE = Util.memoize(
            texture -> RenderTypeInvoker.arcanum$create("arcanum:flame_entity_additive",
                    RenderSetup.builder(FLAME_ENTITY_ADDITIVE_PIPELINE)
                            .withTexture("Sampler0", texture)
                            .useOverlay()
                            .affectsCrumbling()
                            .sortOnUpload()
                            .createRenderSetup()));

    /**
     * "Alev varlığı" GÖVDE katmanı — dokulu, tam parlak, derinliğe yazmayan translucent.
     * <ul>
     *   <li>ENTITY format → UV+normal taşır, GeckoLib modeli normal şekilde dokulanır
     *       (SPELL_BEAM'in {@code POSITION_COLOR} formatı doku taşımadığı için burada kullanılamaz)</li>
     *   <li>emissive shader → lightmap yok sayılır, varlık karanlıkta da tam parlak (ateşin kendi ışığı)</li>
     *   <li>TRANSLUCENT blend → gövde daha aydınlık gökyüzünde bile OKUNUR kalır; ateş parlaması
     *       additive olan {@link #flameEntityAdditive} katmanından gelir</li>
     *   <li>cull kapalı → alev kanatları iki yüzünden de görünür</li>
     *   <li>derinliğe YAZMAZ → iç içe geçen boyun/kuyruk/kanat segmentleri birbirini kesmez,
     *       z-fighting kaynaklı yanıp sönme olmaz (derinlik TESTİ açık → duvar arkasında görünmez)</li>
     * </ul>
     * 1.21.1'deki özel {@code FLAME_ENTITY} tipi vanilla {@code entityTranslucentEmissive(tex, outline=false)} ile
     * BİREBİR aynıydı (emissive shader + TRANSLUCENT + NO_CULL + COLOR_WRITE + OVERLAY + crumbling + sort) →
     * 26.x'te vanilla fabrikası kullanılır (26.1.2 ve 26.2'de aynı imza [javap]; kendisi memoize eder).
     */
    public static RenderType flameEntity(Identifier texture) {
        return RenderTypes.entityTranslucentEmissive(texture, false);
    }

    /** Alev varlığı additive parlama katmanı (glowmask ile). */
    public static RenderType flameEntityAdditive(Identifier texture) {
        return FLAME_ENTITY_ADDITIVE.apply(texture);
    }
    // ===================== GeckoLib parlama katmanı (unicorn / kar baykuşu) =====================

    /**
     * GeckoLib 4.x {@code AutoGlowingTexture.GLOWING_RENDER_TYPE} ("geo_glowing_layer", 1.21.1 kök — javap
     * {@code geckolib-forge-1.21.1-4.9.2} + kaynak {@code geckolib-fabric-1.21.1-4.7.7}) karşılığı:
     * {@code RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE} shader'ı (emissive = lightmap yok, AMA yön/yüz gölgelemesi VAR),
     * {@code translucent_transparency} (= {@link BlendFunction#TRANSLUCENT}), OVERLAY açık, cull VARSAYILAN (açık),
     * yazma maskesi renk+derinlik, crumbling yok, sortOnUpload, outline = parlayan varlık.
     *
     * <p>GeckoLib 5'in kendi {@code geckolib_emissive} pipeline'ı {@code NO_CARDINAL_LIGHTING} tanımlıyor → parlama
     * yüzleri yön gölgelemesi almıyor, 1.21.1'e göre ~%32 daha parlak (baykuş göz turuncusu 1.21.1'de 189,107,0 —
     * dokuda 250,142,0 → ×0.756 yüz gölgesi; GL5'te 250,142,0). Bu pipeline vanilla
     * {@code ENTITY_TRANSLUCENT_EMISSIVE} şablonudur ({@code PER_FACE_LIGHTING}, {@code ALPHA_CUTOUT 0.1}, Sampler1
     * overlay) — farkı GL4 ile aynı olan cull açık + derinliğe yazma.
     */
    private static final RenderPipeline GEO_GLOW_LAYER_PIPELINE = RenderPipelinesInvoker.arcanum$register(
            RenderPipeline.builder()
                    .withLocation(Arcanum.id("pipeline/geo_glowing_layer"))
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                    .withUniform("Lighting", UniformType.UNIFORM_BUFFER)
                    .withVertexShader("core/entity")
                    .withFragmentShader("core/entity")
                    .withSampler("Sampler0")
                    .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
                    .withShaderDefine("EMISSIVE")
                    .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withSampler("Sampler1")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(true)
                    .withDepthStencilState(DepthStencilState.DEFAULT)
                    .build());

    private static final BiFunction<Identifier, Boolean, RenderType> GEO_GLOW_LAYER = Util.memoize(
            (texture, outline) -> RenderTypeInvoker.arcanum$create("arcanum:geo_glowing_layer",
                    RenderSetup.builder(GEO_GLOW_LAYER_PIPELINE)
                            .withTexture("Sampler0", texture)
                            .useOverlay()
                            .setOutline(outline ? RenderSetup.OutlineProperty.AFFECTS_OUTLINE
                                    : RenderSetup.OutlineProperty.NONE)
                            .sortOnUpload()
                            .createRenderSetup()));

    /** GeckoLib 4 "geo_glowing_layer" eşdeğeri (bkz. {@code GeckoLib4GlowLayer}); {@code outline} = parlayan varlık. */
    public static RenderType geoGlowLayer(Identifier texture, boolean outline) {
        return GEO_GLOW_LAYER.apply(texture, outline);
    }
}
