package com.arcanum.forge.client.render.renderer;

import com.arcanum.Arcanum;
import net.minecraft.client.model.ChestBoatModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.vehicle.Boat;

/** Arcanewood sandıklı bot renderer'ı — bkz. {@link ArcanewoodBoatRenderer}. */
public class ArcanewoodChestBoatRenderer extends ArcanewoodBoatRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Arcanum.MODID, "textures/entity/chest_boat/arcanewood.png");

    public ArcanewoodChestBoatRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new ChestBoatModel(context.bakeLayer(ModelLayers.createChestBoatModelName(Boat.Type.OAK)));
    }

    @Override
    public ResourceLocation getTextureLocation(Boat entity) {
        return TEXTURE;
    }
}
