package com.arcanum.fabric.client;

import com.arcanum.platform.client.PlatformClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

/**
 * Fabric {@link PlatformClient} (G3'te {@code com.arcanum.fabric.platform}'dan istemci paketine taşındı).
 * Fabric'te istemci init'i ortak kayıtlar bağlandıktan SONRA çalışır (main entrypoint → client entrypoint)
 * → kuyruk gerekmez, {@code .get()} anında güvenli.
 */
public final class FabricPlatformClient implements PlatformClient {

    /**
     * Son eklenen Arcanum HUD elemanlarının kimlikleri (normal hotbar / izleyici hotbar'ı zinciri).
     * {@code attachElementAfter(X)} yeni elemanı X'in HEMEN arkasına ekler; hepsi HOTBAR'a bağlansaydı
     * kayıt sırası TERSİNE dönerdi → her yeni eleman bir öncekine bağlanır (kök HudRenderCallback sırası).
     */
    private Identifier lastHud = VanillaHudElements.HOTBAR;
    private Identifier lastSpectatorHud = VanillaHudElements.SPECTATOR_MENU;

    @Override
    public KeyMapping registerKeyMapping(KeyMapping mapping) {
        return KeyMappingHelper.registerKeyMapping(mapping);
    }

    @Override
    public InputConstants.Key getBoundKey(KeyMapping mapping) {
        return KeyMappingHelper.getBoundKeyOf(mapping);
    }

    @Override
    public <T extends Entity> void registerEntityRenderer(Supplier<? extends EntityType<? extends T>> type,
                                                          EntityRendererProvider<T> provider) {
        // vanilla EntityRenderers.register: Fabric transitive class-tweaker ile public
        EntityRenderers.register(type.get(), provider);
    }

    @Override
    public <T extends ParticleOptions> void registerParticleProvider(Supplier<? extends ParticleType<T>> type,
                                                                     SpriteProviderFactory<T> factory) {
        ParticleProviderRegistry.getInstance().register(type.get(), factory::create);
    }

    @Override
    public <M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> void registerMenuScreen(
            Supplier<? extends MenuType<? extends M>> type, MenuScreenFactory<M, S> factory) {
        // vanilla MenuScreens.register + ScreenConstructor: Fabric transitive class-tweaker ile erişilebilir
        MenuScreens.<M, S>register(type.get(), factory::create);
    }

    @Override
    public void registerHudElement(Identifier id, HudLayer layer) {
        // Hotbar'ın ARKASINA, ARMOR_BAR/HEALTH_BAR'ın ÖNÜNE (gerekçe: PlatformClient#registerHudElement javadoc'u).
        // HEALTH_BAR'a bağlanmaz: Fabric kök elemanları yalnız vanilla çağrısı olduğunda koşar ve
        // extractPlayerHealth yaratıcı modda çağrılmaz → HUD yaratıcıda kaybolurdu. extractHotbarAndDecorations
        // ise ya extractItemHotbar (HOTBAR) ya SpectatorGui#extractHotbar (SPECTATOR_MENU) çağırır → ikisine de
        // bağlanarak her karede TAM BİR KEZ çizilir (kök TAIL kancası da her modda çiziyordu).
        // 26.2 (fabric-rendering-v1 25.3.3, javap): HudMixin hedefi Gui→Hud; HOTBAR = Hud#extractItemHotbar,
        // SPECTATOR_MENU = SpectatorGui#extractHotbar, ikisi de Hud#extractHotbarAndDecorations içinde (yapı aynı).
        HudElement element = layer::extract;
        HudElementRegistry.attachElementAfter(lastHud, id, element);
        lastHud = id;
        Identifier spectatorId = id.withSuffix("_spectator");
        HudElementRegistry.attachElementAfter(lastSpectatorHud, spectatorId, element);
        lastSpectatorHud = spectatorId;
    }

    @Override
    public void registerModelLayer(ModelLayerLocation loc, Supplier<LayerDefinition> def) {
        // fabric-rendering-v1 23.3.1 [javap]: registerModelLayer(ModelLayerLocation, TexturedLayerDefinitionProvider)
        ModelLayerRegistry.registerModelLayer(loc, def::get);
    }
}
