package com.arcanum.fabric.mixin;

import com.arcanum.item.CloakOfInvisibilityItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Görünmezlik Pelerini giyen VEYA Umbravolo kara duman formundaki varlık için
 * elytra kanat katmanını iptal eder. Vanilla INVISIBILITY yalnız gövde modelini
 * gizler; ElytraLayer görünmezlik kontrolü İÇERMEZ — bu mixin olmadan formda
 * elytra takan oyuncunun kanatları duman bulutunda "uçan kanat" olarak görünürdü
 * (HumanoidArmorLayerMixin ile aynı kapsam boşluğu).
 *
 * Hedef metod (javap ile doğrulandı, 1.21.1):
 *   ElytraLayer#render(PoseStack, MultiBufferSource, int, T, float x6)
 *   descriptor: (Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V
 * T generic parametresi LivingEntity'ye erasure olur; explicit LivingEntity descriptor'ı
 * ile gerçek metod hedeflenir (sentetik Entity köprü metodu DEĞİL).
 */
@Mixin(ElytraLayer.class)
public abstract class ElytraLayerMixin {

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void arcanum$hideElytraWhenCloaked(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                               LivingEntity entity, float limbSwing, float limbSwingAmount,
                                               float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
                                               CallbackInfo ci) {
        if (CloakOfInvisibilityItem.isWearing(entity)
                || com.arcanum.fabric.client.ClientUmbraForms.has(entity.getId())) {
            ci.cancel();
        }
    }
}
