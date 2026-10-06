package com.arcanum.forge.mixin;

import com.arcanum.forge.client.state.ClientUmbraForms;
import com.arcanum.item.CloakOfInvisibilityItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.LivingEntity;
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
 * Forge 1.21.1 portu: kök fabric mixin'inin birebir kopyası; remap = false zorunlu
 * (bkz. HumanoidArmorLayerMixin notu).
 *
 * Hedef metod (javap ile doğrulandı, 1.21.1):
 *   CustomHeadLayer#render(PoseStack, MultiBufferSource, int, T, float x6)
 *   descriptor: (Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V
 * T generic parametresi LivingEntity'ye erasure olur; explicit LivingEntity descriptor'ı
 * ile gerçek metod hedeflenir (sentetik Entity köprü metodu DEĞİL).
 */
@Mixin(CustomHeadLayer.class)
public abstract class CustomHeadLayerMixin {

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void arcanum$hideHeadWhenCloaked(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                             LivingEntity entity, float limbSwing, float limbSwingAmount,
                                             float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
                                             CallbackInfo ci) {
        if (CloakOfInvisibilityItem.isWearing(entity) || ClientUmbraForms.has(entity.getId())) {
            ci.cancel();
        }
    }
}
