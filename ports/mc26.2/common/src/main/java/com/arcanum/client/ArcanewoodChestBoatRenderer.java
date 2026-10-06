package com.arcanum.client;

import com.arcanum.Arcanum;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

/** Arcanewood sandıklı bot renderer'ı — bkz. {@link ArcanewoodBoatRenderer} (OAK sandıklı bot katmanı + kendi dokumuz). */
public class ArcanewoodChestBoatRenderer extends ArcanewoodBoatRenderer {
    private static final Identifier TEXTURE = Arcanum.id("textures/entity/chest_boat/arcanewood.png");

    public ArcanewoodChestBoatRenderer(EntityRendererProvider.Context context) {
        super(context, ModelLayers.OAK_CHEST_BOAT, TEXTURE);
    }
}
