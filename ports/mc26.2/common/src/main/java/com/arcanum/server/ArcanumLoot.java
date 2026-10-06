package com.arcanum.server;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.arcanum.registry.ModItems;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

/**
 * Vanilla loot tablolarına Arcanum enjeksiyonu (loader-bağımsız).
 * Eski "tek-büyülük spell_book" dropları KALDIRILDI — büyü öğrenme artık yalnızca
 * Büyü Masası'nda (reagent + XP maliyetli) olduğu için tek-büyülük loot kitabı
 * anlamsızdı. Onların yerine tematik reagent + deneyim şişesi dolgusu konur;
 * Karanlık Büyü Kitabı (dark + unforgivable erişimi) yalnızca terk edilmiş maden
 * ocaklarında nadir kalır.
 *
 * <p>Loader bağlama: Fabric {@code LootTableEvents.MODIFY} (yalnız {@code source.isBuiltin()} —
 * datapack override'larına dokunma) → {@code modify(key, tableBuilder::withPool)};
 * Forge {@code LootTableLoadEvent} → {@code modify(key, pool -> table.addPool(pool.build()))}.
 */
public final class ArcanumLoot {
    private ArcanumLoot() {}

    /**
     * @param key     yüklenen loot tablosunun anahtarı
     * @param addPool tabloya yeni bir havuz ekleyen loader köprüsü (çağrı sırası = havuz sırası)
     */
    public static void modify(ResourceKey<LootTable> key, Consumer<LootPool.Builder> addPool) {
        if (BuiltInLootTables.ABANDONED_MINESHAFT.equals(key)
                || BuiltInLootTables.SIMPLE_DUNGEON.equals(key)) {
            // %30 şansla basic/advanced reagent + deneyim şişesi (eski advanced kitap havuzunun yerine)
            addPool.accept(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .when(LootItemRandomChanceCondition.randomChance(0.30F))
                    .add(reagent(ModItems.PRACTICE_CHALK, 1, 2).setWeight(4))
                    .add(reagent(ModItems.SPELL_DIAGRAM, 1, 2).setWeight(3))
                    .add(reagent(ModItems.MOONCALF_DUST, 1, 1).setWeight(3))
                    .add(experience(1, 2).setWeight(4)));
            // Başlangıç Büyü Kitabı zindan/madende de %20 (tester: "çıkma rate'ini arttır")
            addPool.accept(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .when(LootItemRandomChanceCondition.randomChance(0.20F))
                    .add(LootItem.lootTableItem(ModItems.STARTER_SPELL_BOOK.get())));
            if (BuiltInLootTables.ABANDONED_MINESHAFT.equals(key)) {
                // Karanlık Büyü Kitabı — yalnızca terk edilmiş maden ocaklarında, %5 şansla
                addPool.accept(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .when(LootItemRandomChanceCondition.randomChance(0.05F))
                        .add(LootItem.lootTableItem(ModItems.DARK_SPELL_BOOK.get())));
                // Seçmen Şapka — craftlanamaz, tek edinim yolu loot: maden ocağı %20
                // (+ Hogsmeade bina sandıkları, bkz. data/arcanum/loot_table/chests).
                addPool.accept(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .when(LootItemRandomChanceCondition.randomChance(0.20F))
                        .add(LootItem.lootTableItem(ModItems.SORTING_HAT.get())));
            }
        } else if (BuiltInLootTables.STRONGHOLD_LIBRARY.equals(key)) {
            // Kütüphane: %45 advanced reagent + deneyim şişesi + %35 Başlangıç Kitabı
            addPool.accept(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .when(LootItemRandomChanceCondition.randomChance(0.45F))
                    .add(reagent(ModItems.MOONCALF_DUST, 1, 2).setWeight(4))
                    .add(reagent(ModItems.ACROMANTULA_SILK, 1, 2).setWeight(3))
                    .add(experience(1, 3).setWeight(3)));
            addPool.accept(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .when(LootItemRandomChanceCondition.randomChance(0.35F))
                    .add(LootItem.lootTableItem(ModItems.STARTER_SPELL_BOOK.get())));
        } else if (BuiltInLootTables.ANCIENT_CITY.equals(key)) {
            // Antik şehir: %25 dark reagent + deneyim şişesi
            addPool.accept(LootPool.lootPool()
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
            // Köy sandıkları (haritacı/aletçi + TÜM ev tipleri): %60 Başlangıç Büyü Kitabı —
            // tester "köy chestlerinde çıksın, rate artsın" dedi; ilk kitap kolay bulunur olsun.
            addPool.accept(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .when(LootItemRandomChanceCondition.randomChance(0.60F))
                    .add(LootItem.lootTableItem(ModItems.STARTER_SPELL_BOOK.get())));
        }
    }

    /** Sayısı [min,max] aralığında olan bir reagent loot girdisi. */
    private static LootPoolSingletonContainer.Builder<?> reagent(Supplier<? extends Item> item, int min, int max) {
        return LootItem.lootTableItem(item.get())
                .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)));
    }

    private static LootPoolSingletonContainer.Builder<?> experience(int min, int max) {
        return LootItem.lootTableItem(Items.EXPERIENCE_BOTTLE)
                .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)));
    }
}
