package com.arcanum.worldgen;

import com.arcanum.Arcanum;
import com.arcanum.registry.ModStructures;
import com.arcanum.util.VanillaBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Azkaban'ın tek prosedürel {@link StructurePiece}'i. Tüm inşa mantığı (temel
 * kolonu, dış kabuk, hücre katları, gardiyan kasası, çatı mazgalları, deniz
 * kapısı, sandıklar) burada kod-tabanlıdır.
 *
 * <p>KRİTİK: {@link #postProcess} yapının kestiği HER chunk için AYRI çağrılır ve
 * {@code box} yalnızca O chunk'ın yazılabilir kutusudur. Bu yüzden her blok
 * yerleştirmesi {@code box}'a KIRPILIR ({@code box.isInside}); her sandık yalnızca
 * kendi konumu {@code box} içindeyken + hücresi boşken 1 kez yerleşir (çoklu
 * sandık önlenir). Sandık seçimi kat indeksinden DETERMİNİSTİKTİR — {@code random}
 * ile seçilseydi her chunk geçişi farklı hücre seçer, sandık sayısı bozulurdu.
 *
 * <p>OKYANUS NOTU: deniz seviyesi (63) altındaki iç hacim chunk üreticisi
 * tarafından SU ile doldurulmuş gelir; bu yüzden iç mekân önce açıkça HAVA'ya
 * oyulur ({@link #carveInterior}) ve deniz altında pencere AÇILMAZ (su basar).
 * Pencere yarıkları yalnızca {@link #SEA_GUARD_Y} üstünde delinir.
 *
 * <p>KAT PLANI (origin = okyanus tabanı, X/Z merkez; kule Y'leri MUTLAKTIR):
 * <pre>
 *   y=94        köşe kulesi fenerleri (soul lantern)
 *   y=90..93    çatı güvertesi + deepslate_tile mazgallar + 2x2 köşe kuleleri
 *   y=82..89    5. kat: GARDİYAN KASASI (reinforced_deepslate aksan + kasa sandığı)
 *   y=50..81    1-4. katlar: HÜCRE KATLARI (kat yüksekliği 8)
 *   y=..49      masif temel kolonu (okyanus tabanından)
 * </pre>
 *
 * <p>Hücre katı halka düzeni (Chebyshev ring = max(|dx|,|dz|)):
 * <pre>
 *   ring 0..2   merkez AVLU (5 genişlik, 1-4. katlar boyunca TAM yükseklik baca)
 *   ring 3..4   devriye koridoru
 *   ring 5      hücre ÖN cephesi: demir parmaklık + hücre başına 1 warded_door
 *   ring 6..9   hücre içi (cephe başına 3 hücre, her biri 3 geniş x 4 derin)
 *   ring 10     dış duvar (yıpranmış deepslate karışımı, pencere yarıkları)
 * </pre>
 * Tek giriş: doğu cephesinde, deniz seviyesinin üstündeki 3. katta (y=66) küçük
 * iskeleli DENİZ KAPISI — doğu cephesinin orta hücresi o katta kapı holüne dönüşür.
 */
public class AzkabanPiece extends StructurePiece {

    /** Hücre sandığı loot tablosu (data/arcanum/loot_table/chests/azkaban_cell.json). */
    private static final ResourceKey<LootTable> CELL_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(Arcanum.MODID, "chests/azkaban_cell"));

    /** Gardiyan kasası loot tablosu (data/arcanum/loot_table/chests/azkaban_vault.json). */
    private static final ResourceKey<LootTable> VAULT_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(Arcanum.MODID, "chests/azkaban_vault"));

    // --- KULE geometrisi (21x21 taban; origin = okyanus tabanı, X/Z merkez) ---
    /** Yatay yarıçap: dış duvar ±HALF (taban 21x21). */
    private static final int HALF = 10;
    /** Kule taban katı Y'si (deniz 63'ün 13 altı — alt iki kat su altındadır). */
    public static final int TOWER_BASE_Y = 50;
    /** Kat yüksekliği (1 döşeme + 7 iç kat). */
    private static final int FLOOR_HEIGHT = 8;
    /** Hücre katı sayısı (1-4. katlar). */
    private static final int PRISON_FLOORS = 4;
    /** Gardiyan kasası (5. kat) döşeme Y'si. = 82 — avlu bacasını da kapatır. */
    private static final int VAULT_FLOOR_Y = TOWER_BASE_Y + PRISON_FLOORS * FLOOR_HEIGHT;
    /** Çatı güvertesi Y'si. = 90 */
    private static final int ROOF_Y = VAULT_FLOOR_Y + FLOOR_HEIGHT;
    /** En üst nokta (köşe kulesi fenerleri). = 94 */
    private static final int TOP_Y = ROOF_Y + 4;

    // --- İç plan halkaları (ring = max(|dx|,|dz|)) ---
    /** Merkez avlu yarıçapı: |dx|<=2 && |dz|<=2 → 5 genişlik. */
    private static final int ATRIUM_HALF = 2;
    /** Hücre ÖN cephesi (parmaklık + kapı) halkası. */
    private static final int FRONT_RING = 5;
    /** Hücre iç derinliği: ring 6..9 (4 derin). */
    private static final int CELL_MIN_RING = FRONT_RING + 1;
    private static final int CELL_MAX_RING = HALF - 1;

    /**
     * Deniz guard'ı: pencere yarıkları yalnızca bu Y'nin ÜSTÜNDE delinir. Deniz
     * seviyesi 63; alt katlarda duvar delinirse okyanus içeri dolar (worldgen
     * sonrası sıvı tick'leri). Bu yüzden 1-2. katlar penceresiz zindandır.
     */
    private static final int SEA_GUARD_Y = 64;

    // --- Cepheler + hücre düzeni ---
    /** Cephe sırası SABİT (sandık indeksleri deterministik kalsın diye dizi, iterasyon değil). */
    private static final Direction[] FACES = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
    /** Cephe başına 3 hücre; hücre merkezlerinin cephe-boyu offset'leri (genişlik 3, bölmeler ±2'de). */
    private static final int[] CELL_CENTERS = { -4, 0, 4 };

    // --- Deniz kapısı (tek giriş) ---
    /** Kapı katı indeksi (floorY = 66 → deniz 63'ün üstü). */
    private static final int GATE_FLOOR = 2;
    /** Kapının açıldığı cephe. */
    private static final Direction GATE_FACE = Direction.EAST;
    /** Kapı holüne dönüşen hücre (cephenin orta hücresi, CELL_CENTERS[1]=0). */
    private static final int GATE_CELL = 1;

    /** Kule merkezi (dünya koordinatı; Y = okyanus tabanı). NBT'ye ox/oy/oz yazılır. */
    private final BlockPos origin;

    /**
     * İlk üretim constructor'u. BoundingBox tüm kuleyi (taban ±12, temel tabanından
     * fener tepesine) kapsar. KUTU KÜÇÜK KALIRSA yapının kenarı üretilmez.
     */
    public AzkabanPiece(BlockPos origin) {
        super(ModStructures.AZKABAN_PIECE.get(), 0, makeBox(origin));
        this.origin = origin;
    }

    /** NBT'den yeniden yükleme constructor'u ({@link net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType} SAM'i). */
    public AzkabanPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(ModStructures.AZKABAN_PIECE.get(), tag);
        this.origin = new BlockPos(tag.getIntOr("ox", 0), tag.getIntOr("oy", 0), tag.getIntOr("oz", 0));
    }

    private static BoundingBox makeBox(BlockPos origin) {
        int minX = origin.getX() - (HALF + 2);
        int maxX = origin.getX() + (HALF + 2);
        int minZ = origin.getZ() - (HALF + 2);
        int maxZ = origin.getZ() + (HALF + 2);
        // Dikey: temel tabanının 1 altı .. fener tepesinin 2 üstü. Origin sığ bir
        // tabana denk gelirse bile kule bandı (50..94) her zaman kapsanır.
        int minY = Math.min(origin.getY() - 4, TOWER_BASE_Y - 2);
        int maxY = TOP_Y + 2;
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        // BoundingBox base sınıf tarafından otomatik yazılır; origin'i ayrıca sakla.
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
        // 1) Okyanus tabanından kule tabanına masif temel kolonu.
        buildFoundation(level, random, box);
        // 2) İç hacmi HAVA'ya oy (deniz altı katlar su dolu gelir — kritik).
        carveInterior(level, box);
        // 3) Dış kabuk (ring 10) + deniz üstü pencere yarıkları.
        buildShell(level, random, box);
        // 4) Hücre katları (döşeme, köşe payandaları, bölmeler, parmaklık+kapı, sandıklar).
        for (int k = 0; k < PRISON_FLOORS; k++) {
            buildPrisonFloor(level, random, box, k);
        }
        // 5) Gardiyan kasası (5. kat) + kasa sandığı.
        buildVault(level, random, box);
        // 6) Çatı güvertesi + mazgallar + köşe kuleleri + seyrek soul lantern.
        buildRoof(level, random, box);
        // 7) Deniz kapısı (dış duvarda kemer + iskele) — kabuktan SONRA oyulmalı.
        buildSeaGate(level, box);
        // 8) Avlu aydınlatması (iç mekânın tek ışığı — geri kalan her yer karanlık).
        lightAtrium(level, box);
    }

    // ---------------------------------------------------------------------
    //  Box-kırpımlı blok yerleştirme yardımcıları
    // ---------------------------------------------------------------------

    /** Dünya konumunu {@code box}'a kırparak yerleştirir (dışındaysa dokunmaz). */
    private void setClipped(WorldGenLevel level, BoundingBox box, BlockPos p, BlockState state) {
        if (box.isInside(p)) {
            level.setBlock(p, state, 2);
        }
    }

    /**
     * setClipped + post-processing işareti: demir parmaklık gibi komşuya göre
     * şekil alan bloklar worldgen'de (flag 2, komşu güncellemesi yok) bağlantısız
     * kalır; vanilla structure'ların yaptığı gibi chunk'a işaretlenir ki yükleme
     * sonrası şekilleri otursun.
     */
    private void setClippedPostProcess(WorldGenLevel level, BoundingBox box, BlockPos p, BlockState state) {
        if (box.isInside(p)) {
            level.setBlock(p, state, 2);
            level.getChunk(p).markPosForPostprocessing(p);
        }
    }

    /** Konum kutu içinde mi ve hava mı? (dolu hücrelere dokunmamak için). */
    private boolean isAirInBox(WorldGenLevel level, BoundingBox box, BlockPos p) {
        return box.isInside(p) && level.getBlockState(p).isAir();
    }

    /** Origin'in X/Z merkezine göre yatay offset + MUTLAK dünya Y'si. */
    private BlockPos at(int dx, int worldY, int dz) {
        return new BlockPos(origin.getX() + dx, worldY, origin.getZ() + dz);
    }

    /**
     * Cephe-yerel koordinatı dünya konumuna çevirir: origin + d*f + perp*side.
     * f = merkezden cephe yönünde ileri (ring), side = cephe boyu offset.
     */
    private BlockPos faceLocal(Direction d, Direction perp, int f, int side, int worldY) {
        int dx = d.getStepX() * f + perp.getStepX() * side;
        int dz = d.getStepZ() * f + perp.getStepZ() * side;
        return at(dx, worldY, dz);
    }

    // ---------------------------------------------------------------------
    //  Yıpranmış malzeme paletleri
    // ---------------------------------------------------------------------

    /** Duvar paleti: %60 deepslate brick, %25 cracked, %10 tuff brick, %5 polished blackstone aksan. */
    private static BlockState towerWall(RandomSource random) {
        int roll = random.nextInt(20);
        if (roll < 12) {
            return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        }
        if (roll < 17) {
            return Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        }
        if (roll < 19) {
            return Blocks.TUFF_BRICKS.defaultBlockState();
        }
        return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    }

    /** Döşeme paleti: tile ağırlıklı, çatlaklarla yıpranmış. */
    private static BlockState towerFloor(RandomSource random) {
        int roll = random.nextInt(10);
        if (roll < 5) {
            return Blocks.DEEPSLATE_TILES.defaultBlockState();
        }
        if (roll < 8) {
            return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        }
        return Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
    }

    /** Çatı güvertesi paleti. */
    private static BlockState roofDeck(RandomSource random) {
        int roll = random.nextInt(20);
        if (roll < 12) {
            return Blocks.DEEPSLATE_TILES.defaultBlockState();
        }
        if (roll < 17) {
            return Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
        }
        return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    }

    // ---------------------------------------------------------------------
    //  Hücre kapısı — arcanum:warded_door (derleme sırası bağımsız çözümleme)
    // ---------------------------------------------------------------------

    /**
     * {@code arcanum:warded_door} bloğunu registry'den çözer. ModBlocks alanına
     * derleme bağımlılığı KURULMAZ (blok ayrı bir devirde ekleniyor olabilir);
     * blok kayıtlı değilse ya da bir {@link DoorBlock} değilse hücreler mühürsüz
     * kalmasın diye vanilla demir kapıya düşülür.
     */
    private static Block resolveCellDoor() {
        Block block = BuiltInRegistries.BLOCK.getValue(
                Identifier.fromNamespaceAndPath(Arcanum.MODID, "warded_door"));
        if (block == null || block == Blocks.AIR || !(block instanceof DoorBlock)) {
            return Blocks.IRON_DOOR;
        }
        return block;
    }

    /** Kapı state'i: verilen yarı (LOWER/UPPER) + koridora bakan yüz; menteşe/açık/güç sabit. */
    private static BlockState doorState(Block doorBlock, Direction face, DoubleBlockHalf half) {
        return doorBlock.defaultBlockState()
                .setValue(DoorBlock.FACING, face.getOpposite())
                .setValue(DoorBlock.HALF, half)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                .setValue(DoorBlock.OPEN, Boolean.FALSE)
                .setValue(DoorBlock.POWERED, Boolean.FALSE);
    }

    // ---------------------------------------------------------------------
    //  1) Temel kolonu — okyanus tabanından y=49'a masif dolgu
    // ---------------------------------------------------------------------

    private void buildFoundation(WorldGenLevel level, RandomSource random, BoundingBox box) {
        // Taban çok sığsa (origin.y-3 > 49) döngü kendiliğinden boş kalır.
        for (int y = origin.getY() - 3; y < TOWER_BASE_Y; y++) {
            for (int dx = -HALF; dx <= HALF; dx++) {
                for (int dz = -HALF; dz <= HALF; dz++) {
                    setClipped(level, box, at(dx, y, dz), towerWall(random));
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    //  2) İç hacmi oy — deniz altındaki katlar SU dolu gelir, açıkça HAVA bas
    // ---------------------------------------------------------------------

    private void carveInterior(WorldGenLevel level, BoundingBox box) {
        for (int y = TOWER_BASE_Y + 1; y < ROOF_Y; y++) {
            for (int dx = -(HALF - 1); dx <= HALF - 1; dx++) {
                for (int dz = -(HALF - 1); dz <= HALF - 1; dz++) {
                    setClipped(level, box, at(dx, y, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    //  3) Dış kabuk (ring 10) + deniz üstü pencere yarıkları
    // ---------------------------------------------------------------------

    private void buildShell(WorldGenLevel level, RandomSource random, BoundingBox box) {
        for (int y = TOWER_BASE_Y; y < ROOF_Y; y++) {
            for (int dx = -HALF; dx <= HALF; dx++) {
                for (int dz = -HALF; dz <= HALF; dz++) {
                    if (Math.abs(dx) != HALF && Math.abs(dz) != HALF) {
                        continue;
                    }
                    setClipped(level, box, at(dx, y, dz), towerWall(random));
                }
            }
        }

        // Pencere yarıkları: hücre merkezlerinin hizasında 1x2 demir parmaklık.
        // YALNIZCA deniz üstünde (SEA_GUARD_Y) — alt katlar penceresiz zindan.
        for (int k = 0; k <= PRISON_FLOORS; k++) { // 0..3 hücre katları + 4 = kasa katı
            int floorY = TOWER_BASE_Y + k * FLOOR_HEIGHT;
            int windowBase = floorY + 3;
            if (windowBase <= SEA_GUARD_Y) {
                continue;
            }
            for (Direction d : FACES) {
                Direction perp = d.getClockWise();
                for (int t : CELL_CENTERS) {
                    // Deniz kapısı boşluğuyla çakışma: o kolon kapı kemeri olur.
                    if (k == GATE_FLOOR && d == GATE_FACE && t == 0) {
                        continue;
                    }
                    for (int y = windowBase; y <= windowBase + 1; y++) {
                        setClippedPostProcess(level, box, faceLocal(d, perp, HALF, t, y),
                                Blocks.IRON_BARS.defaultBlockState());
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    //  4) Hücre katı — döşeme, payandalar, bölmeler, parmaklık cephesi, sandıklar
    // ---------------------------------------------------------------------

    private void buildPrisonFloor(WorldGenLevel level, RandomSource random, BoundingBox box, int k) {
        int floorY = TOWER_BASE_Y + k * FLOOR_HEIGHT;
        int topY = floorY + FLOOR_HEIGHT - 1; // katın en üst iç hücresi (bir üst döşemenin altı)

        // 4a) Döşeme — zemin katı (k=0) tam; üst katlarda avlu bacası açık kalır.
        for (int dx = -(HALF - 1); dx <= HALF - 1; dx++) {
            for (int dz = -(HALF - 1); dz <= HALF - 1; dz++) {
                if (k > 0 && Math.abs(dx) <= ATRIUM_HALF && Math.abs(dz) <= ATRIUM_HALF) {
                    continue; // avlu bacası: 1-4. katlar boyunca tam yükseklik boşluk
                }
                setClipped(level, box, at(dx, floorY, dz), towerFloor(random));
            }
        }

        // 4b) Köşe kütleleri (|dx|>=6 && |dz|>=6) — masif payandalar.
        for (int dx = -CELL_MAX_RING; dx <= CELL_MAX_RING; dx++) {
            for (int dz = -CELL_MAX_RING; dz <= CELL_MAX_RING; dz++) {
                if (Math.abs(dx) < CELL_MIN_RING || Math.abs(dz) < CELL_MIN_RING) {
                    continue;
                }
                for (int y = floorY + 1; y <= topY; y++) {
                    setClipped(level, box, at(dx, y, dz), towerWall(random));
                }
            }
        }

        Block doorBlock = resolveCellDoor();
        int[] chestCells = chestCellsForFloor(k);

        for (int fi = 0; fi < FACES.length; fi++) {
            Direction d = FACES[fi];
            Direction perp = d.getClockWise();

            // 4c) Hücre bölme duvarları (t=±2, ring 6..9) + ön cephedeki bölme başları.
            for (int t = -2; t <= 2; t += 4) {
                for (int f = CELL_MIN_RING; f <= CELL_MAX_RING; f++) {
                    for (int y = floorY + 1; y <= topY; y++) {
                        setClipped(level, box, faceLocal(d, perp, f, t, y), towerWall(random));
                    }
                }
                for (int y = floorY + 1; y <= topY; y++) {
                    setClipped(level, box, faceLocal(d, perp, FRONT_RING, t, y), towerWall(random));
                }
            }

            // 4d) Hücre ön cepheleri: tam boy demir parmaklık + merkezde warded_door.
            for (int ci = 0; ci < CELL_CENTERS.length; ci++) {
                boolean gateHall = (k == GATE_FLOOR && d == GATE_FACE && ci == GATE_CELL);
                if (gateHall) {
                    continue; // kapı holü: ön cephe tamamen açık (oyulmuş hava kalır)
                }
                int center = CELL_CENTERS[ci];
                for (int t = center - 1; t <= center + 1; t++) {
                    boolean doorCol = (t == center);
                    for (int y = floorY + 1; y <= topY; y++) {
                        BlockPos p = faceLocal(d, perp, FRONT_RING, t, y);
                        if (doorCol && y == floorY + 1) {
                            setClipped(level, box, p, doorState(doorBlock, d, DoubleBlockHalf.LOWER));
                        } else if (doorCol && y == floorY + 2) {
                            setClipped(level, box, p, doorState(doorBlock, d, DoubleBlockHalf.UPPER));
                        } else {
                            setClippedPostProcess(level, box, p, Blocks.IRON_BARS.defaultBlockState());
                        }
                    }
                }
            }

            // 4e) Hücre sandıkları — kat başına 2-3 hücre, seçim DETERMİNİSTİK.
            for (int ci = 0; ci < CELL_CENTERS.length; ci++) {
                int cellIndex = fi * 3 + ci;
                if (!containsCell(chestCells, cellIndex)) {
                    continue;
                }
                if (k == GATE_FLOOR && d == GATE_FACE && ci == GATE_CELL) {
                    continue; // kapı holüne sandık koyma
                }
                BlockPos chestPos = faceLocal(d, perp, CELL_MAX_RING - 1, CELL_CENTERS[ci], floorY + 1);
                // Yalnızca kendi konumu box içinde + hücre boşken 1 kez yerleşir.
                if (box.isInside(chestPos) && level.getBlockState(chestPos).isAir()) {
                    level.setBlock(chestPos, Blocks.CHEST.defaultBlockState()
                            .setValue(ChestBlock.FACING, d.getOpposite()), 2);
                    RandomizableContainer.setBlockEntityLootTable(level, random, chestPos, CELL_LOOT);
                }
            }
        }
    }

    /**
     * Kat başına sandıklı hücre indeksleri (0..11: cephe*3 + hücre). Kat indeksinden
     * türetilen SABİT desen — her chunk geçişinde aynı sonucu verir; çift katlar
     * 3, tek katlar 2 sandık alır (görev: kat başına 2-3 hücre).
     */
    private static int[] chestCellsForFloor(int floorIdx) {
        int a = (floorIdx * 7 + 1) % 12;
        int b = (floorIdx * 7 + 5) % 12;
        if (floorIdx % 2 == 0) {
            return new int[] { a, b, (floorIdx * 7 + 9) % 12 };
        }
        return new int[] { a, b };
    }

    private static boolean containsCell(int[] cells, int idx) {
        for (int c : cells) {
            if (c == idx) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------
    //  5) Gardiyan kasası (5. kat) — reinforced_deepslate aksan + kasa sandığı
    // ---------------------------------------------------------------------

    private void buildVault(WorldGenLevel level, RandomSource random, BoundingBox box) {
        // Döşeme — avlu bacasını da kapatır (tam 19x19).
        for (int dx = -(HALF - 1); dx <= HALF - 1; dx++) {
            for (int dz = -(HALF - 1); dz <= HALF - 1; dz++) {
                setClipped(level, box, at(dx, VAULT_FLOOR_Y, dz), towerFloor(random));
            }
        }
        // Payanda sütunları (±6,±6): alt+üst REINFORCED_DEEPSLATE aksan, gövde polished blackstone.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int y = VAULT_FLOOR_Y + 1; y < ROOF_Y; y++) {
                    boolean accent = (y == VAULT_FLOOR_Y + 1) || (y == ROOF_Y - 1);
                    setClipped(level, box, at(sx * 6, y, sz * 6), accent
                            ? Blocks.REINFORCED_DEEPSLATE.defaultBlockState()
                            : Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                }
            }
        }
        // Merkez kaide 3x3: kenarlar oymalı blackstone, merkez reinforced.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean center = dx == 0 && dz == 0;
                setClipped(level, box, at(dx, VAULT_FLOOR_Y + 1, dz), center
                        ? Blocks.REINFORCED_DEEPSLATE.defaultBlockState()
                        : Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
            }
        }
        // Kasa sandığı — kaidenin üstünde, tek sefer guard'lı.
        BlockPos chestPos = at(0, VAULT_FLOOR_Y + 2, 0);
        if (box.isInside(chestPos) && level.getBlockState(chestPos).isAir()) {
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState()
                    .setValue(ChestBlock.FACING, Direction.WEST), 2);
            RandomizableContainer.setBlockEntityLootTable(level, random, chestPos, VAULT_LOOT);
        }
    }

    // ---------------------------------------------------------------------
    //  6) Çatı — güverte + deepslate_tile merdiven mazgallar + köşe kuleleri
    // ---------------------------------------------------------------------

    private void buildRoof(WorldGenLevel level, RandomSource random, BoundingBox box) {
        // Güverte (duvar üstleri dahil tam 21x21).
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                setClipped(level, box, at(dx, ROOF_Y, dz), roofDeck(random));
            }
        }
        // Korkuluk + mazgallar: köşe kuleleri arası (|t|<=8). Mazgal merdivenlerin
        // dolu sırtı DIŞA bakar (FACING = cephe normali) — klasik sur görünümü.
        for (Direction d : FACES) {
            Direction perp = d.getClockWise();
            for (int t = -8; t <= 8; t++) {
                setClipped(level, box, faceLocal(d, perp, HALF, t, ROOF_Y + 1),
                        Blocks.DEEPSLATE_TILES.defaultBlockState());
                if (t == 0) {
                    // Cephe ortasına seyrek soul lantern (ayakta).
                    setClipped(level, box, faceLocal(d, perp, HALF, t, ROOF_Y + 2),
                            Blocks.SOUL_LANTERN.defaultBlockState());
                } else if ((t & 1) == 0) {
                    setClipped(level, box, faceLocal(d, perp, HALF, t, ROOF_Y + 2),
                            Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState()
                                    .setValue(StairBlock.FACING, d));
                }
            }
        }
        // Köşe kuleleri: 2x2 (|dx|,|dz| ∈ 9..10), y=91..93 + tepede fener (y=94).
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int ax = HALF - 1; ax <= HALF; ax++) {
                    for (int az = HALF - 1; az <= HALF; az++) {
                        for (int y = ROOF_Y + 1; y <= ROOF_Y + 3; y++) {
                            setClipped(level, box, at(sx * ax, y, sz * az), towerWall(random));
                        }
                    }
                }
                setClipped(level, box, at(sx * HALF, TOP_Y, sz * HALF),
                        Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }
    }

    // ---------------------------------------------------------------------
    //  7) Deniz kapısı — doğu cephesi, y=66 (deniz üstü); tek giriş
    // ---------------------------------------------------------------------

    private void buildSeaGate(WorldGenLevel level, BoundingBox box) {
        Direction d = GATE_FACE;
        Direction perp = d.getClockWise();
        int floorY = TOWER_BASE_Y + GATE_FLOOR * FLOOR_HEIGHT; // 66

        // Dış duvarda kemer boşluğu (3 geniş, 3 yüksek) — kabuk örüldükten sonra oy.
        for (int t = -1; t <= 1; t++) {
            for (int y = floorY + 1; y <= floorY + 3; y++) {
                setClipped(level, box, faceLocal(d, perp, HALF, t, y), Blocks.AIR.defaultBlockState());
            }
        }
        // Kapı çerçevesi: söveler + lento — polished blackstone aksan.
        for (int y = floorY + 1; y <= floorY + 4; y++) {
            setClipped(level, box, faceLocal(d, perp, HALF, -2, y), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            setClipped(level, box, faceLocal(d, perp, HALF, 2, y), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
        for (int t = -1; t <= 1; t++) {
            setClipped(level, box, faceLocal(d, perp, HALF, t, floorY + 4), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
        // İskele: dalgaların üstünde 3x2 çıkıntı (kayıkla/süpürgeyle yanaşma noktası).
        for (int f = HALF + 1; f <= HALF + 2; f++) {
            for (int t = -1; t <= 1; t++) {
                setClipped(level, box, faceLocal(d, perp, f, t, floorY), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            }
        }
    }

    // ---------------------------------------------------------------------
    //  8) Avlu aydınlatması — iç mekânın TEK ışığı (karanlık = Ruh Emici üretimi)
    // ---------------------------------------------------------------------

    private void lightAtrium(WorldGenLevel level, BoundingBox box) {
        // Zemin avlusunun köşelerinde ayakta soul lantern.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                BlockPos p = at(sx * ATRIUM_HALF, TOWER_BASE_Y + 1, sz * ATRIUM_HALF);
                if (isAirInBox(level, box, p)) {
                    setClipped(level, box, p, Blocks.SOUL_LANTERN.defaultBlockState());
                }
            }
        }
        // Kasa döşemesinden avlu bacasına sarkan zincir + asılı fener.
        for (int y = VAULT_FLOOR_Y - 1; y >= VAULT_FLOOR_Y - 3; y--) {
            BlockPos chain = at(0, y, 0);
            if (isAirInBox(level, box, chain)) {
                setClipped(level, box, chain, VanillaBlocks.ironChain().defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
            }
        }
        BlockPos hang = at(0, VAULT_FLOOR_Y - 4, 0);
        if (isAirInBox(level, box, hang)) {
            setClipped(level, box, hang, Blocks.SOUL_LANTERN.defaultBlockState()
                    .setValue(LanternBlock.HANGING, Boolean.TRUE));
        }
    }
}
