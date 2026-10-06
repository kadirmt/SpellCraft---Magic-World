package com.arcanum.mixin.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Ortak STATİK invoker: {@code private static RenderPipeline RenderPipelines#register(RenderPipeline)}
 * [javap: 26.1.2 ve 26.2 merged jar'ında AYNI imza; Forge 64.1.3 dev jar'ında da private].
 *
 * <p>Vanilla {@code register} yalnız pipeline'ı {@code PIPELINES_BY_LOCATION}'a koyar; {@code ShaderManager.apply}
 * her kaynak yeniden yüklemesinde bu listedeki pipeline'ları ÖN-DERLER (kayıtsız pipeline ilk kullanımda tembel
 * derlenir — o da çalışır, ama F3+T sonrası vanilla ile aynı yaşam döngüsü için kaydediyoruz; GeckoLib de öyle yapar).
 * Yalnız {@code com.arcanum.client.beam.ArcanumRenderTypes} kullanır, yalnız render thread'inde (sınıf ilk kez
 * bir büyü ışını/alev katmanı çizilirken yüklenir). Mixin config'te {@code client} listesinde olmalı.
 */
@Mixin(RenderPipelines.class)
public interface RenderPipelinesInvoker {
    @Invoker("register")
    static RenderPipeline arcanum$register(RenderPipeline pipeline) {
        throw new AssertionError("RenderPipelinesInvoker mixin'i uygulanmadı (arcanum.mixins.json client listesi)");
    }
}
