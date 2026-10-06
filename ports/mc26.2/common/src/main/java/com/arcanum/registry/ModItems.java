package com.arcanum.registry;

import com.arcanum.Arcanum;
import com.arcanum.entity.BroomEntity;
import com.arcanum.item.ArcanewoodBoatItem;
import com.arcanum.item.BroomItem;
import com.arcanum.item.CloakOfInvisibilityItem;
import com.arcanum.item.SortingHatItem;
import com.arcanum.item.WandItem;
import com.arcanum.item.WandTier;
import com.arcanum.platform.registry.DeferredRegister;
import com.arcanum.platform.registry.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Tüm item kayıtları. Ortak {@code platform.registry} shim'i ile loader-bağımsız.
 *
 * <p>26.x: her fabrika kayıt anahtarını alır ({@code key -> ...}) ve {@code Item.Properties.setId(key)}
 * çağırır (1.21.2+ zorunlu). Spawn yumurtaları {@code Properties.spawnEgg(type)} kullanır — tipi ANINDA
 * ister; güvenli çünkü iki loader'da da ENTITY_TYPE, ITEM'dan önce kaydedilir (G2 P4 = EAGER).
 * Yumurta renkleri artık kodda değil, kaynak tarafındaki kendi PNG'lerde (aşağıdaki yorumlarda kök
 * renk çiftleri kayıtlı: taban / benek).
 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Arcanum.MODID, Registries.ITEM);

    // ---- Asalar (5 kademe/arketip) ----
    public static final RegistrySupplier<Item> ARCANEWOOD_WAND =
            ITEMS.register("arcanewood_wand", key -> new WandItem(WandTier.ARCANEWOOD,
                    new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** Çeviklik arketipi — Arcanewood'a yan-yükseltme, Phoenix/Elder'ın yerine değil. */
    public static final RegistrySupplier<Item> THUNDERBIRD_WAND =
            ITEMS.register("thunderbird_wand", key -> new WandItem(WandTier.THUNDERBIRD,
                    new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** Güç arketipi — Arcanewood'a yan-yükseltme, Phoenix/Elder'ın yerine değil. */
    public static final RegistrySupplier<Item> TROLL_WAND =
            ITEMS.register("troll_wand", key -> new WandItem(WandTier.TROLL,
                    new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final RegistrySupplier<Item> PHOENIX_WAND =
            ITEMS.register("phoenix_wand", key -> new WandItem(WandTier.PHOENIX,
                    new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE)));

    public static final RegistrySupplier<Item> ELDER_WAND =
            ITEMS.register("elder_wand", key -> new WandItem(WandTier.ELDER,
                    new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    /** Uçan süpürge — binilebilir. */
    public static final RegistrySupplier<Item> BROOM =
            ITEMS.register("broom", key -> new BroomItem(
                    new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE)));

    // ---- Canlandırma yumurtaları ----
    public static final RegistrySupplier<Item> DEATH_EATER_SPAWN_EGG =
            ITEMS.register("death_eater_spawn_egg", key -> new SpawnEggItem(
                    new Item.Properties().setId(key).spawnEgg(ModEntities.DEATH_EATER.get()))); // kök renkler: 0x1A1A22 / 0xB8C0CC

    public static final RegistrySupplier<Item> DEMENTOR_SPAWN_EGG =
            ITEMS.register("dementor_spawn_egg", key -> new SpawnEggItem(
                    new Item.Properties().setId(key).spawnEgg(ModEntities.DEMENTOR.get()))); // kök renkler: 0x0E0E14 / 0x4A5568

    public static final RegistrySupplier<Item> BOWTRUCKLE_SPAWN_EGG =
            ITEMS.register("bowtruckle_spawn_egg", key -> new SpawnEggItem(
                    new Item.Properties().setId(key).spawnEgg(ModEntities.BOWTRUCKLE.get()))); // kök renkler: 0x5B8C4A / 0xC8E6A0

    public static final RegistrySupplier<Item> MOONCALF_SPAWN_EGG =
            ITEMS.register("mooncalf_spawn_egg", key -> new SpawnEggItem(
                    new Item.Properties().setId(key).spawnEgg(ModEntities.MOONCALF.get()))); // kök renkler: 0x9FA8B8 / 0xDDE8F5

    // ---- HogCraft portu ----
    public static final RegistrySupplier<Item> TROLL_SPAWN_EGG =
            ITEMS.register("troll_spawn_egg", key -> new SpawnEggItem(
                    new Item.Properties().setId(key).spawnEgg(ModEntities.TROLL.get()))); // kök renkler: 0x666600 / 0x996600

    public static final RegistrySupplier<Item> PHOENIX_SPAWN_EGG =
            ITEMS.register("phoenix_spawn_egg", key -> new SpawnEggItem(
                    new Item.Properties().setId(key).spawnEgg(ModEntities.PHOENIX.get()))); // kök renkler: 0xCC6600 / 0xFFFF40

    public static final RegistrySupplier<Item> THUNDERBIRD_SPAWN_EGG =
            ITEMS.register("thunderbird_spawn_egg", key -> new SpawnEggItem(
                    new Item.Properties().setId(key).spawnEgg(ModEntities.THUNDERBIRD.get()))); // kök renkler: 0xFFCC46 / 0xFFFFCC

    public static final RegistrySupplier<Item> SNOWY_OWL_SPAWN_EGG =
            ITEMS.register("snowy_owl_spawn_egg", key -> new SpawnEggItem(
                    new Item.Properties().setId(key).spawnEgg(ModEntities.SNOWY_OWL.get()))); // kök renkler: 0xFFFFFF / 0xCCCCCC

    public static final RegistrySupplier<Item> THESTRAL_SPAWN_EGG =
            ITEMS.register("thestral_spawn_egg", key -> new SpawnEggItem(
                    new Item.Properties().setId(key).spawnEgg(ModEntities.THESTRAL.get()))); // kök renkler: 0x333333 / 0x666666

    public static final RegistrySupplier<Item> UNICORN_SPAWN_EGG =
            ITEMS.register("unicorn_spawn_egg", key -> new SpawnEggItem(
                    new Item.Properties().setId(key).spawnEgg(ModEntities.UNICORN.get()))); // kök renkler: 0xFFFFFF / 0xE8C8FF

    // ---- Yeni yaratıklar (Hippogriff/Acromantula/Kurtadam/Grindylow/Kneazle) ----
    public static final RegistrySupplier<Item> HIPPOGRIFF_SPAWN_EGG =
            ITEMS.register("hippogriff_spawn_egg", key -> new SpawnEggItem(
                    new Item.Properties().setId(key).spawnEgg(ModEntities.HIPPOGRIFF.get()))); // kök renkler: 0x8B5A2B / 0xE8DCC8

    public static final RegistrySupplier<Item> ACROMANTULA_SPAWN_EGG =
            ITEMS.register("acromantula_spawn_egg", key -> new SpawnEggItem(
                    new Item.Properties().setId(key).spawnEgg(ModEntities.ACROMANTULA.get()))); // kök renkler: 0x1A1410 / 0x6B4423

    public static final RegistrySupplier<Item> WEREWOLF_SPAWN_EGG =
            ITEMS.register("werewolf_spawn_egg", key -> new SpawnEggItem(
                    new Item.Properties().setId(key).spawnEgg(ModEntities.WEREWOLF.get()))); // kök renkler: 0x3A3A3A / 0xC0C0C0

    public static final RegistrySupplier<Item> GRINDYLOW_SPAWN_EGG =
            ITEMS.register("grindylow_spawn_egg", key -> new SpawnEggItem(
                    new Item.Properties().setId(key).spawnEgg(ModEntities.GRINDYLOW.get()))); // kök renkler: 0x2E5D4E / 0x8FC9B8

    public static final RegistrySupplier<Item> KNEAZLE_SPAWN_EGG =
            ITEMS.register("kneazle_spawn_egg", key -> new SpawnEggItem(
                    new Item.Properties().setId(key).spawnEgg(ModEntities.KNEAZLE.get()))); // kök renkler: 0xD2A050 / 0x5A4632

    /** Büyücü tüccar — koyu mor cübbe + altın şerit. */
    public static final RegistrySupplier<Item> WIZARD_TRADER_SPAWN_EGG =
            ITEMS.register("wizard_trader_spawn_egg", key -> new SpawnEggItem(
                    new Item.Properties().setId(key).spawnEgg(ModEntities.WIZARD_TRADER.get()))); // kök renkler: 0x5B3A8C / 0xD9A441

    /** Görünmezlik Pelerini — göğüslük slotu, giyiliyken sessiz görünmezlik verir.
     * 26.x: zırh bileşenleri (EQUIPPABLE vb.) CloakOfInvisibilityItem kurucusunda eklenir — KIRILMAZ. */
    public static final RegistrySupplier<Item> CLOAK_OF_INVISIBILITY =
            ITEMS.register("cloak_of_invisibility", key -> new CloakOfInvisibilityItem(
                    new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.EPIC)));

    /** Seçmen Şapka — kafaya giyilir: +1 zırh, +15 mana kapasitesi, +%10 mana
     * yenilenmesi. Craftlanamaz; maden ocağı + Hogsmeade sandık lootu.
     * 26.x: EQUIPPABLE bileşeni SortingHatItem kurucusunda eklenir. */
    public static final RegistrySupplier<Item> SORTING_HAT =
            ITEMS.register("sorting_hat", key -> new SortingHatItem(
                    new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE)
                            .attributes(SortingHatItem.createAttributes())));

    // ---- İksir malzemeleri ----
    /** Troll derisi — Troll'den düşer, Girding İksiri'nin ana malzemesi. */
    public static final RegistrySupplier<Item> TROLL_HIDE =
            ITEMS.register("troll_hide", key -> new Item(new Item.Properties().setId(key)));

    /** Anka tüyü — tamamlanmış Phoenix'ten makasla koparılır. */
    public static final RegistrySupplier<Item> PHOENIX_FEATHER =
            ITEMS.register("phoenix_feather", key -> new Item(new Item.Properties().setId(key).rarity(Rarity.UNCOMMON)));

    /** Gökgürültü Kuşu tüyü — tamamlanmış Thunderbird'den makasla koparılır. */
    public static final RegistrySupplier<Item> THUNDERBIRD_FEATHER =
            ITEMS.register("thunderbird_feather", key -> new Item(new Item.Properties().setId(key).rarity(Rarity.UNCOMMON)));

    /** Kar Baykuşu tüyü — tamamlanmış Snowy Owl'dan makasla koparılır. */
    public static final RegistrySupplier<Item> SNOWY_OWL_FEATHER =
            ITEMS.register("snowy_owl_feather", key -> new Item(new Item.Properties().setId(key).rarity(Rarity.UNCOMMON)));

    // ---- Arcanewood bot ----
    // 26.x: vanilla BoatItem kurucusu entity tipini ANINDA ister (spawn yumurtasıyla aynı güvenli sıra).
    public static final RegistrySupplier<Item> ARCANEWOOD_BOAT =
            ITEMS.register("arcanewood_boat", key -> new ArcanewoodBoatItem(ModEntities.ARCANEWOOD_BOAT.get(),
                    new Item.Properties().setId(key).stacksTo(1)));

    public static final RegistrySupplier<Item> ARCANEWOOD_CHEST_BOAT =
            ITEMS.register("arcanewood_chest_boat", key -> new ArcanewoodBoatItem(ModEntities.ARCANEWOOD_CHEST_BOAT.get(),
                    new Item.Properties().setId(key).stacksTo(1)));

    // ---- Büyü Masası ilerleme sistemi: kara büyü kitabı + reagent'lar ----
    /** Karanlık Büyü Kitabı — Wizard köylüsünden ASLA satılmaz; sadece maden/kamp
     * sandıklarında bulunur. Dark + Unforgivable kademesini Büyü Masası'nda açar. */
    public static final RegistrySupplier<Item> DARK_SPELL_BOOK =
            ITEMS.register("dark_spell_book", key -> new Item(
                    new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE)));

    /** Pratik Tebeşiri — kağıt + kömür ile üretilir (easy, basic büyüler için). */
    public static final RegistrySupplier<Item> PRACTICE_CHALK =
            ITEMS.register("practice_chalk", key -> new Item(new Item.Properties().setId(key)));

    /** Büyü Diyagramı — kağıt + tüy + mürekkep kesesi ile üretilir (easy, basic büyüler için). */
    public static final RegistrySupplier<Item> SPELL_DIAGRAM =
            ITEMS.register("spell_diagram", key -> new Item(new Item.Properties().setId(key)));

    /** Ay Danası Tozu — Mooncalf'tan nadir düşer (medium, advanced büyüler için). */
    public static final RegistrySupplier<Item> MOONCALF_DUST =
            ITEMS.register("mooncalf_dust", key -> new Item(new Item.Properties().setId(key).rarity(Rarity.UNCOMMON)));

    /** Acromantula İpeği — Acromantula'dan düşer veya ip+örümcek gözüyle üretilir
     * (medium, advanced büyüler için). */
    public static final RegistrySupplier<Item> ACROMANTULA_SILK =
            ITEMS.register("acromantula_silk", key -> new Item(new Item.Properties().setId(key).rarity(Rarity.UNCOMMON)));

    /** Ruh Emici Özü — Dementor'dan nadir düşer, üretilemez (hard, dark büyüler için). */
    public static final RegistrySupplier<Item> DEMENTOR_ESSENCE =
            ITEMS.register("dementor_essence", key -> new Item(new Item.Properties().setId(key).rarity(Rarity.RARE)));

    /** Basilisk Dişi Parçası — yalnızca Ölümyiyen kampı sandığında bulunur (hard,
     * dark büyüler için). Bu modda Basilisk yaratığı yok — saf loot itemi. */
    public static final RegistrySupplier<Item> BASILISK_FANG_SHARD =
            ITEMS.register("basilisk_fang_shard", key -> new Item(new Item.Properties().setId(key).rarity(Rarity.RARE)));

    /** Horcrux Parçası — modun en nadir eşyası, sadece derin loot tablolarında
     * bulunur, üretilemez (very hard, unforgivable büyüler için — tek kabul edilen
     * reagent, kasıtlı olarak alternatifi yok). */
    public static final RegistrySupplier<Item> HORCRUX_FRAGMENT =
            ITEMS.register("horcrux_fragment", key -> new Item(new Item.Properties().setId(key).rarity(Rarity.EPIC)));

    // ---- Craftlanabilir combo reagent'lar (eldeki item'lardan üretilir) ----
    /** Büyülü Bal Peteği — bal peteği + parlataşı tozu ile üretilir (basic reagent). */
    public static final RegistrySupplier<Item> ENCHANTED_HONEYCOMB =
            ITEMS.register("enchanted_honeycomb", key -> new Item(new Item.Properties().setId(key)));

    /** Baykuş Tılsımı — kar baykuşu tüyü + altın külçesi + ip ile üretilir (basic reagent). */
    public static final RegistrySupplier<Item> OWL_CHARM =
            ITEMS.register("owl_charm", key -> new Item(new Item.Properties().setId(key)));

    /** Ezilmiş Ametist — ametist parçasından üretilir (advanced reagent). */
    public static final RegistrySupplier<Item> CRUSHED_AMETHYST =
            ITEMS.register("crushed_amethyst", key -> new Item(new Item.Properties().setId(key).rarity(Rarity.UNCOMMON)));

    /** Anka Kalemi — anka tüyü + mürekkep kesesi ile üretilir (advanced reagent). */
    public static final RegistrySupplier<Item> PHOENIX_QUILL =
            ITEMS.register("phoenix_quill", key -> new Item(new Item.Properties().setId(key).rarity(Rarity.UNCOMMON)));

    /** Fırtına Şişesi — gökgürültü kuşu tüyü + cam şişe + kızıltaş ile üretilir (advanced reagent). */
    public static final RegistrySupplier<Item> STORM_VIAL =
            ITEMS.register("storm_vial", key -> new Item(new Item.Properties().setId(key).rarity(Rarity.UNCOMMON)));

    // ---- Yasak Lanetler Kitabı (unforgivable) — 3 laneti birden açar ----
    /** Yasak Lanetler Kitabı — Büyü Masası'nda 3 affedilmez laneti (Avada
     * Kedavra + Crucio + Imperio) birden açar. Basilisk boss'undan düşer. */
    public static final RegistrySupplier<Item> UNFORGIVABLE_GRIMOIRE =
            ITEMS.register("unforgivable_grimoire", key -> new Item(
                    new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.EPIC)));

    /**
     * Başlangıç Büyü Kitabı — Wizard köylüsünden ucuza satın alınır, Büyü
     * Masası'nda basic+advanced kademesindeki büyüleri öğrenmeye erişim açar.
     * NOT: Şimdilik düz bir Item olarak kayıtlı — asıl "kademe anahtarı" item
     * sınıfı (TIER_KEY component'i taşıyan TierSpellBookItem) ilerleme
     * mantığını üstlenen dilimde eklenecek/uygulanacak. Kayıt adı
     * (ModItems.STARTER_SPELL_BOOK) sabit tutuldu; ileride sınıf değişse bile
     * tüccar/tarif/creative-tab referansları etkilenmez.
     */
    public static final RegistrySupplier<Item> STARTER_SPELL_BOOK =
            ITEMS.register("starter_spell_book", key -> new Item(
                    new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));

    // ---- Süpürge kademeleri ----
    /** Oakshaft 79 — ucuz/yavaş başlangıç süpürgesi (kademe 0). */
    public static final RegistrySupplier<Item> OAKSHAFT_BROOM =
            ITEMS.register("oakshaft_broom", key -> new BroomItem(BroomEntity.TIER_OAKSHAFT,
                    new Item.Properties().setId(key).stacksTo(1)));

    /** Comet 260 — orta seviye süpürge (kademe 1). */
    public static final RegistrySupplier<Item> COMET_BROOM =
            ITEMS.register("comet_broom", key -> new BroomItem(BroomEntity.TIER_COMET,
                    new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));

    private ModItems() {}

    public static void init() {
        ITEMS.register();
    }
}
