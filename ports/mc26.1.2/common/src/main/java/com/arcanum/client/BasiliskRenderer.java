package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.BasiliskEntity;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Basilisk BOSS renderer. GeckoLib geo/animation/texture "basilisk" adıyla üretiliyor.
 * Kafa bonu oyuncuyu takip eder (netHeadYaw/headPitch uygulanır — GeckoLib 4 MUTLAK anlamıyla,
 * {@link GeckoLib4HeadRotation}: kafa kemiğinin geo.json dinlenme dönüşü [24,0,0] 1.21.1'deki gibi ezilir;
 * GL5 {@code hardcodedHeadRotation} onu üstüne ekleyip kafayı 24° eğik çiziyordu); eski tek-argümanlı hâlde headBone=null'dı,
 * bu yüzden yılan saldırırken yana/geriye bakıyordu.
 */
public class BasiliskRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<BasiliskEntity, R> {
    public BasiliskRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Arcanum.id("basilisk")));
        this.shadowRadius = 1.6f;
    }

    @Override
    public void scaleModelForRender(RenderPassInfo<R> info, float widthScale, float heightScale) {
        // Boss daha iri/heybetli görünsün — modeli %40 büyüt (yeniden model işlemeden).
        super.scaleModelForRender(info, widthScale * 1.4f, heightScale * 1.4f);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> info, BoneSnapshots snapshots) {
        GeckoLib4HeadRotation.apply(info, snapshots, "head");
    }
}
