package com.arcanum.registry;

import com.arcanum.Arcanum;
import com.arcanum.platform.Platform;
import com.arcanum.platform.registry.DeferredRegister;
import com.arcanum.platform.registry.RegistrySupplier;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;

/**
 * Wizard köylü mesleği — iş istasyonu Büyü Masası (arcanum:spell_table).
 * Vanilla Librarian+Lectern ile birebir aynı ticketCount/searchDistance (1, 1)
 * kullanılır.
 *
 * <p>26.x: POI tipi {@code Platform.get().registerPoi(...)} ile kaydedilir (vanilla'nın blok-durumu→POI
 * haritasını loader'a göre dolduran SPI); ticaret teklifleri artık VERİ-GÜDÜMLÜ
 * ({@code data/arcanum/villager_trade/wizard/1/*.json} + {@code trade_set/wizard/level_1.json} +
 * {@code tags/villager_trade/wizard/level_1.json}) — kökteki Fabric {@code TradeOfferHelper} kaydının yerini alır.
 * Meslek yalnız seviye-1 ticaret setini tanımlar (kökte de yalnız seviye 1 vardı); seviye 2-5 için
 * {@code VillagerProfession#getTrades} null döner → vanilla {@code Villager#updateTrades} sessizce atlar (çökme yok).
 *
 * <p>PARİTE NOTU (bilinçli sapma): kök 1.21.1 Fabric POI'yi Architectury DeferredRegister ile düz kaydediyordu;
 * ne Architectury-fabric ne Fabric API (yalnız {@code PointOfInterestHelper}, kök kullanmıyordu) vanilla
 * blok-durumu→POI haritasını doldurmadığından Büyü Masası orada iş istasyonu olarak tanınmıyordu (Forge
 * portlarında tanınıyordu). SPI haritayı her iki loader'da doldurur → Forge portlarındaki tasarım davranışı.
 * Seviye-1 teklif: 4 zümrüt → 12 Başlangıç Büyü Kitabı (tek yığın), 12 kullanım, 5 XP, çarpan 0.05
 * (veri: villager_trade/wizard/1). BİREBİR PARİTE: kök 1.21.1 {@code ItemsForEmeralds(Item,4,12,5)} 4-arg
 * kurucusunda 3. argüman ADET'tir (kurucuda {@code itemStack.setCount(12)}, getOffer'da {@code copy()},
 * maxUses kurucu sabiti {@code bipush 12} — javap 1.21.1 mojmap);
 * 1.20.1 Forge portu da aynı teklifi "BİREBİR" olarak yazar (bedel 4, sonuç 12 kitap, maxUses 12, xp 5, 0.05).
 * 26.x'te {@code ItemStackTemplate#create} {@code validateStrict} ile istif sınırını (kitap: 1) aşan "count"u
 * boş yığına çevirdiğinden adet {@code gives.count} ile DEĞİL, {@code given_item_modifiers} içindeki
 * {@code minecraft:set_count} ile verilir ({@code SetItemCountFunction#run} = {@code setCount}, doğrulamasız —
 * 1.21.1'deki taşmış yığınla aynı sonuç; {@code MerchantOffer} CODEC/STREAM_CODEC istif sınırı doğrulamaz).
 * Kök yorumu "1 kitap + 12 kullanım" niyetini ima ediyor; bu tasarım kararı tüm sürümlerde birlikte
 * değiştirilmeli (RAPOR açık konu), port tek başına sapmaz.
 */
public final class ModVillagers {
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Arcanum.MODID, Registries.VILLAGER_PROFESSION);

    /** Büyü Masası POI anahtarı ({@code arcanum:wizard}). Kayıt {@link #init()} içinde SPI ile. */
    public static final ResourceKey<PoiType> WIZARD_POI_KEY = ResourceKey.create(
            Registries.POINT_OF_INTEREST_TYPE, Arcanum.id("wizard"));

    /** Wizard seviye-1 ticaret seti ({@code data/arcanum/trade_set/wizard/level_1.json}). */
    public static final ResourceKey<TradeSet> WIZARD_LEVEL_1_TRADES = ResourceKey.create(
            Registries.TRADE_SET, Arcanum.id("wizard/level_1"));

    /**
     * Wizard mesleği — Büyü Masası'nı iş istasyonu olarak talep eder (vanilla Librarian paritesi).
     * Ad bileşeni mevcut lang anahtarını ({@code entity.minecraft.villager.arcanum.wizard}) kullanır.
     */
    public static final RegistrySupplier<VillagerProfession> WIZARD = PROFESSIONS.register("wizard",
            () -> new VillagerProfession(
                    Component.translatable("entity.minecraft.villager.arcanum.wizard"),
                    holder -> holder.is(WIZARD_POI_KEY),
                    holder -> holder.is(WIZARD_POI_KEY),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_LIBRARIAN,
                    Int2ObjectMap.ofEntries(Int2ObjectMap.entry(1, WIZARD_LEVEL_1_TRADES))));

    private ModVillagers() {}

    public static void init() {
        // Büyü Masası'nın olası tüm blockstate'leri (4 facing) POI eşleşen state setidir (SPI bloğun
        // tüm durumlarını kullanır). Kök sırası: önce POI, sonra meslek.
        ResourceKey<PoiType> poi = Platform.get().registerPoi("wizard", 1, 1, ModBlocks.SPELL_TABLE);
        if (!WIZARD_POI_KEY.equals(poi)) {
            Arcanum.LOGGER.error("[Arcanum] Wizard POI anahtarı beklenenden farklı: {} != {}", poi, WIZARD_POI_KEY);
        }
        PROFESSIONS.register();
    }
}
