package com.arcanum.registry;

import java.util.Optional;
import java.util.function.Function;

import com.arcanum.Arcanum;
import com.arcanum.block.ArcanewoodDoorBlock;
import com.arcanum.block.ArcanewoodLeavesBlock;
import com.arcanum.block.ArcanewoodSaplingBlock;
import com.arcanum.block.SpellTableBlock;
import com.arcanum.block.WardedDoorBlock;
import com.arcanum.mixin.FireBlockInvoker;
import com.arcanum.platform.Platform;
import com.arcanum.platform.registry.DeferredRegister;
import com.arcanum.platform.registry.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Arcanewood odun seti — sihirli mor ağaç. Asalar bu odunun planklarından yapılır.
 *
 * <p>26.x: her blok/blok-item fabrikası kayıt anahtarını alır ({@code key -> ...}) ve
 * {@code Properties.setId(key)} ÇAĞIRIR (1.21.2+ zorunlu). Blok item'ları
 * {@code useBlockDescriptionPrefix()} ile "block.arcanum.x" çeviri anahtarını korur.
 */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Arcanum.MODID, Registries.BLOCK);
    public static final DeferredRegister<Item> BLOCK_ITEMS = DeferredRegister.create(Arcanum.MODID, Registries.ITEM);

    /** Saplingin büyüttüğü configured feature (data/.../configured_feature/arcanewood_tree.json). */
    public static final ResourceKey<ConfiguredFeature<?, ?>> ARCANEWOOD_TREE =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, Arcanum.id("arcanewood_tree"));

    public static final TreeGrower ARCANEWOOD_GROWER = new TreeGrower(
            "arcanum:arcanewood", Optional.empty(), Optional.of(ARCANEWOOD_TREE), Optional.empty());

    public static final RegistrySupplier<Block> ARCANEWOOD_LOG = reg("arcanewood_log",
            key -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(2.0F).sound(SoundType.WOOD).setId(key)));

    public static final RegistrySupplier<Block> STRIPPED_ARCANEWOOD_LOG = reg("stripped_arcanewood_log",
            key -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_PURPLE).strength(2.0F).sound(SoundType.WOOD).setId(key)));

    public static final RegistrySupplier<Block> ARCANEWOOD_PLANKS = reg("arcanewood_planks",
            key -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(2.0F).sound(SoundType.WOOD).setId(key)));

    public static final RegistrySupplier<Block> ARCANEWOOD_LEAVES = reg("arcanewood_leaves",
            key -> new ArcanewoodLeavesBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN).strength(0.2F).randomTicks()
                    .sound(SoundType.GRASS).noOcclusion().setId(key)));

    /** 26.x: {@code SaplingBlock} kurucusu (Forge'da) protected → ince alt sınıf ({@link ArcanewoodSaplingBlock}). */
    public static final RegistrySupplier<Block> ARCANEWOOD_SAPLING = reg("arcanewood_sapling",
            key -> new ArcanewoodSaplingBlock(ARCANEWOOD_GROWER, BlockBehaviour.Properties.of()
                    .noCollision().randomTicks().instabreak()
                    .sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY).setId(key)));

    /**
     * Mühürlü Kapı — elle/redstone'la AÇILMAZ, yalnız Alohomora büyüsü açar
     * (bkz. WardedDoorBlock). Kapılar vanilla gibi DoubleHighBlockItem ister
     * (üst yarım hayalet blok kalmasın diye) — bu yüzden reg() helper'ı
     * KULLANILMAZ, item ayrıca elle kaydedilir.
     * <p>26.x: {@code ofFullCopy} kimliği KOPYALAMAZ → {@code setId(key)} kopyadan sonra da şart.
     */
    public static final RegistrySupplier<Block> WARDED_DOOR = BLOCKS.register("warded_door",
            key -> new WardedDoorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_DOOR)
                    .mapColor(MapColor.DEEPSLATE).strength(6.0F).noOcclusion().setId(key)));

    public static final RegistrySupplier<Item> WARDED_DOOR_ITEM = BLOCK_ITEMS.register("warded_door",
            key -> new DoubleHighBlockItem(WARDED_DOOR.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix()));

    public static final RegistrySupplier<Block> ARCANEWOOD_DOOR = reg("arcanewood_door",
            key -> new ArcanewoodDoorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(3.0F).sound(SoundType.WOOD)
                    .noOcclusion().pushReaction(PushReaction.DESTROY).setId(key)));

    // ---- Gloomwood biyomunun özel zemini ----

    /** Kasvet toprağı — gloomwood'un koyu alt katmanı. */
    public static final RegistrySupplier<Block> GLOOM_SOIL = reg("gloom_soil",
            key -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BLACK).strength(0.5F).sound(SoundType.ROOTED_DIRT).setId(key)));

    /** Kasvet çimi — üstü soluk teal parıltılı karanlık çim örtüsü. */
    public static final RegistrySupplier<Block> GLOOM_GRASS_BLOCK = reg("gloom_grass_block",
            key -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_GREEN).strength(0.6F).sound(SoundType.GRASS).setId(key)));

    // ---- Büyü Masası (Spell Table) ----

    /**
     * Büyü Masası — HogCraft'ın "diagon alley merchant" masasından portlanan
     * statik model (bkz. SpellTableBlock). Köylerde Wizard mesleğinin iş
     * istasyonu (POI) olarak kullanılır; büyü öğrenme etkileşimi ayrı bir
     * dilimde ele alınır. Enchanting Table'a benzer orta zorlukta bir tarifle
     * craftlanır (bkz. data/arcanum/recipe/spell_table.json).
     */
    public static final RegistrySupplier<Block> SPELL_TABLE = reg("spell_table",
            key -> new SpellTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(2.5F).sound(SoundType.WOOD).noOcclusion().setId(key)));

    private ModBlocks() {}

    private static RegistrySupplier<Block> reg(String name, Function<ResourceKey<Block>, Block> block) {
        RegistrySupplier<Block> b = BLOCKS.register(name, block);
        BLOCK_ITEMS.register(name,
                key -> new BlockItem(b.get(), new Item.Properties().setId(key).useBlockDescriptionPrefix()));
        return b;
    }

    public static void init() {
        BLOCKS.register();
        BLOCK_ITEMS.register();
        // Balta ile soyma (kök: Fabric StrippableBlockRegistry). Backend kuyruğa alır, doğru anda bağlar.
        Platform.get().registerStrippable(ARCANEWOOD_LOG, STRIPPED_ARCANEWOOD_LOG);
    }

    /**
     * Yanabilirlik (kök: Fabric {@code FlammableBlockRegistry} — aynı değerler, vanilla
     * {@code FireBlock#setFlammable(block, igniteOdds, burnOdds)} sırasıyla). Ortak invoker mixin'iyle
     * iki loader'da tek yol. {@code .get()} gerektirdiği için {@code Arcanum.commonSetup()} içinden çağrılır.
     */
    public static void registerFlammables() {
        FireBlockInvoker fire = (FireBlockInvoker) (FireBlock) Blocks.FIRE;
        fire.arcanum$setFlammable(ARCANEWOOD_LOG.get(), 5, 5);
        fire.arcanum$setFlammable(STRIPPED_ARCANEWOOD_LOG.get(), 5, 5);
        fire.arcanum$setFlammable(ARCANEWOOD_PLANKS.get(), 5, 20);
        fire.arcanum$setFlammable(ARCANEWOOD_LEAVES.get(), 30, 60);
    }
}
