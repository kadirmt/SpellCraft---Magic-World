package com.arcanum.mixin.client;

import com.arcanum.client.duck.ArcanumRenderStateFlags;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Görünmezlik Pelerini giyen varlık için zırh render katmanını tamamen iptal eder.
 * Böylece giyilen HİÇBİR zırh parçası (pelerinin kendi göğüslük modeli dahil, CHEST
 * slotu) çizilmez. Oyuncu gövdesi zaten vanilla INVISIBILITY efekti ile gizli olduğundan
 * bu katmanın kesilmesiyle TAM görünmezlik elde edilir.
 *
 * <p>26.x: katman entity görmez; koşul extract anında {@link LivingEntityRendererMixin}
 * tarafından render-state'e yazılır ({@link ArcanumRenderStateFlags}).
 *
 * <p>Hedef metod [javap 26.1.2 — Fabric merged jar ve Forge 64.1.3 yamalı jar'da AYNI]:
 *   {@code HumanoidArmorLayer#submit(PoseStack, SubmitNodeCollector, int, S, float, float)}
 *   descriptor: {@code (Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V}
 * S generic parametresi HumanoidRenderState'e erasure olur; explicit descriptor ile gerçek
 * metod hedeflenir ({@code EntityRenderState} alan sentetik köprü metodu DEĞİL).
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void arcanum$hideArmorWhenCloaked(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                              int lightCoords, HumanoidRenderState state, float yRot, float xRot,
                                              CallbackInfo ci) {
        // Pelerin giyiliyse VEYA Umbravolo kara duman formundaysa (ClientUmbraForms
        // sunucu-senkronlu set) zırh katmanı tamamen kesilir.
        if (ArcanumRenderStateFlags.shouldHideLayers(state)) {
            ci.cancel();
        }
    }
}
