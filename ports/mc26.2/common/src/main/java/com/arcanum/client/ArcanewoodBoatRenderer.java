package com.arcanum.client;

import com.arcanum.Arcanum;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.boat.BoatModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractBoatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

/**
 * Arcanewood bot renderer'ı — mevcut vanilla bot model katmanı (geometri tüm
 * ahşap tiplerinde bambu hariç ortak) OAK üzerinden bake edilip sabit kendi
 * dokumuzla render ediliyor.
 * <p>
 * 26.x: vanilla {@code BoatRenderer} dokuyu model katmanı kimliğinden türettiği için
 * (OAK katmanı → oak dokusu) doğrudan kullanılamıyor; bunun yerine
 * {@link AbstractBoatRenderer} (poz/sallanma/kürek render-state'i vanilla ile aynı)
 * + OAK katmanı + kendi dokumuz. Böylece ek model katmanı KAYDI gerekmez.
 * Su yaması (tekne içini sudan maskeleyen katman) vanilla botlardaki gibi çizilir.
 */
public class ArcanewoodBoatRenderer extends AbstractBoatRenderer {
    private static final Identifier TEXTURE = Arcanum.id("textures/entity/boat/arcanewood.png");

    private final Model.Simple waterPatchModel;
    private final EntityModel<BoatRenderState> model;

    public ArcanewoodBoatRenderer(EntityRendererProvider.Context context) {
        this(context, ModelLayers.OAK_BOAT, TEXTURE);
    }

    protected ArcanewoodBoatRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer,
                                     Identifier texture) {
        super(context, texture);
        this.shadowRadius = 0.8f;
        this.waterPatchModel = new Model.Simple(context.bakeLayer(ModelLayers.BOAT_WATER_PATCH),
                t -> RenderTypes.waterMask());
        this.model = new BoatModel(context.bakeLayer(layer));
    }

    @Override
    protected EntityModel<BoatRenderState> model() {
        return this.model;
    }

    @Override
    protected void submitTypeAdditions(BoatRenderState state, PoseStack poseStack,
                                       SubmitNodeCollector submitNodeCollector, int lightCoords) {
        if (!state.isUnderWater) {
            submitNodeCollector.submitModel(this.waterPatchModel, Unit.INSTANCE, poseStack, this.texture,
                    lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        }
    }
}
