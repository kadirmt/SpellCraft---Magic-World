package com.arcanum.forge.platform;

import com.arcanum.Arcanum;
import com.arcanum.platform.PlatformBackend;
import com.arcanum.platform.PlatformNet;
import com.arcanum.platform.registry.RegistryBackend;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Forge {@link PlatformBackend}. Mod ctor'unda kurulur ({@code ArcanumForge}); kuyruklar ilgili Forge olayında boşalır.
 *
 * <p><b>EventBus 7 bus'ları (javap + Forge 64.1.3 kaynağı ile doğrulandı):</b> {@code EntityAttributeCreationEvent} ve
 * {@code SpawnPlacementRegisterEvent} MOD bus'ta DEĞİL — statik {@code XEvent.BUS} (BusGroup.DEFAULT). İkisi de
 * {@code GameData.postRegisterEvents()} sonunda (tüm RegisterEvent'lerden sonra, FMLCommonSetup'tan önce) post edilir.
 */
public final class ForgePlatform implements PlatformBackend {
    private final BusGroup modBusGroup;
    private final ForgeNet net = new ForgeNet();
    private final List<Consumer<EntityAttributeCreationEvent>> attributeQueue = new ArrayList<>();
    private final List<Consumer<SpawnPlacementRegisterEvent>> placementQueue = new ArrayList<>();
    /** commonSetup'ta (ana thread) doldurulur, sunucu thread'inde okunur. */
    private final List<Strippable> strippables = new CopyOnWriteArrayList<>();
    private RegistryBackend<PoiType> poiBackend;

    public ForgePlatform(BusGroup modBusGroup) {
        this.modBusGroup = modBusGroup;
        // Dinleyiciler HEMEN (mod ctor'u) bağlanır; kuyruk olay anında okunur → Arcanum.init() sonrası eklenenler de gider.
        EntityAttributeCreationEvent.BUS.addListener(this::onAttributeCreation);
        SpawnPlacementRegisterEvent.BUS.addListener(this::onSpawnPlacementRegister);
        // Soyma: BlockToolModificationEvent statik BUS (DEFAULT, CancellableEventBus) — void dinleyici = iptal etmez.
        BlockEvent.BlockToolModificationEvent.BUS.addListener(this::onToolModification);
    }

    public BusGroup modBusGroup() {
        return modBusGroup;
    }

    @Override
    public String loaderName() {
        return "forge";
    }

    @Override
    public boolean isClientDist() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    @Override
    public boolean isDevelopment() {
        return !FMLEnvironment.production;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.isLoaded(modId);
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public <T> RegistryBackend<T> createRegistryBackend(String modId, ResourceKey<? extends Registry<T>> registryKey) {
        return new ForgeRegistryBackend<>(modId, registryKey, modBusGroup);
    }

    @Override
    public <M extends AbstractContainerMenu> MenuType<M> createMenuType(MenuFactory<M> factory) {
        // Forge AT'si MenuType(MenuSupplier, FeatureFlagSet) ctor'unu public yapar [javap forge-26.1.2-64.1.3].
        // IForgeMenuType.create(...) de olurdu ama FeatureFlags.DEFAULT_FLAGS verir; Fabric paritesi için VANILLA_SET.
        return new MenuType<>(factory::create, FeatureFlags.VANILLA_SET);
    }

    @Override
    public <T extends Mob> void registerSpawnPlacement(Supplier<? extends EntityType<T>> type, SpawnPlacementType placement,
                                                       Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<T> predicate) {
        placementQueue.add(event -> event.register(type.get(), placement, heightmap, predicate,
                SpawnPlacementRegisterEvent.Operation.REPLACE));
    }

    @Override
    public void registerAttributes(Supplier<? extends EntityType<? extends LivingEntity>> type,
                                   Supplier<AttributeSupplier.Builder> attributes) {
        attributeQueue.add(event -> event.put(type.get(), attributes.get().build()));
    }

    @Override
    public ResourceKey<PoiType> registerPoi(String name, int maxTickets, int validRange, Supplier<? extends Block> block) {
        Identifier id = Identifier.fromNamespaceAndPath(Arcanum.MODID, name);
        // Normal POINT_OF_INTEREST_TYPE kaydı: Forge durum→POI haritasını (PoiTypes.TYPE_BY_STATE =
        // GameData.PoiTypeCallbacks.getStateToPoi()) kayıt callback'inde KENDİSİ doldurur [Forge 64.1.3 kaynağı].
        // Ortak invoker ile elle doldurmak Forge'da "defined in more than one PoI type" çökmesi verir.
        // Fabrika RegisterEvent<point_of_interest_type>'ta koşar; blok registry'si ondan ÖNCE (vanilla PoiTypes
        // bootstrap'ı da blokları ister) → block.get() burada güvenli.
        if (poiBackend == null) {
            poiBackend = createRegistryBackend(Arcanum.MODID, Registries.POINT_OF_INTEREST_TYPE);
            poiBackend.bind(); // bind sonrası add, RegisterEvent gelmeden önce serbest (ForgeRegistryBackend)
        }
        poiBackend.add(id, key -> new PoiType(
                Set.copyOf(block.get().getStateDefinition().getPossibleStates()), maxTickets, validRange));
        return ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, id);
    }

    @Override
    public void registerStrippable(Supplier<? extends Block> log, Supplier<? extends Block> stripped) {
        strippables.add(new Strippable(log, stripped));
    }

    @Override
    public PlatformNet net() {
        return net;
    }

    /**
     * Baltayla soyma — Fabric {@code StrippableBlockRegistry.register(log, stripped)} paritesi (VANILLA dönüştürücü:
     * AXIS kopyalanır; {@code withPropertiesOf} kütükte yalnız AXIS'i taşır). Simülasyon dahil finalState set edilir
     * (yan etkimiz yok; AxeItem'ın ön-kontrolü simulate=true ile gelir).
     */
    private void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.getToolAction() != ToolActions.AXE_STRIP) return;
        BlockState state = event.getState();
        for (Strippable s : strippables) {
            if (state.is(s.log().get())) {
                event.setFinalState(s.stripped().get().withPropertiesOf(state));
                return;
            }
        }
    }

    private record Strippable(Supplier<? extends Block> log, Supplier<? extends Block> stripped) {}

    private void onAttributeCreation(EntityAttributeCreationEvent event) {
        for (Consumer<EntityAttributeCreationEvent> c : attributeQueue) c.accept(event);
    }

    private void onSpawnPlacementRegister(SpawnPlacementRegisterEvent event) {
        for (Consumer<SpawnPlacementRegisterEvent> c : placementQueue) c.accept(event);
    }
}
