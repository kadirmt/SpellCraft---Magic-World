package com.arcanum.mixin.client.render;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Ortak STATİK invoker: {@code static RenderType RenderType#create(String, RenderSetup)} — vanilla'da
 * PAKET-ÖZEL [javap: 26.1.2 ve 26.2 merged jar'ında AYNI imza]. Kurucu da private olduğundan 1.21.1'deki
 * "RenderType'ı extend eden trampolin" deseni artık çalışmaz.
 *
 * <p>Fabric'in transitive-AW'si ve Forge'un AT'si bu metodu geliştirme ortamında public yapıyor; ortak kod
 * ikisine de YASLANMAZ (ARCHITECTURE §3: loader API'si yerine TEK mekanizma, iki loader'da aynı davranış).
 * Yalnız {@code com.arcanum.client.beam.ArcanumRenderTypes} kullanır. Mixin config'te {@code client} listesinde
 * olmalı (hedef istemci-özel sınıf).
 */
@Mixin(RenderType.class)
public interface RenderTypeInvoker {
    @Invoker("create")
    static RenderType arcanum$create(String name, RenderSetup setup) {
        throw new AssertionError("RenderTypeInvoker mixin'i uygulanmadı (arcanum.mixins.json client listesi)");
    }
}
