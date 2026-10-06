package com.arcanum.mixin.client;

import com.arcanum.client.duck.ArcanumRenderStateFlags;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Görünmezlik Pelerini giyen varlık için "eldeki eşya" render katmanını iptal eder.
 * Böylece pelerin giyiliyken oyuncunun elinde tuttuğu eşya (asa, kılıç, meşale vb.)
 * çizilmez ve TAM görünmezlik korunur.
 *
 * <p>26.x: katman entity görmez; koşul extract anında {@link LivingEntityRendererMixin}
 * tarafından render-state'e yazılır ({@link ArcanumRenderStateFlags}).
 * {@code PlayerItemInHandLayer} (oyuncu) {@code submit}'i override ETMEZ (yalnız
 * {@code submitArmWithItem}) → bu kanca oyuncuyu da kapsar.
 *
 * <p>Hedef metod [javap 26.1.2 — Fabric merged jar ve Forge 64.1.3 yamalı jar'da AYNI]:
 *   {@code ItemInHandLayer#submit(PoseStack, SubmitNodeCollector, int, S, float, float)}
 *   descriptor: {@code (Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V}
 * S generic parametresi ArmedEntityRenderState'e erasure olur; explicit descriptor ile gerçek
 * metod hedeflenir ({@code EntityRenderState} alan sentetik köprü metodu DEĞİL).
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void arcanum$hideHeldItemWhenCloaked(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                                 int lightCoords, ArmedEntityRenderState state, float yRot, float xRot,
                                                 CallbackInfo ci) {
        // Pelerin giyiliyse VEYA Umbravolo kara duman formundaysa (ClientUmbraForms
        // sunucu-senkronlu set) eldeki eşya (asa dahil) çizilmez.
        if (ArcanumRenderStateFlags.shouldHideLayers(state)) {
            ci.cancel();
        }
    }
}
