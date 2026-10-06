package com.arcanum.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.arcanum.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Arcanum YÜZEY SPAWNER'ı — vanilla doğal spawn'ın üstüne, oyuncu çevresinde büyük
 * yüzey yaratıklarının GARANTİ görünmesini sağlayan tamamlayıcı spawner.
 *
 * <p><b>Neden gerekli:</b> Troll (ve diğer yüzey canavarları) {@code MobCategory.MONSTER}.
 * Gündüz yüzeyde vanilla canavarlar ışık yüzünden doğmaz; ama yer altındaki mağaralarda
 * karanlıkta doğan zombi/iskeletler <b>global MONSTER mob-cap'ini doldurur</b> → yüzeydeki
 * troll spawn denemelerine sıra gelmez. Sonuç: oyuncu gündüz ovada saatlerce dolaşır, hiç
 * troll görmez. Bu spawner o cap yarışını atlar: her ~5 sn'de bir, uygun biyomdaki her
 * oyuncunun 48–60 blok çevresinde, YEREL bir kapasiteye (ör. 2 troll / 96 blok) kadar,
 * checkSpawnRules benzeri geçerlilik kontrolüyle (sağlam zemin + boş gövde + sıvısız) doğal
 * ({@link EntitySpawnReason#NATURAL}) yaratık doğurur. Böylece "gündüz de spawn olsunlar" isteği
 * garanti karşılanır ve "hiç göremiyorum" durumu biter.
 */
public final class ArcanumSurfaceSpawner {
    private ArcanumSurfaceSpawner() {}

    /** Deneme turu aralığı (tick). 100 = ~5 sn. */
    private static final int INTERVAL = 100;
    /** Oyuncudan minimum/maksimum spawn mesafesi (24 blok altı vanilla'da yasak). */
    private static final int MIN_DIST = 48;
    private static final int MAX_DIST = 60;
    /** Yerel cap ölçüm yarıçapı (blok). */
    private static final int COUNT_RADIUS = 96;
    /** Aday yüzey Y'si oyuncunun Y'sinden bu kadar uzaksa atla (mağaradaki oyuncuya
     *  göremeyeceği yüzey spawn'ı yapma). */
    private static final int MAX_Y_DELTA = 26;
    // NOT: deneme olasılığı artık kullanıcı config'inden okunur
    // (ArcanumConfig.surfaceSpawnerChance, varsayılan 0.35) — sabit kaldırıldı.

    private enum When { ANY, DAY, NIGHT }

    private record Entry(Supplier<EntityType<? extends Mob>> type,
                         Set<ResourceKey<Biome>> biomes, When when, int localCap,
                         java.util.function.ToDoubleFunction<com.arcanum.config.ArcanumConfig> mult) {}

    /**
     * Garanti edilen yüzey yaratıkları. Biyom listeleri {@code ArcanumFabric} addSpawn
     * girdileriyle uyumlu. Troll HER ZAMAN (gündüz dahil); karanlık orman tehditleri de
     * atmosfer için eklendi.
     */
    private static final List<Entry> ENTRIES = List.of(
            new Entry(() -> ModEntities.TROLL.get(),
                    Set.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW,
                            Biomes.DARK_FOREST, Biomes.WINDSWEPT_FOREST,
                            Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS),
                    When.ANY, 2,
                    c -> c.trollSpawnMult),
            // Kurtadam — GECE avcısı. Bu spawner canSpawn'ı ATLADIĞI için (trySpawnNear
            // yalnız isSpawnable çağırır) WerewolfEntity.canSpawn'daki gece şartı burada
            // When.NIGHT ile verilir; eski When.ANY dark_forest'ta gündüz kurtadam doğurup
            // lore'u bozuyordu. Kullanıcı şikayeti "hiç werewolf görmedim": biyom kümesi
            // vanilla orman/tayga biyomlarına genişletildi (ArcanumFabric addSpawn ile uyumlu).
            new Entry(() -> ModEntities.WEREWOLF.get(),
                    Set.of(Biomes.DARK_FOREST, Biomes.FOREST, Biomes.BIRCH_FOREST,
                            Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA,
                            Biomes.OLD_GROWTH_SPRUCE_TAIGA),
                    When.NIGHT, 2,
                    c -> c.werewolfSpawnMult),
            new Entry(() -> ModEntities.ACROMANTULA.get(),
                    Set.of(Biomes.DARK_FOREST), When.ANY, 2,
                    c -> c.acromantulaSpawnMult),
            new Entry(() -> ModEntities.DEATH_EATER.get(),
                    Set.of(Biomes.DARK_FOREST), When.ANY, 2,
                    c -> c.deathEaterSpawnMult),
            // REAGENT MOB'LARI (tester: "büyü öğrenmek için gereken kuşlar/moblar
            // sıklaşsın"). BOŞ biyom kümesi = HER biyom (bkz. inBiome). CREATURE cap'i
            // dolu mevcut dünyalarda addSpawn işe yaramaz — bu spawner oyuncu yakınında
            // AKTİF doğurur; kalıcılık + yerel(2)/global(6) tavanlar birikimi sınırlar.
            // Kuşlar gündüz (canSpawn ışık şartıyla uyumlu), Mooncalf her saat.
            // Efsanevi kuşların yerel tavanı 2 → 1: boş biyom kümesi (HER biyom) + mult 1.0
            // muafiyeti yüzünden bu üç giriş her konumda uygun sayılıyor ve 96 blokluk
            // yarıçapta 2+2+2 = 6 egzotik kuş birikebiliyordu ("creatures spawn quite
            // frequently"). Tavan 1 olunca tüy/reagent erişimi korunur (kuş hep bulunur),
            // ama sürü hissi kalkar; global tavan da localCap*3 = 3'e iner.
            new Entry(() -> ModEntities.PHOENIX.get(), Set.of(), When.DAY, 1,
                    c -> c.phoenixSpawnMult),
            new Entry(() -> ModEntities.THUNDERBIRD.get(), Set.of(), When.DAY, 1,
                    c -> c.thunderbirdSpawnMult),
            new Entry(() -> ModEntities.SNOWY_OWL.get(), Set.of(), When.DAY, 1,
                    c -> c.snowyOwlSpawnMult),
            new Entry(() -> ModEntities.MOONCALF.get(), Set.of(), When.ANY, 2,
                    c -> c.mooncalfSpawnMult),
            // BİNEK/EVCİL YARATIKLAR (spawn denetimi bulgusu): CREATURE mob-cap'i mevcut
            // dünyalarda worldgen hayvanlarıyla zaten dolu olduğundan addSpawn tek başına
            // pratikte HİÇ doğurmuyordu — reagent kuşlarıyla aynı kök neden. Biyom listeleri
            // ArcanumFabric addSpawn girdileriyle birebir; yerel(2)/global(6) tavanlar birikimi sınırlar.
            new Entry(() -> ModEntities.THESTRAL.get(),
                    Set.of(Biomes.DARK_FOREST), When.ANY, 2,
                    c -> c.thestralSpawnMult),
            new Entry(() -> ModEntities.UNICORN.get(),
                    Set.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.FLOWER_FOREST),
                    When.DAY, 2,
                    c -> c.unicornSpawnMult),
            new Entry(() -> ModEntities.HIPPOGRIFF.get(),
                    Set.of(Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS,
                            Biomes.MEADOW, Biomes.WINDSWEPT_FOREST),
                    When.DAY, 2,
                    c -> c.hippogriffSpawnMult),
            new Entry(() -> ModEntities.KNEAZLE.get(),
                    Set.of(Biomes.PLAINS, Biomes.FOREST, Biomes.SUNFLOWER_PLAINS),
                    When.ANY, 2,
                    c -> c.kneazleSpawnMult),
            // Bowtruckle — denetim bulgusu: YALNIZ özel biyomlara (gloomwood/arcanewood_grove)
            // kayıtlıydı; o biyomları bulamayan oyuncu hiç göremiyordu. Vanilla ormanlara da eklendi.
            new Entry(() -> ModEntities.BOWTRUCKLE.get(),
                    Set.of(Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST,
                            Biomes.FLOWER_FOREST, Biomes.DARK_FOREST),
                    When.DAY, 2,
                    c -> c.bowtruckleSpawnMult));

    private static int ticks = 0;

    /** {@code ServerTickEvents.END_SERVER_TICK}'e kaydedilir (bkz. ArcanumFabric). */
    public static void tick(MinecraftServer server) {
        if (!com.arcanum.config.ArcanumConfig.get().surfaceSpawnerEnabled) {
            return; // kullanıcı config: yüzey spawner kapalı
        }
        if (++ticks % INTERVAL != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() != Level.OVERWORLD) {
                continue; // sadece overworld yüzeyi
            }
            if (level.getDifficulty() == Difficulty.PEACEFUL) {
                continue; // barışçılda hostile spawn yok
            }
            RandomSource rand = level.getRandom();
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator()) {
                    continue;
                }
                if (rand.nextFloat() > (float) com.arcanum.config.ArcanumConfig.get().surfaceSpawnerChance) {
                    continue;
                }
                trySpawnNear(level, player.getX(), player.getZ(), player.getBlockY(), rand);
            }
        }
    }

    /**
     * Verilen (px,pz) merkezinin çevresinde tek bir spawn dener. Başarılıysa {@code true}.
     * {@code refY} = referans yükseklik (oyuncunun Y'si); yüzey bundan {@link #MAX_Y_DELTA}
     * fazla uzaksa atlanır. {@code refY == Integer.MIN_VALUE} → Y kontrolü yapılmaz
     * (komut burst testi konsoldan çağırırken).
     */
    private static boolean trySpawnNear(ServerLevel level, double px, double pz, int refY, RandomSource rand) {
        double ang = rand.nextDouble() * Math.PI * 2.0;
        double dist = MIN_DIST + rand.nextDouble() * (MAX_DIST - MIN_DIST);
        int x = Mth.floor(px + Math.cos(ang) * dist);
        int z = Mth.floor(pz + Math.sin(ang) * dist);
        if (!level.hasChunkAt(new BlockPos(x, 64, z))) {
            return false; // yüklenmemiş chunk — zorla yükleme yapma
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (refY != Integer.MIN_VALUE && Math.abs(y - refY) > MAX_Y_DELTA) {
            return false; // yüzey oyuncudan çok uzakta (oyuncu mağarada) — atla
        }
        BlockPos pos = new BlockPos(x, y, z);
        Holder<Biome> biome = level.getBiome(pos);
        boolean day = level.isBrightOutside();

        List<Entry> eligible = new ArrayList<>();
        for (Entry e : ENTRIES) {
            if (matchesWhen(e.when(), day) && inBiome(e, biome)) {
                eligible.add(e);
            }
        }
        if (eligible.isEmpty()) {
            return false;
        }
        Entry chosen = eligible.get(rand.nextInt(eligible.size()));
        EntityType<? extends Mob> et = chosen.type().get();
        // KULLANICI CONFIG'İ: mob-başına spawn çarpanı (reagent verenler 1.0,
        // diğerleri 0.85). Yalnız SEÇİLEN girdiye uygulanır — surfaceSpawnerChance
        // kapısına DEĞİL, yoksa reagent mob'ları da birlikte kısılırdı.
        // ÇİFT UYGULAMA YOK: bu spawner SpawnPlacements.checkSpawnRules'ı çağırmaz,
        // dolayısıyla ModEntities'teki SpawnGate.gated kapısından geçmez.
        if (!SpawnGate.roll(chosen.mult(), rand)) {
            return false;
        }

        AABB countBox = new AABB(pos).inflate(COUNT_RADIUS);
        int nearby = level.getEntitiesOfClass(Mob.class, countBox, m -> m.getType() == et).size();
        if (nearby >= chosen.localCap()) {
            return false;
        }
        // GLOBAL TAVAN: mob'lar setPersistenceRequired() ile artık despawn OLMADIĞINDAN,
        // yerel yoğunluk kontrolü tek başına dünya-genel toplamı sınırlamaz — oyuncu
        // keşfettikçe geride kalan kalıcı mob'lar birikip geri dönüşte sürü/lag yapardı.
        // Oyuncu-merkezli geniş (yüklü) bir kutuda türün toplamı localCap*3'ü aşarsa yeni
        // spawn yok: kaybolma düzeltmesi korunur ama birikim güvenli bir tavana bağlanır.
        // 26.x: getMinBuildHeight/getMaxBuildHeight → getMinY/getMaxY. getMaxY() DAHİL (319),
        // eski getMaxBuildHeight HARİÇ (320) idi → +1 ile birebir aynı kutu.
        AABB globalBox = new AABB(px - 160, level.getMinY(), pz - 160,
                px + 160, level.getMaxY() + 1, pz + 160);
        int loadedNear = level.getEntitiesOfClass(Mob.class, globalBox, m -> m.getType() == et).size();
        if (loadedNear >= chosen.localCap() * 3) {
            return false;
        }
        if (!isSpawnable(level, pos, et)) {
            return false;
        }
        Mob mob = et.spawn(level, pos, EntitySpawnReason.NATURAL);
        if (mob != null) {
            // NATURAL spawn'lar kalıcı DEĞİL → oyuncu uzaklaşınca despawn olurdu.
            // Özel spawner ile doğanları kalıcı yap ki "mob'lar kayboluyor" bitsin.
            mob.setPersistenceRequired();
        }
        return mob != null;
    }

    private static boolean inBiome(Entry e, Holder<Biome> biome) {
        if (e.biomes().isEmpty()) {
            return true; // boş küme = HER biyom (reagent kuşları/mooncalf)
        }
        for (ResourceKey<Biome> k : e.biomes()) {
            if (biome.is(k)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesWhen(When w, boolean day) {
        return w == When.ANY || (w == When.DAY) == day;
    }

    /** Konum doğal spawn için uygun mu: altı katı zemin, pos+üstü boş, sıvısız, gövde sığar. */
    private static boolean isSpawnable(ServerLevel level, BlockPos pos, EntityType<? extends Mob> et) {
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        if (!ground.blocksMotion()) {
            return false; // üstünde durulacak sağlam zemin yok (hava/su/uzun ot)
        }
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(below).isEmpty()) {
            return false; // su/lav üstüne değil
        }
        AABB box = et.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return level.noCollision(box); // gövde (troll 2.8 blok) boşluğa sığıyor mu
    }

    // ---------------------------------------------------------------- komut yardımcıları

    /** '/arcanum spawncheck' → (x,z) çevresinde {@code attempts} deneme yapar, doğan sayısını döner.
     *  Y kontrolü yapılmaz (konsol/RCON'dan da çalışsın diye). */
    public static int forceBurst(ServerLevel level, double x, double z, int attempts) {
        RandomSource rand = level.getRandom();
        int spawned = 0;
        for (int i = 0; i < attempts; i++) {
            if (trySpawnNear(level, x, z, Integer.MIN_VALUE, rand)) {
                spawned++;
            }
        }
        return spawned;
    }

    /** '/arcanum spawncheck' → bulunulan biyomda hangi Arcanum yaratıklarının garanti
     *  spawnlanabileceğini + mevcut yakın sayılarını insan-okur metin olarak döner. */
    public static List<String> eligibilityReport(ServerLevel level, BlockPos pos) {
        List<String> out = new ArrayList<>();
        Holder<Biome> biome = level.getBiome(pos);
        String biomeName = biome.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
        boolean day = level.isBrightOutside();
        out.add("Biyom: " + biomeName + " | " + (day ? "gündüz" : "gece"));
        for (Entry e : ENTRIES) {
            EntityType<? extends Mob> et = e.type().get();
            boolean bOk = inBiome(e, biome);
            boolean tOk = matchesWhen(e.when(), day);
            AABB box = new AABB(pos).inflate(COUNT_RADIUS);
            int nearby = level.getEntitiesOfClass(Mob.class, box, m -> m.getType() == et).size();
            String name = et.getDescription().getString();
            String status = (bOk && tOk)
                    ? "UYGUN (" + nearby + "/" + e.localCap() + " yakinda)"
                    : (!bOk ? "bu biyomda yok" : "bu saatte yok");
            out.add((bOk && tOk ? "[+] " : "[-] ") + name + ": " + status);
        }
        return out;
    }
}
