package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.item.WandItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Asa için GeckoLib item renderer'ı. Animasyon paylaşımlıdır
 * (animations/item/wand.animation.json); texture kademeye göre değişir
 * (textures/item/&lt;assetId&gt;.png + _glowmask emissive). Geometri de
 * varsayılan olarak paylaşımlıdır (geo/item/wand.geo.json) — özel modele
 * sahip kademeler kendi geo dosyasını kullanır (arcanewood: topluluk
 * sanatçısı modeli; troll: Meshy "Crimson Rail Wand" küp uyarlaması;
 * phoenix: Kasu'nun wand_lord bbmodel dönüşümü; elder: wand_eldergecko
 * bbmodel dönüşümü; thunderbird: el yapımı "Storm Spire"). Kemik adları
 * her modelde wand_root + tip aynı olduğundan paylaşımlı animasyon
 * hepsini değiştirmeden sürer.
 */
public class WandItemRenderer extends GeoItemRenderer<WandItem> {
    public WandItemRenderer(String assetId) {
        super(buildModel(assetId));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    private static DefaultedItemGeoModel<WandItem> buildModel(String assetId) {
        DefaultedItemGeoModel<WandItem> model = new DefaultedItemGeoModel<WandItem>(
                new ResourceLocation(Arcanum.MODID, "wand"))
                .withAltTexture(new ResourceLocation(Arcanum.MODID, assetId));
        String altModel = null;
        if ("arcanewood_wand".equals(assetId)) {
            altModel = "wand_arcanewood";
        } else if ("troll_wand".equals(assetId)) {
            altModel = "wand_troll";
        } else if ("phoenix_wand".equals(assetId)) {
            altModel = "wand_phoenix";
        } else if ("elder_wand".equals(assetId)) {
            altModel = "wand_elder";
        } else if ("thunderbird_wand".equals(assetId)) {
            altModel = "wand_thunderbird";
        }
        if (altModel != null) {
            // withAltModel yalnızca geo yolunu değiştirir (geo/item/<altModel>.geo.json);
            // animasyon "wand" olarak kalır, texture mantığına dokunmaz.
            model = model.withAltModel(
                    new ResourceLocation(Arcanum.MODID, altModel));
        }
        return model;
    }
}
