package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.BowtruckleEntity;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Bowtruckle GeckoLib renderer'ı — geckolib/models/entity/bowtruckle.geo.json + textures/entity/bowtruckle.png.
 * Yavrular %50 ölçekte çizilir (scaleModelForRender kancası; bebeklik render-state'ten okunur).
 * Kafa bonu bakış yönünü takip eder.
 */
public class BowtruckleRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<BowtruckleEntity, R> {
    public BowtruckleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Arcanum.id("bowtruckle")));
        this.shadowRadius = 0.2f;
    }

    @Override
    public void scaleModelForRender(RenderPassInfo<R> info, float widthScale, float heightScale) {
        // Yavru bowtruckle yarı boy (LivingEntityRenderState.isBaby = entity.isBaby(), GeckoLib doldurur)
        float babyScale = info.renderState().isBaby ? 0.5f : 1.0f;
        super.scaleModelForRender(info, widthScale * babyScale, heightScale * babyScale);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> info, BoneSnapshots snapshots) {
        GeckoLib4HeadRotation.apply(info, snapshots, "head");
    }
}
