package com.arcanum.platform;

import com.arcanum.platform.registry.RegistryBackend;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Loader backend'i (sunucu+istemci ortak yüzey). Fabric: {@code com.arcanum.fabric.platform.FabricPlatform};
 * Forge: {@code com.arcanum.forge.platform.ForgePlatform}.
 *
 * <p><b>Kural:</b> kayıt nesnesi alan her metod {@link Supplier} alır — Forge'da mod ctor'unda {@code .get()} YASAK.
 * Backend bu çağrıları KUYRUĞA alır ve doğru anda boşaltır (Fabric: kayıtlar bağlandıktan hemen sonra;
 * Forge: ilgili mod-bus olayı).
 */
public interface PlatformBackend {

    /** "fabric" | "forge". */
    String loaderName();

    /** Fiziksel taraf istemci mi (dist). */
    boolean isClientDist();

    boolean isDevelopment();

    boolean isModLoaded(String modId);

    /** {@code <oyun>/config}. */
    Path configDir();

    /** {@link com.arcanum.platform.registry.DeferredRegister} bunun üstüne kurulur. */
    <T> RegistryBackend<T> createRegistryBackend(String modId, ResourceKey<? extends Registry<T>> registryKey);

    /**
     * Vanilla {@code MenuType(MenuSupplier, FeatureFlagSet)} ctor'u private + {@code MenuSupplier} paket-özel →
     * ortak kod doğrudan çağıramaz. FeatureFlagSet = {@code FeatureFlags.VANILLA_SET}.
     * Kayıt fabrikası lambda'sı İÇİNDEN çağrılır.
     */
    <M extends AbstractContainerMenu> MenuType<M> createMenuType(MenuFactory<M> factory);

    /** Vanilla {@code SpawnPlacements.register} private. Kuyruğa alınır. */
    <T extends Mob> void registerSpawnPlacement(Supplier<? extends EntityType<T>> type, SpawnPlacementType placement,
                                                Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<T> predicate);

    /** Varsayılan attribute kümesi. Kuyruğa alınır. */
    void registerAttributes(Supplier<? extends EntityType<? extends LivingEntity>> type,
                            Supplier<AttributeSupplier.Builder> attributes);

    /**
     * Köylü iş istasyonu POI tipi: {@code block}'un TÜM blockstate'leri eşleşen durum kümesi olur
     * (kökteki {@code Set.copyOf(getStateDefinition().getPossibleStates())} paritesi).
     * Anahtar ({@code arcanum:<name>}) HEMEN döner; kaydın kendisi loader'da tembel yapılır —
     * Fabric: blok kaydından sonra {@code PoiHelper.register} (durum→POI haritasını da doldurur);
     * Forge: normal {@code POINT_OF_INTEREST_TYPE} kaydı (Forge durum→POI haritasını kendi doldurur —
     * ortak invoker ile ELLE doldurmak Forge'da çift-kayıt çökmesi verir).
     * {@code Arcanum.init()} içinden (statik alan/ModX.init) çağrılır; {@code block.get()} burada ÇAĞRILMAZ.
     */
    ResourceKey<PoiType> registerPoi(String name, int maxTickets, int validRange, Supplier<? extends Block> block);

    /**
     * Baltayla soyma eşlemesi (log → stripped, AXIS korunur). Fabric: {@code StrippableBlockRegistry};
     * Forge: {@code BlockToolModificationEvent} + {@code ToolActions.AXE_STRIP}.
     * {@code Arcanum.init()} içinden ({@code ModBlocks.init}) çağrılır; backend KUYRUĞA alır, {@code .get()} bağlandıktan sonra.
     */
    void registerStrippable(Supplier<? extends Block> log, Supplier<? extends Block> stripped);

    PlatformNet net();

    @FunctionalInterface
    interface MenuFactory<M extends AbstractContainerMenu> {
        M create(int containerId, Inventory playerInventory);
    }
}
