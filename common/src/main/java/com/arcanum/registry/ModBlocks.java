package com.arcanum.registry;

import java.util.Optional;
import java.util.function.Supplier;

import com.arcanum.Arcanum;
import com.arcanum.block.ArcanewoodDoorBlock;
import com.arcanum.block.ArcanewoodLeavesBlock;
import com.arcanum.block.SpellTableBlock;
import com.arcanum.block.WardedDoorBlock;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Arcanewood odun seti — sihirli mor ağaç. Asalar bu odunun planklarından yapılır.
 */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Arcanum.MODID, Registries.BLOCK);
    public static final DeferredRegister<Item> BLOCK_ITEMS = DeferredRegister.create(Arcanum.MODID, Registries.ITEM);

    /** Saplingin büyüttüğü configured feature (data/.../configured_feature/arcanewood_tree.json). */
    public static final ResourceKey<ConfiguredFeature<?, ?>> ARCANEWOOD_TREE =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "arcanewood_tree"));

    public static final TreeGrower ARCANEWOOD_GROWER = new TreeGrower(
            "arcanum:arcanewood", Optional.empty(), Optional.of(ARCANEWOOD_TREE), Optional.empty());

    public static final RegistrySupplier<Block> ARCANEWOOD_LOG = reg("arcanewood_log",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(2.0F).sound(SoundType.WOOD)));

    public static final RegistrySupplier<Block> STRIPPED_ARCANEWOOD_LOG = reg("stripped_arcanewood_log",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_PURPLE).strength(2.0F).sound(SoundType.WOOD)));

    public static final RegistrySupplier<Block> ARCANEWOOD_PLANKS = reg("arcanewood_planks",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(2.0F).sound(SoundType.WOOD)));

    public static final RegistrySupplier<Block> ARCANEWOOD_LEAVES = reg("arcanewood_leaves",
            () -> new ArcanewoodLeavesBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN).strength(0.2F).randomTicks()
                    .sound(SoundType.GRASS).noOcclusion()));

    public static final RegistrySupplier<Block> ARCANEWOOD_SAPLING = reg("arcanewood_sapling",
            () -> new SaplingBlock(ARCANEWOOD_GROWER, BlockBehaviour.Properties.of()
                    .noCollission().randomTicks().instabreak()
                    .sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY)));

    /**
     * Mühürlü Kapı — elle/redstone'la AÇILMAZ, yalnız Alohomora büyüsü açar
     * (bkz. WardedDoorBlock). Kapılar vanilla gibi DoubleHighBlockItem ister
     * (üst yarım hayalet blok kalmasın diye) — bu yüzden reg() helper'ı
     * KULLANILMAZ, item ayrıca elle kaydedilir.
     */
    public static final RegistrySupplier<Block> WARDED_DOOR = BLOCKS.register("warded_door",
            () -> new WardedDoorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_DOOR)
                    .mapColor(MapColor.DEEPSLATE).strength(6.0F).noOcclusion()));

    public static final RegistrySupplier<Item> WARDED_DOOR_ITEM = BLOCK_ITEMS.register("warded_door",
            () -> new DoubleHighBlockItem(WARDED_DOOR.get(), new Item.Properties()));

    public static final RegistrySupplier<Block> ARCANEWOOD_DOOR = reg("arcanewood_door",
            () -> new ArcanewoodDoorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(3.0F).sound(SoundType.WOOD)
                    .noOcclusion().pushReaction(PushReaction.DESTROY)));

    // ---- Gloomwood biyomunun özel zemini ----

    /** Kasvet toprağı — gloomwood'un koyu alt katmanı. */
    public static final RegistrySupplier<Block> GLOOM_SOIL = reg("gloom_soil",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BLACK).strength(0.5F).sound(SoundType.ROOTED_DIRT)));

    /** Kasvet çimi — üstü soluk teal parıltılı karanlık çim örtüsü. */
    public static final RegistrySupplier<Block> GLOOM_GRASS_BLOCK = reg("gloom_grass_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_GREEN).strength(0.6F).sound(SoundType.GRASS)));

    // ---- Büyü Masası (Spell Table) ----

    /**
     * Büyü Masası — HogCraft'ın "diagon alley merchant" masasından portlanan
     * statik model (bkz. SpellTableBlock). Köylerde Wizard mesleğinin iş
     * istasyonu (POI) olarak kullanılır; büyü öğrenme etkileşimi ayrı bir
     * dilimde ele alınır. Enchanting Table'a benzer orta zorlukta bir tarifle
     * craftlanır (bkz. data/arcanum/recipe/spell_table.json).
     */
    public static final RegistrySupplier<Block> SPELL_TABLE = reg("spell_table",
            () -> new SpellTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(2.5F).sound(SoundType.WOOD).noOcclusion()));

    private ModBlocks() {}

    private static RegistrySupplier<Block> reg(String name, Supplier<Block> block) {
        RegistrySupplier<Block> b = BLOCKS.register(name, block);
        BLOCK_ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties()));
        return b;
    }

    public static void init() {
        BLOCKS.register();
        BLOCK_ITEMS.register();
    }
}
