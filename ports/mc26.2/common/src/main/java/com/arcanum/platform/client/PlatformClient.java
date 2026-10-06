package com.arcanum.platform.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

/**
 * İstemci dikişi. YALNIZ {@code com.arcanum.client.**} + loader istemci giriş noktasından erişilir.
 * Tüm {@code register*} çağrıları {@code ArcanumClient.init()} içinden yapılır; backend gerekirse kuyruğa alır
 * (Forge: RegisterKeyMappingsEvent / EntityRenderersEvent.RegisterRenderers / RegisterParticleProvidersEvent /
 * AddGuiOverlayLayersEvent / FMLClientSetupEvent / EntityRenderersEvent.RegisterLayerDefinitions).
 *
 * <p>Backend'ler (G3): Fabric {@code com.arcanum.fabric.client.FabricPlatformClient},
 * Forge {@code com.arcanum.forge.client.ForgePlatformClient}.
 */
public interface PlatformClient {

    /** Kategori ortak kodda {@code KeyMapping.Category.register(Identifier)} ile yaratılır; loader yalnız KAYDEDER. */
    KeyMapping registerKeyMapping(KeyMapping mapping);

    /** Kullanıcının o an bağladığı tuş ({@code KeyMapping.key} protected). */
    InputConstants.Key getBoundKey(KeyMapping mapping);

    <T extends Entity> void registerEntityRenderer(Supplier<? extends EntityType<? extends T>> type,
                                                   EntityRendererProvider<T> provider);

    /** Vanilla {@code ParticleResources.SpriteParticleRegistration} paket-özel → ortak arayüz. */
    <T extends ParticleOptions> void registerParticleProvider(Supplier<? extends ParticleType<T>> type,
                                                              SpriteProviderFactory<T> factory);

    /** Vanilla {@code MenuScreens.register} private + {@code ScreenConstructor} paket-özel → ortak arayüz. */
    <M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> void registerMenuScreen(
            Supplier<? extends MenuType<? extends M>> type, MenuScreenFactory<M, S> factory);

    /**
     * HUD elemanı, vanilla HOTBAR'dan HEMEN SONRA, oyuncu sağlık/zırh/açlık katmanından ÖNCE çizilir; kayıt sırası
     * korunur. HUD-gizli (F1) kontrolü elemanın kendi işidir ({@code ClientCompat.hudHidden()}; 26.2 {@code gui.hud.isHidden()}).
     *
     * <p>Neden (G4 düzeltme turu 3, kök 1.21.1 kaynağıyla doğrulandı): kökteki {@code HudRenderCallback}
     * {@code Gui.render} TAIL'inde çalışıyordu; {@code Gui.render} katmanları derinlik testi AÇIK, her katman
     * +200 z ile çizip sonda {@code disableDepthTest()} yapıyordu. Bu yüzden kökte Arcanum'un {@code blit}'leri
     * (derinlik testi kapalı) hotbar'ın ÜSTÜNDE, {@code drawString}/{@code fill}'leri (render tipi LEQUAL, z=0)
     * ise kalplerin/hotbar'ın ALTINDA kalıyordu. 26.x GUI derinliksiz, yalnız gönderim sırasıyla çizer; bu iki
     * gözlemi (mana barı hotbar'ın üstünde, sayfa göstergesi kalplerin altında) aynı anda veren tek sıra
     * "hotbar → Arcanum → sağlık" sırasıdır. Fabric: {@code HudElementRegistry.attachElementAfter(HOTBAR)}
     * (+ izleyici modu için {@code SPECTATOR_MENU}); Forge: {@code AddGuiOverlayLayersEvent} →
     * {@code ForgeLayeredDraw.addBelow(HOTBAR_AND_DECOS, …, HEALTH_BAR, …)}.
     */
    void registerHudElement(Identifier id, HudLayer layer);

    /**
     * Özel entity model katmanı ({@code EntityModelSet}'e girer; renderer {@code context.bakeLayer(loc)} ile okur).
     * {@code def} model bake anında (kaynak yüklemesi) çağrılır — kayıt nesnesi {@code .get()}'i İÇERMEMELİ.
     * Fabric: {@code ModelLayerRegistry.registerModelLayer} (fabric-rendering-v1 23.3.1, javap);
     * Forge: {@code EntityRenderersEvent.RegisterLayerDefinitions#registerLayerDefinition}.
     */
    void registerModelLayer(ModelLayerLocation loc, Supplier<LayerDefinition> def);

    @FunctionalInterface
    interface SpriteProviderFactory<T extends ParticleOptions> {
        ParticleProvider<T> create(SpriteSet sprites);
    }

    @FunctionalInterface
    interface MenuScreenFactory<M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> {
        S create(M menu, Inventory playerInventory, Component title);
    }

    @FunctionalInterface
    interface HudLayer {
        void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker);
    }
}
