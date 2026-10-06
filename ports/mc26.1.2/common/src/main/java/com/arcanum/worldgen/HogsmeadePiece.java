package com.arcanum.worldgen;

import com.arcanum.Arcanum;
import com.arcanum.entity.WizardTraderEntity;
import com.arcanum.registry.ModBlocks;
import com.arcanum.registry.ModStructures;
import com.arcanum.util.VanillaBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Hogsmeade köyünün tek prosedürel {@link StructurePiece}'i — MEGA sürüm.
 * ~89x89'luk parsel ("iki köy birleşimi" ölçeği): merkezde kuyu + fener
 * direkli taş meydan, meydan etrafında KIVRIMLI çakıl/patika sokaklar ve
 * 11 bina. Her binanın kendine has silüeti + renk kimliği vardır (terakota/
 * yün aksanlı, bina başına FARKLI malzemeden dik basamak çatılar):
 * <ul>
 *   <li><b>Üç Süpürge (han)</b> — 13x10, 2 katlı; ladin kütük iskelet +
 *       arcanewood duvar, taş tuğla baca (tepesinde tüten kamp ateşi);
 *       wizard_trader (varyant 0) + sandık {@code arcanum:chests/hogsmeade_inn}.</li>
 *   <li><b>Honeydukes (şekerci)</b> — beyaz terakota + pembe şerit + kiraz
 *       çatı; pasta tezgâhı; sandık {@code hogsmeade_honeydukes}.</li>
 *   <li><b>Asa Dükkânı</b> — uzun-dar-ÇARPIK: üst kat her yana 1-2 blok
 *       taşar (jetty); koyu meşe çatı; wizard_trader (varyant 0) + sandık
 *       {@code hogsmeade_wand_shop}.</li>
 *   <li><b>İKSİRCİ</b> — yeşil terakota + mor şerit + purpur çatı; içeride
 *       iksir kazanı, 2 simya standı, şişe rafları; wizard_trader
 *       (VARYANT 1 = iksir satıcısı) + YENİ sandık
 *       {@code arcanum:chests/hogsmeade_potion_shop}.</li>
 *   <li><b>Baykuşhane</b> — 13 blok taş tuğla kule, mazgallı tepe, açık
 *       kemer pencereler, tünekler + saman; küçük sandık (han tablosu).</li>
 *   <li><b>Kitapçı</b> — çamur tuğla duvar + çamur tuğla çatı, kitaplık
 *       dolu iç mekân.</li>
 *   <li><b>Cübbeci</b> — beyaz terakota üzerine RENKLİ yün şeritler,
 *       warped (turkuaz) çatı, tezgâh + kumaş topları.</li>
 *   <li><b>Çarpık büyücü evi #1</b> — açık mavi zemin kat, üstte her yana
 *       taşan koyu mavi jetty kat, deepslate kiremit çatı; küçük sandık.</li>
 *   <li><b>Çarpık büyücü evi #2</b> — lime/sarı terakota jetty ev, yosunlu
 *       taş tuğla çatı.</li>
 *   <li><b>Şirin köy evi</b> — taş temel + meşe, meşe çatı, saksılar,
 *       kendi bacası; en "normal köy" görünümlü ev.</li>
 *   <li><b>Türbe/çeşme köşesi</b> — taş tuğla platform, fıskiyeli havuz,
 *       yanan mumlar + ametist kümeleri.</li>
 * </ul>
 *
 * <p>KRİTİK (bkz. {@link ForgottenChamberPiece}): {@link #postProcess} yapının
 * kestiği HER chunk için AYRI çağrılır ve {@code box} yalnızca o chunk'ın
 * yazılabilir kutusudur. Bu yüzden her blok yerleştirmesi {@code box}'a
 * KIRPILIR; sandık/varlık üretimi yalnızca kendi konumu {@code box} içindeyken
 * 1 kez yapılır (çoklu sandık/NPC önlenir). Piece bounding box'ı parselin
 * TAMAMINI kapsar — küçük kalırsa uzak binalar üretilmez. Ayrıca TÜM bina
 * geometrisi origin'e göre SABİTTİR (chunk'a/random'a bağlı değil) — böylece
 * farklı chunk geçişleri aynı binayı birebir aynı üretir; {@code random}
 * yalnızca yol dokusunda (çakıl/patika karışımı, hücre başına 1 kez yazılır)
 * ve sandık loot tohumunda kullanılır.
 *
 * <p>ZEMİN: eski küresel düzleme kaldırıldı. Her bina PADİ ayrı düzlenir
 * (origin yürüme seviyesine: altı toprak dolgu, üstü temizlik); SOKAKLAR ise
 * kolon başına {@code level.getHeight(WORLD_SURFACE_WG)} ile araziyi takip
 * eder (uçurum kolonlarında yol atlanır). Fenerler cömerttir — gece canlı
 * görünür.
 */
public class HogsmeadePiece extends StructurePiece {

    // --- Loot tabloları ---
    private static final ResourceKey<LootTable> INN_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(Arcanum.MODID, "chests/hogsmeade_inn"));
    private static final ResourceKey<LootTable> HONEYDUKES_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(Arcanum.MODID, "chests/hogsmeade_honeydukes"));
    private static final ResourceKey<LootTable> WAND_SHOP_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(Arcanum.MODID, "chests/hogsmeade_wand_shop"));
    private static final ResourceKey<LootTable> POTION_SHOP_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(Arcanum.MODID, "chests/hogsmeade_potion_shop"));

    /** Handa/asa dükkânında/iksircide doğacak NPC (kayıtlı değilse sessizce atlanır). */
    private static final Identifier TRADER_ID =
            Identifier.fromNamespaceAndPath(Arcanum.MODID, "wizard_trader");

    // --- Parsel geometrisi (origin = meydan merkezi, yürüme seviyesi) ---
    /** Parsel yarıçapı: dx,dz ∈ [-PLOT_HALF..PLOT_HALF] → 89x89 ("2 köy"). */
    private static final int PLOT_HALF = 44;
    /** Bina padlerinde zemin dolgusunun origin'e göre en derin katı (dy). */
    private static final int FILL_DEPTH = 8;
    /** Pad üst temizliğinin origin'e göre en yüksek katı (dy) — ağaç süpürme. */
    private static final int CLEAR_HEIGHT = 18;
    /** Sokak kolonu origin'den bu kadar AŞAĞIDAysa uçurum sayılır, yol atlanır. */
    private static final int PATH_DROP_LIMIT = 10;
    /** Sokak kolonu origin'den bu kadar YUKARIDAysa yol atlanır. */
    private static final int PATH_RISE_LIMIT = 18;

    /** Meydan merkezi, yürüme seviyesi (dünya koordinatı). NBT'ye ox/oy/oz yazılır. */
    private final BlockPos origin;

    /**
     * İlk üretim constructor'u. BoundingBox parselin tamamını (±PLOT_HALF yatay;
     * dikeyde en derin dolgu/yol katından kule+baca üstüne) kapsar — böylece
     * 11 bina da kesiştikleri TÜM chunk'larda üretilir.
     */
    public HogsmeadePiece(BlockPos origin) {
        super(ModStructures.HOGSMEADE_PIECE.get(), 0, makeBox(origin));
        this.origin = origin;
    }

    /** NBT'den yeniden yükleme constructor'u (StructurePieceType SAM'i). */
    public HogsmeadePiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(ModStructures.HOGSMEADE_PIECE.get(), tag);
        this.origin = new BlockPos(tag.getIntOr("ox", 0), tag.getIntOr("oy", 0), tag.getIntOr("oz", 0));
    }

    private static BoundingBox makeBox(BlockPos origin) {
        // Dikey: arazi-takipli yolun en derin kolonu (−PATH_DROP_LIMIT−1) ile
        // pad dolgusunun (−FILL_DEPTH−1) altı .. en yüksek çatı/baca/kule tepesi + pay.
        return new BoundingBox(
                origin.getX() - PLOT_HALF, origin.getY() - 14, origin.getZ() - PLOT_HALF,
                origin.getX() + PLOT_HALF, origin.getY() + 30, origin.getZ() + PLOT_HALF);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("ox", origin.getX());
        tag.putInt("oy", origin.getY());
        tag.putInt("oz", origin.getZ());
    }

    // =====================================================================
    //  postProcess — tüm inşa (box'a KIRPILMIŞ)
    // =====================================================================

    @Override
    public void postProcess(WorldGenLevel level, StructureManager sm, ChunkGenerator cg,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        // Önce sokaklar (araziyi takip eder), sonra meydan padi, sonra binalar
        // (her bina kendi padini düzler ve sokak/çim üstüne inşa edilir).
        buildStreets(level, random, box);
        buildPlaza(level, random, box);
        buildInn(level, random, box);
        buildHoneydukes(level, random, box);
        buildWandShop(level, random, box);
        buildPotionShop(level, random, box);
        buildOwlery(level, random, box);
        buildBookShop(level, random, box);
        buildRobeShop(level, random, box);
        buildCrookedHouseBlue(level, random, box);
        buildCrookedHouseLime(level, random, box);
        buildCozyCottage(level, random, box);
        buildShrine(level, random, box);
    }

    // ---------------------------------------------------------------------
    //  Box-kırpımlı yerleştirme yardımcıları (origin-göreli koordinatlar)
    // ---------------------------------------------------------------------

    /** origin-göreli (dx,dy,dz) konumuna, {@code box}'a kırparak yerleştirir. */
    private void place(WorldGenLevel level, BoundingBox box, int dx, int dy, int dz, BlockState state) {
        BlockPos p = origin.offset(dx, dy, dz);
        if (box.isInside(p)) {
            level.setBlock(p, state, 2);
        }
    }

    /** origin-göreli dolu kutu doldurma (dahil sınırlar), box'a kırpılı. */
    private void fill(WorldGenLevel level, BoundingBox box,
                      int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int dx = x1; dx <= x2; dx++) {
            for (int dy = y1; dy <= y2; dy++) {
                for (int dz = z1; dz <= z2; dz++) {
                    place(level, box, dx, dy, dz, state);
                }
            }
        }
    }

    /** Kolonun (dx,dz) yatayda bu chunk box'ının içinde olup olmadığı. */
    private boolean columnInBox(BoundingBox box, int dx, int dz) {
        int wx = origin.getX() + dx;
        int wz = origin.getZ() + dz;
        return wx >= box.minX() && wx <= box.maxX() && wz >= box.minZ() && wz <= box.maxZ();
    }

    // ---------------------------------------------------------------------
    //  Bina padi: origin yürüme seviyesine düzleme (dolgu + temizlik + çim halka)
    // ---------------------------------------------------------------------

    /**
     * Bina taban dikdörtgenini (+1 çevre halkası) origin yürüme seviyesine
     * düzler: dy=-1 katı çim (bina zemini sonra üstüne yazar), altı toprak
     * dolgu (yalnız hava/sıvı hücreler), üstü {@link #CLEAR_HEIGHT}'a dek
     * temizlik. Geometri SABİT → her chunk geçişinde birebir aynı.
     */
    private void levelPad(WorldGenLevel level, BoundingBox box, int x1, int z1, int x2, int z2) {
        for (int dx = x1 - 1; dx <= x2 + 1; dx++) {
            for (int dz = z1 - 1; dz <= z2 + 1; dz++) {
                if (!columnInBox(box, dx, dz)) {
                    continue;
                }
                place(level, box, dx, -1, dz, Blocks.GRASS_BLOCK.defaultBlockState());
                for (int k = 2; k <= FILL_DEPTH; k++) {
                    BlockPos p = origin.offset(dx, -k, dz);
                    if (!box.isInside(p)) {
                        continue;
                    }
                    BlockState st = level.getBlockState(p);
                    if (st.isAir() || !st.getFluidState().isEmpty()) {
                        level.setBlock(p, Blocks.DIRT.defaultBlockState(), 2);
                    }
                }
                for (int k = 0; k <= CLEAR_HEIGHT; k++) {
                    BlockPos p = origin.offset(dx, k, dz);
                    if (box.isInside(p) && !level.getBlockState(p).isAir()) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    //  Sokaklar: arazi-takipli kıvrımlı yollar + fener direkleri
    // ---------------------------------------------------------------------

    /** Kıvrım ofseti — DETERMİNİSTİK sinüs (chunk'tan bağımsız, her geçişte aynı). */
    private static int windingOffset(int t, double phase) {
        return (int) Math.round(3.0 * Math.sin(t * 0.11 + phase));
    }

    /**
     * Tek yol kolonu: yüzey yüksekliği {@code WORLD_SURFACE_WG} ile örneklenir,
     * en üst blok çakıl/patika ile değiştirilir, üstü 3 blok yürünebilir
     * temizlenir. Kolon origin'den aşırı sapmışsa (uçurum/tepe) atlanır.
     * Her kolon tam 1 chunk box'ına düşer → tam 1 kez yazılır.
     */
    private void pathColumn(WorldGenLevel level, RandomSource random, BoundingBox box, int dx, int dz) {
        if (!columnInBox(box, dx, dz)) {
            return;
        }
        int wx = origin.getX() + dx;
        int wz = origin.getZ() + dz;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, wx, wz); // ilk boş (hava) Y
        int dy = top - 1 - origin.getY(); // yüzey bloğunun origin-göreli katı
        if (dy < -PATH_DROP_LIMIT || dy > PATH_RISE_LIMIT) {
            return; // uçurum/aşırı tepe: yol döşenmez
        }
        BlockState surface = random.nextInt(3) == 0
                ? Blocks.GRAVEL.defaultBlockState()
                : Blocks.DIRT_PATH.defaultBlockState();
        place(level, box, dx, dy, dz, surface);
        for (int k = 1; k <= 3; k++) {
            place(level, box, dx, dy + k, dz, Blocks.AIR.defaultBlockState());
        }
    }

    /** Düz yol şeridi (yan sokak/bina kolu) — kolon başına arazi-takipli. */
    private void lane(WorldGenLevel level, RandomSource random, BoundingBox box,
                      int x1, int z1, int x2, int z2) {
        for (int dx = x1; dx <= x2; dx++) {
            for (int dz = z1; dz <= z2; dz++) {
                pathColumn(level, random, box, dx, dz);
            }
        }
    }

    /** Arazi-takipli fener direği: yürüme seviyesi kolon yüksekliğinden örneklenir. */
    private void lampPostTerrain(WorldGenLevel level, BoundingBox box, int dx, int dz) {
        if (!columnInBox(box, dx, dz)) {
            return;
        }
        int wx = origin.getX() + dx;
        int wz = origin.getZ() + dz;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, wx, wz);
        int dy = top - origin.getY(); // ilk hava katı = direğin taban katı
        if (dy < -PATH_DROP_LIMIT || dy > PATH_RISE_LIMIT) {
            return;
        }
        for (int k = 0; k <= 2; k++) {
            place(level, box, dx, dy + k, dz, Blocks.SPRUCE_FENCE.defaultBlockState());
        }
        place(level, box, dx, dy + 3, dz, Blocks.LANTERN.defaultBlockState());
    }

    /** Sabit-yükseklikli fener direği (düzlenmiş meydan içi). */
    private void lampPost(WorldGenLevel level, BoundingBox box, int dx, int dz) {
        for (int dy = 0; dy <= 2; dy++) {
            place(level, box, dx, dy, dz, Blocks.SPRUCE_FENCE.defaultBlockState());
        }
        place(level, box, dx, 3, dz, Blocks.LANTERN.defaultBlockState());
    }

    private void buildStreets(WorldGenLevel level, RandomSource random, BoundingBox box) {
        // Doğu-batı ana sokak: kıvrımlı, 3 genişlik.
        for (int dx = -PLOT_HALF; dx <= PLOT_HALF; dx++) {
            int zc = windingOffset(dx, 0.4);
            for (int dz = zc - 1; dz <= zc + 1; dz++) {
                pathColumn(level, random, box, dx, dz);
            }
        }
        // Kuzey-güney ana sokak: farklı fazla kıvrımlı, 3 genişlik.
        for (int dz = -PLOT_HALF; dz <= PLOT_HALF; dz++) {
            int xc = windingOffset(dz, 1.9);
            for (int dx = xc - 1; dx <= xc + 1; dx++) {
                pathColumn(level, random, box, dx, dz);
            }
        }
        // Bina kolları (kapı önünden ana sokağa/meydana). Aralıklar kıvrımlı
        // sokağın ±4'lük salınım bandını GARANTİ kesecek kadar uzun tutuldu.
        lane(level, random, box, -19, -10, -17, -1);  // Üç Süpürge
        lane(level, random, box, 15, -10, 17, 4);     // Honeydukes
        lane(level, random, box, 10, 2, 12, 9);       // Asa Dükkânı
        lane(level, random, box, -15, -2, -13, 7);    // İksirci
        lane(level, random, box, 30, -27, 32, -2);    // Baykuşhane
        lane(level, random, box, 8, -1, 23, 1);       // Kitapçı
        lane(level, random, box, -25, -1, -8, 1);     // Cübbeci
        lane(level, random, box, -4, -23, -2, -6);    // Çarpık ev #1
        lane(level, random, box, -2, 22, 21, 24);     // Çarpık ev #2
        lane(level, random, box, -29, -1, -27, 19);   // Şirin köy evi
        lane(level, random, box, -3, 25, 1, 29);      // Türbe/çeşme

        // Sokak boyu fener direkleri — cömert aydınlatma (hepsi bina
        // padlerinin DIŞINDA; pad temizliği fenerleri silmesin).
        lampPostTerrain(level, box, -12, 4);
        lampPostTerrain(level, box, 12, -4);
        lampPostTerrain(level, box, 22, 4);
        lampPostTerrain(level, box, 34, -4);
        lampPostTerrain(level, box, -22, -4);
        lampPostTerrain(level, box, -36, 3);
        lampPostTerrain(level, box, 4, -14);
        lampPostTerrain(level, box, 3, -26);
        lampPostTerrain(level, box, 4, -38);
        lampPostTerrain(level, box, -4, 14);
        lampPostTerrain(level, box, 4, 26);
        lampPostTerrain(level, box, -4, 38);
        lampPostTerrain(level, box, 19, -8);
        lampPostTerrain(level, box, 33, -8);
        lampPostTerrain(level, box, -24, -3);
        lampPostTerrain(level, box, -28, 16);
        lampPostTerrain(level, box, 16, 22);
    }

    // ---------------------------------------------------------------------
    //  Meydan: düzlenmiş taş/çakıl daire + kuyu + fenerler
    // ---------------------------------------------------------------------

    private void buildPlaza(WorldGenLevel level, RandomSource random, BoundingBox box) {
        // Yarıçap 8 daire: origin seviyesine düzlenir (pad mantığı + yol dokusu).
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                if (dx * dx + dz * dz > 64 || !columnInBox(box, dx, dz)) {
                    continue;
                }
                BlockState surface = random.nextInt(3) == 0
                        ? Blocks.GRAVEL.defaultBlockState()
                        : Blocks.DIRT_PATH.defaultBlockState();
                place(level, box, dx, -1, dz, surface);
                for (int k = 2; k <= FILL_DEPTH; k++) {
                    BlockPos p = origin.offset(dx, -k, dz);
                    if (!box.isInside(p)) {
                        continue;
                    }
                    BlockState st = level.getBlockState(p);
                    if (st.isAir() || !st.getFluidState().isEmpty()) {
                        level.setBlock(p, Blocks.DIRT.defaultBlockState(), 2);
                    }
                }
                for (int k = 0; k <= 6; k++) {
                    BlockPos p = origin.offset(dx, k, dz);
                    if (box.isInside(p) && !level.getBlockState(p).isAir()) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        buildWell(level, box);

        // Meydan köşe fenerleri (düzlenmiş daire İÇİNDE → sabit yükseklik).
        lampPost(level, box, 5, 5);
        lampPost(level, box, -5, 5);
        lampPost(level, box, 5, -5);
        lampPost(level, box, -5, -5);
    }

    /** Meydan merkezine klasik taş kuyu: çerçeve + su + çit direkleri + kapak. */
    private void buildWell(WorldGenLevel level, BoundingBox box) {
        fill(level, box, -1, -2, -1, 1, -2, 1, Blocks.COBBLESTONE.defaultBlockState());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean rim = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                if (rim) {
                    place(level, box, dx, -1, dz, Blocks.COBBLESTONE.defaultBlockState());
                    place(level, box, dx, 0, dz, Blocks.COBBLESTONE.defaultBlockState());
                }
            }
        }
        place(level, box, 0, -1, 0, Blocks.WATER.defaultBlockState());
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                place(level, box, sx, 1, sz, Blocks.SPRUCE_FENCE.defaultBlockState());
                place(level, box, sx, 2, sz, Blocks.SPRUCE_FENCE.defaultBlockState());
            }
        }
        fill(level, box, -1, 3, -1, 1, 3, 1, Blocks.COBBLESTONE_SLAB.defaultBlockState());
        place(level, box, 0, 3, 0, Blocks.COBBLESTONE.defaultBlockState());
    }

    // ---------------------------------------------------------------------
    //  Ortak bina yardımcıları
    // ---------------------------------------------------------------------

    /**
     * Dikdörtgen bina kabuğu: plank/malzeme zemin (dy=-1), köşelerde dikey
     * direkler, aralarında duvar malzemesi. Duvarlar dy=y0..y0+wallH-1.
     */
    private void buildShell(WorldGenLevel level, BoundingBox box,
                            int x1, int z1, int x2, int z2, int y0, int wallH,
                            BlockState wall, BlockState cornerLog, BlockState floor) {
        fill(level, box, x1, y0 - 1, z1, x2, y0 - 1, z2, floor);
        for (int dx = x1; dx <= x2; dx++) {
            for (int dz = z1; dz <= z2; dz++) {
                boolean edgeX = dx == x1 || dx == x2;
                boolean edgeZ = dz == z1 || dz == z2;
                if (!edgeX && !edgeZ) {
                    continue;
                }
                boolean corner = edgeX && edgeZ;
                for (int dy = y0; dy < y0 + wallH; dy++) {
                    place(level, box, dx, dy, dz, corner ? cornerLog : wall);
                }
            }
        }
    }

    /** Duvarın belirli katını (köşeler hariç) aksan malzemesiyle şeritler. */
    private void accentBand(WorldGenLevel level, BoundingBox box,
                            int x1, int z1, int x2, int z2, int dy, BlockState accent) {
        for (int dx = x1; dx <= x2; dx++) {
            for (int dz = z1; dz <= z2; dz++) {
                boolean edgeX = dx == x1 || dx == x2;
                boolean edgeZ = dz == z1 || dz == z2;
                if ((edgeX || edgeZ) && !(edgeX && edgeZ)) {
                    place(level, box, dx, dy, dz, accent);
                }
            }
        }
    }

    /**
     * X ekseni boyunca sırtlı (mahya X'e paralel) dik beşik çatı. Saçaklar her
     * yönde 1 blok taşar; kalkan (gable) üçgenleri {@code gable} ile kapatılır.
     */
    private void roofAlongX(WorldGenLevel level, BoundingBox box,
                            int x1, int z1, int x2, int z2, int baseY,
                            BlockState stairsBase, BlockState ridge, BlockState gable) {
        int lo = z1 - 1;
        int hi = z2 + 1;
        int y = baseY;
        while (lo <= hi) {
            if (lo == hi) {
                for (int dx = x1 - 1; dx <= x2 + 1; dx++) {
                    place(level, box, dx, y, lo, ridge);
                }
            } else {
                for (int dx = x1 - 1; dx <= x2 + 1; dx++) {
                    place(level, box, dx, y, lo,
                            stairsBase.setValue(StairBlock.FACING, Direction.SOUTH));
                    place(level, box, dx, y, hi,
                            stairsBase.setValue(StairBlock.FACING, Direction.NORTH));
                }
                for (int dz = lo + 1; dz <= hi - 1; dz++) {
                    if (dz >= z1 && dz <= z2) {
                        place(level, box, x1, y, dz, gable);
                        place(level, box, x2, y, dz, gable);
                    }
                }
            }
            lo++;
            hi--;
            y++;
        }
    }

    /** {@link #roofAlongX}'in Z eksenli eşi (mahya Z'ye paralel). */
    private void roofAlongZ(WorldGenLevel level, BoundingBox box,
                            int x1, int z1, int x2, int z2, int baseY,
                            BlockState stairsBase, BlockState ridge, BlockState gable) {
        int lo = x1 - 1;
        int hi = x2 + 1;
        int y = baseY;
        while (lo <= hi) {
            if (lo == hi) {
                for (int dz = z1 - 1; dz <= z2 + 1; dz++) {
                    place(level, box, lo, y, dz, ridge);
                }
            } else {
                for (int dz = z1 - 1; dz <= z2 + 1; dz++) {
                    place(level, box, lo, y, dz,
                            stairsBase.setValue(StairBlock.FACING, Direction.EAST));
                    place(level, box, hi, y, dz,
                            stairsBase.setValue(StairBlock.FACING, Direction.WEST));
                }
                for (int dx = lo + 1; dx <= hi - 1; dx++) {
                    if (dx >= x1 && dx <= x2) {
                        place(level, box, dx, y, z1, gable);
                        place(level, box, dx, y, z2, gable);
                    }
                }
            }
            lo++;
            hi--;
            y++;
        }
    }

    /** Duvara 1x2 kapı boşluğu açar ve çift parçalı kapı yerleştirir (taban dy=0). */
    private void placeDoor(WorldGenLevel level, BoundingBox box,
                           int dx, int dz, Direction facing, BlockState doorBase) {
        BlockState lower = doorBase
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upper = doorBase
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        place(level, box, dx, 0, dz, lower);
        place(level, box, dx, 1, dz, upper);
    }

    /** X eksenli duvara (z sabit) gömülü cam pane — doğu/batı kollarına bağlı görünür. */
    private static BlockState paneAlongX() {
        return Blocks.GLASS_PANE.defaultBlockState()
                .setValue(CrossCollisionBlock.EAST, Boolean.TRUE)
                .setValue(CrossCollisionBlock.WEST, Boolean.TRUE);
    }

    /** Z eksenli duvara (x sabit) gömülü cam pane — kuzey/güney kollarına bağlı görünür. */
    private static BlockState paneAlongZ() {
        return Blocks.GLASS_PANE.defaultBlockState()
                .setValue(CrossCollisionBlock.NORTH, Boolean.TRUE)
                .setValue(CrossCollisionBlock.SOUTH, Boolean.TRUE);
    }

    /** Klasik masa: çit ayak + üstünde bas-plaka. */
    private void placeTable(WorldGenLevel level, BoundingBox box, int dx, int dy, int dz) {
        place(level, box, dx, dy, dz, Blocks.SPRUCE_FENCE.defaultBlockState());
        place(level, box, dx, dy + 1, dz, Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
    }

    /** Dik duran namlulu fıçı (depo görünümü). */
    private static BlockState barrelUp() {
        return Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
    }

    /** Yanan mum (renkli varyantlar için taban state verilir). */
    private static BlockState litCandle(BlockState candle) {
        return candle.setValue(BlockStateProperties.LIT, Boolean.TRUE);
    }

    /**
     * Loot sandığı — YALNIZCA kendi konumu box içinde ve hücre boşken yerleşir
     * (kesişen komşu chunk'larda TEKRAR üretilmez → çoklu sandık önlenir).
     */
    private void placeLootChest(WorldGenLevel level, RandomSource random, BoundingBox box,
                                int dx, int dy, int dz, Direction facing, ResourceKey<LootTable> loot) {
        BlockPos p = origin.offset(dx, dy, dz);
        if (!box.isInside(p) || !level.getBlockState(p).isAir()) {
            return;
        }
        level.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 2);
        RandomizableContainer.setBlockEntityLootTable(level, random, p, loot);
    }

    /**
     * Bir adet wizard_trader doğurur — YALNIZCA konum bu chunk'ın box'u
     * içindeyken (çoklu NPC önlenir). EntityType registry'den ID ile aranır;
     * kayıtlı değilse sessizce atlanır. {@code variant}: 0 = genel satıcı,
     * 1 = İKSİR satıcısı (İksirci dükkânı).
     */
    private void spawnTrader(WorldGenLevel level, BoundingBox box, int dx, int dz, int variant) {
        BlockPos p = origin.offset(dx, 0, dz);
        if (!box.isInside(p)) {
            return;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(TRADER_ID).orElse(null);
        if (type == null) {
            return; // wizard_trader kayıtlı değil — null-fallback: doğurma.
        }
        ServerLevel serverLevel = level.getLevel();
        Entity entity = type.create(serverLevel, EntitySpawnReason.STRUCTURE);
        if (!(entity instanceof Mob mob)) {
            return;
        }
        mob.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.0F, 0.0F);
        mob.setPersistenceRequired();
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), EntitySpawnReason.STRUCTURE, null);
        if (variant > 0 && mob instanceof WizardTraderEntity trader) {
            trader.setVariant(variant);
        }
        level.addFreshEntity(mob);
    }

    // =====================================================================
    //  1) ÜÇ SÜPÜRGE — han (x -24..-12, z -20..-11; 2 kat + baca)
    // =====================================================================

    private void buildInn(WorldGenLevel level, RandomSource random, BoundingBox box) {
        int x1 = -24;
        int z1 = -20;
        int x2 = -12;
        int z2 = -11;
        BlockState wall = ModBlocks.ARCANEWOOD_PLANKS.get().defaultBlockState();
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState();

        levelPad(level, box, x1, z1, x2, z2);
        buildShell(level, box, x1, z1, x2, z2, 0, 8, wall, log, floor);

        // Kat arası kiriş bandı (dy=4): çevrede yatay ladin kütükler (iskelet görünümü).
        for (int dx = x1; dx <= x2; dx++) {
            for (int dz = z1; dz <= z2; dz++) {
                boolean edgeX = dx == x1 || dx == x2;
                boolean edgeZ = dz == z1 || dz == z2;
                if (!edgeX && !edgeZ) {
                    continue;
                }
                BlockState beam;
                if (edgeX && edgeZ) {
                    beam = log; // köşe direği devam eder (dikey)
                } else if (edgeZ) {
                    beam = log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
                } else {
                    beam = log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
                }
                place(level, box, dx, 4, dz, beam);
            }
        }

        // Dik ladin çatı (mahya X'e paralel).
        roofAlongX(level, box, x1, z1, x2, z2, 8,
                Blocks.SPRUCE_STAIRS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), wall);

        // 2. kat döşemesi (dy=4, iç hücreler) — merdiven deliği (-13,-15) hariç.
        for (int dx = x1 + 1; dx <= x2 - 1; dx++) {
            for (int dz = z1 + 1; dz <= z2 - 1; dz++) {
                if (dx == -13 && dz == -15) {
                    continue; // el merdiveni boşluğu
                }
                place(level, box, dx, 4, dz, floor);
            }
        }
        for (int dy = 0; dy <= 4; dy++) {
            place(level, box, -13, dy, -15, Blocks.LADDER.defaultBlockState()
                    .setValue(LadderBlock.FACING, Direction.WEST));
        }

        // Kapı: güney duvar, meydana bakar (+ pad halkasında paspas patikası).
        placeDoor(level, box, -18, z2, Direction.SOUTH, Blocks.SPRUCE_DOOR.defaultBlockState());
        place(level, box, -18, -1, z2 + 1, Blocks.DIRT_PATH.defaultBlockState());

        // Pencereler (zemin dy=1, üst kat dy=6).
        place(level, box, -21, 1, z2, paneAlongX());
        place(level, box, -15, 1, z2, paneAlongX());
        place(level, box, -21, 1, z1, paneAlongX());
        place(level, box, -15, 1, z1, paneAlongX());
        place(level, box, x1, 1, -16, paneAlongZ());
        place(level, box, x2, 1, -18, paneAlongZ()); // -15 değil: el merdiveninin arkası olmasın
        place(level, box, -18, 6, z2, paneAlongX());
        place(level, box, -18, 6, z1, paneAlongX());

        // --- İç mekân: zemin kat (meyhane) ---
        placeTable(level, box, -21, 0, -14);
        placeTable(level, box, -16, 0, -18);
        placeTable(level, box, -20, 0, -18);
        place(level, box, -23, 0, -18, barrelUp());
        place(level, box, -23, 1, -18, barrelUp());
        place(level, box, -22, 0, -19, barrelUp());
        place(level, box, -18, 3, -15, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, Boolean.TRUE));
        place(level, box, -21, 3, -13, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, Boolean.TRUE));
        place(level, box, -14, 3, -13, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, Boolean.TRUE));

        // --- 2. kat (konaklama) ---
        place(level, box, -23, 5, -19, barrelUp());
        place(level, box, -14, 5, -13, Blocks.LANTERN.defaultBlockState());
        place(level, box, -22, 5, -19, Blocks.LANTERN.defaultBlockState());

        // Loot sandığı (batı duvarı dibi, doğuya bakar).
        placeLootChest(level, random, box, -23, 0, -13, Direction.EAST, INN_LOOT);

        // Baca: batı saçağın dış hattında taş tuğla; tepesinde tüten kamp ateşi.
        for (int dy = -1; dy <= 14; dy++) {
            place(level, box, -25, dy, -16, Blocks.STONE_BRICKS.defaultBlockState());
        }
        place(level, box, -25, 15, -16, Blocks.CAMPFIRE.defaultBlockState()); // LIT varsayılan → duman

        // Hancı: wizard_trader (varyant 0).
        spawnTrader(level, box, -18, -16, 0);
        spawnTrader(level, box, -16, -14, 0); // han ikinci tuccar (kullanici: 3 kat fazla ciksin)
    }

    // =====================================================================
    //  2) HONEYDUKES — şekerci (x 12..20, z -18..-11; beyaz + pembe + kiraz)
    // =====================================================================

    private void buildHoneydukes(WorldGenLevel level, RandomSource random, BoundingBox box) {
        int x1 = 12;
        int z1 = -18;
        int x2 = 20;
        int z2 = -11;
        BlockState wall = VanillaBlocks.terracotta(DyeColor.WHITE).defaultBlockState();
        BlockState accent = VanillaBlocks.terracotta(DyeColor.PINK).defaultBlockState();
        BlockState log = Blocks.CHERRY_LOG.defaultBlockState();
        BlockState floor = Blocks.CHERRY_PLANKS.defaultBlockState();

        levelPad(level, box, x1, z1, x2, z2);
        buildShell(level, box, x1, z1, x2, z2, 0, 4, wall, log, floor);
        accentBand(level, box, x1, z1, x2, z2, 3, accent);

        // Dik kiraz çatı (pembe!) — mahya X'e paralel.
        roofAlongX(level, box, x1, z1, x2, z2, 4,
                Blocks.CHERRY_STAIRS.defaultBlockState(), Blocks.CHERRY_PLANKS.defaultBlockState(), wall);

        placeDoor(level, box, 16, z2, Direction.SOUTH, Blocks.CHERRY_DOOR.defaultBlockState());
        place(level, box, 16, -1, z2 + 1, Blocks.DIRT_PATH.defaultBlockState());

        // Vitrin pencereleri.
        place(level, box, 14, 1, z2, paneAlongX());
        place(level, box, 18, 1, z2, paneAlongX());
        place(level, box, x1, 1, -15, paneAlongZ());
        place(level, box, x2, 1, -15, paneAlongZ());

        // --- İç mekân: tezgâh + pasta + şeker köşesi ---
        place(level, box, 18, 0, -15, floor);                          // tezgâh
        place(level, box, 18, 1, -15, Blocks.CAKE.defaultBlockState()); // pasta!
        placeTable(level, box, 14, 0, -15);
        place(level, box, 13, 0, -17, litCandle(VanillaBlocks.candle(DyeColor.PINK).defaultBlockState()));
        place(level, box, 19, 0, -17, litCandle(VanillaBlocks.candle(DyeColor.MAGENTA).defaultBlockState()));
        place(level, box, 19, 0, -12, Blocks.LANTERN.defaultBlockState());

        // Loot sandığı (kuzey duvar dibi, güneye bakar).
        placeLootChest(level, random, box, 14, 0, -17, Direction.SOUTH, HONEYDUKES_LOOT);
    }

    // =====================================================================
    //  3) ASA DÜKKÂNI — uzun-dar-ÇARPIK (zemin x 8..14, z 10..16; jetty üst kat)
    // =====================================================================

    private void buildWandShop(WorldGenLevel level, RandomSource random, BoundingBox box) {
        int x1 = 8;
        int z1 = 10;
        int x2 = 14;
        int z2 = 16;
        BlockState wall = ModBlocks.ARCANEWOOD_PLANKS.get().defaultBlockState();
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState();

        // Pad üst kat taşmasını da kapsar (+2 doğu).
        levelPad(level, box, x1 - 1, z1 - 1, x2 + 2, z2 + 1);
        buildShell(level, box, x1, z1, x2, z2, 0, 4, wall, log, floor);

        // ÇARPIK jetty üst kat: batıya 1, doğuya 2, kuzey/güneye 1 taşar.
        int ux1 = x1 - 1;
        int uz1 = z1 - 1;
        int ux2 = x2 + 2;
        int uz2 = z2 + 1;
        fill(level, box, ux1, 4, uz1, ux2, 4, uz2, floor); // jetty döşemesi (alt yüzü sarkık)
        for (int dx = ux1; dx <= ux2; dx++) {
            for (int dz = uz1; dz <= uz2; dz++) {
                boolean edgeX = dx == ux1 || dx == ux2;
                boolean edgeZ = dz == uz1 || dz == uz2;
                if (!edgeX && !edgeZ) {
                    continue;
                }
                boolean corner = edgeX && edgeZ;
                for (int dy = 5; dy <= 8; dy++) {
                    place(level, box, dx, dy, dz, corner ? log : wall);
                }
            }
        }
        // Koyu meşe dik çatı — mahya Z'ye paralel (dar cephe kuzeye bakar).
        roofAlongZ(level, box, ux1, uz1, ux2, uz2, 9,
                Blocks.DARK_OAK_STAIRS.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState(), wall);

        // Kapı: kuzey duvar, meydana bakar — ARCANEWOOD kapı (dükkânın imzası).
        placeDoor(level, box, 11, z1, Direction.NORTH,
                ModBlocks.ARCANEWOOD_DOOR.get().defaultBlockState());
        place(level, box, 11, -1, z1 - 1, Blocks.DIRT_PATH.defaultBlockState());
        place(level, box, 11, -1, z1 - 2, Blocks.DIRT_PATH.defaultBlockState());

        // Pencereler: vitrin (dy=1) + jetty üst kat (dy=6).
        place(level, box, 9, 1, z1, paneAlongX());
        place(level, box, 13, 1, z1, paneAlongX());
        place(level, box, x1, 1, 13, paneAlongZ());
        place(level, box, x2, 1, 13, paneAlongZ());
        place(level, box, 10, 6, uz1, paneAlongX());
        place(level, box, 13, 6, uz1, paneAlongX());
        place(level, box, ux1, 6, 13, paneAlongZ());
        place(level, box, ux2, 6, 13, paneAlongZ());

        // --- İç mekân: asa kutuları (kitaplık rafları) + kürsü ---
        fill(level, box, x1 + 1, 0, z2 - 1, x2 - 1, 2, z2 - 1, Blocks.BOOKSHELF.defaultBlockState());
        fill(level, box, x1 + 1, 0, z1 + 1, x1 + 1, 1, z2 - 2, Blocks.BOOKSHELF.defaultBlockState());
        place(level, box, 11, 0, 13, Blocks.LECTERN.defaultBlockState()
                .setValue(LecternBlock.FACING, Direction.NORTH));
        place(level, box, 11, 3, 12, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, Boolean.TRUE));

        // Tavan arasına el merdiveni (jetty döşeme deliği + batı duvara dayalı).
        place(level, box, 9, 4, 15, Blocks.AIR.defaultBlockState()); // döşeme deliği
        for (int dy = 0; dy <= 5; dy++) {
            place(level, box, 9, dy, 15, Blocks.LADDER.defaultBlockState()
                    .setValue(LadderBlock.FACING, Direction.EAST));
        }
        place(level, box, 12, 5, 15, Blocks.LANTERN.defaultBlockState()); // tavan arası feneri

        // Loot sandığı (güney iç raf önü, kuzeye bakar).
        placeLootChest(level, random, box, 13, 0, 14, Direction.NORTH, WAND_SHOP_LOOT);

        // Asa ustası: wizard_trader (varyant 0).
        spawnTrader(level, box, 10, 12, 0);
        spawnTrader(level, box, 12, 14, 0); // asa dukkani ikinci tuccar
    }

    // =====================================================================
    //  4) İKSİRCİ — iksir dükkânı (x -18..-10, z 8..15; yeşil + mor + purpur çatı)
    // =====================================================================

    private void buildPotionShop(WorldGenLevel level, RandomSource random, BoundingBox box) {
        int x1 = -18;
        int z1 = 8;
        int x2 = -10;
        int z2 = 15;
        BlockState wall = VanillaBlocks.terracotta(DyeColor.GREEN).defaultBlockState();
        BlockState accent = VanillaBlocks.terracotta(DyeColor.PURPLE).defaultBlockState();
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState floor = Blocks.DARK_OAK_PLANKS.defaultBlockState();

        levelPad(level, box, x1, z1, x2, z2);
        buildShell(level, box, x1, z1, x2, z2, 0, 5, wall, log, floor);
        accentBand(level, box, x1, z1, x2, z2, 4, accent);

        // Purpur (mor) dik çatı — mahya X'e paralel.
        roofAlongX(level, box, x1, z1, x2, z2, 5,
                Blocks.PURPUR_STAIRS.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(), wall);

        // Kapı: kuzey duvar, meydana bakar.
        placeDoor(level, box, -14, z1, Direction.NORTH, Blocks.DARK_OAK_DOOR.defaultBlockState());
        place(level, box, -14, -1, z1 - 1, Blocks.DIRT_PATH.defaultBlockState());

        // Pencereler: kuzey vitrin (uzun, dy=1..2) + yan duvarlar.
        place(level, box, -16, 1, z1, paneAlongX());
        place(level, box, -16, 2, z1, paneAlongX());
        place(level, box, -12, 1, z1, paneAlongX());
        place(level, box, -12, 2, z1, paneAlongX());
        place(level, box, x1, 1, 11, paneAlongZ());
        place(level, box, x2, 1, 11, paneAlongZ());

        // --- İç mekân: simya tezgâhı + kazan + şişe rafları ---
        // Tezgâh (koyu meşe) + 2 simya standı + fener.
        fill(level, box, -15, 0, 12, -13, 0, 12, floor);
        place(level, box, -15, 1, 12, Blocks.BREWING_STAND.defaultBlockState());
        place(level, box, -14, 1, 12, Blocks.BREWING_STAND.defaultBlockState());
        place(level, box, -13, 1, 12, Blocks.LANTERN.defaultBlockState());
        // Su dolu iksir kazanı (köşe).
        place(level, box, -17, 0, 14, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 3));
        // Şişe rafları: güney iç duvar boydan kitaplık + üstünde yanan mor mumlar.
        fill(level, box, -16, 0, 14, -12, 2, 14, Blocks.BOOKSHELF.defaultBlockState());
        place(level, box, -15, 3, 14, litCandle(VanillaBlocks.candle(DyeColor.PURPLE).defaultBlockState()));
        place(level, box, -13, 3, 14, litCandle(VanillaBlocks.candle(DyeColor.GREEN).defaultBlockState()));
        // Batı iç duvar alçak raf + ametist kümesi (simya havası).
        fill(level, box, -17, 0, 10, -17, 1, 12, Blocks.BOOKSHELF.defaultBlockState());
        place(level, box, -11, 0, 14, Blocks.AMETHYST_CLUSTER.defaultBlockState());

        // Loot sandığı — YENİ iksirci tablosu (doğu duvar dibi, batıya bakar).
        placeLootChest(level, random, box, -11, 0, 9, Direction.WEST, POTION_SHOP_LOOT);

        // İksir satıcısı: wizard_trader VARYANT 1.
        spawnTrader(level, box, -14, 10, 1);
        spawnTrader(level, box, -12, 12, 1); // iksirci ikinci tuccar
        // meydan gezginleri — koy canli hissettirsin (kullanici: tuccar sayisi 3 kat)
        spawnTrader(level, box, 5, 4, 0);
        spawnTrader(level, box, -5, -4, 0);
        spawnTrader(level, box, 4, -6, 0);
    }

    // =====================================================================
    //  5) BAYKUŞHANE — taş kule (x 28..34, z -34..-28; mazgallı tepe + tünekler)
    // =====================================================================

    private void buildOwlery(WorldGenLevel level, RandomSource random, BoundingBox box) {
        int x1 = 28;
        int z1 = -34;
        int x2 = 34;
        int z2 = -28;
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState corner = Blocks.COBBLESTONE.defaultBlockState();
        BlockState floor = Blocks.STONE_BRICKS.defaultBlockState();

        levelPad(level, box, x1, z1, x2, z2);
        buildShell(level, box, x1, z1, x2, z2, 0, 12, wall, corner, floor);

        // Açık kemer pencereler (camsız — baykuşlar girip çıksın): dy=8..9.
        fill(level, box, 30, 8, z1, 32, 9, z1, Blocks.AIR.defaultBlockState());
        fill(level, box, 30, 8, z2, 32, 9, z2, Blocks.AIR.defaultBlockState());
        fill(level, box, x1, 8, -32, x1, 9, -30, Blocks.AIR.defaultBlockState());
        fill(level, box, x2, 8, -32, x2, 9, -30, Blocks.AIR.defaultBlockState());

        // Mazgallı tepe: dy=12 tam halka + dy=13 köşe/orta dişler.
        for (int dx = x1; dx <= x2; dx++) {
            for (int dz = z1; dz <= z2; dz++) {
                boolean edgeX = dx == x1 || dx == x2;
                boolean edgeZ = dz == z1 || dz == z2;
                if (!edgeX && !edgeZ) {
                    continue;
                }
                place(level, box, dx, 12, dz, wall);
                boolean tooth = (edgeX && edgeZ) || (dx == 31 && edgeZ) || (dz == -31 && edgeX);
                if (tooth) {
                    place(level, box, dx, 13, dz, corner);
                }
            }
        }
        // Kule tepe fenerleri (köşe dişlerinin üstü) — geceleyin deniz feneri gibi.
        place(level, box, x1, 14, z1, Blocks.LANTERN.defaultBlockState());
        place(level, box, x2, 14, z2, Blocks.LANTERN.defaultBlockState());

        // Tünek katı (dy=6): ladin döşeme + çit tünekler + saman + fener.
        for (int dx = x1 + 1; dx <= x2 - 1; dx++) {
            for (int dz = z1 + 1; dz <= z2 - 1; dz++) {
                if (dx == 29 && dz == -29) {
                    continue; // el merdiveni boşluğu
                }
                place(level, box, dx, 6, dz, Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        }
        place(level, box, 31, 7, -33, Blocks.SPRUCE_FENCE.defaultBlockState());
        place(level, box, 33, 7, -31, Blocks.SPRUCE_FENCE.defaultBlockState());
        place(level, box, 29, 7, -32, Blocks.SPRUCE_FENCE.defaultBlockState());
        place(level, box, 33, 7, -33, Blocks.HAY_BLOCK.defaultBlockState());
        place(level, box, 30, 7, -30, Blocks.LANTERN.defaultBlockState());

        // Zemin: saman balyaları + sandık; el merdiveni tünek katına.
        place(level, box, 33, 0, -33, Blocks.HAY_BLOCK.defaultBlockState());
        place(level, box, 32, 0, -33, Blocks.HAY_BLOCK.defaultBlockState());
        place(level, box, 33, 0, -32, Blocks.HAY_BLOCK.defaultBlockState());
        for (int dy = 0; dy <= 6; dy++) {
            place(level, box, 29, dy, -29, Blocks.LADDER.defaultBlockState()
                    .setValue(LadderBlock.FACING, Direction.EAST));
        }
        placeLootChest(level, random, box, 29, 0, -33, Direction.EAST, INN_LOOT);

        // Kapı: güney duvar (köye bakar) + üstünde fener.
        placeDoor(level, box, 31, z2, Direction.SOUTH, Blocks.SPRUCE_DOOR.defaultBlockState());
        place(level, box, 31, -1, z2 + 1, Blocks.DIRT_PATH.defaultBlockState());
        place(level, box, 31, 2, z2 + 1, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, Boolean.TRUE));
        place(level, box, 31, 3, z2 + 1, wall); // fener askı taşı
    }

    // =====================================================================
    //  6) KİTAPÇI — çamur tuğla (x 24..31, z -3..4; kitaplık dolu)
    // =====================================================================

    private void buildBookShop(WorldGenLevel level, RandomSource random, BoundingBox box) {
        int x1 = 24;
        int z1 = -3;
        int x2 = 31;
        int z2 = 4;
        BlockState wall = Blocks.MUD_BRICKS.defaultBlockState();
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState floor = Blocks.DARK_OAK_PLANKS.defaultBlockState();

        levelPad(level, box, x1, z1, x2, z2);
        buildShell(level, box, x1, z1, x2, z2, 0, 5, wall, log, floor);

        // Çamur tuğla dik çatı — mahya Z'ye paralel (dar cephe batıya, meydana).
        roofAlongZ(level, box, x1, z1, x2, z2, 5,
                Blocks.MUD_BRICK_STAIRS.defaultBlockState(), Blocks.MUD_BRICKS.defaultBlockState(), wall);

        // Kapı: batı duvar, meydana bakar.
        placeDoor(level, box, x1, 0, Direction.WEST, Blocks.DARK_OAK_DOOR.defaultBlockState());
        place(level, box, x1 - 1, -1, 0, Blocks.DIRT_PATH.defaultBlockState());

        // Pencereler.
        place(level, box, x1, 1, -2, paneAlongZ());
        place(level, box, x1, 1, 2, paneAlongZ());
        place(level, box, 27, 1, z1, paneAlongX());
        place(level, box, 27, 1, z2, paneAlongX());

        // --- İç mekân: kitaplık duvarları + okuma köşesi ---
        fill(level, box, 25, 0, 3, 30, 2, 3, Blocks.BOOKSHELF.defaultBlockState());   // güney raf
        fill(level, box, 30, 0, -2, 30, 2, 2, Blocks.BOOKSHELF.defaultBlockState());  // doğu raf
        fill(level, box, 25, 0, -2, 26, 1, -2, Blocks.BOOKSHELF.defaultBlockState()); // kuzey alçak raf
        place(level, box, 26, 0, 0, Blocks.LECTERN.defaultBlockState()
                .setValue(LecternBlock.FACING, Direction.WEST));
        placeTable(level, box, 28, 0, 1);
        place(level, box, 28, 4, -1, wall); // fener askı kirişi
        place(level, box, 28, 3, -1, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, Boolean.TRUE));
    }

    // =====================================================================
    //  7) CÜBBECİ — renkli yün şeritli terzi (x -34..-26, z -4..3)
    // =====================================================================

    private void buildRobeShop(WorldGenLevel level, RandomSource random, BoundingBox box) {
        int x1 = -34;
        int z1 = -4;
        int x2 = -26;
        int z2 = 3;
        BlockState wall = VanillaBlocks.terracotta(DyeColor.WHITE).defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState floor = Blocks.OAK_PLANKS.defaultBlockState();

        levelPad(level, box, x1, z1, x2, z2);
        buildShell(level, box, x1, z1, x2, z2, 0, 5, wall, log, floor);
        // RENKLİ yün şeritler: dy=1 macenta, dy=3 açık mavi (vitrin kimliği).
        accentBand(level, box, x1, z1, x2, z2, 1, VanillaBlocks.wool(DyeColor.MAGENTA).defaultBlockState());
        accentBand(level, box, x1, z1, x2, z2, 3, VanillaBlocks.wool(DyeColor.LIGHT_BLUE).defaultBlockState());

        // Warped (turkuaz) dik çatı — mahya X'e paralel.
        roofAlongX(level, box, x1, z1, x2, z2, 5,
                Blocks.WARPED_STAIRS.defaultBlockState(), Blocks.WARPED_PLANKS.defaultBlockState(), wall);

        // Kapı: doğu duvar, meydana bakar (dy=1 şeridini kapı deler).
        placeDoor(level, box, x2, 0, Direction.EAST, Blocks.OAK_DOOR.defaultBlockState());
        place(level, box, x2 + 1, -1, 0, Blocks.DIRT_PATH.defaultBlockState());

        // Vitrin pencereleri (dy=2 — şeritlerin arasında).
        place(level, box, x2, 2, -2, paneAlongZ());
        place(level, box, x2, 2, 2, paneAlongZ());
        place(level, box, -30, 2, z1, paneAlongX());
        place(level, box, -30, 2, z2, paneAlongX());

        // --- İç mekân: tezgâh + kumaş topları + halı sergisi ---
        place(level, box, -32, 0, -3, Blocks.LOOM.defaultBlockState());
        place(level, box, -33, 0, 2, VanillaBlocks.wool(DyeColor.MAGENTA).defaultBlockState());
        place(level, box, -33, 1, 2, VanillaBlocks.wool(DyeColor.YELLOW).defaultBlockState());
        place(level, box, -32, 0, 2, VanillaBlocks.wool(DyeColor.CYAN).defaultBlockState());
        place(level, box, -30, 0, 0, VanillaBlocks.carpet(DyeColor.RED).defaultBlockState());
        place(level, box, -29, 0, 0, VanillaBlocks.carpet(DyeColor.WHITE).defaultBlockState());
        place(level, box, -28, 0, 0, VanillaBlocks.carpet(DyeColor.LIME).defaultBlockState());
        placeTable(level, box, -28, 0, -3);
        place(level, box, -27, 0, 2, Blocks.LANTERN.defaultBlockState());
    }

    // =====================================================================
    //  8) ÇARPIK BÜYÜCÜ EVİ #1 — mavi jetty (zemin x -6..0, z -30..-24)
    // =====================================================================

    private void buildCrookedHouseBlue(WorldGenLevel level, RandomSource random, BoundingBox box) {
        int x1 = -6;
        int z1 = -30;
        int x2 = 0;
        int z2 = -24;
        BlockState lower = VanillaBlocks.terracotta(DyeColor.LIGHT_BLUE).defaultBlockState();
        BlockState upper = VanillaBlocks.terracotta(DyeColor.BLUE).defaultBlockState();
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState();

        levelPad(level, box, x1 - 1, z1 - 1, x2 + 1, z2 + 1); // jetty taşmasını da kapsa
        buildShell(level, box, x1, z1, x2, z2, 0, 4, lower, log, floor);

        // Jetty üst kat: her yana 1 taşar (garip büyücü evi silüeti).
        int ux1 = x1 - 1;
        int uz1 = z1 - 1;
        int ux2 = x2 + 1;
        int uz2 = z2 + 1;
        fill(level, box, ux1, 4, uz1, ux2, 4, uz2, floor);
        for (int dx = ux1; dx <= ux2; dx++) {
            for (int dz = uz1; dz <= uz2; dz++) {
                boolean edgeX = dx == ux1 || dx == ux2;
                boolean edgeZ = dz == uz1 || dz == uz2;
                if (!edgeX && !edgeZ) {
                    continue;
                }
                boolean corner = edgeX && edgeZ;
                for (int dy = 5; dy <= 8; dy++) {
                    place(level, box, dx, dy, dz, corner ? log : upper);
                }
            }
        }
        // Deepslate kiremit dik çatı — mahya X'e paralel (koyu, sivri).
        roofAlongX(level, box, ux1, uz1, ux2, uz2, 9,
                Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState(),
                Blocks.DEEPSLATE_TILES.defaultBlockState(), upper);

        // Kapı: güney duvar + jetty altında asılı fener (karşılama ışığı).
        placeDoor(level, box, -3, z2, Direction.SOUTH, Blocks.SPRUCE_DOOR.defaultBlockState());
        place(level, box, -3, -1, z2 + 1, Blocks.DIRT_PATH.defaultBlockState());
        place(level, box, -3, -1, z2 + 2, Blocks.DIRT_PATH.defaultBlockState());
        place(level, box, -1, 3, uz2, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, Boolean.TRUE));

        // Pencereler: zemin + jetty üst kat.
        place(level, box, -5, 1, z2, paneAlongX());
        place(level, box, -1, 1, z2, paneAlongX());
        place(level, box, -3, 6, uz2, paneAlongX());
        place(level, box, -3, 6, uz1, paneAlongX());
        place(level, box, ux1, 6, -27, paneAlongZ());
        place(level, box, ux2, 6, -27, paneAlongZ());

        // --- İç mekân + tavan arası ---
        placeTable(level, box, -5, 0, -29);
        place(level, box, -5, 0, -25, barrelUp());
        place(level, box, -2, 0, -29, Blocks.LANTERN.defaultBlockState());
        place(level, box, -1, 4, -29, Blocks.AIR.defaultBlockState()); // döşeme deliği
        for (int dy = 0; dy <= 5; dy++) {
            place(level, box, -1, dy, -29, Blocks.LADDER.defaultBlockState()
                    .setValue(LadderBlock.FACING, Direction.WEST));
        }
        place(level, box, -4, 5, -26, Blocks.LANTERN.defaultBlockState());
        placeLootChest(level, random, box, -6, 5, -30, Direction.SOUTH, INN_LOOT);
    }

    // =====================================================================
    //  9) ÇARPIK BÜYÜCÜ EVİ #2 — lime/sarı jetty (zemin x 22..28, z 20..26)
    // =====================================================================

    private void buildCrookedHouseLime(WorldGenLevel level, RandomSource random, BoundingBox box) {
        int x1 = 22;
        int z1 = 20;
        int x2 = 28;
        int z2 = 26;
        BlockState lower = VanillaBlocks.terracotta(DyeColor.LIME).defaultBlockState();
        BlockState upper = VanillaBlocks.terracotta(DyeColor.YELLOW).defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState floor = Blocks.OAK_PLANKS.defaultBlockState();

        levelPad(level, box, x1 - 1, z1 - 1, x2 + 1, z2 + 1);
        buildShell(level, box, x1, z1, x2, z2, 0, 4, lower, log, floor);

        int ux1 = x1 - 1;
        int uz1 = z1 - 1;
        int ux2 = x2 + 1;
        int uz2 = z2 + 1;
        fill(level, box, ux1, 4, uz1, ux2, 4, uz2, floor);
        for (int dx = ux1; dx <= ux2; dx++) {
            for (int dz = uz1; dz <= uz2; dz++) {
                boolean edgeX = dx == ux1 || dx == ux2;
                boolean edgeZ = dz == uz1 || dz == uz2;
                if (!edgeX && !edgeZ) {
                    continue;
                }
                boolean corner = edgeX && edgeZ;
                for (int dy = 5; dy <= 7; dy++) {
                    place(level, box, dx, dy, dz, corner ? log : upper);
                }
            }
        }
        // Yosunlu taş tuğla dik çatı — mahya Z'ye paralel.
        roofAlongZ(level, box, ux1, uz1, ux2, uz2, 8,
                Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState(),
                Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), upper);

        // Kapı: batı duvar (köy merkezine bakar) + jetty altı feneri.
        placeDoor(level, box, x1, 23, Direction.WEST, Blocks.OAK_DOOR.defaultBlockState());
        place(level, box, x1 - 1, -1, 23, Blocks.DIRT_PATH.defaultBlockState());
        place(level, box, x1 - 2, -1, 23, Blocks.DIRT_PATH.defaultBlockState());
        place(level, box, ux1, 3, 25, Blocks.LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, Boolean.TRUE));

        // Pencereler.
        place(level, box, x1, 1, 21, paneAlongZ());
        place(level, box, x1, 1, 25, paneAlongZ());
        place(level, box, 25, 6, uz1, paneAlongX());
        place(level, box, 25, 6, uz2, paneAlongX());
        place(level, box, ux1, 6, 23, paneAlongZ());

        // --- İç mekân + tavan arası ---
        placeTable(level, box, 26, 0, 21);
        place(level, box, 27, 0, 25, barrelUp());
        place(level, box, 23, 0, 25, Blocks.LANTERN.defaultBlockState());
        place(level, box, 27, 4, 21, Blocks.AIR.defaultBlockState()); // döşeme deliği
        for (int dy = 0; dy <= 5; dy++) {
            place(level, box, 27, dy, 21, Blocks.LADDER.defaultBlockState()
                    .setValue(LadderBlock.FACING, Direction.WEST));
        }
        place(level, box, 24, 5, 24, Blocks.LANTERN.defaultBlockState());
    }

    // =====================================================================
    //  10) ŞİRİN KÖY EVİ — taş temel + meşe (x -32..-24, z 20..27)
    // =====================================================================

    private void buildCozyCottage(WorldGenLevel level, RandomSource random, BoundingBox box) {
        int x1 = -32;
        int z1 = 20;
        int x2 = -24;
        int z2 = 27;
        BlockState wall = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState floor = Blocks.OAK_PLANKS.defaultBlockState();

        levelPad(level, box, x1, z1, x2, z2);
        buildShell(level, box, x1, z1, x2, z2, 0, 5, wall, log, floor);
        // Taş temel bandı: duvarın en alt katı (köşeler dahil değil) taş.
        accentBand(level, box, x1, z1, x2, z2, 0, Blocks.COBBLESTONE.defaultBlockState());

        // Meşe dik çatı — mahya X'e paralel (en "normal köy evi" görünümü).
        roofAlongX(level, box, x1, z1, x2, z2, 5,
                Blocks.OAK_STAIRS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), wall);

        // Kapı: kuzey duvar (temel bandını kapı deler).
        placeDoor(level, box, -28, z1, Direction.NORTH, Blocks.OAK_DOOR.defaultBlockState());
        place(level, box, -28, -1, z1 - 1, Blocks.DIRT_PATH.defaultBlockState());

        // Pencereler.
        place(level, box, -30, 1, z1, paneAlongX());
        place(level, box, -26, 1, z1, paneAlongX());
        place(level, box, x1, 1, 23, paneAlongZ());
        place(level, box, x2, 1, 23, paneAlongZ());

        // --- İç mekân: yaşam alanı ---
        placeTable(level, box, -30, 0, 25);
        place(level, box, -31, 0, 26, barrelUp());
        place(level, box, -25, 0, 26, barrelUp());
        place(level, box, -25, 0, 21, Blocks.LANTERN.defaultBlockState());
        place(level, box, -31, 0, 21, Blocks.POTTED_POPPY.defaultBlockState());

        // Kendi bacası: doğu saçak hattında taş; tepesinde tüten kamp ateşi.
        for (int dy = -1; dy <= 10; dy++) {
            place(level, box, -23, dy, 24, Blocks.COBBLESTONE.defaultBlockState());
        }
        place(level, box, -23, 11, 24, Blocks.CAMPFIRE.defaultBlockState());
    }

    // =====================================================================
    //  11) TÜRBE/ÇEŞME KÖŞESİ — taş platform + fıskiye (x -4..4, z 30..36)
    // =====================================================================

    private void buildShrine(WorldGenLevel level, RandomSource random, BoundingBox box) {
        int x1 = -4;
        int z1 = 30;
        int x2 = 4;
        int z2 = 36;

        levelPad(level, box, x1, z1, x2, z2);
        // Platform döşemesi.
        fill(level, box, x1, -1, z1, x2, -1, z2, Blocks.STONE_BRICKS.defaultBlockState());

        // Fıskiyeli havuz (merkez (0,33)): taş bilezik + su.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 32; dz <= 34; dz++) {
                boolean rim = Math.abs(dx) == 1 || dz != 33;
                place(level, box, dx, 0, dz, rim
                        ? Blocks.STONE_BRICKS.defaultBlockState()
                        : Blocks.WATER.defaultBlockState());
            }
        }
        // Fıskiye direği: oyma taş tuğla + tepesinde fener (gece parlar).
        place(level, box, 0, 1, 33, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        place(level, box, 0, 2, 33, Blocks.LANTERN.defaultBlockState());

        // Yanan mumlar (platform köşeleri) + ametist kümeleri (büyü havası).
        place(level, box, -3, 0, 31, litCandle(Blocks.CANDLE.defaultBlockState()));
        place(level, box, 3, 0, 31, litCandle(Blocks.CANDLE.defaultBlockState()));
        place(level, box, -3, 0, 35, litCandle(Blocks.CANDLE.defaultBlockState()));
        place(level, box, 3, 0, 35, litCandle(Blocks.CANDLE.defaultBlockState()));
        place(level, box, -2, 0, 34, Blocks.AMETHYST_CLUSTER.defaultBlockState());
        place(level, box, 2, 0, 32, Blocks.AMETHYST_CLUSTER.defaultBlockState());

        // Giriş direkleri: taş tuğla duvar + fener (kuzey kenar, yola bakar).
        place(level, box, -2, 0, 30, Blocks.STONE_BRICK_WALL.defaultBlockState());
        place(level, box, -2, 1, 30, Blocks.LANTERN.defaultBlockState());
        place(level, box, 2, 0, 30, Blocks.STONE_BRICK_WALL.defaultBlockState());
        place(level, box, 2, 1, 30, Blocks.LANTERN.defaultBlockState());
    }
}
