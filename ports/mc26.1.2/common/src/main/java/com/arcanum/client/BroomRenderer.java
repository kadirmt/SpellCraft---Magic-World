package com.arcanum.client;

import com.arcanum.entity.BroomEntity;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * Broom GeckoLib renderer'ı — geo/doku/animasyon seçimi kademeye göre
 * {@link BroomModel}'de (Oakshaft kendi geo'su, Comet recolor doku, Cleansweep
 * mevcut broom asset'leri). Uçuş hissi için gövdeyi binicinin bakış pitch'iyle
 * eğer (yumuşatılmış xRot BroomEntity.tickRidden'da ayarlanır); vanilla
 * Phantom ile aynı işaret düzeni.
 * <p>
 * GeckoLib 5: render aşamasında entity yok → lerp'li pitch {@link #addRenderData}
 * (çıkarım aşaması) içinde {@link #PITCH} biletine yazılır, {@link #applyRotations}'ta okunur.
 */
public class BroomRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<BroomEntity, R> {

    /** Kökteki {@code Mth.lerp(partialTick, xRotO, getXRot())} değeri. */
    public static final DataTicket<Float> PITCH = DataTicket.create("arcanum_broom_pitch", Float.class);

    public BroomRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new BroomModel());
        this.shadowRadius = 0.4f;
    }

    @Override
    public void addRenderData(BroomEntity entity, Void relatedObject, R renderState, float partialTick) {
        super.addRenderData(entity, relatedObject, renderState, partialTick);
        renderState.addGeckolibData(PITCH, Mth.lerp(partialTick, entity.xRotO, entity.getXRot()));
    }

    @Override
    protected void applyRotations(RenderPassInfo<R> info, PoseStack poseStack, float nativeScale) {
        super.applyRotations(info, poseStack, nativeScale);
        // burun yukarı/aşağı eğimi — model önü -Z, 180° yaw sonrası +X ekseni pitch'i.
        // Dönme merkezi sap yüksekliği (~0.4) olsun ki süpürge yerinde eğilsin.
        float pitch = info.getOrDefaultGeckolibData(PITCH, 0.0f);
        poseStack.translate(0.0f, 0.4f, 0.0f);
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.translate(0.0f, -0.4f, 0.0f);
    }
}
