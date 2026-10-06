package com.arcanum.fabric;

import com.arcanum.registry.ModItems;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.world.item.Items;

/**
 * Vanilla loot tablolarına Arcanum enjeksiyonu (1.20.1 PORT: fabric-loot-api-v2 —
 * v3 1.21'e özgü; v2'de callback (resourceManager, lootManager, id, tableBuilder, source)
 * imzasını kullanır, tablo anahtarı ResourceKey değil ResourceLocation'dır).
 * Eski "tek-büyülük spell_book" dropları KALDIRILDI — büyü öğrenme artık yalnızca
 * Büyü Masası'nda (reagent + XP maliyetli) olduğu için tek-büyülük loot kitabı
 * anlamsızdı. Onların yerine tematik reagent + deneyim şişesi dolgusu konur;
 * Karanlık Büyü Kitabı (dark + unforgivable erişimi) yalnızca terk edilmiş maden
 * ocaklarında nadir kalır.
 */
public final class ArcanumLoot {
    private ArcanumLoot() {}

    public static void register() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, key, tableBuilder, source) -> {
            // Sadece vanilla/mod'un kendi tablolarını değiştir; datapack override'larına dokunma
            if (!source.isBuiltin()) return;

            if (BuiltInLootTables.ABANDONED_MINESHAFT.equals(key)
                    || BuiltInLootTables.SIMPLE_DUNGEON.equals(key)) {
                // %30 şansla basic/advanced reagent + deneyim şişesi (eski advanced kitap havuzunun yerine)
                tableBuilder.withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .when(LootItemRandomChanceCondition.randomChance(0.30F))
                        .add(reagent(ModItems.PRACTICE_CHALK, 1, 2).setWeight(4))
                        .add(reagent(ModItems.SPELL_DIAGRAM, 1, 2).setWeight(3))
                        .add(reagent(ModItems.MOONCALF_DUST, 1, 1).setWeight(3))
                        .add(experience(1, 2).setWeight(4)));
            // Başlangıç Büyü Kitabı zindan/madende de %20 (tester: "çıkma rate'ini arttır")
            tableBuilder.withPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .when(LootItemRandomChanceCondition.randomChance(0.20F))
                    .add(LootItem.lootTableItem(ModItems.STARTER_SPELL_BOOK.get())));
                if (BuiltInLootTables.ABANDONED_MINESHAFT.equals(key)) {
                    // Karanlık Büyü Kitabı — yalnızca terk edilmiş maden ocaklarında, %5 şansla
                    tableBuilder.withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1.0F))
                            .when(LootItemRandomChanceCondition.randomChance(0.05F))
                            .add(LootItem.lootTableItem(ModItems.DARK_SPELL_BOOK.get())));
                    // Seçmen Şapka — craftlanamaz, tek edinim yolu loot: maden ocağı %20
                    // (+ Hogsmeade bina sandıkları, bkz. data/arcanum/loot_tables/chests).
                    tableBuilder.withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1.0F))
                            .when(LootItemRandomChanceCondition.randomChance(0.20F))
                            .add(LootItem.lootTableItem(ModItems.SORTING_HAT.get())));
                }
            } else if (BuiltInLootTables.STRONGHOLD_LIBRARY.equals(key)) {
                // Kütüphane: %45 advanced reagent + deneyim şişesi
                tableBuilder.withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .when(LootItemRandomChanceCondition.randomChance(0.45F))
                        .add(reagent(ModItems.MOONCALF_DUST, 1, 2).setWeight(4))
                        .add(reagent(ModItems.ACROMANTULA_SILK, 1, 2).setWeight(3))
                        .add(experience(1, 3).setWeight(3)));
            // Başlangıç Kitabı kütüphanede %35 (tester istegi)
            tableBuilder.withPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .when(LootItemRandomChanceCondition.randomChance(0.35F))
                    .add(LootItem.lootTableItem(ModItems.STARTER_SPELL_BOOK.get())));
            } else if (BuiltInLootTables.ANCIENT_CITY.equals(key)) {
                // Antik şehir: %25 dark reagent + deneyim şişesi
                tableBuilder.withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .when(LootItemRandomChanceCondition.randomChance(0.25F))
                        .add(reagent(ModItems.BASILISK_FANG_SHARD, 1, 1).setWeight(6))
                        .add(experience(1, 3).setWeight(4)));
            } else if (BuiltInLootTables.VILLAGE_CARTOGRAPHER.equals(key)
                    || BuiltInLootTables.VILLAGE_TOOLSMITH.equals(key)
                    || BuiltInLootTables.VILLAGE_PLAINS_HOUSE.equals(key)
                    || BuiltInLootTables.VILLAGE_DESERT_HOUSE.equals(key)
                    || BuiltInLootTables.VILLAGE_SAVANNA_HOUSE.equals(key)
                    || BuiltInLootTables.VILLAGE_SNOWY_HOUSE.equals(key)
                    || BuiltInLootTables.VILLAGE_TAIGA_HOUSE.equals(key)) {
                // Köy sandığı (haritacı/aletçi): %45 şansla Başlangıç Büyü Kitabı — yeni
                // oyuncu ilk büyü kitabına köyden de ulaşabilsin (tek kaynak wizard köylü olmasın).
                tableBuilder.withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .when(LootItemRandomChanceCondition.randomChance(0.60F))
                        .add(LootItem.lootTableItem(ModItems.STARTER_SPELL_BOOK.get())));
            }
        });
    }

    /** Sayısı [min,max] aralığında olan bir reagent loot girdisi. */
    private static net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer.Builder<?>
            reagent(dev.architectury.registry.registries.RegistrySupplier<net.minecraft.world.item.Item> item,
                    int min, int max) {
        return LootItem.lootTableItem(item.get())
                .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)));
    }

    private static net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer.Builder<?>
            experience(int min, int max) {
        return LootItem.lootTableItem(Items.EXPERIENCE_BOTTLE)
                .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)));
    }
}
