package com.arcanum.fabric.platform;

import com.arcanum.Arcanum;
import com.arcanum.platform.PlatformBackend;
import com.arcanum.platform.PlatformNet;
import com.arcanum.platform.registry.RegistryBackend;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Fabric {@link PlatformBackend}. Kuyruklar {@link #drainDeferred()} ile {@code Arcanum.init()} SONRASI boşaltılır. */
public final class FabricPlatform implements PlatformBackend {
    private final FabricNet net = new FabricNet();
    private final List<Runnable> deferred = new ArrayList<>();
    private boolean drained;

    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public boolean isClientDist() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public boolean isDevelopment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public <T> RegistryBackend<T> createRegistryBackend(String modId, ResourceKey<? extends Registry<T>> registryKey) {
        return new FabricRegistryBackend<>(registryKey);
    }

    @Override
    public <M extends AbstractContainerMenu> MenuType<M> createMenuType(MenuFactory<M> factory) {
        // MenuType ctor + MenuSupplier: Fabric transitive class-tweaker ile erişilebilir
        return new MenuType<>(factory::create, FeatureFlags.VANILLA_SET);
    }

    @Override
    public <T extends Mob> void registerSpawnPlacement(Supplier<? extends EntityType<T>> type, SpawnPlacementType placement,
                                                       Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<T> predicate) {
        // SpawnPlacements.register: Fabric transitive class-tweaker ile public
        defer(() -> SpawnPlacements.register(type.get(), placement, heightmap, predicate));
    }

    @Override
    public void registerAttributes(Supplier<? extends EntityType<? extends LivingEntity>> type,
                                   Supplier<AttributeSupplier.Builder> attributes) {
        defer(() -> FabricDefaultAttributeRegistry.register(type.get(), attributes.get()));
    }

    @Override
    public ResourceKey<PoiType> registerPoi(String name, int maxTickets, int validRange, Supplier<? extends Block> block) {
        Identifier id = Identifier.fromNamespaceAndPath(Arcanum.MODID, name);
        // PoiHelper.register: POINT_OF_INTEREST_TYPE'a kaydeder + bloğun TÜM durumlarını durum→POI haritasına yazar
        // [javap fabric-object-builder-api-v1 23.1.1]. Blok gerektiği için kayıtlar bağlandıktan sonra (drain).
        defer(() -> PoiHelper.register(id, maxTickets, validRange, block.get()));
        return ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, id);
    }

    @Override
    public void registerStrippable(Supplier<? extends Block> log, Supplier<? extends Block> stripped) {
        // İki blokta da AXIS varsa StrippingTransformer.VANILLA (AXIS kopyalanır) [javap content-registries 11.3.1]
        defer(() -> StrippableBlockRegistry.register(log.get(), stripped.get()));
    }

    @Override
    public PlatformNet net() {
        return net;
    }

    private void defer(Runnable r) {
        if (drained) r.run();
        else deferred.add(r);
    }

    /** {@code ArcanumFabric.onInitialize}: {@code Arcanum.init()} döndükten hemen sonra. */
    public void drainDeferred() {
        drained = true;
        for (Runnable r : deferred) r.run();
        deferred.clear();
    }
}
