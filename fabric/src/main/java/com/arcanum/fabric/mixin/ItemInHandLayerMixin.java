package com.arcanum.fabric.mixin;

import com.arcanum.item.CloakOfInvisibilityItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Görünmezlik Pelerini giyen varlık için "eldeki eşya" render katmanını iptal eder.
 * Böylece pelerin giyiliyken oyuncunun elinde tuttuğu eşya (asa, kılıç, meşale vb.)
 * çizilmez ve TAM görünmezlik korunur.
 *
 * Hedef metod (javap ile doğrulandı):
 *   ItemInHandLayer#render(PoseStack, MultiBufferSource, int, T, float x6)
 *   descriptor: (Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V
 * T generic parametresi LivingEntity'ye erasure olur; explicit LivingEntity descriptor'ı
 * ile gerçek metod hedeflenir (sentetik Entity köprü metodu DEĞİL).
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void arcanum$hideHeldItemWhenCloaked(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                                 LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                 float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
                                                 CallbackInfo ci) {
        // Pelerin giyiliyse VEYA Umbravolo kara duman formundaysa (ClientUmbraForms
        // sunucu-senkronlu set) eldeki eşya (asa dahil) çizilmez.
        if (CloakOfInvisibilityItem.isWearing(entity)
                || com.arcanum.fabric.client.ClientUmbraForms.has(entity.getId())) {
            ci.cancel();
        }
    }
}
