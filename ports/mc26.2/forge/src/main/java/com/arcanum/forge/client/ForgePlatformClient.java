package com.arcanum.forge.client;

import com.arcanum.platform.client.PlatformClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Forge {@link PlatformClient} (G3'te {@code com.arcanum.forge.platform}'dan istemci paketine taşındı).
 * {@code ArcanumClient.init()} MOD CTOR'UNDA çağrılır (registry'ler henüz BOŞ) → her kayıt KUYRUĞA alınır,
 * {@code .get()} yalnız olay anında yapılır.
 *
 * <p><b>Bus'lar (javap + kaynak, Forge 64.1.3 / EventBus 7.0.5):</b> {@code RegisterKeyMappingsEvent},
 * {@code EntityRenderersEvent.RegisterLayerDefinitions}, {@code EntityRenderersEvent.RegisterRenderers},
 * {@code RegisterParticleProvidersEvent}, {@code AddGuiOverlayLayersEvent} → statik {@code XEvent.BUS}
 * (DEFAULT grup, MOD bus DEĞİL — "sessiz bus tuzağı"); {@code FMLClientSetupEvent} → {@code getBus(modBusGroup)}.
 * Key/katman/renderer/HUD olayları {@code SelfDestructing}: dinleyici post'tan ÖNCE (mod ctor'u) bağlanmalı —
 * bu sınıfın ctor'u öyle yapar. {@code RegisterLayerDefinitions} ve {@code RegisterRenderers}
 * {@code ForgeHooksClient.initClientHooks}'ta (Minecraft kurulumu, ilk kaynak yüklemesinden önce) bu sırayla post edilir.
 */
public final class ForgePlatformClient implements PlatformClient {
    private final List<KeyMapping> keyMappings = new ArrayList<>();
    private final List<Consumer<EntityRenderersEvent.RegisterLayerDefinitions>> layerQueue = new ArrayList<>();
    private final List<Consumer<EntityRenderersEvent.RegisterRenderers>> rendererQueue = new ArrayList<>();
    private final List<Consumer<RegisterParticleProvidersEvent>> particleQueue = new ArrayList<>();
    private final List<Runnable> menuScreenQueue = new ArrayList<>();
    private final List<HudEntry> hudQueue = new ArrayList<>();

    private record HudEntry(Identifier id, HudLayer layer) {}

    public ForgePlatformClient(BusGroup modBusGroup) {
        RegisterKeyMappingsEvent.BUS.addListener(this::onRegisterKeyMappings);
        EntityRenderersEvent.RegisterLayerDefinitions.BUS.addListener(this::onRegisterLayerDefinitions);
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(this::onRegisterRenderers);
        RegisterParticleProvidersEvent.BUS.addListener(this::onRegisterParticleProviders);
        AddGuiOverlayLayersEvent.BUS.addListener(this::onAddGuiOverlayLayers);
        FMLClientSetupEvent.getBus(modBusGroup).addListener(this::onClientSetup);
    }

    @Override
    public KeyMapping registerKeyMapping(KeyMapping mapping) {
        keyMappings.add(mapping);
        return mapping;
    }

    @Override
    public InputConstants.Key getBoundKey(KeyMapping mapping) {
        return mapping.getKey(); // Forge IForgeKeyMapping: public getKey() [javap]
    }

    @Override
    public <T extends Entity> void registerEntityRenderer(Supplier<? extends EntityType<? extends T>> type,
                                                          EntityRendererProvider<T> provider) {
        rendererQueue.add(event -> event.registerEntityRenderer(type.get(), provider));
    }

    @Override
    public <T extends ParticleOptions> void registerParticleProvider(Supplier<? extends ParticleType<T>> type,
                                                                     SpriteProviderFactory<T> factory) {
        particleQueue.add(event -> event.registerSpriteSet(type.get(), factory::create));
    }

    @Override
    public <M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> void registerMenuScreen(
            Supplier<? extends MenuType<? extends M>> type, MenuScreenFactory<M, S> factory) {
        // MenuScreens.register Forge'da public [javap]; resmi yer FMLClientSetupEvent.enqueueWork (ana thread).
        menuScreenQueue.add(() -> MenuScreens.<M, S>register(type.get(), factory::create));
    }

    @Override
    public void registerHudElement(Identifier id, HudLayer layer) {
        hudQueue.add(new HudEntry(id, layer));
    }

    @Override
    public void registerModelLayer(ModelLayerLocation loc, Supplier<LayerDefinition> def) {
        // Forge 64.1.3 kaynak: RegisterLayerDefinitions#registerLayerDefinition(ModelLayerLocation, Supplier<LayerDefinition>)
        layerQueue.add(event -> event.registerLayerDefinition(loc, def));
    }

    // ---------------------------------------------------------------- olaylar

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        for (KeyMapping k : keyMappings) event.register(k);
    }

    private void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (Consumer<EntityRenderersEvent.RegisterLayerDefinitions> c : layerQueue) c.accept(event);
    }

    private void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (Consumer<EntityRenderersEvent.RegisterRenderers> c : rendererQueue) c.accept(event);
    }

    private void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        for (Consumer<RegisterParticleProvidersEvent> c : particleQueue) c.accept(event);
    }

    private void onAddGuiOverlayLayers(AddGuiOverlayLayersEvent event) {
        // Hotbar kümesinde ITEM_HOTBAR'ın ARKASINA, HEALTH_BAR'ın (zırh+kalp+açlık+hava) ÖNÜNE ekle — gerekçe:
        // PlatformClient#registerHudElement javadoc'u (kök 1.21.1: mana blit'leri hotbar'ın üstünde, sayfa
        // göstergesi metni kalplerin altında). addBelow(HEALTH_BAR) her yeni katmanı HEALTH_BAR'ın hemen önüne
        // koyar → kayıt sırası korunur. Katmanımız koşulsuz (HEALTH_BAR'ın canHurtPlayer koşulu yalnız kendisine
        // ait) → yaratıcı/izleyici modunda da çizilir. hideGui kontrolü elemanın kendi işi (sözleşme).
        // 26.2 / Forge 65.1.3: ForgeLayeredDraw.init artık Gui değil Hud alıyor; HOTBAR_AND_DECOS kümesinin katman
        // kimlikleri/sırası (SPECTATOR_HOTBAR, ITEM_HOTBAR, HEALTH_BAR, ...) ve AddGuiOverlayLayersEvent 64.1.3 ile aynı.
        for (HudEntry h : hudQueue) {
            HudLayer layer = h.layer();
            event.getLayeredDraw().addBelow(ForgeLayeredDraw.HOTBAR_AND_DECOS, h.id(), ForgeLayeredDraw.HEALTH_BAR,
                    layer::extract);
        }
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (Runnable r : menuScreenQueue) r.run();
        });
    }
}
