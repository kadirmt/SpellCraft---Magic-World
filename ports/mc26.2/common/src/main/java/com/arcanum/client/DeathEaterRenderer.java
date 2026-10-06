package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.DeathEaterEntity;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * DeathEater GeckoLib renderer'ı — geckolib/models/entity/death_eater.geo.json + textures/entity/death_eater.png.
 * Kafa bonu bakış yönünü takip eder (GeckoLib 4'teki {@code DefaultedEntityGeoModel(id, true)} karşılığı).
 */
public class DeathEaterRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<DeathEaterEntity, R> {
    public DeathEaterRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Arcanum.id("death_eater")));
        this.shadowRadius = 0.5f;
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> info, BoneSnapshots snapshots) {
        // "head" kemiği: X = -pitch, Y = -netHeadYaw (GeckoLib 4 turnsHead ile aynı işaret/kemik)
        GeckoLib4HeadRotation.apply(info, snapshots, "head");
    }
}
