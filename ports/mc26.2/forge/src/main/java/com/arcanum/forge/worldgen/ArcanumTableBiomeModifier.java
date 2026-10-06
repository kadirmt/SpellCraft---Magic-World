package com.arcanum.forge.worldgen;

import com.arcanum.Arcanum;
import com.arcanum.server.ArcanumBiomeModifications;
import com.arcanum.server.ArcanumBiomeModifications.FeatureEntry;
import com.arcanum.server.ArcanumBiomeModifications.Selector;
import com.arcanum.server.ArcanumBiomeModifications.SpawnEntry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo.BiomeInfo;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.Regions;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Forge: TEK özel BiomeModifier tipi {@code arcanum:table} — ortak {@link ArcanumBiomeModifications} tablosunu
 * (kök Fabric {@code BiomeModifications.addSpawn} ×24 + {@code addFeature} ×2) uygular.
 * Veri: {@code data/arcanum/forge/biome_modifier/arcanum_table.json} = {@code {"type": "arcanum:table"}}.
 *
 * <p><b>"overworld" seçicisi = Fabric {@code BiomeSelectors.foundInOverworld()} ile AYNI küme.</b> Fabric tanımı
 * [javap fabric-biome-api-v1 18.0.5]: {@code canGenerateIn(OVERWORLD)} = overworld LevelStem'inin biyom kaynağının
 * {@code possibleBiomes()} kümesi. Fabric'te bu, TerraBlender'ın {@code MinecraftServer.<init>} RETURN kancasından
 * SONRA okunur (TB mixin önceliği 995 &lt; Fabric 1000 → TB önce çalışır ve bölge biyomlarını — gloomwood /
 * arcanewood_grove — {@code possibleBiomes}'a ekler) → Fabric kümesi arcanum biyomlarını KAPSAR.
 * Forge'da ise {@code runModifiers}, TB'nin {@code ServerAboutToStartEvent} başlatmasından ÖNCE koşar
 * ({@code ServerLifecycleHooks.handleServerAboutToStart}) → aynı kümeyi üretmek için TB'nin ekleyeceği listeyi
 * burada TB'nin kendi kuralıyla yeniden hesaplarız ({@code LevelUtils.initializeBiomes} paritesi: NoiseBased +
 * MultiNoise + boyut tipi {@code #terrablender:overworld_regions} → tüm OVERWORLD bölgelerinin registry'de
 * bulunan biyomları).
 *
 * <p>Kategori tablonun kendi kategorisidir (Fabric {@code addSpawn(selector, category, …)} paritesi) —
 * Forge'un hazır {@code add_spawns}'ı gibi {@code type.getCategory()} KULLANILMAZ.
 */
public final class ArcanumTableBiomeModifier implements BiomeModifier {
    public static final ArcanumTableBiomeModifier INSTANCE = new ArcanumTableBiomeModifier();
    public static final MapCodec<ArcanumTableBiomeModifier> CODEC = MapCodec.unit(INSTANCE);

    /** TerraBlender'ın bölge boyut tipi etiketleri ({@code terrablender.DimensionTypeTags}). */
    private static final TagKey<DimensionType> TB_OVERWORLD_REGIONS = TagKey.create(Registries.DIMENSION_TYPE,
            Identifier.fromNamespaceAndPath("terrablender", "overworld_regions"));
    private static final TagKey<DimensionType> TB_NETHER_REGIONS = TagKey.create(Registries.DIMENSION_TYPE,
            Identifier.fromNamespaceAndPath("terrablender", "nether_regions"));

    /** Bir runModifiers geçişi boyunca overworld kümesi önbelleği (anahtar: RegistryAccess kimliği). */
    private RegistryAccess cachedFor;
    private Set<ResourceKey<Biome>> cachedOverworld = Set.of();

    private ArcanumTableBiomeModifier() {}

    /**
     * Mod ctor'unda: serializer'ı Forge'un {@code forge:biome_modifier_serializers} registry'sine
     * {@code arcanum:table} olarak kaydeder (RegisterEvent mod-bus olayıdır).
     */
    public static void register(BusGroup modBusGroup) {
        DeferredRegister<MapCodec<? extends BiomeModifier>> serializers =
                DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, Arcanum.MODID);
        serializers.register("table", () -> CODEC);
        serializers.register(modBusGroup);
    }

    @Override
    public void modify(Holder<Biome> biome, Phase phase, BiomeInfo.Builder builder) {
        if (phase != Phase.ADD) {
            return;
        }
        Optional<ResourceKey<Biome>> keyOpt = biome.unwrapKey();
        if (keyOpt.isEmpty()) {
            return;
        }
        ResourceKey<Biome> key = keyOpt.get();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            Arcanum.LOGGER.warn("[Arcanum/Forge] arcanum:table biome modifier sunucusuz cagrildi — atlandi ({})",
                    key.identifier());
            return;
        }
        RegistryAccess access = server.registryAccess();
        Set<ResourceKey<Biome>> overworld = overworldBiomes(access);

        // Kök sırası: addFeature ×2 (ArcanumWorldgenFabric.register) addSpawn bloğundan ÖNCE.
        for (FeatureEntry f : ArcanumBiomeModifications.features()) {
            if (matches(f.biomes(), key, overworld)) {
                Holder<PlacedFeature> feature = access.lookupOrThrow(Registries.PLACED_FEATURE).getOrThrow(f.feature());
                builder.getGenerationSettings().addFeature(f.step(), feature);
            }
        }
        for (SpawnEntry s : ArcanumBiomeModifications.spawns()) {
            if (matches(s.biomes(), key, overworld)) {
                builder.getMobSpawnSettings().addSpawn(s.category(), s.weight(),
                        new MobSpawnSettings.SpawnerData(s.type().get(), s.minCount(), s.maxCount()));
            }
        }
    }

    private static boolean matches(Selector sel, ResourceKey<Biome> key, Set<ResourceKey<Biome>> overworld) {
        return sel.overworld() ? overworld.contains(key) : sel.keys().contains(key);
    }

    /** Fabric {@code foundInOverworld()} kümesinin Forge eşdeğeri (sınıf notuna bakın). */
    private Set<ResourceKey<Biome>> overworldBiomes(RegistryAccess access) {
        if (access == cachedFor) {
            return cachedOverworld;
        }
        Set<ResourceKey<Biome>> set = new HashSet<>();
        LevelStem stem = access.lookupOrThrow(Registries.LEVEL_STEM).getValue(LevelStem.OVERWORLD);
        if (stem != null) {
            ChunkGenerator generator = stem.generator();
            for (Holder<Biome> h : generator.getBiomeSource().possibleBiomes()) {
                h.unwrapKey().ifPresent(set::add);
            }
            // TerraBlender (LevelUtils.initializeBiomes) paritesi: Fabric'te TB bu listeyi possibleBiomes'a
            // Fabric biyom değişikliklerinden ÖNCE ekler; Forge'da runModifiers TB'den önce koştuğu için burada eklenir.
            if (generator instanceof NoiseBasedChunkGenerator
                    && generator.getBiomeSource() instanceof MultiNoiseBiomeSource
                    && !isTagged(stem.type(), TB_NETHER_REGIONS, false)
                    && isTagged(stem.type(), TB_OVERWORLD_REGIONS, true)) {
                Registry<Biome> biomes = access.lookupOrThrow(Registries.BIOME);
                for (Region region : Regions.get(RegionType.OVERWORLD)) {
                    region.addBiomes(biomes, pair -> {
                        ResourceKey<Biome> k = pair.getSecond();
                        if (biomes.containsKey(k)) {
                            set.add(k);
                        }
                    });
                }
            }
        }
        cachedFor = access;
        cachedOverworld = Set.copyOf(set);
        return cachedOverworld;
    }

    /**
     * Etiket denetimi; etiketler (beklenmedik şekilde) henüz bağlanmamışsa {@code Holder.Reference.is}
     * "Tags not bound" fırlatır → TB'nin varsayılan verisine göre geri dönüş değeri (overworld tipi
     * {@code #terrablender:overworld_regions} içinde, nether etiketinde değil).
     */
    private static boolean isTagged(Holder<DimensionType> type, TagKey<DimensionType> tag, boolean fallback) {
        try {
            return type.is(tag);
        } catch (IllegalStateException e) {
            Arcanum.LOGGER.warn("[Arcanum/Forge] {} etiketi bagli degil — varsayilan {} kullanildi", tag.location(), fallback);
            return fallback;
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}
