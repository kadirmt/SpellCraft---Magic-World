package com.arcanum.fabric.client;

import com.arcanum.entity.BroomEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Broom GeckoLib renderer'ı — geo/doku/animasyon seçimi kademeye göre
 * {@link BroomModel}'de (Oakshaft kendi geo'su, Comet recolor doku, Cleansweep
 * mevcut broom asset'leri). Uçuş hissi için gövdeyi binicinin bakış pitch'iyle
 * eğer (yumuşatılmış xRot BroomEntity.tickRidden'da ayarlanır); vanilla
 * Phantom ile aynı işaret düzeni.
 */
public class BroomRenderer extends GeoEntityRenderer<BroomEntity> {
    public BroomRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new BroomModel());
        this.shadowRadius = 0.4f;
    }

    // 1.20.1 PORT: GeckoLib 4.8.4'te applyRotations 5 parametrelidir (nativeScale yok).
    @Override
    protected void applyRotations(BroomEntity entity, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTick) {
        super.applyRotations(entity, poseStack, ageInTicks, rotationYaw, partialTick);
        // burun yukarı/aşağı eğimi — model önü -Z, 180° yaw sonrası +X ekseni pitch'i.
        // Dönme merkezi sap yüksekliği (~0.4) olsun ki süpürge yerinde eğilsin.
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        poseStack.translate(0.0f, 0.4f, 0.0f);
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.translate(0.0f, -0.4f, 0.0f);
    }
}
