package com.arcanum.fabric.client;

import com.arcanum.Arcanum;
import com.arcanum.entity.WerewolfEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class WerewolfRenderer extends GeoEntityRenderer<WerewolfEntity> {
    public WerewolfRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(
                ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "werewolf")));
        this.shadowRadius = 0.5f;
    }
}
