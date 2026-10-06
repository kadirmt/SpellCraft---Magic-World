package com.arcanum.fabric.client;

import com.arcanum.Arcanum;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.vehicle.Boat;

/**
 * Arcanewood bot renderer'ı — vanilla Boat.Type kapalı enum olduğu için
 * BoatRenderer'ın kendi varyant haritası kullanılamıyor; bunun yerine
 * mevcut (herhangi bir) vanilla bot model katmanı (geometri tüm tiplerde
 * bambu hariç ortak) OAK üzerinden bake edilip sabit kendi dokumuzla
 * render ediliyor.
 */
public class ArcanewoodBoatRenderer extends EntityRenderer<Boat> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Arcanum.MODID, "textures/entity/boat/arcanewood.png");

    protected BoatModel model;

    public ArcanewoodBoatRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.8f;
        this.model = new BoatModel(context.bakeLayer(ModelLayers.createBoatModelName(Boat.Type.OAK)));
    }

    @Override
    public ResourceLocation getTextureLocation(Boat entity) {
        return TEXTURE;
    }

    @Override
    public void render(Boat entity, float yaw, float partialTick, PoseStack poseStack,
                        MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0, 0.375, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));

        this.model.setupAnim(entity, 0.0f, 0.0f, entity.tickCount + partialTick, 0.0f, 0.0f);
        VertexConsumer vertexConsumer = buffer.getBuffer(this.model.renderType(getTextureLocation(entity)));
        // 1.20.1: renderToBuffer r,g,b,a ister — beyaz/opak (1,1,1,1) = 1.21.1'deki varsayılan renkle birebir aynı görünüm
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }
}
