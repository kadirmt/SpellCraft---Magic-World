package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.MooncalfEntity;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Mooncalf GeckoLib renderer'ı — geckolib/models/entity/mooncalf.geo.json + textures/entity/mooncalf.png.
 * Yavrular %50 ölçekte çizilir (scaleModelForRender kancası).
 * <p>
 * GeckoLib 5: {@code DefaultedEntityGeoModel(id, true)} (kafa çevirme) kurucusu kalktı →
 * aynı davranış {@link #adjustModelBonesForRender} içinde "head" kemiğine uygulanır.
 */
public class MooncalfRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<MooncalfEntity, R> {
    public MooncalfRenderer(EntityRendererProvider.Context ctx) {
        // DefaultedEntityGeoModel "entity/" alt yolunu kendisi ekler → arcanum:entity/mooncalf
        super(ctx, new DefaultedEntityGeoModel<>(Arcanum.id("mooncalf")));
        this.shadowRadius = 0.4f;
    }

    @Override
    public void scaleModelForRender(RenderPassInfo<R> info, float widthScale, float heightScale) {
        // Yavru mooncalf yarı boy (GeckoLib yalnız SCALE özniteliğini uygular, yaş ölçeğini değil)
        float babyScale = info.renderState().isBaby ? 0.5f : 1.0f;
        super.scaleModelForRender(info, widthScale * babyScale, heightScale * babyScale);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> info, BoneSnapshots snapshots) {
        // Eski "turnsHead=true": head kemiği bakış yönüne döner
        GeckoLib4HeadRotation.apply(info, snapshots, "head");
    }
}
