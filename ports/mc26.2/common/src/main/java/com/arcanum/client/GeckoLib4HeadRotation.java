package com.arcanum.client;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.util.Mth;

/**
 * GeckoLib 4 {@code DefaultedEntityGeoModel(id, true)} kafa çevirme anlamının GeckoLib 5'te BİREBİR kurulumu.
 *
 * <p>GL4 (4.7.7, kaynak jar): {@code GeoEntityRenderer} → {@code new EntityModelData(sit, child, -netHeadYaw, -headPitch)};
 * {@code DefaultedEntityGeoModel#setCustomAnimations} → {@code head.setRotX(headPitch * DEG_TO_RAD)},
 * {@code head.setRotY(netHeadYaw * DEG_TO_RAD)}. GL4 {@code GeoBone} dönüşü MUTLAKTIR (dinlenme dönüşü
 * {@code initialSnapshot} içinde; animasyonlar {@code anim + initial} yazar) → kafa X/Y = −pitch / −yaw, geo.json'daki
 * dinlenme dönüşü EZİLİR.
 *
 * <p>GL5 (5.5.2, vineflower): {@code DefaultAnimations#hardcodedHeadRotation} snapshot'a −pitch / −yaw yazar, ama
 * {@code RenderUtil#translateAndRotateMatrixForBone} çizimde {@code baseRot + snapshot} uygular → dinlenme dönüşü
 * ÜSTÜNE EKLENİR. İşaret iki sürümde AYNI (ikisi de negatif); fark yalnız dinlenme dönüşü sıfır olmayan kafa
 * kemiklerinde görünür (Arcanum: yalnız basilisk {@code head} = [24,0,0] → port kafası 24° eğik çiziliyordu).
 *
 * <p>Burada snapshot = (−pitch − baseRot), çizim = baseRot + snapshot = −pitch → GL4'ün mutlak değeri. Z ekseni ve
 * öteleme GL4'te de dokunulmuyordu (animasyon değeri kalır). Kaynak açılar GL5 {@code DataTickets.ENTITY_PITCH/YAW}
 * = {@code LivingEntityRenderState.xRot/yRot} (GL4 ile aynı lerp; yRot {@code wrapDegrees}'li — 360° denk).
 */
public final class GeckoLib4HeadRotation {
    private GeckoLib4HeadRotation() {}

    public static <R extends GeoRenderState> void apply(RenderPassInfo<R> info, BoneSnapshots snapshots, String headBone) {
        snapshots.get(headBone).ifPresent(snapshot -> {
            GeoBone bone = snapshot.getBone();
            float pitch = info.getOrDefaultGeckolibData(DataTickets.ENTITY_PITCH, 0.0F);
            float yaw = info.getOrDefaultGeckolibData(DataTickets.ENTITY_YAW, 0.0F);
            snapshot.setRotX(-pitch * Mth.DEG_TO_RAD - bone.baseRotX());
            snapshot.setRotY(-yaw * Mth.DEG_TO_RAD - bone.baseRotY());
        });
    }
}
