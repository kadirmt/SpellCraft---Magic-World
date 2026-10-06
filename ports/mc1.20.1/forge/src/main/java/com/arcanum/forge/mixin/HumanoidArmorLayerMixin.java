package com.arcanum.forge.mixin;

import com.arcanum.forge.client.state.ClientUmbraForms;
import com.arcanum.item.CloakOfInvisibilityItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
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
 * <p><b>FORGE'DA KRİTİK:</b> ModArmorMaterials getName()='arcanum:cloak_of_invisibility' —
 * vanilla zırh katmanı bu isimden doku yolu kurmaya kalkarsa ResourceLocationException
 * fırlatır (F3 denetim uyarısı). Bu mixin katmanı pelerinli varlıkta HEAD'de kestiği için
 * o doku yolu hiç kurulmaz; mixin olmadan pelerin giyen HERKESTE crash olur. Fabric'teki
 * {@code com.arcanum.fabric.mixin.HumanoidArmorLayerMixin}'in birebir taşıması — hedef
 * vanilla sınıfı olduğundan aynen çalışır (Forge 1.20.1 jar'ında javap ile doğrulandı:
 * generic {@code render(..., T, ...)} LivingEntity'ye erasure olan metod duruyor; Forge'un
 * eklediği ayrı {@code (..., Entity, ...)} köprüsü DEĞİL bu metod hedefleniyor).
 *
 * Hedef metod (javap ile doğrulandı):
 *   HumanoidArmorLayer#render(PoseStack, MultiBufferSource, int, T, float x6)
 *   descriptor: (Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V
 * T generic parametresi LivingEntity'ye erasure olur; explicit LivingEntity descriptor'ı
 * ile gerçek metod hedeflenir (sentetik Entity köprü metodu DEĞİL).
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void arcanum$hideArmorWhenCloaked(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                              LivingEntity entity, float limbSwing, float limbSwingAmount,
                                              float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
                                              CallbackInfo ci) {
        // Pelerin VEYA Umbravolo kara duman formu (sunucudan senkronlanan entity-id seti)
        if (CloakOfInvisibilityItem.isWearing(entity) || ClientUmbraForms.has(entity.getId())) {
            ci.cancel();
        }
    }
}
