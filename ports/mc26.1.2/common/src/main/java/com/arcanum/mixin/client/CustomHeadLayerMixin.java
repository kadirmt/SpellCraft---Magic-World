package com.arcanum.mixin.client;

import com.arcanum.client.duck.ArcanumRenderStateFlags;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Görünmezlik Pelerini giyen VEYA Umbravolo kara duman formundaki varlık için
 * kafaya giyilen blok/kafatası/oyuncu-kafası katmanını (CustomHeadLayer) iptal
 * eder. Vanilla INVISIBILITY bu katmanı gizlemez — bu mixin olmadan formda kabak
 * takan oyuncunun kafası duman bulutunda görünür süzülürdü (HumanoidArmorLayerMixin
 * ile aynı kapsam boşluğu; pelerin CHEST slotunda olduğundan pelerin için asıl
 * kritik boşluk buydu).
 *
 * <p>26.x: katman entity görmez; koşul extract anında {@link LivingEntityRendererMixin}
 * tarafından render-state'e yazılır ({@link ArcanumRenderStateFlags}). Bayrak
 * LivingEntityRenderState seviyesinde olduğundan kökteki gibi HER canlı kapsanır.
 *
 * <p>Hedef metod [javap 26.1.2 — Fabric merged jar ve Forge 64.1.3 yamalı jar'da AYNI]:
 *   {@code CustomHeadLayer#submit(PoseStack, SubmitNodeCollector, int, S, float, float)}
 *   descriptor: {@code (Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V}
 * S generic parametresi LivingEntityRenderState'e erasure olur; explicit descriptor ile gerçek
 * metod hedeflenir ({@code EntityRenderState} alan sentetik köprü metodu DEĞİL).
 */
@Mixin(CustomHeadLayer.class)
public abstract class CustomHeadLayerMixin {

    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void arcanum$hideHeadWhenCloaked(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                             int lightCoords, LivingEntityRenderState state, float yRot, float xRot,
                                             CallbackInfo ci) {
        if (ArcanumRenderStateFlags.shouldHideLayers(state)) {
            ci.cancel();
        }
    }
}
