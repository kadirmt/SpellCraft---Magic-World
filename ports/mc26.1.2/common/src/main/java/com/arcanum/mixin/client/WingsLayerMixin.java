package com.arcanum.mixin.client;

import com.arcanum.client.duck.ArcanumRenderStateFlags;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (Kökteki {@code ElytraLayerMixin}; 1.21.2'de {@code ElytraLayer} → {@code WingsLayer} oldu.)
 * Görünmezlik Pelerini giyen VEYA Umbravolo kara duman formundaki varlık için
 * elytra kanat katmanını iptal eder. Vanilla INVISIBILITY yalnız gövde modelini
 * gizler; WingsLayer görünmezlik kontrolü İÇERMEZ — bu mixin olmadan formda
 * elytra takan oyuncunun kanatları duman bulutunda "uçan kanat" olarak görünürdü
 * (HumanoidArmorLayerMixin ile aynı kapsam boşluğu).
 *
 * <p>26.x: katman entity görmez; koşul extract anında {@link LivingEntityRendererMixin}
 * tarafından render-state'e yazılır ({@link ArcanumRenderStateFlags}).
 *
 * <p>Hedef metod [javap 26.1.2 — Fabric merged jar ve Forge 64.1.3 yamalı jar'da AYNI]:
 *   {@code WingsLayer#submit(PoseStack, SubmitNodeCollector, int, S, float, float)}
 *   descriptor: {@code (Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V}
 * S generic parametresi HumanoidRenderState'e erasure olur; explicit descriptor ile gerçek
 * metod hedeflenir ({@code EntityRenderState} alan sentetik köprü metodu DEĞİL).
 */
@Mixin(WingsLayer.class)
public abstract class WingsLayerMixin {

    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void arcanum$hideElytraWhenCloaked(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                               int lightCoords, HumanoidRenderState state, float yRot, float xRot,
                                               CallbackInfo ci) {
        if (ArcanumRenderStateFlags.shouldHideLayers(state)) {
            ci.cancel();
        }
    }
}
