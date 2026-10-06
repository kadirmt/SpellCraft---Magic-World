package com.arcanum.forge.client.render;

import com.arcanum.client.WandItemRenderer;
import com.arcanum.item.WandItem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Asa (WandItem) için Forge istemci item uzantısı — GeckoLib item renderer köprüsü.
 *
 * <p>Fabric'te WandItem (common) {@code createRenderer}/{@code getRenderProvider}
 * (geckolib-fabric RenderProvider deseni) ile {@code WandItemRenderer}'a bağlanır.
 * geckolib-forge-1.20.1'de RenderProvider deseni YOK (javap: {@code GeoItem}'da
 * makeRenderer/createRenderer yok) — Forge'un resmî deseni
 * {@code Item.initializeClient(Consumer<IClientItemExtensions>)} +
 * {@link IClientItemExtensions#getCustomRenderer()} (geckolib-forge'un kendi örnek
 * item'ları da aynen böyle). Bu sınıf o uzantı; {@code WandItemMixin} bunu
 * initializeClient override'ı olarak asaya takar.
 *
 * <p>Renderer TEMBEL kurulur: initializeClient, Item üst-ctor'undan çağrıldığı için o
 * anda WandItem.tier alanı HENÜZ atanmamıştır — tier'a ilk render anında erişilir
 * (fabric'teki tembel {@code createRenderer} gövdesiyle aynı disiplin). Ayrıca model
 * JSON'ları zaten {@code "parent": "builtin/entity"} (BEWLR şartı — doğrulandı).
 */
public final class WandClientExtensions implements IClientItemExtensions {
    private final WandItem item;
    private WandItemRenderer renderer;

    public WandClientExtensions(WandItem item) {
        this.item = item;
    }

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (this.renderer == null) {
            this.renderer = new WandItemRenderer(this.item.tier().assetId());
        }
        return this.renderer;
    }
}
