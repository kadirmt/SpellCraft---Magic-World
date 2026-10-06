package com.arcanum.worldgen;

import com.arcanum.Arcanum;
import com.arcanum.entity.BasiliskEntity;
import com.arcanum.registry.ModEntities;
import com.arcanum.registry.ModStructures;
import com.arcanum.util.VanillaBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Unutulmuş Oda'nın tek prosedürel {@link StructurePiece}'i. Tüm inşa mantığı
 * (kabuk/oyma, zemin, sütunlar, duvar dekoru, tavan, altar, sandıklar, boss,
 * ayrıca 4 MADEN TEMALI koridor + 4 yan loot odası) burada kod-tabanlıdır.
 *
 * <p>KRİTİK FARK (feature'a göre): {@link #postProcess} yapının kestiği HER chunk
 * için AYRI çağrılır ve {@code box} yalnızca O chunk'ın yazılabilir kutusudur.
 * Bu yüzden her blok yerleştirmesi {@code box}'a KIRPILIR ({@code box.isInside}).
 * Aksi halde:
 * <ul>
 *   <li>Bloklar chunk sınırının dışına taşarsa kayıp/çökme olur.</li>
 *   <li>Boss/sandık her kesişen chunk'ta tekrar üretilirse ÇOKLU boss/sandık olur.</li>
 * </ul>
 * Boss yalnızca kendi konumu {@code box} içindeyken 1 kez, her sandık yalnızca
 * kendi konumu {@code box} içindeyken 1 kez üretilir.
 *
 * <p>YAPI PLANI (origin = boss salonu merkezi):
 * <pre>
 *                       [Yan Oda N]
 *                           |
 *                      [Koridor N]
 *                           |
 *   [Yan Oda W]--[Kor. W]--[BOSS SALONU]--[Kor. E]--[Yan Oda E]
 *                           |
 *                      [Koridor S]
 *                           |
 *                       [Yan Oda S]
 * </pre>
 */
public class ForgottenChamberPiece extends StructurePiece {

    /** Sandık loot tablosu (data/arcanum/loot_table/chests/forgotten_chamber.json). */
    private static final ResourceKey<LootTable> CHAMBER_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(Arcanum.MODID, "chests/forgotten_chamber"));

    // --- BOSS SALONU geometrisi (origin merkez) — ESKİDEN 17x17x13, ŞİMDİ ~25x25x16 ---
    /** Yatay yarıçap: iç boşluk ±HALF (25 genişlik). Duvar kabuğu +1 daha dışta. */
    private static final int HALF = 12;
    /** Zeminin origin'e göre dikey offset'i (zemin origin'in 2 altında). */
    private static final int FLOOR_DY = -2;
    /** İç boşluk yüksekliği (zemin katı hariç kat sayısı). ESKİDEN 11, ŞİMDİ 15. */
    private static final int INNER_HEIGHT = 15;
    /** Tavan katı (zemin=FLOOR_DY, üstünde INNER_HEIGHT kat, sonra tavan). = 14 */
    private static final int CEIL_DY = FLOOR_DY + INNER_HEIGHT + 1;

    // --- KORİDOR geometrisi ---
    /** Koridorun salon iç duvarından (±HALF) dışa doğru uzunluğu. */
    private static final int CORRIDOR_LEN = 16;
    /** Koridor iç yarı-genişliği (toplam iç genişlik = 2*HW+1 = 5). */
    private static final int CORRIDOR_HW = 2;
    /** Koridor iç yüksekliği (zeminden itibaren kat sayısı, tavan hariç). */
    private static final int CORRIDOR_HEIGHT = 4;

    // --- YAN ODA geometrisi ---
    /** Yan odanın koridor ucundan dışa doğru derinliği. */
    private static final int ROOM_DEPTH = 9;
    /** Yan oda iç yarı-genişliği (toplam iç genişlik = 2*HW+1 = 9). */
    private static final int ROOM_HW = 4;
    /** Yan oda iç yüksekliği. */
    private static final int ROOM_HEIGHT = 5;

    /**
     * Yatay eksende yapının origin'den en uzak noktası:
     * HALF (salon kabuğu için +1) + koridor + yan oda derinliği.
     * = 12 + 16 + 9 = 37; kabuk/pay için +3 -> 40.
     */
    private static final int MAX_REACH = HALF + 1 + CORRIDOR_LEN + ROOM_DEPTH + 2;

    /** Oda merkezi (dünya koordinatı). NBT'ye ox/oy/oz olarak yazılır. */
    private final BlockPos origin;

    /**
     * İlk üretim constructor'u. BoundingBox origin ± MAX_REACH yatay olarak TÜM
     * yapıyı (salon + 4 koridor + 4 yan oda) + kabuğunu kapsar. Dikey sınır boss
     * salonunun tam yüksekliğine göre. KUTU KÜÇÜK KALIRSA yapının uzak kısmı
     * (koridor uçları / yan odalar) üretilmez — bu yüzden MAX_REACH kritiktir.
     */
    public ForgottenChamberPiece(BlockPos origin) {
        super(ModStructures.FORGOTTEN_CHAMBER_PIECE.get(), 0, makeBox(origin));
        this.origin = origin;
    }

    /** NBT'den yeniden yükleme constructor'u ({@link net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType} SAM'i). */
    public ForgottenChamberPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(ModStructures.FORGOTTEN_CHAMBER_PIECE.get(), tag);
        this.origin = new BlockPos(tag.getIntOr("ox", 0), tag.getIntOr("oy", 0), tag.getIntOr("oz", 0));
    }

    private static BoundingBox makeBox(BlockPos origin) {
        int minX = origin.getX() - MAX_REACH;
        int maxX = origin.getX() + MAX_REACH;
        int minZ = origin.getZ() - MAX_REACH;
        int maxZ = origin.getZ() + MAX_REACH;
        // Dikey: salon zeminin 1 altı .. salon tavanının 1 üstü. Koridor/oda tavanı
        // bu bandın içindedir (hepsi salon zemininden başlar, salon tavanından alçaktır).
        int minY = origin.getY() + FLOOR_DY - 1;
        int maxY = origin.getY() + CEIL_DY + 1;
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        // BoundingBox base sınıf tarafından otomatik yazılır; origin'i ayrıca sakla,
        // yoksa yeniden yüklemede merkez kaybolur.
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
        int floorY = origin.getY() + FLOOR_DY;
        int ceilY = origin.getY() + CEIL_DY;

        // 1) BOSS SALONU
        carveAndShell(level, random, box, floorY, ceilY);
        buildFloor(level, random, box, floorY);
        buildColumns(level, random, box, floorY, ceilY);
        decorateWalls(level, random, box, floorY, ceilY);
        buildCeiling(level, random, box, ceilY);

        // 2) KORİDORLAR + YAN ODALAR (her yön ayrı; hepsi box'a kırpılı)
        for (Direction d : Direction.Plane.HORIZONTAL) {
            buildCorridor(level, random, box, floorY, d);
            buildSideRoom(level, random, box, floorY, d);
        }

        // 3) ALTAR + boss + salon sandıkları (salon içinde)
        BlockPos altarTop = buildAltar(level, random, box, floorY);
        placeChests(level, random, box, floorY);
        spawnBoss(level, box, altarTop);
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

    /** Konum kutu içinde mi ve hava mı? (dolu hücrelere dokunmamak için). */
    private boolean isAirInBox(WorldGenLevel level, BoundingBox box, BlockPos p) {
        return box.isInside(p) && level.getBlockState(p).isAir();
    }

    // ---------------------------------------------------------------------
    //  Kabuk + oyma (BOSS SALONU)
    // ---------------------------------------------------------------------

    private void carveAndShell(WorldGenLevel level, RandomSource random, BoundingBox box,
                               int floorY, int ceilY) {
        int wall = HALF + 1;
        for (int dx = -wall; dx <= wall; dx++) {
            for (int dz = -wall; dz <= wall; dz++) {
                boolean edge = Math.abs(dx) == wall || Math.abs(dz) == wall;
                for (int y = floorY; y <= ceilY; y++) {
                    BlockPos p = origin.offset(dx, y - origin.getY(), dz);
                    if (!box.isInside(p)) {
                        continue;
                    }
                    if (edge) {
                        if (y > floorY && y < ceilY) {
                            setClipped(level, box, p, wallBlock(random));
                        }
                    } else {
                        if (y > floorY && y < ceilY) {
                            setClipped(level, box, p, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    private static BlockState wallBlock(RandomSource random) {
        int roll = random.nextInt(12);
        if (roll < 6) {
            return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        }
        if (roll < 9) {
            return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        }
        if (roll < 11) {
            return Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        }
        return Blocks.CHISELED_DEEPSLATE.defaultBlockState();
    }

    // ---------------------------------------------------------------------
    //  Zemin (BOSS SALONU)
    // ---------------------------------------------------------------------

    private void buildFloor(WorldGenLevel level, RandomSource random, BoundingBox box, int floorY) {
        int r = HALF;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                BlockPos p = origin.offset(dx, floorY - origin.getY(), dz);
                if (!box.isInside(p)) {
                    continue;
                }
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                BlockState floor;
                if (ring == r - 1) {
                    boolean corner = Math.abs(dx) == r - 1 && Math.abs(dz) == r - 1;
                    floor = corner
                            ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                            : Blocks.WATER.defaultBlockState();
                } else if ((dx + dz) % 2 == 0) {
                    floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                } else {
                    floor = Blocks.DEEPSLATE_TILES.defaultBlockState();
                }
                int man = Math.abs(dx) + Math.abs(dz);
                if (man <= 3 && (man % 2 == 1)) {
                    floor = Blocks.GILDED_BLACKSTONE.defaultBlockState();
                } else if (man == 4 && random.nextInt(3) == 0) {
                    floor = Blocks.CHISELED_DEEPSLATE.defaultBlockState();
                }
                setClipped(level, box, p, floor);
            }
        }
    }

    // ---------------------------------------------------------------------
    //  Sütunlar (BOSS SALONU) — büyütülen salon için 8 sütun (4 köşe + 4 kenar orta)
    // ---------------------------------------------------------------------

    private void buildColumns(WorldGenLevel level, RandomSource random, BoundingBox box,
                              int floorY, int ceilY) {
        int off = HALF - 3;
        // 4 köşe + 4 kenar-orta = daha görkemli sütun dizilimi.
        int[][] spots = {
                { off, off }, { off, -off }, { -off, off }, { -off, -off },
                { off, 0 }, { -off, 0 }, { 0, off }, { 0, -off }
        };
        for (int[] s : spots) {
            int cx = s[0];
            int cz = s[1];
            for (int y = floorY + 1; y < ceilY; y++) {
                BlockPos p = origin.offset(cx, y - origin.getY(), cz);
                if (!box.isInside(p)) {
                    continue;
                }
                BlockState body = ((y - floorY) % 3 == 0)
                        ? Blocks.CHISELED_DEEPSLATE.defaultBlockState()
                        : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                setClipped(level, box, p, body);
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos base = origin.offset(cx + d.getStepX(), floorY + 1 - origin.getY(), cz + d.getStepZ());
                if (isAirInBox(level, box, base)) {
                    setClipped(level, box, base, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
                }
            }
            int capY = ceilY - 1;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos cap = origin.offset(cx + d.getStepX(), capY - origin.getY(), cz + d.getStepZ());
                if (isAirInBox(level, box, cap)) {
                    setClipped(level, box, cap, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, d)
                            .setValue(StairBlock.HALF, Half.TOP));
                }
            }
            BlockPos glow = origin.offset(cx, capY - origin.getY(), cz);
            setClipped(level, box, glow, Blocks.SEA_LANTERN.defaultBlockState());
            BlockPos cluster = origin.offset(cx, capY - 1 - origin.getY(), cz);
            if (isAirInBox(level, box, cluster)) {
                setClipped(level, box, cluster, Blocks.AMETHYST_CLUSTER.defaultBlockState()
                        .setValue(AmethystClusterBlock.FACING, Direction.DOWN));
            }
        }
    }

    // ---------------------------------------------------------------------
    //  Duvar dekorasyonu / aydınlatma (BOSS SALONU)
    // ---------------------------------------------------------------------

    private void decorateWalls(WorldGenLevel level, RandomSource random, BoundingBox box,
                               int floorY, int ceilY) {
        int inner = HALF;
        int lampY = floorY + (ceilY - floorY) / 2;
        for (int t = -HALF + 2; t <= HALF - 2; t += 4) {
            placeWallLight(level, box, t, lampY, -inner);
            placeWallLight(level, box, t, lampY, inner);
            placeWallLight(level, box, -inner, lampY, t);
            placeWallLight(level, box, inner, lampY, t);
        }
    }

    private void placeWallLight(WorldGenLevel level, BoundingBox box, int dx, int y, int dz) {
        BlockPos lanternPos = origin.offset(dx, y - origin.getY(), dz);
        BlockPos lampPos = origin.offset(dx, y + 1 - origin.getY(), dz);
        if (isAirInBox(level, box, lampPos)) {
            setClipped(level, box, lampPos, Blocks.SEA_LANTERN.defaultBlockState());
        }
        if (isAirInBox(level, box, lanternPos)) {
            setClipped(level, box, lanternPos, Blocks.SOUL_LANTERN.defaultBlockState()
                    .setValue(LanternBlock.HANGING, Boolean.TRUE));
        }
    }

    // ---------------------------------------------------------------------
    //  Tavan (BOSS SALONU: kubbe + oculus + sarkan zincirler)
    // ---------------------------------------------------------------------

    private void buildCeiling(WorldGenLevel level, RandomSource random, BoundingBox box, int ceilY) {
        int r = HALF;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                BlockPos p = origin.offset(dx, ceilY - origin.getY(), dz);
                if (!box.isInside(p)) {
                    continue;
                }
                int man = Math.abs(dx) + Math.abs(dz);
                BlockState roof;
                if (man <= 1) {
                    roof = Blocks.AMETHYST_BLOCK.defaultBlockState();
                } else if (man == 2) {
                    roof = Blocks.CHISELED_DEEPSLATE.defaultBlockState();
                } else if ((dx + dz) % 2 == 0) {
                    roof = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
                } else {
                    roof = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                }
                setClipped(level, box, p, roof);
            }
        }
        BlockPos oculus = origin.offset(0, ceilY - 1 - origin.getY(), 0);
        setClipped(level, box, oculus, Blocks.AMETHYST_CLUSTER.defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, Direction.DOWN));
        int off = HALF - 4;
        int[][] spots = { { off, off }, { off, -off }, { -off, off }, { -off, -off } };
        for (int[] s : spots) {
            for (int k = 1; k <= 2; k++) {
                BlockPos chain = origin.offset(s[0], ceilY - k - origin.getY(), s[1]);
                if (isAirInBox(level, box, chain)) {
                    setClipped(level, box, chain, VanillaBlocks.ironChain().defaultBlockState()
                            .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
                }
            }
            BlockPos hangLantern = origin.offset(s[0], ceilY - 3 - origin.getY(), s[1]);
            if (isAirInBox(level, box, hangLantern)) {
                setClipped(level, box, hangLantern, Blocks.SOUL_LANTERN.defaultBlockState()
                        .setValue(LanternBlock.HANGING, Boolean.TRUE));
            }
        }
    }

    // =====================================================================
    //  KORİDOR — maden temalı tünel (rail + oak destek arkları + cobweb + torch)
    // =====================================================================

    /**
     * Salonun bir duvarından (d yönü) dışa uzanan koridoru inşa eder. Koridorun
     * salona bağlandığı ağız açık kalır (geçiş). Koordinatlar d ekseni boyunca
     * "ileri" (fwd) ve dik eksen boyunca "yan" (side) ile parametrize edilir.
     *
     * <p>Yerel koordinat sistemi:
     * <ul>
     *   <li>fwd  = origin'den d yönünde ileri (1..CORRIDOR_LEN, salon iç duvarı HALF'te)</li>
     *   <li>side = d'ye dik yatay offset (-CORRIDOR_HW..CORRIDOR_HW)</li>
     * </ul>
     * Dünya offset'i: origin + d*fwd + perp*side (+ y).
     */
    private void buildCorridor(WorldGenLevel level, RandomSource random, BoundingBox box,
                               int floorY, Direction d) {
        Direction perp = d.getClockWise();
        int startF = HALF;                         // salon iç duvarı hizası (ağız burada)
        int endF = HALF + CORRIDOR_LEN;            // koridor dış ucu
        int topY = floorY + CORRIDOR_HEIGHT + 1;   // koridor tavan katı

        for (int f = startF; f <= endF; f++) {
            for (int side = -CORRIDOR_HW; side <= CORRIDOR_HW; side++) {
                boolean sideWall = Math.abs(side) == CORRIDOR_HW;
                for (int y = floorY; y <= topY; y++) {
                    BlockPos p = local(d, perp, f, side, y - origin.getY());
                    if (!box.isInside(p)) {
                        continue;
                    }
                    boolean floorLevel = (y == floorY);
                    boolean ceilLevel = (y == topY);
                    if (floorLevel) {
                        setClipped(level, box, p, mineFloor(random));
                    } else if (ceilLevel) {
                        setClipped(level, box, p, mineWall(random));
                    } else if (sideWall) {
                        setClipped(level, box, p, mineWall(random));
                    } else {
                        // İç boşluk: salon ağzı (f==startF) hariç HAVA aç.
                        // f==startF salon duvarı katmanıyla çakışır; orada da hava aç
                        // ki geçiş (kapı) oluşsun.
                        setClipped(level, box, p, Blocks.AIR.defaultBlockState());
                    }
                }
            }

            // --- Maden teması dekoru (yalnızca iç boşlukta) ---
            // Orta hatta düz ray.
            BlockPos railPos = local(d, perp, f, 0, floorY + 1 - origin.getY());
            if (isAirInBox(level, box, railPos)) {
                setClipped(level, box, railPos, railFor(d));
            }
            // Her 4 blokta bir mineshaft destek arkı: iki yanda fence direk + üstte plank kiriş.
            if (f > startF && (f - startF) % 4 == 0) {
                placeSupportArch(level, box, d, perp, f, floorY, topY);
            }
            // Seyrek cobweb (köşelerde) + araliklı tavan meşalesi.
            if ((f - startF) % 3 == 1) {
                BlockPos web = local(d, perp, f, CORRIDOR_HW - 1, floorY + CORRIDOR_HEIGHT - origin.getY());
                if (isAirInBox(level, box, web) && random.nextInt(2) == 0) {
                    setClipped(level, box, web, Blocks.COBWEB.defaultBlockState());
                }
                BlockPos web2 = local(d, perp, f, -(CORRIDOR_HW - 1), floorY + CORRIDOR_HEIGHT - origin.getY());
                if (isAirInBox(level, box, web2) && random.nextInt(2) == 0) {
                    setClipped(level, box, web2, Blocks.COBWEB.defaultBlockState());
                }
            }
            if ((f - startF) % 5 == 3) {
                // Duvara dayalı meşale (yan duvarın iç yüzüne).
                placeWallTorch(level, box, d, perp, f, floorY + CORRIDOR_HEIGHT - 1);
            }
        }
    }

    /** Koridor destek arkı: yan duvar diplerinde oak_fence direkler + tavanda oak_planks kiriş. */
    private void placeSupportArch(WorldGenLevel level, BoundingBox box, Direction d, Direction perp,
                                  int f, int floorY, int topY) {
        for (int sign = -1; sign <= 1; sign += 2) {
            int side = sign * (CORRIDOR_HW - 1);
            for (int y = floorY + 1; y < topY; y++) {
                BlockPos post = local(d, perp, f, side, y - origin.getY());
                if (isAirInBox(level, box, post)) {
                    setClipped(level, box, post, Blocks.OAK_FENCE.defaultBlockState());
                }
            }
        }
        // Üst kiriş: iki direk arasını plank ile bağla (topY-1 katında).
        int beamY = topY - 1;
        for (int side = -(CORRIDOR_HW - 1); side <= (CORRIDOR_HW - 1); side++) {
            BlockPos beam = local(d, perp, f, side, beamY - origin.getY());
            if (isAirInBox(level, box, beam)) {
                setClipped(level, box, beam, Blocks.OAK_PLANKS.defaultBlockState());
            }
        }
    }

    /** Koridor yönüne göre düz ray state'i. */
    private static BlockState railFor(Direction d) {
        RailShape shape = (d.getAxis() == Direction.Axis.Z) ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST;
        return Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape);
    }

    /**
     * Yan duvarın iç yüzüne dayalı meşale. Meşale, dayandığı bloktan UZAĞA bakar
     * (FACING = duvardan boşluğa doğru = -perp / +perp). Basitçe +perp yönündeki
     * duvara dayalı meşale koyar (FACING = perp).
     */
    private void placeWallTorch(WorldGenLevel level, BoundingBox box, Direction d, Direction perp,
                                int f, int torchY) {
        // +perp tarafındaki duvara dayalı meşale: meşale gövdesi (CORRIDOR_HW-1) hücresinde,
        // destek duvarı +perp yönünde. WALL_TORCH.FACING meşalenin BAKTIĞI (duvardan
        // boşluğa doğru = -perp) yönü olmalı; aksi halde destek yok sayılır ve meşale
        // havada asılı kalır/kopar. Bu yüzden perp.getOpposite().
        BlockPos t = local(d, perp, f, CORRIDOR_HW - 1, torchY - origin.getY());
        if (isAirInBox(level, box, t)) {
            setClipped(level, box, t, Blocks.WALL_TORCH.defaultBlockState()
                    .setValue(WallTorchBlock.FACING, perp.getOpposite()));
        }
    }

    // =====================================================================
    //  YAN ODA — koridor ucunda küçük loot odası
    // =====================================================================

    /**
     * Koridorun ucundan (endF = HALF+CORRIDOR_LEN) itibaren dışa uzanan yan odayı
     * inşa eder. Oda iç boşluğu [roomStartF..roomEndF] x [-ROOM_HW..ROOM_HW].
     * Koridorun odaya bağlandığı ağız açık kalır. İçinde 1 loot sandığı + maden
     * dekoru.
     */
    private void buildSideRoom(WorldGenLevel level, RandomSource random, BoundingBox box,
                               int floorY, Direction d) {
        Direction perp = d.getClockWise();
        int roomStartF = HALF + CORRIDOR_LEN + 1;
        int roomEndF = roomStartF + ROOM_DEPTH - 1;
        int topY = floorY + ROOM_HEIGHT + 1;

        for (int f = roomStartF - 1; f <= roomEndF; f++) {
            for (int side = -ROOM_HW; side <= ROOM_HW; side++) {
                boolean sideWall = Math.abs(side) == ROOM_HW;
                boolean endWall = (f == roomEndF);
                // Odaya giriş ağzı: koridor genişliği kadar (|side| <= CORRIDOR_HW) ve
                // en yakın kenar duvarında (f == roomStartF-1) açık bırak.
                boolean mouth = (f == roomStartF - 1) && Math.abs(side) <= CORRIDOR_HW;
                for (int y = floorY; y <= topY; y++) {
                    BlockPos p = local(d, perp, f, side, y - origin.getY());
                    if (!box.isInside(p)) {
                        continue;
                    }
                    boolean floorLevel = (y == floorY);
                    boolean ceilLevel = (y == topY);
                    boolean nearWall = (f == roomStartF - 1);
                    if (floorLevel) {
                        setClipped(level, box, p, mineFloor(random));
                    } else if (ceilLevel) {
                        setClipped(level, box, p, mineWall(random));
                    } else if (mouth) {
                        setClipped(level, box, p, Blocks.AIR.defaultBlockState());
                    } else if (sideWall || endWall || nearWall) {
                        setClipped(level, box, p, mineWall(random));
                    } else {
                        setClipped(level, box, p, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        // --- Oda dekoru + loot sandığı ---
        int centerF = roomStartF + ROOM_DEPTH / 2;
        // Orta hattı ray ile döşe (madene bağlanma hissi). centerF'i ATLA: sandık
        // oraya gelecek; ray konursa hücre dolar ve sandığın isAir() kontrolü
        // başarısız olur (sandık hiç yerleşmez). Bu yüzden centerF'te ray yok.
        for (int f = roomStartF; f <= roomEndF; f++) {
            if (f == centerF) {
                continue;
            }
            BlockPos railPos = local(d, perp, f, 0, floorY + 1 - origin.getY());
            if (isAirInBox(level, box, railPos)) {
                setClipped(level, box, railPos, railFor(d));
            }
        }
        // Cevher imalı bloklar (deepslate ore / tuff) — kenarlarda seyrek.
        placeOreHint(level, box, d, perp, roomStartF + 1, -(ROOM_HW - 1), floorY + 1, random);
        placeOreHint(level, box, d, perp, roomEndF - 1, (ROOM_HW - 1), floorY + 2, random);
        placeOreHint(level, box, d, perp, centerF, (ROOM_HW - 1), floorY + 1, random);
        // Cobweb köşelerde.
        BlockPos web = local(d, perp, roomStartF, ROOM_HW - 1, floorY + ROOM_HEIGHT - origin.getY());
        if (isAirInBox(level, box, web)) {
            setClipped(level, box, web, Blocks.COBWEB.defaultBlockState());
        }
        // Tavan asma lanterni (merkez).
        BlockPos lantern = local(d, perp, centerF, 0, topY - 1 - origin.getY());
        if (isAirInBox(level, box, lantern)) {
            setClipped(level, box, lantern, Blocks.LANTERN.defaultBlockState()
                    .setValue(LanternBlock.HANGING, Boolean.TRUE));
        }

        // Loot sandığı: oda merkezinin biraz gerisinde, zemin üstünde. YALNIZCA
        // kendi konumu box içinde + hücre boş iken yerleşir (çoklu sandık önlenir).
        BlockPos chestPos = local(d, perp, centerF, 0, floorY + 1 - origin.getY());
        if (box.isInside(chestPos) && level.getBlockState(chestPos).isAir()) {
            // Sandık salona doğru (d'nin tersi) baksın.
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState()
                    .setValue(ChestBlock.FACING, d.getOpposite()), 2);
            RandomizableContainer.setBlockEntityLootTable(level, random, chestPos, CHAMBER_LOOT);
        }
    }

    private void placeOreHint(WorldGenLevel level, BoundingBox box, Direction d, Direction perp,
                              int f, int side, int y, RandomSource random) {
        BlockPos p = local(d, perp, f, side, y - origin.getY());
        if (!box.isInside(p)) {
            return;
        }
        int roll = random.nextInt(3);
        BlockState ore = switch (roll) {
            case 0 -> Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
            case 1 -> Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
            default -> Blocks.TUFF.defaultBlockState();
        };
        setClipped(level, box, p, ore);
    }

    // ---------------------------------------------------------------------
    //  Maden temalı blok seçimleri (koridor/oda duvar+zemin)
    // ---------------------------------------------------------------------

    private static BlockState mineWall(RandomSource random) {
        int roll = random.nextInt(10);
        if (roll < 5) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        if (roll < 7) {
            return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        if (roll < 9) {
            return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        }
        return Blocks.DEEPSLATE.defaultBlockState();
    }

    private static BlockState mineFloor(RandomSource random) {
        int roll = random.nextInt(10);
        if (roll < 5) {
            return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        }
        if (roll < 8) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        return Blocks.TUFF.defaultBlockState();
    }

    /**
     * Yerel koridor/oda koordinatını dünya {@link BlockPos}'una çevirir.
     * origin + d*fwd + perp*side, y offset dyFromOrigin.
     */
    private BlockPos local(Direction d, Direction perp, int fwd, int side, int dyFromOrigin) {
        int dx = d.getStepX() * fwd + perp.getStepX() * side;
        int dz = d.getStepZ() * fwd + perp.getStepZ() * side;
        return origin.offset(dx, dyFromOrigin, dz);
    }

    // ---------------------------------------------------------------------
    //  Altar (boss podyumu) — BOSS SALONU
    // ---------------------------------------------------------------------

    private BlockPos buildAltar(WorldGenLevel level, RandomSource random, BoundingBox box, int floorY) {
        int platY = floorY + 1;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos p = origin.offset(dx, platY - origin.getY(), dz);
                if (!box.isInside(p)) {
                    continue;
                }
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                BlockState top = edge
                        ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                        : Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
                setClipped(level, box, p, top);
            }
        }
        int coreY = platY + 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = origin.offset(dx, coreY - origin.getY(), dz);
                if (!box.isInside(p)) {
                    continue;
                }
                boolean center = dx == 0 && dz == 0;
                setClipped(level, box, p, center
                        ? Blocks.AMETHYST_BLOCK.defaultBlockState()
                        : Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState()
                              .setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            }
        }
        BlockPos crown = origin.offset(0, coreY + 1 - origin.getY(), 0);
        setClipped(level, box, crown, Blocks.AMETHYST_CLUSTER.defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, Direction.UP));
        // Boss, altar üst yüzeyinin (coreY) hemen üstüne doğar.
        return origin.atY(coreY + 1);
    }

    // ---------------------------------------------------------------------
    //  Loot sandıkları (BOSS SALONU)
    // ---------------------------------------------------------------------

    private void placeChests(WorldGenLevel level, RandomSource random, BoundingBox box, int floorY) {
        int y = floorY + 1;
        int a = HALF - 2;
        int[] cx = { a, -a, a - 3 };
        int[] cz = { a - 3, -a + 3, -a };
        Direction[] facings = { Direction.WEST, Direction.EAST, Direction.NORTH };
        for (int i = 0; i < cx.length; i++) {
            BlockPos p = origin.offset(cx[i], y - origin.getY(), cz[i]);
            // Sandık yalnızca kendi konumu box içinde ve hücre boşken yerleşir.
            // Böylece kesişen komşu chunk'ta TEKRAR üretilmez (çoklu sandık önlenir).
            if (!box.isInside(p) || !level.getBlockState(p).isAir()) {
                continue;
            }
            level.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facings[i]), 2);
            RandomizableContainer.setBlockEntityLootTable(level, random, p, CHAMBER_LOOT);
        }
    }

    // ---------------------------------------------------------------------
    //  Boss spawn
    // ---------------------------------------------------------------------

    /**
     * Altar merkezine BİR adet Basilisk doğurur — YALNIZCA boss konumu bu chunk'ın
     * {@code box}'u içindeyken. postProcess her kesişen chunk için çağrıldığından
     * bu kontrol olmadan her chunk bir boss doğurur (ÇOKLU BOSS). Altar merkez
     * chunk'ta olduğundan boss tam olarak 1 kez doğar.
     */
    private void spawnBoss(WorldGenLevel level, BoundingBox box, BlockPos altarTop) {
        if (!box.isInside(altarTop)) {
            return;
        }
        ServerLevel serverLevel = level.getLevel();
        BasiliskEntity basilisk = ModEntities.BASILISK.get().create(serverLevel, EntitySpawnReason.STRUCTURE);
        if (basilisk == null) {
            return;
        }
        basilisk.snapTo(altarTop.getX() + 0.5, altarTop.getY(), altarTop.getZ() + 0.5, 0.0F, 0.0F);
        basilisk.setPersistenceRequired();
        basilisk.finalizeSpawn(level, level.getCurrentDifficultyAt(basilisk.blockPosition()),
                EntitySpawnReason.STRUCTURE, null);
        level.addFreshEntity(basilisk);
    }
}
