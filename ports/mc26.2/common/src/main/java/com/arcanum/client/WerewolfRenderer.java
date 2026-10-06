package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.WerewolfEntity;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class WerewolfRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<WerewolfEntity, R> {
    public WerewolfRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Arcanum.id("werewolf")));
        this.shadowRadius = 0.5f;
    }
}
